// See LICENSE for license details.
package sifive.blocks.devices.i2c

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{BaseSubsystem}

case object PeripheryI2CKey extends Field[Seq[I2CParams]]

trait HasPeripheryI2C { this: BaseSubsystem =>
  val i2cs =  p(PeripheryI2CKey).map { ps =>
    I2C.attach(AttachedI2CParams(ps), pbus, ibus.fromSync, None)
  }
  val i2cNodes = i2cs.map(_.ioNode.makeSink())
}

trait HasPeripheryI2CBundle {
  val i2c: Seq[I2CPort]
}

trait HasPeripheryI2CModuleImp extends LazyModuleImp with HasPeripheryI2CBundle {
  val outer: HasPeripheryI2C
  val i2c = outer.i2cNodes.map(_.makeIO())
}
