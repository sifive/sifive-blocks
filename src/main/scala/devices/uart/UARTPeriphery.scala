// See LICENSE for license details.
package sifive.blocks.devices.uart

import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{BaseSubsystem, PeripheryBusKey}

case object PeripheryUARTKey extends Field[Seq[UARTParams]]

trait HasPeripheryUART { this: BaseSubsystem =>
  val uarts = p(PeripheryUARTKey).map { ps =>
    val divinit = (p(PeripheryBusKey).frequency / 115200).toInt
    UART.attach(AttachedUARTParams(ps, divinit), pbus, ibus.fromSync, None)
  }
  val uartNodes = uarts.map(_.ioNode.makeSink())
}

trait HasPeripheryUARTBundle {
  val uart: Seq[UARTPortIO]
}


trait HasPeripheryUARTModuleImp extends LazyModuleImp with HasPeripheryUARTBundle {
  val outer: HasPeripheryUART
  val uart = outer.uartNodes.map(_.makeIO())
}
