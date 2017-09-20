// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import chisel3.experimental.{withClockAndReset}
import freechips.rocketchip.config.Field
import freechips.rocketchip.util.SyncResetSynchronizerShiftReg
import freechips.rocketchip.coreplex.{HasPeripheryBus, PeripheryBusKey, HasInterruptBus}
import freechips.rocketchip.diplomacy.{LazyModule, LazyMultiIOModuleImp}
import sifive.blocks.devices.pinctrl.{Pin}


class UARTSignals[T <: Data] (pingen: () => T) extends Bundle {
  val rxd = pingen()
  val txd = pingen()

  override def cloneType: this.type =
    this.getClass.getConstructors.head.newInstance(pingen).asInstanceOf[this.type]
}

class UARTPins[T <: Pin] (pingen: () => T) extends UARTSignals[T](pingen, c) {
  override def cloneType: this.type =
    this.getClass.getConstructors.head.newInstance(pingen).asInstanceOf[this.type]

  def fromPort(uart: UARTPortIO, clock: Clock, reset: Bool, syncStages: Int = 0) {
    withClockAndReset(clock, reset) {
      txd.outputPin(uart.txd)
      val rxd_t = rxd.inputPin()
      uart.rxd := SyncResetSynchronizerShiftReg(rxd_t, syncStages, init = Bool(true), name = Some("uart_rxd_sync"))
    }
  }
}

