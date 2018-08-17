// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.subsystem.{BaseSubsystem}
import freechips.rocketchip.diplomacy._

case object PeripherySPIKey extends Field[Seq[SPIParams]]

trait HasPeripherySPI { this: BaseSubsystem =>
  val spiNodes = p(PeripherySPIKey).map { ps => SPI.attach(SPIAttachParams(ps, pbus, ibus.fromAsync)).ioNode.makeSink() }
}

trait HasPeripherySPIBundle {
  val spi: Seq[SPIPortIO]
}

trait HasPeripherySPIModuleImp extends LazyModuleImp with HasPeripherySPIBundle {
  val outer: HasPeripherySPI
  val spi  = outer.spiNodes.zipWithIndex.map  { case(n,i) => n.makeIO()(ValName(s"spi_$i")) }
}

case object PeripherySPIFlashKey extends Field[Seq[SPIFlashParams]]

trait HasPeripherySPIFlash { this: BaseSubsystem =>
  val qspiNodes = p(PeripherySPIFlashKey).map { ps =>
    SPI.attachFlash(SPIFlashAttachParams(ps, pbus, pbus, ibus.fromAsync, fBufferDepth = 8)).ioNode.makeSink()
  }
}

trait HasPeripherySPIFlashBundle {
  val qspi: Seq[SPIPortIO]
}

trait HasPeripherySPIFlashModuleImp extends LazyModuleImp with HasPeripherySPIFlashBundle {
  val outer: HasPeripherySPIFlash
  val qspi = outer.qspiNodes.zipWithIndex.map { case(n,i) => n.makeIO()(ValName(s"qspi_$i")) }
}
