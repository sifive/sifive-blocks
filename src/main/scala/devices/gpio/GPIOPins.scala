// See LICENSE for license details.
package sifive.blocks.devices.gpio

import Chisel._
import sifive.blocks.devices.pinctrl.{Pin}

// While this is a bit pendantic, it keeps the GPIO
// device more similar to the other devices. It's not 'special'
// even though it looks like something that more directly talks to
// a pin. It also makes it possible to change the exact
// type of pad this connects to.
class GPIOSignals[T <: Data] (pingen: ()=> T,  c: GPIOParams) extends Bundle {
  val pins = Vec(c.width, pingen())

  override def cloneType: this.type =
    this.getClass.getConstructors.head.newInstance(pingen, c).asInstanceOf[this.type]
}

class GPIOPins[T <: Pin] (pingen: ()=> T,  c: GPIOParams) extends GPIOSignals[T](pingen, c) {
  override def cloneType: this.type =
    this.getClass.getConstructors.head.newInstance(pingen, c).asInstanceOf[this.type]
}

object GPIOPinsFromPort {

  def apply[T <: Pin](pins: GPIOSignals[T], port: GPIOPortIO){

    // This will just match up the components of the Bundle that
    // exist in both.
    (pins.pins zip port.pins) foreach {case (pin, port) =>
      pin <> port
    }
  }
}
