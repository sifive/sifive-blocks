// See LICENSE for license details.
package sifive.blocks.devices.i2c

import Chisel._
import chisel3.experimental.{withClockAndReset}
import sifive.blocks.devices.pinctrl.{Pin, PinCtrl}
import sifive.blocks.util.ShiftRegisterInit


class I2CPins[T <: Pin](pingen: () => T) extends Bundle {

  val scl: T = pingen()
  val sda: T = pingen()

  override def cloneType: this.type =
    this.getClass.getConstructors.head.newInstance(pingen).asInstanceOf[this.type]

  def fromI2CPort(i2c: I2CPort, clock: Clock, reset: Bool, syncStages: Int = 0) = {
    withClockAndReset(clock, reset) {
      scl.outputPin(i2c.scl.out, pue=true.B, ie = true.B)
      scl.o.oe := i2c.scl.oe
      i2c.scl.in := ShiftRegisterInit(scl.i.ival, syncStages, Bool(true))

      sda.outputPin(i2c.sda.out, pue=true.B, ie = true.B)
      sda.o.oe := i2c.sda.oe
      i2c.sda.in := ShiftRegisterInit(sda.i.ival, syncStages, Bool(true))
    }
  }
}
