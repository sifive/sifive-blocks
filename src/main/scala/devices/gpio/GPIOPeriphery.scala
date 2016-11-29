// See LICENSE for license details.
package sifive.blocks.devices.gpio

import Chisel._
import diplomacy.LazyModule
import rocketchip.{TopNetwork,TopNetworkModule}
import uncore.tilelink2.TLFragmenter

trait PeripheryGPIO {
  this: TopNetwork { val gpioConfig: GPIOConfig } =>
  val gpio = LazyModule(new TLGPIO(p, gpioConfig))
  gpio.node := TLFragmenter(peripheryBusConfig.beatBytes, cacheBlockBytes)(peripheryBus.node)
  intBus.intnode := gpio.intnode
}

trait PeripheryGPIOBundle {
  this: { val gpioConfig: GPIOConfig } =>
  val gpio = new GPIOPortIO(gpioConfig)
}

trait PeripheryGPIOModule {
  this: TopNetworkModule {
    val gpioConfig: GPIOConfig
    val outer: PeripheryGPIO
    val io: PeripheryGPIOBundle
  } =>
  io.gpio <> outer.gpio.module.io.port
}
