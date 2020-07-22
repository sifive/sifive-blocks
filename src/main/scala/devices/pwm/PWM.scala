// See LICENSE for license details.
package sifive.blocks.devices.pwm

import Chisel.{defaultCompileOptions => _, _}
import freechips.rocketchip.util.CompileOptions.NotStrictInferReset
import Chisel.ImplicitConversions._
import chisel3.MultiIOModule

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import freechips.rocketchip.diplomaticobjectmodel.model.{OMComponent, OMRegister}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalModuleTree, LogicalTreeNode}

import sifive.blocks.util._

// Core PWM Functionality  & Register Interface
class PWMTimer(val ncmp: Int = 4, val cmpWidth: Int = 16, val prefix: String = "pwm") extends MultiIOModule with GenericTimer {

  def orR(v: Vec[Bool]): Bool = v.foldLeft(Bool(false))( _||_ )
  protected def countWidth = ((1 << scaleWidth) - 1) + cmpWidth
  protected lazy val countAlways = RegEnable(io.regs.cfg.write.countAlways, Bool(false), io.regs.cfg.write_countAlways && unlocked)
  protected lazy val feed = count.carryOut(scale + UInt(cmpWidth))
  protected lazy val countEn = Wire(Bool())
  override protected lazy val oneShot = RegEnable(io.regs.cfg.write.running && !countReset, Bool(false), (io.regs.cfg.write_running && unlocked) || countReset)
  override protected lazy val extra: Vec[Bool]  = RegEnable(io.regs.cfg.write.extra, init = Vec.fill(maxcmp){false.B}, orR(io.regs.cfg.write_extra) && unlocked)
  override protected lazy val center: Vec[Bool] = RegEnable(io.regs.cfg.write.center, orR(io.regs.cfg.write_center) && unlocked)
  override protected lazy val gang: Vec[Bool] = RegEnable(io.regs.cfg.write.gang, orR(io.regs.cfg.write_gang) && unlocked)
  override protected lazy val deglitch = RegEnable(io.regs.cfg.write.deglitch, io.regs.cfg.write_deglitch && unlocked)(0)
  override protected lazy val sticky = RegEnable(io.regs.cfg.write.sticky, io.regs.cfg.write_sticky && unlocked)(0)
  override protected lazy val ip = {
    val doSticky = Reg(next = (deglitch && !countReset) || sticky)
    val sel = (0 until ncmp).map(i => s(cmpWidth-1) && center(i))
    val reg = Reg(Vec(ncmp, Bool()))
    reg := (sel & elapsed) | (~sel & (elapsed | (Vec.fill(ncmp){doSticky} & reg)))
    when (orR(io.regs.cfg.write_ip) && unlocked) { reg := io.regs.cfg.write.ip }
    reg
  }

  override protected lazy val feed_desc = RegFieldDesc.reserved
  override protected lazy val key_desc = RegFieldDesc.reserved
  override protected lazy val cfg_desc = DefaultGenericTimerCfgDescs(prefix, ncmp).copy(
    extra = Seq.tabulate(ncmp){ i => RegFieldDesc(s"${prefix}invert${i}", s"Invert Comparator ${i} Output", reset = Some(0))}
  )

  lazy val io = IO(new GenericTimerIO(regWidth, ncmp, maxcmp, scaleWidth, countWidth, cmpWidth) {
    val gpio = Vec(ncmp, Bool()).asOutput
  })

  val invert = extra.asUInt

  val ipU = ip.asUInt
  val gangU = gang.asUInt

  io.gpio := io.gpio.fromBits((ipU & ~(gangU & Cat(ipU(0), ipU >> 1))) ^ invert)
  countEn := countAlways || oneShot
}

case class PWMParams(
  address: BigInt,
  size: Int = 0x1000,
  regBytes: Int = 4,
  ncmp: Int = 4,
  cmpWidth: Int = 16) extends DeviceParams

class PWMPortIO(val c: PWMParams) extends Bundle {
  val gpio = Vec(c.ncmp, Bool()).asOutput
}

abstract class PWM(busWidthBytes: Int, val params: PWMParams)(implicit p: Parameters)
    extends IORegisterRouter(
      RegisterRouterParams(
        name = "pwm",
        compat = Seq("sifive,pwm0"),
        base = params.address,
        size = params.size,
        beatBytes = busWidthBytes),
      new PWMPortIO(params))
    with HasInterruptSources {

  def nInterrupts = params.ncmp
  override def extraResources(resources: ResourceBindings) = Map[String, Seq[ResourceValue]](
    "sifive,comparator-widthbits" -> Seq(ResourceInt(params.cmpWidth)),
    "sifive,ncomparators" -> Seq(ResourceInt(params.ncmp))
    )

  lazy val module = new LazyModuleImp(this) {
    val pwm = Module(new PWMTimer(params.ncmp, params.cmpWidth, "pwm"))
    interrupts := pwm.io.ip
    port.gpio := pwm.io.gpio
    //regmap((GenericTimer.timerRegMap(pwm, 0, params.regBytes)):_*)
    val mapping = (GenericTimer.timerRegMap(pwm, 0, params.regBytes))
    regmap(mapping:_*)
    val omRegMap = OMRegister.convert(mapping:_*)
  }
  val logicalTreeNode = new LogicalTreeNode(() => Some(device)) {
    def getOMComponents(resourceBindings: ResourceBindings, children: Seq[OMComponent] = Nil): Seq[OMComponent] = {
      Seq(
        OMPWM(
          numComparators = params.ncmp,
          compareWidth   = params.cmpWidth,
          //TODO CHECK FOR REG BINDINGS memoryRegions = DiplomaticObjectModelAddressing.getOMMemoryRegions("UART", resourceBindings, Some(omRegMap)),
          memoryRegions = DiplomaticObjectModelAddressing.getOMMemoryRegions("PWM", resourceBindings, Some(module.omRegMap)),
          interrupts = DiplomaticObjectModelAddressing.describeGlobalInterrupts(device.describe(resourceBindings).name, resourceBindings),
        )
      )
    }
  }

}

class TLPWM(busWidthBytes: Int, params: PWMParams)(implicit p: Parameters)
  extends PWM(busWidthBytes, params) with HasTLControlRegMap

case class PWMLocated(loc: HierarchicalLocation) extends Field[Seq[PWMAttachParams]](Nil)

case class PWMAttachParams(
  device: PWMParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLPWM = {
    val name = s"pwm_${PWM.nextId()}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val pwmClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val pwm = pwmClockDomainWrapper { LazyModule(new TLPWM(tlbus.beatBytes, device)) }
    pwm.suggestName(name)

    tlbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus) := _ }
        blocker
      }

      pwmClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          tlbus.dtsClk.map(_.bind(pwm.device))
          tlbus.fixedClockNode
        case _: RationalCrossing =>
          tlbus.clockNode
        case _: AsynchronousCrossing =>
          val pwmClockGroup = ClockGroup()
          pwmClockGroup := where.asyncClockGroupsNode
          blockerOpt.map { _.clockNode := pwmClockGroup } .getOrElse { pwmClockGroup }
      })

      (pwm.controlXing(controlXType)
        := TLFragmenter(tlbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := pwm.intXing(intXType)

    LogicalModuleTree.add(where.logicalTreeNode, pwm.logicalTreeNode)

    pwm
  }
}

object PWM {
  val nextId = { var i = -1; () => { i += 1; i} }

  def makePort(node: BundleBridgeSource[PWMPortIO], name: String)(implicit p: Parameters): ModuleValue[PWMPortIO] = {
    val pwmNode = node.makeSink()
    InModuleBody { pwmNode.makeIO()(ValName(name)) }
  }
}
