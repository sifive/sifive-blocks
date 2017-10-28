// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.coreplex.{HasPeripheryBus, HasInterruptBus}
import freechips.rocketchip.diplomacy.{LazyModule,LazyModuleImp,BufferParams}
import freechips.rocketchip.tilelink.{TLFragmenter,TLBuffer}
import freechips.rocketchip.util.HeterogeneousBag

case object PeripherySPIKey extends Field[Seq[SPIParams]]

trait HasPeripherySPI extends HasPeripheryBus with HasInterruptBus {
  val spiParams = p(PeripherySPIKey)  
  val spis = spiParams map { params =>
    val spi = LazyModule(new TLSPI(pbus.beatBytes, params))
    spi.rnode := pbus.toVariableWidthSlaves
    ibus.fromSync := spi.intnode
    spi
  }
}

trait HasPeripherySPIBundle {
  val spi: HeterogeneousBag[SPIPortIO]

}

trait HasPeripherySPIModuleImp extends LazyModuleImp with HasPeripherySPIBundle {
  val outer: HasPeripherySPI
  val spi = IO(HeterogeneousBag(outer.spiParams.map(new SPIPortIO(_))))

  (spi zip outer.spis).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}

case object PeripherySPIFlashKey extends Field[Seq[SPIFlashParams]]

trait HasPeripherySPIFlash extends HasPeripheryBus with HasInterruptBus {
  val spiFlashParams = p(PeripherySPIFlashKey)  
  val qspis = spiFlashParams map { params =>
    val qspi = LazyModule(new TLSPIFlash(pbus.beatBytes, params))
    qspi.rnode := pbus.toVariableWidthSlaves
    (qspi.fnode
      := TLFragmenter(1, pbus.blockBytes)
      := TLBuffer(BufferParams(params.fBufferDepth), BufferParams.none)
      := pbus.toFixedWidthSlaves)
    ibus.fromSync := qspi.intnode
    qspi
  }
}

trait HasPeripherySPIFlashBundle {
  val qspi: HeterogeneousBag[SPIPortIO]

}

trait HasPeripherySPIFlashModuleImp extends LazyModuleImp with HasPeripherySPIFlashBundle {
  val outer: HasPeripherySPIFlash
  val qspi = IO(HeterogeneousBag(outer.spiFlashParams.map(new SPIPortIO(_))))

  (qspi zip outer.qspis) foreach { case (io, device) => 
    io <> device.module.io.port
  }
}
