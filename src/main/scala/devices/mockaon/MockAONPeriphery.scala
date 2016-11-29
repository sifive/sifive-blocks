// See LICENSE for license details.
package sifive.blocks.devices.mockaon

import Chisel._
import diplomacy.LazyModule
import rocketchip.{TopNetwork,TopNetworkModule}
import uncore.tilelink2.{IntXing, TLAsyncCrossingSource, TLFragmenter}
import coreplex._

trait PeripheryMockAON extends TopNetwork {
  val mockAONConfig: MockAONConfig
  val coreplex: CoreplexRISCVPlatform

  // We override the clock & Reset here so that all synchronizers, etc
  // are in the proper clock domain.
  val aon = LazyModule(new MockAONWrapper(mockAONConfig))
  val aon_int = LazyModule(new IntXing)
  aon.node := TLAsyncCrossingSource()(TLFragmenter(peripheryBusConfig.beatBytes, cacheBlockBytes)(peripheryBus.node))
  aon_int.intnode := aon.intnode
  intBus.intnode := aon_int.intnode
}

trait PeripheryMockAONBundle {
  val aon = new MockAONWrapperBundle()
}

trait PeripheryMockAONModule {
  this: TopNetworkModule {
    val outer: PeripheryMockAON
    val io: PeripheryMockAONBundle
  } =>

  io.aon <> outer.aon.module.io

  // Explicit clock & reset are unused in MockAONWrapper.
  // Tie  to check this assumption.
  outer.aon.module.clock := Bool(false).asClock
  outer.aon.module.reset := Bool(true)

  outer.coreplex.module.io.rtcToggle := outer.aon.module.io.rtc.asUInt.toBool

}
