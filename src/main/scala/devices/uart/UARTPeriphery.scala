// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import config.Field
import diplomacy.LazyModule
import rocketchip.{
  HasTopLevelNetworks,
  HasTopLevelNetworksBundle,
  HasTopLevelNetworksModule
}
import uncore.tilelink2._

import sifive.blocks.devices.gpio.{GPIOPin, GPIOOutputPinCtrl, GPIOInputPinCtrl}
import sifive.blocks.util.ShiftRegisterInit

case object PeripheryUARTKey extends Field[Seq[UARTParams]]

trait HasPeripheryUART extends HasTopLevelNetworks {
  val uartParams = p(PeripheryUARTKey)  
  val uarts = uartParams map { params =>
    val uart = LazyModule(new TLUART(peripheryBusBytes, params))
    uart.node := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := uart.intnode
    uart
  }
}

trait HasPeripheryUARTBundle extends HasTopLevelNetworksBundle {
  val outer: HasPeripheryUART
  val uarts = Vec(outer.uartParams.size, new UARTPortIO)
}

trait HasPeripheryUARTModule extends HasTopLevelNetworksModule {
  val outer: HasPeripheryUART
  val io: HasPeripheryUARTBundle
  (io.uarts zip outer.uarts).foreach { case (io, device) =>
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
