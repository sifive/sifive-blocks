// See LICENSE for license details.
package sifive.blocks.devices.pwm

import Chisel._
import sifive.blocks.devices.pinctrl.{Pin}

class PWMSignals[T <: Data](private val pingen: () => T, val c: PWMParams) extends Bundle {
  val pwm: Vec[T] = Vec(c.ncmp, pingen())
}

class PWMPins[T <: Pin](pingen: () => T, c: PWMParams) extends PWMSignals[T](pingen, c)

object PWMPinsFromPort {
  def apply[T <: Pin] (pins: PWMSignals[T], port: PWMPortIO): Unit = {
    (pins.pwm zip port.port)  foreach {case (pin, port) =>
      pin.outputPin(port)
    }
  }
}
