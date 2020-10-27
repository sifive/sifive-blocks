// See LICENSE for license details.
package sifive.blocks.devices.gpio

import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.BaseSubsystem

case object PeripheryGPIOKey extends Field[Seq[GPIOParams]](Nil)

trait HasPeripheryGPIO { this: BaseSubsystem =>
  val (gpioNodes, iofNodes) = p(PeripheryGPIOKey).map { ps =>
    val gpio = GPIOAttachParams(ps).attachTo(this)
    (gpio.ioNode.makeSink(), gpio.iofNode.map { _.makeSink() })
  }.unzip
}

trait HasPeripheryGPIOBundle {
  val gpio: Seq[GPIOPortIO]
  val iof: Seq[Option[IOFPortIO]]
}

trait HasPeripheryGPIOModuleImp extends LazyModuleImp with HasPeripheryGPIOBundle {
  val outer: HasPeripheryGPIO
  val gpio = outer.gpioNodes.zipWithIndex.map { case(n,i) => n.makeIO()(ValName(s"gpio_$i")) }
  val iof = outer.iofNodes.zipWithIndex.map { case(o,i) => o.map { n => n.makeIO()(ValName(s"iof_$i")) } }
}
