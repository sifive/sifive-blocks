// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import chisel3.experimental.{withClockAndReset}
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
import freechips.rocketchip.subsystem.{BaseSubsystem, PeripheryBusKey}

case object PeripheryUARTKey extends Field[Seq[UARTParams]]

trait HasPeripheryUART { this: BaseSubsystem =>
  private val divinit = (p(PeripheryBusKey).frequency / 115200).toInt
  val uartParams = p(PeripheryUARTKey).map(_.copy(divisorInit = divinit))
  val uarts = uartParams.zipWithIndex.map { case(params, i) =>
    val name = Some(s"uart_$i")
    val uart = LazyModule(new TLUART(pbus.beatBytes, params)).suggestName(name)
    pbus.toVariableWidthSlave(name) { uart.node }
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

trait HasPeripheryUARTModuleImp extends LazyModuleImp with HasPeripheryUARTBundle {
  val outer: HasPeripheryUART
  val uart = IO(Vec(outer.uartParams.size, new UARTPortIO))

  (uart zip outer.uarts).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}
