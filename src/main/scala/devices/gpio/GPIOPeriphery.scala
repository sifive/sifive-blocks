// See LICENSE for license details.
package sifive.blocks.devices.gpio

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy.{LazyModule,LazyMultiIOModuleImp}
import freechips.rocketchip.chip.HasSystemNetworks
import freechips.rocketchip.tilelink.TLFragmenter
import freechips.rocketchip.util.HeterogeneousBag

case object PeripheryGPIOKey extends Field[Seq[GPIOParams]]

trait HasPeripheryGPIO extends HasSystemNetworks {
  val gpioParams = p(PeripheryGPIOKey)
  val gpio = gpioParams map {params =>
    val gpio = LazyModule(new TLGPIO(peripheryBusBytes, params))
    gpio.node := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := gpio.intnode
    gpio
  }
}

trait HasPeripheryGPIOBundle {
  val gpio: HeterogeneousBag[GPIOPortIO]
}

trait HasPeripheryGPIOModuleImp extends LazyMultiIOModuleImp with HasPeripheryGPIOBundle {
  val outer: HasPeripheryGPIO
  val gpio = IO(HeterogeneousBag(outer.gpioParams.map(new GPIOPortIO(_))))

  (gpio zip outer.gpio) foreach { case (io, device) =>
    io <> device.module.io.port
  }
}
