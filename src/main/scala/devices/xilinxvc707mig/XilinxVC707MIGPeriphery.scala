// See LICENSE for license details.
package sifive.blocks.devices.xilinxvc707mig

import Chisel._
import diplomacy.{LazyModule, LazyMultiIOModuleImp}
import rocketchip.HasSystemNetworks

trait HasPeripheryXilinxVC707MIG extends HasSystemNetworks {
  val module: HasPeripheryXilinxVC707MIGModuleImp

  val xilinxvc707mig = LazyModule(new XilinxVC707MIG)
  require(nMemoryChannels == 1, "Coreplex must have 1 master memory port")
  xilinxvc707mig.node := mem(0).node
}

trait HasPeripheryXilinxVC707MIGBundle {
  val xilinxvc707mig: XilinxVC707MIGIO
  def connectXilinxVC707MIGToPads(pads: XilinxVC707MIGPads) {
    pads <> xilinxvc707mig
  }
}

trait HasPeripheryXilinxVC707MIGModuleImp extends LazyMultiIOModuleImp
    with HasPeripheryXilinxVC707MIGBundle {
  val outer: HasPeripheryXilinxVC707MIG
  val xilinxvc707mig = IO(new XilinxVC707MIGIO)

  xilinxvc707mig <> outer.xilinxvc707mig.module.io.port
}
