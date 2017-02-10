// See LICENSE for license details.
package sifive.blocks.devices.xilinxvc707mig

import Chisel._
import diplomacy._
import rocketchip.{TopNetwork,TopNetworkModule,TopNetworkBundle}
import coreplex.BankedL2Config

trait PeripheryXilinxVC707MIG extends TopNetwork {
  val module: PeripheryXilinxVC707MIGModule

  val xilinxvc707mig = LazyModule(new XilinxVC707MIG)
  require(p(BankedL2Config).nMemoryChannels == 1, "Coreplex must have 1 master memory port")
  xilinxvc707mig.node := mem(0).node
}

trait PeripheryXilinxVC707MIGBundle extends TopNetworkBundle {
  val xilinxvc707mig = new XilinxVC707MIGIO
}

trait PeripheryXilinxVC707MIGModule extends TopNetworkModule {
  val outer: PeripheryXilinxVC707MIG
  val io: PeripheryXilinxVC707MIGBundle

  io.xilinxvc707mig <> outer.xilinxvc707mig.module.io.port
}
