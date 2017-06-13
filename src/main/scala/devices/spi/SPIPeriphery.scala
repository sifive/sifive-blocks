// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import config.Field
import diplomacy.{LazyModule,LazyMultiIOModuleImp}
import rocketchip.HasSystemNetworks
import uncore.tilelink2.{TLFragmenter,TLWidthWidget}
import util.HeterogeneousBag

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
  val spis: HeterogeneousBag[SPIPortIO]

  def SPItoGPIOPins(sync_stages: Int = 0): Seq[SPIGPIOPort] = spis.map { s =>
    val pin = Module(new SPIGPIOPort(s.c, sync_stages))
    pin.io.spi <> s
    pin
  }
}

trait HasPeripherySPIModuleImp extends LazyMultiIOModuleImp with HasPeripherySPIBundle {
  val outer: HasPeripherySPI
  val spis = IO(HeterogeneousBag(outer.spiParams.map(new SPIPortIO(_))))

  (spis zip outer.spis).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}

case object PeripherySPIFlashKey extends Field[Seq[SPIFlashParams]]

trait HasPeripherySPIFlash extends HasSystemNetworks {
  val spiFlashParams = p(PeripherySPIFlashKey)  
  val qspi = spiFlashParams map { params =>
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

  (qspi zip outer.qspi) foreach { case (io, device) => 
    io <> device.module.io.port
  }
}

