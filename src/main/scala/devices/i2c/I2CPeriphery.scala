// See LICENSE for license details.
package sifive.blocks.devices.i2c

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.coreplex.{HasPeripheryBus, HasInterruptBus}
import freechips.rocketchip.diplomacy.{LazyModule, LazyMultiIOModuleImp}

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
  val i2cs: Vec[I2CPort]

  def I2CtoGPIOPins(syncStages: Int = 0): Seq[I2CPinsIO] = i2cs.map { i =>
    val pins = Module(new I2CGPIOPort(syncStages))
    pins.io.i2c <> i
    pins.io.pins
  }
}

trait HasPeripheryI2CModuleImp extends LazyMultiIOModuleImp with HasPeripheryI2CBundle {
  val outer: HasPeripheryI2C
  val i2cs = IO(Vec(outer.i2cParams.size, new I2CPort))

  (i2cs zip outer.i2c).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}
