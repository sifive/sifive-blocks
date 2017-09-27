// See LICENSE for license details.
package sifive.blocks.devices.mockaon

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.util.SynchronizerShiftReg
import freechips.rocketchip.coreplex.{HasPeripheryBus, HasInterruptBus}
import freechips.rocketchip.devices.debug.HasPeripheryDebug
import freechips.rocketchip.devices.tilelink.HasPeripheryClint
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
import freechips.rocketchip.tilelink.{IntXing, TLAsyncCrossingSource}
import freechips.rocketchip.util.ResetCatchAndSync

case object PeripheryMockAONKey extends Field[MockAONParams]

trait HasPeripheryMockAON extends HasPeripheryBus
    with HasInterruptBus
    with HasPeripheryClint
    with HasPeripheryDebug {
  // We override the clock & Reset here so that all synchronizers, etc
  // are in the proper clock domain.
  val mockAONParams= p(PeripheryMockAONKey)
  val aon = LazyModule(new MockAONWrapper(pbus.beatBytes, mockAONParams))
  aon.node := pbus.toAsyncVariableWidthSlaves(sync = 3)
  ibus.fromAsync := aon.intnode
}

trait HasPeripheryMockAONBundle {
  val aon: MockAONWrapperBundle
  def coreResetCatchAndSync(core_clock: Clock) = {
    ResetCatchAndSync(core_clock, aon.rsts.corerst, 20)
  }
}

trait HasPeripheryMockAONModuleImp extends LazyModuleImp with HasPeripheryMockAONBundle {
  val outer: HasPeripheryMockAON
  val aon = IO(new MockAONWrapperBundle)

  aon <> outer.aon.module.io

  // Explicit clock & reset are unused in MockAONWrapper.
  // Tie  to check this assumption.
  outer.aon.module.clock := Bool(false).asClock
  outer.aon.module.reset := Bool(true)

  // Synchronize the external toggle into the clint
  val rtc_sync = SynchronizerShiftReg(outer.aon.module.io.rtc.asUInt.toBool, 3, Some("rtc"))
  val rtc_last = Reg(init = Bool(false), next=rtc_sync)
  val rtc_tick = Reg(init = Bool(false), next=(rtc_sync & (~rtc_last)))

  outer.clint.module.io.rtcTick := rtc_tick

  outer.aon.module.io.ndreset := outer.debug.module.io.ctrl.ndreset
}
