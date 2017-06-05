// See LICENSE for license details.
package sifive.blocks.devices.xilinxvc707pciex1

import Chisel._
import diplomacy.{LazyModule, LazyMultiIOModuleImp}
import rocketchip.HasSystemNetworks
import uncore.tilelink2._

trait HasPeripheryXilinxVC707PCIeX1 extends HasSystemNetworks {
  val xilinxvc707pcie = LazyModule(new XilinxVC707PCIeX1)
  private val intXing = LazyModule(new IntXing)

  fsb.node := TLAsyncCrossingSink()(xilinxvc707pcie.master)
  xilinxvc707pcie.slave   := TLAsyncCrossingSource()(TLWidthWidget(socBusConfig.beatBytes)(socBus.node))
  xilinxvc707pcie.control := TLAsyncCrossingSource()(TLWidthWidget(socBusConfig.beatBytes)(socBus.node))
  intBus.intnode := intXing.intnode
  intXing.intnode := xilinxvc707pcie.intnode
}

trait HasPeripheryXilinxVC707PCIeX1Bundle {
  val xilinxvc707pcie: XilinxVC707PCIeX1IO
  def connectXilinxVC707PCIeX1ToPads(pads: XilinxVC707PCIeX1Pads) {
    pads <> xilinxvc707pcie
  }
}

trait HasPeripheryXilinxVC707PCIeX1ModuleImp extends LazyMultiIOModuleImp
    with HasPeripheryXilinxVC707PCIeX1Bundle {
  val outer: HasPeripheryXilinxVC707PCIeX1
  val xilinxvc707pcie = IO(new XilinxVC707PCIeX1IO)

  xilinxvc707pcie <> outer.xilinxvc707pcie.module.io.port

  outer.xilinxvc707pcie.module.clock := outer.xilinxvc707pcie.module.io.port.axi_aclk_out
  outer.xilinxvc707pcie.module.reset := ~xilinxvc707pcie.axi_aresetn
}
