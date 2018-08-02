// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.subsystem.{BaseSubsystem}
import freechips.rocketchip.diplomacy._

case object PeripherySPIKey extends Field[Seq[SPIParams]]

trait HasPeripherySPI { this: BaseSubsystem =>
  val spiParams = p(PeripherySPIKey)  
  val spis = p(PeripherySPIKey).map { ps =>
    SPI.attach(AttachedSPIParams(ps), pbus, ibus.fromAsync, None)
  }
  val spiNodes = spis.map(_.ioNode.makeSink())
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
  val qspis = p(PeripherySPIFlashKey).map { ps =>
    SPI.attachFlash(AttachedSPIFlashParams(ps, fBufferDepth = 8), pbus, pbus, ibus.fromAsync, None)
  }
  val qspiNodes = qspis.map(_.ioNode.makeSink())
}

trait HasPeripherySPIFlashBundle {
  val qspi: Seq[SPIPortIO]
}

trait HasPeripherySPIFlashModuleImp extends LazyModuleImp with HasPeripherySPIFlashBundle {
  val outer: HasPeripherySPIFlash
  val qspi = outer.qspiNodes.zipWithIndex.map { case(n,i) => n.makeIO()(ValName(s"qspi_$i")) }
}
