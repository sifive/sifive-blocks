// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.coreplex.{HasPeripheryBus, HasInterruptBus}
import freechips.rocketchip.diplomacy.{LazyModule, LazyMultiIOModuleImp}
import sifive.blocks.devices.gpio.{GPIOPin, GPIOOutputPinCtrl, GPIOInputPinCtrl}
import sifive.blocks.util.ShiftRegisterInit

case object PeripheryUARTKey extends Field[Seq[UARTParams]]

trait HasPeripheryUART extends HasPeripheryBus with HasInterruptBus {
  val uartParams = p(PeripheryUARTKey)  
  val uarts = uartParams map { params =>
    val uart = LazyModule(new TLUART(pbus.beatBytes, params))
    uart.node := pbus.toVariableWidthSlaves
    ibus.fromSync := uart.intnode
    uart
  }
}

trait HasPeripheryUARTBundle {
  val uarts: Vec[UARTPortIO]

  def tieoffUARTs(dummy: Int = 1) {
    uarts.foreach { _.rxd := UInt(1) }
  }

  def UARTtoGPIOPins(syncStages: Int = 0): Seq[UARTPinsIO] = uarts.map { u =>
    val pins = Module(new UARTGPIOPort(syncStages))
    pins.io.uart <> u
    pins.io.pins
  }
}

trait HasPeripheryUARTModuleImp extends LazyMultiIOModuleImp with HasPeripheryUARTBundle {
  val outer: HasPeripheryUART
  val uarts = IO(Vec(outer.uartParams.size, new UARTPortIO))

  (uarts zip outer.uarts).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}

class UARTPinsIO extends Bundle {
  val rxd = new GPIOPin
  val txd = new GPIOPin
}

class UARTGPIOPort(syncStages: Int = 0) extends Module {
  val io = new Bundle{
    val uart = new UARTPortIO().flip()
    val pins = new UARTPinsIO
  }

  GPIOOutputPinCtrl(io.pins.txd, io.uart.txd)
  val rxd = GPIOInputPinCtrl(io.pins.rxd)
  io.uart.rxd := ShiftRegisterInit(rxd, syncStages, Bool(true))
}
