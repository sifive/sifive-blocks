// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import chisel3.experimental.{withClockAndReset}
import sifive.blocks.devices.pinctrl.{PinCtrl, Pin}

class SPIPins[T <: Pin] (pingen: ()=> T, c: SPIParamsBase) extends SPIBundle(c) {

  val sck: T      = pingen()
  val dq: Vec[T]  = Vec(4, pingen())
  val cs: Vec[T]  = Vec(c.csWidth, pingen())

  def fromSPIPort(spi: SPIPortIO, clock: Clock, reset: Bool,
    syncStages: Int = 0, driveStrength: Bool = Bool(false)) {

    withClockAndReset(clock, reset) {
      sck.outputPin(spi.sck, ds = driveStrength)

      (dq zip spi.dq).foreach {case (p, s) =>
        p.outputPin(s.o, pue = Bool(true), ds = driveStrength)
        p.o.oe := s.oe
        p.o.ie := ~s.oe
        s.i := ShiftRegister(p.i.ival, syncStages)
      }

      (cs zip spi.cs) foreach { case (c, s) =>
        c.outputPin(s, ds = driveStrength)
      }
    }
  }
}
