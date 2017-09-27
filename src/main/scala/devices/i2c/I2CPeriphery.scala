// See LICENSE for license details.
package sifive.blocks.devices.i2c

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.coreplex.{HasPeripheryBus, HasInterruptBus}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}

case object PeripheryI2CKey extends Field[Seq[I2CParams]]

trait HasPeripheryI2C extends HasPeripheryBus {
  val i2cParams = p(PeripheryI2CKey)
  val i2c = i2cParams map { params =>
    val i2c = LazyModule(new TLI2C(pbus.beatBytes, params))
    i2c.node := pbus.toVariableWidthSlaves
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
