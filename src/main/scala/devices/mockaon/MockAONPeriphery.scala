// See LICENSE for license details.
package sifive.blocks.devices.mockaon

import Chisel._
import config.Field
import diplomacy.{LazyModule, LazyMultiIOModuleImp}
import rocketchip.{HasSystemNetworks, HasCoreplexRISCVPlatform}
import uncore.tilelink2.{IntXing, TLAsyncCrossingSource, TLFragmenter}
import util.ResetCatchAndSync

case object PeripheryMockAONKey extends Field[MockAONParams]

trait HasPeripheryMockAON extends HasSystemNetworks with HasCoreplexRISCVPlatform {
  // We override the clock & Reset here so that all synchronizers, etc
  // are in the proper clock domain.
  val mockAONParams= p(PeripheryMockAONKey)
  val aon = LazyModule(new MockAONWrapper(peripheryBusBytes, mockAONParams))
  val aon_int = LazyModule(new IntXing)
  aon.node := TLAsyncCrossingSource()(TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node))
  aon_int.intnode := aon.intnode
  intBus.intnode := aon_int.intnode
}

trait HasPeripheryMockAONBundle {
  val aon: MockAONWrapperBundle
  def coreResetCatchAndSync(core_clock: Clock) = {
    ResetCatchAndSync(core_clock, aon.rsts.corerst, 20)
  }
}

trait HasPeripheryMockAONModuleImp extends LazyMultiIOModuleImp with HasPeripheryMockAONBundle {
  val outer: HasPeripheryMockAON
  val aon = IO(new MockAONWrapperBundle)

  aon <> outer.aon.module.io

  // Explicit clock & reset are unused in MockAONWrapper.
  // Tie  to check this assumption.
  outer.aon.module.clock := Bool(false).asClock
  outer.aon.module.reset := Bool(true)

  outer.coreplex.module.io.rtcToggle := outer.aon.module.io.rtc.asUInt.toBool

  outer.aon.module.io.ndreset := outer.coreplex.module.io.ndreset
}
