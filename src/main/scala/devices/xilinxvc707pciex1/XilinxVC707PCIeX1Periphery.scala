// See LICENSE for license details.
package sifive.blocks.devices.xilinxvc707pciex1

import Chisel._
import diplomacy.LazyModule
import rocketchip.{TopNetwork,TopNetworkModule,TopNetworkBundle}
import uncore.tilelink2.TLWidthWidget

trait PeripheryXilinxVC707PCIeX1 extends TopNetwork {

  val xilinxvc707pcie = LazyModule(new XilinxVC707PCIeX1)
  l2.node := xilinxvc707pcie.master
  xilinxvc707pcie.slave   := TLWidthWidget(socBusConfig.beatBytes)(socBus.node)
  xilinxvc707pcie.control := TLWidthWidget(socBusConfig.beatBytes)(socBus.node)
  intBus.intnode := xilinxvc707pcie.intnode
}

trait PeripheryXilinxVC707PCIeX1Bundle extends TopNetworkBundle {
  val xilinxvc707pcie = new XilinxVC707PCIeX1IO
}

trait PeripheryXilinxVC707PCIeX1Module extends TopNetworkModule {
  val outer: PeripheryXilinxVC707PCIeX1
  val io: PeripheryXilinxVC707PCIeX1Bundle

  io.xilinxvc707pcie <> outer.xilinxvc707pcie.module.io.port
}
