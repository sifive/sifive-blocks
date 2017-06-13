// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import config.Field
import diplomacy.{LazyModule, LazyMultiIOModuleImp}
import rocketchip.HasSystemNetworks
import uncore.tilelink2.TLFragmenter

import sifive.blocks.devices.gpio.{GPIOPin, GPIOOutputPinCtrl, GPIOInputPinCtrl}
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
