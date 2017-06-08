// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import config.Field
import diplomacy.LazyModule
import rocketchip.{
  HasTopLevelNetworks,
  HasTopLevelNetworksBundle,
  HasTopLevelNetworksModule
}
import uncore.tilelink2.{TLFragmenter, TLWidthWidget}
import util.HeterogeneousBag

case object PeripherySPIKey extends Field[Seq[SPIParams]]

trait HasPeripherySPI extends HasTopLevelNetworks {
  val spiParams = p(PeripherySPIKey)  
  val spis = spiParams map { params =>
    val spi = LazyModule(new TLSPI(peripheryBusBytes, params))
    spi.rnode := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := spi.intnode
    spi
  }
}

trait HasPeripherySPIBundle extends HasTopLevelNetworksBundle {
  val outer: HasPeripherySPI
  val spis = HeterogeneousBag(outer.spiParams.map(new SPIPortIO(_)))
}

trait HasPeripherySPIModule extends HasTopLevelNetworksModule {
  val outer: HasPeripherySPI
  val io: HasPeripherySPIBundle
  (io.spis zip outer.spis).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}

case object PeripherySPIFlashKey extends Field[Seq[SPIFlashParams]]

trait HasPeripherySPIFlash extends HasTopLevelNetworks {
  val spiFlashParams = p(PeripherySPIFlashKey)  
  val qspi = spiFlashParams map { params =>
    val qspi = LazyModule(new TLSPIFlash(peripheryBusBytes, params))
    qspi.rnode := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
    qspi.fnode := TLFragmenter(1, cacheBlockBytes)(TLWidthWidget(peripheryBusBytes)(peripheryBus.node))
    intBus.intnode := qspi.intnode
    qspi
  }
}

trait HasPeripherySPIFlashBundle extends HasTopLevelNetworksBundle {
  val outer: HasPeripherySPIFlash 
  val qspi = HeterogenousBag(outer.spiFlashParams.map(new SPIPortIO(_)))
}

trait HasPeripherySPIFlashModule extends HasTopLevelNetworksModule {
  val outer: HasPeripherySPIFlash
  val io: HasPeripherySPIFlashBundle

  (io.qspi zip outer.qspi) foreach { case (io, device) => 
    io.qspi <> device.module.io.port
  }
}

