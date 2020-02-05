// See LICENSE for license details.
package sifive.blocks.devices.timer

import Chisel._

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.regmapper.{RegisterRouter, RegisterRouterParams}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import freechips.rocketchip.diplomaticobjectmodel.model.{OMComponent, OMRegister}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalModuleTree, LogicalTreeNode}

import sifive.blocks.util.{GenericTimer, BasicBusBlocker}
import sifive.blocks.devices.pwm._

case class TimerParams(
  address: BigInt,
  size: Int = 0x1000,
  regBytes: Int = 4,
  cmpWidth: Int = 16)

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

case class TimerAttachParams(
  timer: TimerParams,
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

object TimerDevice { // This anti-pattern name is needed because Timer is a Chisel keyword
  val nextId = { var i = -1; () => { i += 1; i} }

  def attach(params: TimerAttachParams): Timer = {
    implicit val p = params.p
    val name = s"timer_${nextId()}"
    val cbus = params.controlBus
    val timer = LazyModule(new Timer(cbus.beatBytes, params.timer))
    timer.suggestName(name)
    cbus.coupleTo(s"device_named_$name") { bus =>
      val blockerNode = params.blockerAddr.map(BasicBusBlocker(_, cbus, cbus.beatBytes, name))
      (timer.controlXing(params.controlXType)
        := TLFragmenter(cbus)
        := blockerNode.map { _ := bus } .getOrElse { bus })
    }
    params.clockDev.map(_.bind(timer.device))
    params.intNode := timer.intXing(params.intXType)
    InModuleBody { timer.module.clock := params.mclock.map(_.getWrappedValue).getOrElse(cbus.module.clock) }
    InModuleBody { timer.module.reset := params.mreset.map(_.getWrappedValue).getOrElse(cbus.module.reset) }

    params.parentLogicalTreeNode.foreach { parent =>
      LogicalModuleTree.add(parent, timer.logicalTreeNode)
    }
    timer
  }

}
