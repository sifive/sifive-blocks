// See LICENSE for license details.
package sifive.blocks.devices.xilinxvc707pciex1

import Chisel._
import diplomacy.LazyModule
import rocketchip.{L2Crossbar,L2CrossbarModule,L2CrossbarBundle}
import uncore.tilelink2.TLWidthWidget

trait PeripheryXilinxVC707PCIeX1 extends L2Crossbar {

  val xilinxvc707pcie = LazyModule(new XilinxVC707PCIeX1)
  l2.node := xilinxvc707pcie.master
  xilinxvc707pcie.slave   := TLWidthWidget(socBusConfig.beatBytes)(socBus.node)
  xilinxvc707pcie.control := TLWidthWidget(socBusConfig.beatBytes)(socBus.node)
  intBus.intnode := xilinxvc707pcie.intnode
}

trait PeripheryXilinxVC707PCIeX1Bundle extends L2CrossbarBundle {
  val xilinxvc707pcie = new XilinxVC707PCIeX1IO
}

trait PeripheryXilinxVC707PCIeX1Module extends L2CrossbarModule {
  val outer: PeripheryXilinxVC707PCIeX1
  val io: PeripheryXilinxVC707PCIeX1Bundle

  io.xilinxvc707pcie <> outer.xilinxvc707pcie.module.io.port
}
