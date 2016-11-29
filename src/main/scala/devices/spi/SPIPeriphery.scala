// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import diplomacy.LazyModule
import uncore.tilelink2._
import rocketchip.{TopNetwork,TopNetworkModule}

trait PeripherySPI {
  this: TopNetwork { val spiConfigs: Seq[SPIConfig] } =>
  val spiDevices = (spiConfigs.zipWithIndex) map {case (c, i) =>
    val spi = LazyModule(new TLSPI(c) { override lazy val valName = Some(s"spi$i") } )
    spi.rnode := TLFragmenter(peripheryBusConfig.beatBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := spi.intnode
    spi
  }
}

trait PeripherySPIBundle {
  this: { val spiConfigs: Seq[SPIConfig] } =>
  val spi_bc = spiConfigs.map(_.bc).reduce(_.union(_))
  val spis = Vec(spiConfigs.size, new SPIPortIO(spi_bc.toSPIConfig))
}

trait PeripherySPIModule {
  this: TopNetworkModule {
    val spiConfigs: Seq[SPIConfig]
    val outer: PeripherySPI
    val io: PeripherySPIBundle
  } =>
  (io.spis zip outer.spiDevices).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}


trait PeripherySPIFlash {
  this: TopNetwork { val spiFlashConfig: SPIFlashConfig } =>
  val qspi = LazyModule(new TLSPIFlash(spiFlashConfig))
  qspi.rnode := TLFragmenter(peripheryBusConfig.beatBytes, cacheBlockBytes)(peripheryBus.node)
  qspi.fnode := TLFragmenter(1, cacheBlockBytes)(TLWidthWidget(peripheryBusConfig.beatBytes)(peripheryBus.node))
  intBus.intnode := qspi.intnode
}

trait PeripherySPIFlashBundle {
  this: { val spiFlashConfig: SPIFlashConfig } =>
  val qspi = new SPIPortIO(spiFlashConfig)
}

trait PeripherySPIFlashModule {
  this: TopNetworkModule {
    val spiConfigs: Seq[SPIConfig]
    val outer: PeripherySPIFlash
    val io: PeripherySPIFlashBundle
  } =>
  io.qspi <> outer.qspi.module.io.port
}
