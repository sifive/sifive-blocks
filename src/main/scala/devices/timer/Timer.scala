// See LICENSE for license details.
package sifive.blocks.devices.timer

import Chisel.{defaultCompileOptions => _, _}
import freechips.rocketchip.util.CompileOptions.NotStrictInferReset

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper.{RegisterRouter, RegisterRouterParams}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import freechips.rocketchip.diplomaticobjectmodel.model.{OMComponent, OMRegister}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalModuleTree, LogicalTreeNode}

import sifive.blocks.util._
import sifive.blocks.devices.pwm._

case class TimerParams(
  address: BigInt,
  size: Int = 0x1000,
  regBytes: Int = 4,
  cmpWidth: Int = 16) extends DeviceParams

case object PeripheryTimerKey extends Field[Seq[TimerParams]] (Nil)

// It's not really worth stripping away the PWM-specific parts of a timer
// as far as I'm concerned. Just override it to make it clear that there are
// no I/Os (that's basically the difference between PWM and timer).
class Timer(w: Int, c: TimerParams)(implicit p: Parameters)
    extends RegisterRouter(
      RegisterRouterParams(
        name = "timer",
        compat = Seq("sifive,timer0"),
        base = c.address,
        size = c.size,
        beatBytes = w))
    with HasInterruptSources with HasTLControlRegMap {

  def nInterrupts: Int = 1
  lazy val module = new LazyModuleImp(this) {
    val timer = Module(new PWMTimer(1, c.cmpWidth, "timer"))
    interrupts := timer.io.ip
    val mapping = (GenericTimer.timerRegMap(timer, 0, c.regBytes))
    regmap(mapping:_*)
    val omRegMap = OMRegister.convert(mapping:_*)  
  }
  val logicalTreeNode = new LogicalTreeNode(() => Some(device)) {
    def getOMComponents(resourceBindings: ResourceBindings, children: Seq[OMComponent] = Nil): Seq[OMComponent] = {
      Seq(
        OMTimer(
          comparatorWidthBits = c.cmpWidth,
          memoryRegions = DiplomaticObjectModelAddressing.getOMMemoryRegions("Timer", resourceBindings, Some(module.omRegMap)),
          interrupts = DiplomaticObjectModelAddressing.describeGlobalInterrupts(device.describe(resourceBindings).name, resourceBindings)
        )
      )
    }
  }
}

trait HasPeripheryTimer { this: BaseSubsystem =>
  val timerParams = p(PeripheryTimerKey)
  val timers = timerParams map { params =>
    val timer = LazyModule(new Timer(cbus.beatBytes, params))
    cbus.coupleTo(s"slave_named_timer") {
      timer.controlXing(NoCrossing) := TLFragmenter(cbus) := _
    }

    ibus.fromSync := timer.intXing(NoCrossing)
    InModuleBody { timer.module.clock := cbus.module.clock }

    timer
  }
}

case class TimerLocated(loc: HierarchicalLocation) extends Field[Seq[TimerAttachParams]](Nil)

case class TimerAttachParams(
  device: TimerParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): Timer = where {
    val name = s"timer_${TimerDevice.nextId()}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val timerClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val timer = timerClockDomainWrapper { LazyModule(new Timer(tlbus.beatBytes, device)) }
    timer.suggestName(name)

    tlbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus) := _ }
        blocker
      }

      timerClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          tlbus.dtsClk.map(_.bind(timer.device))
          tlbus.fixedClockNode
        case _: RationalCrossing =>
          tlbus.clockNode
        case _: AsynchronousCrossing =>
          val timerClockGroup = ClockGroup()
          timerClockGroup := where.asyncClockGroupsNode
          blockerOpt.map { _.clockNode := timerClockGroup } .getOrElse { timerClockGroup }
      })

      (timer.controlXing(controlXType)
        := TLFragmenter(tlbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := timer.intXing(intXType)

    LogicalModuleTree.add(where.logicalTreeNode, timer.logicalTreeNode)

    timer
  }
}

object TimerDevice { // This anti-pattern name is needed because Timer is a Chisel keyword
  val nextId = { var i = -1; () => { i += 1; i} }

}
