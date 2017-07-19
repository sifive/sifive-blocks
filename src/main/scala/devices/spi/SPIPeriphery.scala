// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy.{LazyModule,LazyMultiIOModuleImp}
import freechips.rocketchip.chip.HasSystemNetworks
import freechips.rocketchip.tilelink.{TLFragmenter,TLWidthWidget}
import freechips.rocketchip.util.HeterogeneousBag

case object PeripherySPIKey extends Field[Seq[SPIParams]]

trait HasPeripherySPI extends HasSystemNetworks {
  val spiParams = p(PeripherySPIKey)  
  val spis = spiParams map { params =>
    val spi = LazyModule(new TLSPI(peripheryBusBytes, params))
    spi.rnode := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := spi.intnode
    spi
  }
}

trait HasPeripherySPIBundle {
  val spi: HeterogeneousBag[SPIPortIO]

}

trait HasPeripherySPIModuleImp extends LazyMultiIOModuleImp with HasPeripherySPIBundle {
  val outer: HasPeripherySPI
  val spi = IO(HeterogeneousBag(outer.spiParams.map(new SPIPortIO(_))))

  (spi zip outer.spis).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}

case object PeripherySPIFlashKey extends Field[Seq[SPIFlashParams]]

trait HasPeripherySPIFlash extends HasSystemNetworks {
  val spiFlashParams = p(PeripherySPIFlashKey)  
  val qspis = spiFlashParams map { params =>
    val qspi = LazyModule(new TLSPIFlash(peripheryBusBytes, params))
    qspi.rnode := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
    qspi.fnode := TLFragmenter(1, cacheBlockBytes)(TLWidthWidget(peripheryBusBytes)(peripheryBus.node))
    intBus.intnode := qspi.intnode
    qspi
  }
}

trait HasPeripherySPIFlashBundle {
  val qspi: HeterogeneousBag[SPIPortIO]

}

trait HasPeripherySPIFlashModuleImp extends LazyMultiIOModuleImp with HasPeripherySPIFlashBundle {
  val outer: HasPeripherySPIFlash
  val qspi = IO(HeterogeneousBag(outer.spiFlashParams.map(new SPIPortIO(_))))

  (qspi zip outer.qspis) foreach { case (io, device) => 
    io <> device.module.io.port
  }
}

