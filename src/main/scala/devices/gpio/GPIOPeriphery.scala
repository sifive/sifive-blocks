// See LICENSE for license details.
package sifive.blocks.devices.gpio

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.coreplex.{HasPeripheryBus, HasInterruptBus}
import freechips.rocketchip.diplomacy.{LazyModule,LazyModuleImp}
import freechips.rocketchip.util.HeterogeneousBag

case object PeripheryGPIOKey extends Field[Seq[GPIOParams]]

trait HasPeripheryGPIO extends HasPeripheryBus with HasInterruptBus {
  val gpioParams = p(PeripheryGPIOKey)
  val gpios = gpioParams map { params =>
    val gpio = LazyModule(new TLGPIO(pbus.beatBytes, params))
    gpio.node := pbus.toVariableWidthSlaves
    ibus.fromSync := gpio.intnode
    gpio
  }
}

trait HasPeripheryGPIOBundle {
  val gpio: HeterogeneousBag[GPIOPortIO]
}

trait HasPeripheryGPIOModuleImp extends LazyModuleImp with HasPeripheryGPIOBundle {
  val outer: HasPeripheryGPIO
  val gpio = IO(HeterogeneousBag(outer.gpioParams.map(new GPIOPortIO(_))))

  (gpio zip outer.gpios) foreach { case (io, device) =>
    io <> device.module.io.port
  }
}
