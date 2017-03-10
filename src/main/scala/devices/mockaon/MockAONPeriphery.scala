// See LICENSE for license details.
package sifive.blocks.devices.mockaon

import Chisel._
import config.Field
import coreplex.CoreplexRISCVPlatform
import diplomacy.LazyModule
import rocketchip.{
  HasTopLevelNetworks,
  HasTopLevelNetworksBundle,
  HasTopLevelNetworksModule
}
import uncore.tilelink2.{IntXing, TLAsyncCrossingSource, TLFragmenter}

case object PeripheryMockAONKey extends Field[MockAONParams]

trait HasPeripheryMockAON extends HasTopLevelNetworks {
  val coreplex: CoreplexRISCVPlatform

  // We override the clock & Reset here so that all synchronizers, etc
  // are in the proper clock domain.
  val mockAONParams= p(PeripheryMockAONKey)
  val aon = LazyModule(new MockAONWrapper(peripheryBusBytes, mockAONParams))
  val aon_int = LazyModule(new IntXing)
  aon.node := TLAsyncCrossingSource()(TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node))
  aon_int.intnode := aon.intnode
  intBus.intnode := aon_int.intnode
}

trait HasPeripheryMockAONBundle extends HasTopLevelNetworksBundle {
  val aon = new MockAONWrapperBundle()
}

trait HasPeripheryMockAONModule extends HasTopLevelNetworksModule {
  val outer: HasPeripheryMockAON
  val io: HasPeripheryMockAONBundle

  io.aon <> outer.aon.module.io

  // Explicit clock & reset are unused in MockAONWrapper.
  // Tie  to check this assumption.
  outer.aon.module.clock := Bool(false).asClock
  outer.aon.module.reset := Bool(true)

  outer.coreplex.module.io.rtcToggle := outer.aon.module.io.rtc.asUInt.toBool

}
