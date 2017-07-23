// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.coreplex.{HasPeripheryBus, HasInterruptBus}
import freechips.rocketchip.diplomacy.{LazyModule,LazyMultiIOModuleImp}
import freechips.rocketchip.tilelink.{TLFragmenter}
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
  val spis: HeterogeneousBag[SPIPortIO]

  def SPItoGPIOPins(syncStages: Int = 0): Seq[SPIPinsIO] = spis.map { s =>
    val pins = Module(new SPIGPIOPort(s.c, syncStages))
    pins.io.spi <> s
    pins.io.pins
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

trait HasPeripherySPIFlash extends HasPeripheryBus with HasInterruptBus {
  val spiFlashParams = p(PeripherySPIFlashKey)  
  val qspi = spiFlashParams map { params =>
    val qspi = LazyModule(new TLSPIFlash(pbus.beatBytes, params))
    qspi.rnode := pbus.toVariableWidthSlaves
    qspi.fnode := TLFragmenter(1, pbus.blockBytes)(pbus.toFixedWidthSlaves)
    ibus.fromSync := qspi.intnode
    qspi
  }
}

trait HasPeripherySPIFlashBundle {
  val qspi: HeterogeneousBag[SPIPortIO]

  // It is important for SPIFlash that the syncStages is agreed upon, because
  // internally it needs to realign the input data to the output SCK.
  // Therefore, we rely on the syncStages parameter.
  def SPIFlashtoGPIOPins(syncStages: Int = 0): Seq[SPIPinsIO] = qspi.map { s =>
    val pins = Module(new SPIGPIOPort(s.c, syncStages))
    pins.io.spi <> s
    pins.io.pins
  }
}

trait HasPeripherySPIFlashModuleImp extends LazyMultiIOModuleImp with HasPeripherySPIFlashBundle {
  val outer: HasPeripherySPIFlash
  val qspi = IO(HeterogeneousBag(outer.spiFlashParams.map(new SPIPortIO(_))))

  (qspi zip outer.qspi) foreach { case (io, device) => 
    io <> device.module.io.port
  }
}
