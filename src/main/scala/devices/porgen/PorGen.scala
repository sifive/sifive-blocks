// See LICENSE for license details.
package sifive.blocks.devices.porgen

import Chisel._
import chisel3.{Input,Output,dontTouch} //Parameterized black box
import chisel3.experimental.IO
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

// Ideally, we want SOFT(configurable) reset inputs like this. 
// case class PorResetGenParams(
//   address: BigInt
//   includeExtButtonReset: Boolean = true
//   includeExtPowerReset: Boolean = true
//   includeWDTReset: Boolean = true
//   includeDebugReset: Boolean = true  
// ) extends DeviceParams {
//   require(includeExtButtonReset == true)
// }

case class PorGenParams(
  address: BigInt,

) extends DeviceParams

class PorGenPortIO(val c : PorGenParams) extends Bundle {
  //  Button. Active LOW Async reset and need to be debounced abd synchronized with hfclk
  val ereset_n      = Input(Bool())
  //  External PMIC. Active LOW. Async to hfclkin and causes aysnchronous assert of por_reset
  val poreset_n      = Input(Bool())
  // PERSTn. ACTIVE LOW
  val perstn    = Input(Bool())
}

class PorGenPinIO(val c : PorGenParams) extends Bundle {
  // Input clock source for synchrnonizers
  val hfclk = Input(Clock())

  // Watchdog reset. This could be parametrized in case there are n watchdogs
  val wdt_rst = Input(Bool())

  // Debug module ndreset
  val ndreset = Input(Bool())

  // Internal on chip power good indicator
  val powerGood = Input(Bool())
  
  // Active high POR reset out of the reset controller
  val async_por_reset    = Output(Bool())
}

class PorGenModuleImp(outer: PorGen) extends LazyModuleImp(outer) {
  val port = outer.port
  val pin = outer.pin.bundle

  val por_reset = ~port.poreset_n | ~pin.powerGood

  //----Reset Control Logic-----//
  // eresetn button is synchrnoized to hfclk and debounced with a counter
  // poreset inputs are not synchrnoized - directly asserts async_por_reset
  // There was no WDT reset  
  // ndreset from the debug module is also not synchrnoized - directly asserts async_por_reset

  //-------- Features missing compared to PRCI ---------- //
  // Missing Reset bypass function

  //-------- Other features we might want to include ----- //
  // Missing PCie Function level reset
  // Missing Software control of reset

  val ereset_catch = ResetCatchAndSync(pin.hfclk, ~port.ereset_n, name = "e_reset_sync")
  // Copied - AsyncDownCounter from federation to sifive-blocks.util package for debounce counter
  val ereset_debouncer = Module(new AsyncDownCounter(
    clock = pin.hfclk,
    reset = ereset_catch,
    value = 255))
  ereset_debouncer.suggestName("ereset_debouncer")
  val ereset = ~ereset_debouncer.io.done

  // Note Asyncdowncounter counts to 0 and stops so is one shot already
  val downctr = Module(new AsyncDownCounter(
    clock = pin.hfclk,
    reset = port.perstn | ~pin.powerGood,
    value = 255
  ))
  downctr.suggestName("oneshotdownctr")
  val trig = ~downctr.io.done

  val perstn_reset = ~(trig | port.perstn)

  // Need to double check the polarity of ereset in sims
  pin.async_por_reset :=  por_reset | pin.wdt_rst | pin.ndreset | ereset | perstn_reset

  // This is done directly with the test mode reset so not sure if we want a passthrough.
  // io.jtag_reset := io.trst_n
}

abstract class PorGen(busWidthBytes: Int, val c: PorGenParams)(implicit p: Parameters) extends IORegisterRouter(
  RegisterRouterParams(
    name = "poweronresetgen",
    compat = Seq("sifive, porgen0"),
    base = c.address,
    beatBytes = busWidthBytes),
  new PorGenPortIO(c)) {

  val pin: BundleBridgeSource[PorGenPinIO] = BundleBridgeSource(() => new PorGenPinIO(c))
  lazy val module = new PorGenModuleImp(this)
}

class TLPorGen(busWidthBytes: Int, params: PorGenParams)(implicit p: Parameters)
  extends PorGen(busWidthBytes, params) with HasTLControlRegMap

case class PorGenLocated(loc: HierarchicalLocation) extends Field[Seq[PorGenAttachParams]](Nil)

case class PorGenAttachParams (
  device: PorGenParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLPorGen = where {
    val name = s"por_${PorGen.nextId()}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val porgenClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val porgen = porgenClockDomainWrapper { LazyModule(new TLPorGen(tlbus.beatBytes, device)) }
    porgen.suggestName(name)

    tlbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus) := _ }
        blocker
      }

      porgenClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          tlbus.dtsClk.map(_.bind(porgen.device))
          tlbus.fixedClockNode
        case _: RationalCrossing =>
          tlbus.clockNode
        case _: AsynchronousCrossing =>
          val porgenClockGroup = ClockGroup()
          porgenClockGroup := where.asyncClockGroupsNode
          blockerOpt.map { _.clockNode := porgenClockGroup } .getOrElse { porgenClockGroup }
      })

      (porgen.controlXing(controlXType)
        := TLFragmenter(tlbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    // (intXType match {
    //   case _: SynchronousCrossing => where.ibus.fromSync
    //   case _: RationalCrossing => where.ibus.fromRational
    //   case _: AsynchronousCrossing => where.ibus.fromAsync
    // }) := porgen.intXing(intXType)

    porgen
  }
}

object PorGen {
  val nextId = { var i = -1; () => { i += 1; i} }

  def makePort(node: BundleBridgeSource[PorGenPortIO], name: String)(implicit p: Parameters): ModuleValue[PorGenPortIO] = {
    val porNode = node.makeSink()
    InModuleBody { porNode.makeIO()(ValName(name)) }
  }

  def makePin(node: BundleBridgeSource[PorGenPinIO], name: String)(implicit p: Parameters): ModuleValue[PorGenPinIO] = {
    val porNode = node.makeSink()
    InModuleBody { porNode.makeIO()(ValName(name)) }
  }

  def tieoffPort(port: PorGenPortIO) {
    port.poreset_n := true.B
    port.ereset_n := true.B
  }
}
