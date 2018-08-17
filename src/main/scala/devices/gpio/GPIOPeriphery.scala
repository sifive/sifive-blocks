// See LICENSE for license details.
package sifive.blocks.devices.gpio

import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.BaseSubsystem

case object PeripheryGPIOKey extends Field[Seq[GPIOParams]]

trait HasPeripheryGPIO { this: BaseSubsystem =>
  val gpioNodes = p(PeripheryGPIOKey).map { ps => GPIO.attach(GPIOAttachParams(ps, pbus, ibus.fromAsync)).ioNode.makeSink }
}

trait HasPeripheryGPIOBundle {
  val gpio: Seq[GPIOPortIO]
}

trait HasPeripheryGPIOModuleImp extends LazyModuleImp with HasPeripheryGPIOBundle {
  val outer: HasPeripheryGPIO
  val gpio = outer.gpioNodes.zipWithIndex.map { case(n,i) => n.makeIO()(ValName(s"gpio_$i")) }
}
