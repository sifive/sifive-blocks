// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import chisel3.experimental.{withClockAndReset}
import freechips.rocketchip.config.Field
import freechips.rocketchip.coreplex.{HasPeripheryBus, PeripheryBusKey, HasInterruptBus}
import freechips.rocketchip.diplomacy.{LazyModule, LazyMultiIOModuleImp}

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
