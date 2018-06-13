// See LICENSE for license details.
package sifive.blocks.devices.gpio

import Chisel._
import chisel3.experimental.{withClockAndReset}
import sifive.blocks.devices.pinctrl.{Pin}

// While this is a bit pendantic, it keeps the GPIO
// device more similar to the other devices. It's not 'special'
// even though it looks like something that more directly talks to
// a pin. It also makes it possible to change the exact
// type of pad this connects to.
class GPIOSignals[T <: Data](private val pingen: () => T, private val c: GPIOParams) extends Bundle {
  val pins = Vec(c.width, pingen())
}

class GPIOPins[T <: Pin](pingen: () => T, c: GPIOParams) extends GPIOSignals[T](pingen, c)

object GPIOPinsFromPort {

  def apply[T <: Pin](pins: GPIOSignals[T], port: GPIOPortIO, clock: Clock, reset: Bool){

    // This will just match up the components of the Bundle that
    // exist in both.
    withClockAndReset(clock, reset) {
      (pins.pins zip port.pins) foreach {case (pin, port) =>
        pin <> port
      }
    }
  }

  def apply[T <: Pin](pins: GPIOSignals[T], port: GPIOPortIO){

    // This will just match up the components of the Bundle that
    // exist in both.
    (pins.pins zip port.pins) foreach {case (pin, port) =>
      pin <> port
    }
  }
}
