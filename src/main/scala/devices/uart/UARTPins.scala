// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import chisel3.experimental.{withClockAndReset}
import freechips.rocketchip.util.SyncResetSynchronizerShiftReg
import sifive.blocks.devices.pinctrl.{Pin}

class UARTSignals[T <: Data](private val pingen: () => T) extends Bundle {
  val rxd = pingen()
  val txd = pingen()
}

class UARTPins[T <: Pin](pingen: () => T) extends UARTSignals[T](pingen)

object UARTPinsFromPort {
  def apply[T <: Pin](pins: UARTSignals[T], uart: UARTPortIO, clock: Clock, reset: Bool, syncStages: Int = 0) {
    withClockAndReset(clock, reset) {
      pins.txd.outputPin(uart.txd)
      val rxd_t = pins.rxd.inputPin()
      uart.rxd := SyncResetSynchronizerShiftReg(rxd_t, syncStages, init = Bool(true), name = Some("uart_rxd_sync"))
    }
  }
}

