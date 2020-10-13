// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel.{defaultCompileOptions => _, _}
import freechips.rocketchip.util.CompileOptions.NotStrictInferReset
import freechips.rocketchip.config.Field
import freechips.rocketchip.subsystem.{BaseSubsystem}
import freechips.rocketchip.diplomacy._

case object PeripherySPIKey extends Field[Seq[SPIParams]](Nil)

trait HasPeripherySPI { this: BaseSubsystem =>
  val tlSpiNodes = p(PeripherySPIKey).map { ps =>
    SPIAttachParams(ps).attachTo(this)
  }
  val spiNodes = tlSpiNodes.map { n => n.ioNode.makeSink() }
}

trait HasPeripherySPIBundle {
  val spi: Seq[SPIPortIO]
}

trait HasPeripherySPIModuleImp extends LazyModuleImp with HasPeripherySPIBundle {
  val outer: HasPeripherySPI
  val spi  = outer.spiNodes.zipWithIndex.map  { case(n,i) => n.makeIO()(ValName(s"spi_$i")) }
}

case object PeripherySPIFlashKey extends Field[Seq[SPIFlashParams]](Nil)

trait HasPeripherySPIFlash { this: BaseSubsystem =>
  val tlQSpiNodes = p(PeripherySPIFlashKey).map { ps =>
    SPIFlashAttachParams(ps, fBufferDepth = 8).attachTo(this)
  }
  val qspiNodes = tlQSpiNodes.map { n => n.ioNode.makeSink() }
}

trait HasPeripherySPIFlashBundle {
  val qspi: Seq[SPIPortIO]
}

trait HasPeripherySPIFlashModuleImp extends LazyModuleImp with HasPeripherySPIFlashBundle {
  val outer: HasPeripherySPIFlash
  val qspi = outer.qspiNodes.zipWithIndex.map { case(n,i) => n.makeIO()(ValName(s"qspi_$i")) }
}
