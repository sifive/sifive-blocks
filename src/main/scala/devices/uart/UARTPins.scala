// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel.{defaultCompileOptions => _, _}
import freechips.rocketchip.util.CompileOptions.NotStrictInferReset
import chisel3.{withClockAndReset}
import freechips.rocketchip.util.SyncResetSynchronizerShiftReg
import sifive.blocks.devices.pinctrl.{Pin}

class UARTSignals[T <: Data](private val pingen: () => T, val wire4: Boolean = false) extends Bundle {
  val rxd = pingen()
  val txd = pingen()
  val cts_n = if (wire4) Option(pingen()) else None
  val rts_n = if (wire4) Option(pingen()) else None
}

class UARTPins[T <: Pin](pingen: () => T) extends UARTSignals[T](pingen)

object UARTPinsFromPort {
  def apply[T <: Pin](pins: UARTSignals[T], uart: UARTPortIO, clock: Clock, reset: Bool, syncStages: Int = 0) {
    withClockAndReset(clock, reset) {
      pins.txd.outputPin(uart.txd)
      val rxd_t = pins.rxd.inputPin()
      uart.rxd := SyncResetSynchronizerShiftReg(rxd_t, syncStages, init = Bool(true), name = Some("uart_rxd_sync"))
      pins.rts_n.foreach { rt => rt.outputPin(uart.rts_n.get) }
      pins.cts_n.foreach { ct => 
      	val cts_t = ct.inputPin()
      	uart.cts_n.get := SyncResetSynchronizerShiftReg(cts_t, syncStages, init = Bool(false), name = Some("uart_cts_sync"))
      }
    }
  }
}

