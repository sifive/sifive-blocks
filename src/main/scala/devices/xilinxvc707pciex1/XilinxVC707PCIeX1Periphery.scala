// See LICENSE for license details.
package sifive.blocks.devices.xilinxvc707pciex1

import Chisel._
import diplomacy.LazyModule
import rocketchip.{
  HasTopLevelNetworks,
  HasTopLevelNetworksModule,
  HasTopLevelNetworksBundle
}
import uncore.tilelink2.TLWidthWidget

trait HasPeripheryXilinxVC707PCIeX1 extends HasTopLevelNetworks {

  val xilinxvc707pcie = LazyModule(new XilinxVC707PCIeX1)
  fsb.node := xilinxvc707pcie.master
  xilinxvc707pcie.slave   := TLWidthWidget(socBusConfig.beatBytes)(socBus.node)
  xilinxvc707pcie.control := TLWidthWidget(socBusConfig.beatBytes)(socBus.node)
  intBus.intnode := xilinxvc707pcie.intnode
}

trait HasPeripheryXilinxVC707PCIeX1Bundle extends HasTopLevelNetworksBundle {
  val xilinxvc707pcie = new XilinxVC707PCIeX1IO
}

trait HasPeripheryXilinxVC707PCIeX1Module extends HasTopLevelNetworksModule {
  val outer: HasPeripheryXilinxVC707PCIeX1
  val io: HasPeripheryXilinxVC707PCIeX1Bundle

  io.xilinxvc707pcie <> outer.xilinxvc707pcie.module.io.port
}
