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
import util.HeterogeneousBag

case object PeripheryGPIOKey extends Field[Seq[GPIOParams]]

trait HasPeripheryGPIO extends HasTopLevelNetworks {
  val gpioParams = p(PeripheryGPIOKey)
  val gpio = gpioParams map {params =>
    val gpio = LazyModule(new TLGPIO(peripheryBusBytes, params))
    gpio.node := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := gpio.intnode
    gpio
  }
}

trait HasPeripheryGPIOBundle extends HasTopLevelNetworksBundle {
  val outer: HasPeripheryGPIO
  val gpio = HeterogeneousBag(outer.gpioParams.map(new GPIOPortIO(_)))
}

trait HasPeripheryGPIOModule extends HasTopLevelNetworksModule {
  val outer: HasPeripheryGPIO
  val io: HasPeripheryGPIOBundle
  (io.gpio zip outer.gpio) foreach { case (io, device) =>
    io <> device.module.io.port
  }
}
