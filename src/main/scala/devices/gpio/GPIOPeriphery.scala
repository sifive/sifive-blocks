// See LICENSE for license details.
package sifive.blocks.devices.gpio

import Chisel._
import config.Field
import diplomacy.LazyModule
import rocketchip.{
  HasTopLevelNetworks,
  HasTopLevelNetworksBundle,
  HasTopLevelNetworksModule
}
import uncore.tilelink2.TLFragmenter

case object PeripheryGPIOKey extends Field[GPIOParams]

trait HasPeripheryGPIO extends HasTopLevelNetworks {
  val gpioParams = p(PeripheryGPIOKey)
  val gpio = LazyModule(new TLGPIO(peripheryBusBytes, gpioParams))
  gpio.node := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
  intBus.intnode := gpio.intnode
}

trait HasPeripheryGPIOBundle extends HasTopLevelNetworksBundle {
  val outer: HasPeripheryGPIO
  val gpio = new GPIOPortIO(outer.gpioParams)
}

trait HasPeripheryGPIOModule extends HasTopLevelNetworksModule {
  val outer: HasPeripheryGPIO
  val io: HasPeripheryGPIOBundle
  io.gpio <> outer.gpio.module.io.port
}
