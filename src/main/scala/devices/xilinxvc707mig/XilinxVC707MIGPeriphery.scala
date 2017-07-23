// See LICENSE for license details.
package sifive.blocks.devices.xilinxvc707mig

import Chisel._
import freechips.rocketchip.coreplex.HasMemoryBus
import freechips.rocketchip.diplomacy.{LazyModule, LazyMultiIOModuleImp}

trait HasMemoryXilinxVC707MIG extends HasMemoryBus {
  val module: HasMemoryXilinxVC707MIGModuleImp

  val xilinxvc707mig = LazyModule(new XilinxVC707MIG)

  require(nMemoryChannels == 1, "Coreplex must have 1 master memory port")
  xilinxvc707mig.node := memBuses.head.toDRAMController
}

trait HasMemoryXilinxVC707MIGBundle {
  val xilinxvc707mig: XilinxVC707MIGIO
  def connectXilinxVC707MIGToPads(pads: XilinxVC707MIGPads) {
    pads <> xilinxvc707mig
  }
}

trait HasMemoryXilinxVC707MIGModuleImp extends LazyMultiIOModuleImp
    with HasMemoryXilinxVC707MIGBundle {
  val outer: HasMemoryXilinxVC707MIG
  val xilinxvc707mig = IO(new XilinxVC707MIGIO)

  xilinxvc707mig <> outer.xilinxvc707mig.module.io.port
}
