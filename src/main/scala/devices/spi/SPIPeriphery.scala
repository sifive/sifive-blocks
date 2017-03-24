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

case object PeripherySPIFlashKey extends Field[SPIFlashParams]

trait HasPeripherySPIFlash extends HasTopLevelNetworks {
  val spiFlashParams = p(PeripherySPIFlashKey)  
  val qspi = LazyModule(new TLSPIFlash(peripheryBusBytes, spiFlashParams))
  qspi.rnode := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
  qspi.fnode := TLFragmenter(1, cacheBlockBytes)(TLWidthWidget(peripheryBusBytes)(peripheryBus.node))
  intBus.intnode := qspi.intnode
}

trait HasPeripherySPIFlashBundle extends HasTopLevelNetworksBundle {
  val outer: HasPeripherySPIFlash 
  val qspi = new SPIPortIO(outer.spiFlashParams)
}

trait HasPeripherySPIFlashModule extends HasTopLevelNetworksModule {
  val outer: HasPeripherySPIFlash
  val io: HasPeripherySPIFlashBundle
  io.qspi <> outer.qspi.module.io.port
  //HACK -- introduce delay elements to synchronize these inputs.
  (io.qspi.dq zip outer.qspi.module.io.port.dq).foreach{ case (i, o) => 
    o.i := ShiftRegister(i.i, 3)
  }
}
