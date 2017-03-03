// See LICENSE for license details.
package sifive.blocks.devices.xilinxvc707mig

import Chisel._
import diplomacy._
import rocketchip.{
  HasTopLevelNetworks,
  HasTopLevelNetworksModule,
  HasTopLevelNetworksBundle
}
import coreplex.BankedL2Config

trait HasPeripheryXilinxVC707MIG extends HasTopLevelNetworks {
  val module: HasPeripheryXilinxVC707MIGModule

  val xilinxvc707mig = LazyModule(new XilinxVC707MIG)
  require(p(BankedL2Config).nMemoryChannels == 1, "Coreplex must have 1 master memory port")
  xilinxvc707mig.node := mem(0).node
}

trait HasPeripheryXilinxVC707MIGBundle extends HasTopLevelNetworksBundle {
  val xilinxvc707mig = new XilinxVC707MIGIO
}

trait HasPeripheryXilinxVC707MIGModule extends HasTopLevelNetworksModule {
  val outer: HasPeripheryXilinxVC707MIG
  val io: HasPeripheryXilinxVC707MIGBundle

  io.xilinxvc707mig <> outer.xilinxvc707mig.module.io.port
}
