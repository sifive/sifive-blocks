// See LICENSE for license details.
package sifive.blocks.devices.gpio

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.diplomacy.{LazyModule,LazyModuleImp}
import freechips.rocketchip.util.HeterogeneousBag

case object PeripheryGPIOKey extends Field[Seq[GPIOParams]]

trait HasPeripheryGPIO { this: BaseSubsystem =>
  val gpioParams = p(PeripheryGPIOKey)
  val gpios = gpioParams.zipWithIndex.map { case(params, i) =>
    val name = Some(s"gpio_$i")
    val gpio = LazyModule(new TLGPIO(pbus.beatBytes, params)).suggestName(name)
    pbus.toVariableWidthSlave(name) { gpio.node }
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
