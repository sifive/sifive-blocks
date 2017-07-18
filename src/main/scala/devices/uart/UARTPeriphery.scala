// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy.{LazyModule, LazyMultiIOModuleImp}
import freechips.rocketchip.chip.HasSystemNetworks
import freechips.rocketchip.tilelink.TLFragmenter
import sifive.blocks.devices.pinctrl.{Pin, PinCtrl}
import sifive.blocks.util.ShiftRegisterInit

case object PeripheryUARTKey extends Field[Seq[UARTParams]]

trait HasPeripheryUART extends HasSystemNetworks {
  val uartParams = p(PeripheryUARTKey)  
  val uarts = uartParams map { params =>
    val uart = LazyModule(new TLUART(peripheryBusBytes, params))
    uart.node := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := uart.intnode
    uart
  }
}

trait HasPeripheryUARTBundle {
  val uarts: Vec[UARTPortIO]

  def tieoffUARTs(dummy: Int = 1) {
    uarts.foreach { _.rxd := UInt(1) }
  }

}

trait HasPeripheryUARTModuleImp extends LazyMultiIOModuleImp with HasPeripheryUARTBundle {
  val outer: HasPeripheryUART
  val uarts = IO(Vec(outer.uartParams.size, new UARTPortIO))

  (uarts zip outer.uarts).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}

class UARTPins(pingen: () => Pin) extends Bundle {
  val rxd = pingen()
  val txd = pingen()

  def fromUARTPort(uart: UARTPortIO, syncStages: Int = 0) {
    txd.outputPin(uart.txd)
    val rxd_t = rxd.inputPin()
    uart.rxd := ShiftRegisterInit(rxd_t, syncStages, Bool(true))
  }
}

