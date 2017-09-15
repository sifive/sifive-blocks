// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import chisel3.experimental.{withClockAndReset}
import freechips.rocketchip.config.Field
import freechips.rocketchip.util.SyncResetSynchronizerShiftReg
import freechips.rocketchip.coreplex.{HasPeripheryBus, PeripheryBusKey, HasInterruptBus}
import freechips.rocketchip.diplomacy.{LazyModule, LazyMultiIOModuleImp}
import sifive.blocks.devices.pinctrl.{Pin}

case object PeripheryUARTKey extends Field[Seq[UARTParams]]

trait HasPeripheryUART extends HasPeripheryBus with HasInterruptBus {
  private val divinit = (p(PeripheryBusKey).frequency / 115200).toInt
  val uartParams = p(PeripheryUARTKey).map(_.copy(divisorInit = divinit))
  val uarts = uartParams map { params =>
    val uart = LazyModule(new TLUART(pbus.beatBytes, params))
    uart.node := pbus.toVariableWidthSlaves
    ibus.fromSync := uart.intnode
    uart
  }
}

trait HasPeripheryUARTBundle {
  val uart: Vec[UARTPortIO]

  def tieoffUARTs(dummy: Int = 1) {
    uart.foreach { _.rxd := UInt(1) }
  }

}

trait HasPeripheryUARTModuleImp extends LazyMultiIOModuleImp with HasPeripheryUARTBundle {
  val outer: HasPeripheryUART
  val uart = IO(Vec(outer.uartParams.size, new UARTPortIO))

  (uart zip outer.uarts).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}

class UARTPins[T <: Pin] (pingen: () => T) extends Bundle {
  val rxd = pingen()
  val txd = pingen()

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

