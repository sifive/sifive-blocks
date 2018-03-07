// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import chisel3.experimental.{withClockAndReset}
import freechips.rocketchip.util.{SynchronizerShiftReg}
import sifive.blocks.devices.pinctrl.{PinCtrl, Pin}

class SPISignals[T <: Data](private val pingen: () => T, c: SPIParamsBase) extends SPIBundle(c) {

  val sck = pingen()
  val dq  = Vec(4, pingen())
  val cs  = Vec(c.csWidth, pingen())
}

class SPIPins[T <: Pin] (pingen: ()=> T, c: SPIParamsBase) extends SPISignals(pingen, c)

object SPIPinsFromPort {
  
  def apply[T <: Pin](pins: SPISignals[T], spi: SPIPortIO, clock: Clock, reset: Bool,
    syncStages: Int = 0, driveStrength: Bool = Bool(false)) {

    withClockAndReset(clock, reset) {
      pins.sck.outputPin(spi.sck, ds = driveStrength)

      (pins.dq zip spi.dq).zipWithIndex.foreach {case ((p, s), i) =>
        p.outputPin(s.o, pue = Bool(true), ds = driveStrength)
        p.o.oe := s.oe
        p.o.ie := ~s.oe
        s.i := SynchronizerShiftReg(p.i.ival, syncStages, name = Some(s"spi_dq_${i}_sync"))
      }

      (pins.cs zip spi.cs) foreach { case (c, s) =>
        c.outputPin(s, ds = driveStrength)
      }
    }
  }
}
