// See LICENSE for license details.
package sifive.blocks.devices.i2c

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}

case object PeripheryI2CKey extends Field[Seq[I2CParams]]

trait HasPeripheryI2C { this: BaseSubsystem =>
  val i2cParams = p(PeripheryI2CKey)
  val i2c = i2cParams.zipWithIndex.map { case(params, i) =>
    val name = Some(s"i2c_$i")
    val i2c = LazyModule(new TLI2C(pbus.beatBytes, params)).suggestName(name)
    pbus.toVariableWidthSlave(name) { i2c.node }
    ibus.fromSync := i2c.intnode
    i2c
  }
}

trait HasPeripheryI2CBundle {
  val i2c: Vec[I2CPort]
}

trait HasPeripheryI2CModuleImp extends LazyModuleImp with HasPeripheryI2CBundle {
  val outer: HasPeripheryI2C
  val i2c = IO(Vec(outer.i2cParams.size, new I2CPort))

  (i2c zip outer.i2c).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}
