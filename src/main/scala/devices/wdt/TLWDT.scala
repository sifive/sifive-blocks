// See LICENSE for license details.
package sifive.blocks.devices.wdt

import Chisel._
import Chisel.ImplicitConversions._
import chisel3.MultiIOModule

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import freechips.rocketchip.diplomaticobjectmodel.model.{OMComponent, OMRegister}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalModuleTree, LogicalTreeNode}

import sifive.blocks.util.{BasicBusBlocker, GenericTimer, GenericTimerIO, DefaultGenericTimerCfgDescs}
import sifive.blocks.devices.mockaon.WatchdogTimer

case class WDTParams(
  address: BigInt,
  size: Int = 0x1000,
  regBytes: Int = 4)

class WDTPortIO(val c: WDTParams) extends Bundle {
  val corerst = Bool(INPUT)
  val rst = Bool(OUTPUT)
}

abstract class WDT(busWidthBytes: Int, val params: WDTParams)(implicit p: Parameters)
    extends IORegisterRouter(
      RegisterRouterParams(
        name = "wdt",
        compat = Seq("sifive,wdt0"),
        base = params.address,
        size = params.size,
        beatBytes = busWidthBytes),
      new WDTPortIO(params))
    with HasInterruptSources {

  def nInterrupts: Int = 1

  lazy val module = new LazyModuleImp(this) {
    val wdt = Module(new WatchdogTimer())
    interrupts := wdt.io.ip
    port.rst := wdt.io.rst
    wdt.io.corerst := port.corerst
    //regmap((GenericTimer.timerRegMap(wdt, 0, params.regBytes)):_*)
    val mapping = (GenericTimer.timerRegMap(wdt, 0, params.regBytes))
    regmap(mapping:_*)
    val omRegMap = OMRegister.convert(mapping:_*)  
  }

  val logicalTreeNode = new LogicalTreeNode(() => Some(device)) {
    def getOMComponents(resourceBindings: ResourceBindings, children: Seq[OMComponent] = Nil): Seq[OMComponent] = {
      Seq(
        OMWDT(
          memoryRegions = DiplomaticObjectModelAddressing.getOMMemoryRegions("WDT", resourceBindings, Some(module.omRegMap)),
          interrupts = DiplomaticObjectModelAddressing.describeGlobalInterrupts(device.describe(resourceBindings).name, resourceBindings)
        )
      )
    }
  }

}

class TLWDT(busWidthBytes: Int, params: WDTParams)(implicit p: Parameters)
  extends WDT(busWidthBytes, params) with HasTLControlRegMap

case class WDTAttachParams(
  wdt: WDTParams,
  controlBus: TLBusWrapper,
  intNode: IntInwardNode,
  blockerAddr: Option[BigInt] = None,
  mclock: Option[ModuleValue[Clock]] = None,
  mreset: Option[ModuleValue[Bool]] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing,
  clockDev: Option[FixedClockResource] = None,
  parentLogicalTreeNode: Option[LogicalTreeNode] = None)
  (implicit val p: Parameters)

object WDT {
  val nextId = { var i = -1; () => { i += 1; i} }

  def attach(params: WDTAttachParams): TLWDT = {
    implicit val p = params.p
    val name = s"wdt_${nextId()}"
    val cbus = params.controlBus
    val wdt = LazyModule(new TLWDT(cbus.beatBytes, params.wdt))
    wdt.suggestName(name)
    cbus.coupleTo(s"device_named_$name") { bus =>
      val blockerNode = params.blockerAddr.map(BasicBusBlocker(_, cbus, cbus.beatBytes, name))
      (wdt.controlXing(params.controlXType)
        := TLFragmenter(cbus)
        := blockerNode.map { _ := bus } .getOrElse { bus })
    }
    params.intNode := wdt.intXing(params.intXType)
    params.clockDev.map(_.bind(wdt.device))
    InModuleBody { wdt.module.clock := params.mclock.map(_.getWrappedValue).getOrElse(cbus.module.clock) }
    InModuleBody { wdt.module.reset := params.mreset.map(_.getWrappedValue).getOrElse(cbus.module.reset) }

    params.parentLogicalTreeNode.foreach { parent =>
      LogicalModuleTree.add(parent, wdt.logicalTreeNode)
    }

    wdt
  }

  def attachAndMakePort(params: WDTAttachParams): ModuleValue[WDTPortIO] = {
    val wdt = attach(params)
    val wdtNode = wdt.ioNode.makeSink()(params.p)
    InModuleBody { wdtNode.makeIO()(ValName(wdt.name)) }
  }
}
