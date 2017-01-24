// See LICENSE for license details.
package sifive.blocks.devices.i2c

import Chisel._
import sifive.blocks.devices.gpio.{GPIOPin, GPIOOutputPinCtrl}
import sifive.blocks.util.ShiftRegisterInit


class I2CPinsIO extends Bundle {
  val scl = new GPIOPin
  val sda = new GPIOPin
}

class I2CGPIOPort(syncStages: Int = 0) extends Module {
  val io = new Bundle{
    val i2c = new I2CPort().flip()
    val pins = new I2CPinsIO
  }

  GPIOOutputPinCtrl(io.pins.scl, io.i2c.scl.out, pue=true.B, ie = true.B)
  io.pins.scl.o.oe := io.i2c.scl.oe
  io.i2c.scl.in := ShiftRegisterInit(io.pins.scl.i.ival, syncStages, Bool(true))

  GPIOOutputPinCtrl(io.pins.sda, io.i2c.sda.out, pue=true.B, ie = true.B)
  io.pins.sda.o.oe := io.i2c.sda.oe
  io.i2c.sda.in := ShiftRegisterInit(io.pins.sda.i.ival, syncStages, Bool(true))
}
