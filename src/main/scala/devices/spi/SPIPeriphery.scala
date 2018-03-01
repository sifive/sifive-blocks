// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.diplomacy.{LazyModule,LazyModuleImp,BufferParams}
import freechips.rocketchip.tilelink.{TLFragmenter,TLBuffer}
import freechips.rocketchip.util.HeterogeneousBag

case object PeripherySPIKey extends Field[Seq[SPIParams]]

trait HasPeripherySPI { this: BaseSubsystem =>
  val spiParams = p(PeripherySPIKey)  
  val spis = spiParams.zipWithIndex.map { case(params, i) =>
    val name = Some(s"spi_$i")
    val spi = LazyModule(new TLSPI(pbus.beatBytes, params)).suggestName(name)
    pbus.toVariableWidthSlave(name) { spi.rnode }
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

trait HasPeripherySPIFlash { this: BaseSubsystem =>
  val spiFlashParams = p(PeripherySPIFlashKey)  
  val qspis = spiFlashParams.zipWithIndex.map { case(params, i) =>
    val name = Some(s"qspi_$i")
    val qspi = LazyModule(new TLSPIFlash(pbus.beatBytes, params))
    pbus.toVariableWidthSlave(name) { qspi.rnode }
    qspi.fnode := pbus.toFixedWidthSlave(name) {
      TLFragmenter(1, pbus.blockBytes) :=
        TLBuffer(BufferParams(params.fBufferDepth), BufferParams.none)
    }
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
