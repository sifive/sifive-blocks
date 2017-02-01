// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import config._
import diplomacy._
import uncore.tilelink2._
import rocketchip._

import sifive.blocks.devices.gpio.{GPIOPin, GPIOOutputPinCtrl, GPIOInputPinCtrl}
import sifive.blocks.util.ShiftRegisterInit

trait PeripheryUART {
  this: TopNetwork {
    val uartConfigs: Seq[UARTConfig]
  } =>
  val uart = uartConfigs.zipWithIndex.map { case (c, i) =>
    val uart = LazyModule(new UART(c))
    uart.node := TLFragmenter(peripheryBusConfig.beatBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := uart.intnode
    uart
  }
}

trait PeripheryUARTBundle {
  this: { val uartConfigs: Seq[UARTConfig] } =>
  val uarts = Vec(uartConfigs.size, new UARTPortIO)
}

trait PeripheryUARTModule {
  this: TopNetworkModule {
    val outer: PeripheryUART
    val io: PeripheryUARTBundle
  } =>
  (io.uarts zip outer.uart).foreach { case (io, device) =>
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
