// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

case class AttachedSPIParams(
  spi: SPIParams,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing)

case class AttachedSPIFlashParams(
  qspi: SPIFlashParams,
  fBufferDepth: Int = 0,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing,
  memXType: ClockCrossingType = NoCrossing)

object SPI {
  def attach(params: AttachedSPIParams, controlBus: TLBusWrapper, intNode: IntInwardNode, mclock: Option[ModuleValue[Clock]])
            (implicit p: Parameters, valName: ValName): TLSPI = {
    val spi = LazyModule(new TLSPI(controlBus.beatBytes, params.spi))

    controlBus.coupleTo(s"slave_named_${valName.name}") {
      spi.controlXing(params.controlXType) := TLFragmenter(controlBus.beatBytes, controlBus.blockBytes) := _
    }

    intNode := spi.intXing(params.intXType)

    InModuleBody { spi.module.clock := mclock.map(_.getWrappedValue).getOrElse(controlBus.module.clock) }

    spi
  }

  def attachFlash(params: AttachedSPIFlashParams, controlBus: TLBusWrapper, memBus: TLBusWrapper, intNode: IntInwardNode, mclock: Option[ModuleValue[Clock]])
            (implicit p: Parameters, valName: ValName): TLSPIFlash = {

    val qspi = LazyModule(new TLSPIFlash(controlBus.beatBytes, params.qspi))

    controlBus.coupleTo(s"slave_named_${valName.name}") {
      qspi.controlXing(params.controlXType) := TLFragmenter(controlBus.beatBytes, controlBus.blockBytes) := _
    }

    memBus.coupleTo(s"mem_named_${valName.name}") {
      (qspi.memXing(params.memXType)
        := TLFragmenter(1, memBus.blockBytes)
        := TLBuffer(BufferParams(params.fBufferDepth), BufferParams.none)
        := TLWidthWidget(memBus.beatBytes)
        := _)
    }

    intNode := qspi.intXing(params.intXType)

    InModuleBody { qspi.module.clock := mclock.map(_.getWrappedValue).getOrElse(controlBus.module.clock) }

    qspi
  }
}
