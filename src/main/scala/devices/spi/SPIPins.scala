// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import sifive.blocks.devices.gpio.{GPIOPin, GPIOOutputPinCtrl, GPIOInputPinCtrl}

class SPIPinsIO(c: SPIConfigBase) extends SPIBundle(c) {
  val sck = new GPIOPin
  val dq = Vec(4, new GPIOPin)
  val cs = Vec(c.csWidth, new GPIOPin)
}

class SPIGPIOPort(c: SPIConfigBase, syncStages: Int = 0, driveStrength: Bool = Bool(false)) extends Module {
  val io = new SPIBundle(c) {
    val spi = new SPIPortIO(c).flip
    val pins = new SPIPinsIO(c)
  }

  GPIOOutputPinCtrl(io.pins.sck, io.spi.sck, ds = driveStrength)

  GPIOOutputPinCtrl(io.pins.dq, Bits(0, io.spi.dq.size))
  (io.pins.dq zip io.spi.dq).foreach {
    case (p, s) =>
      p.o.oval := s.o
      p.o.oe  := s.oe
      p.o.ie  := ~s.oe
      p.o.pue := Bool(true)
      p.o.ds  := driveStrength
      s.i := ShiftRegister(p.i.ival, syncStages)
  }

  GPIOOutputPinCtrl(io.pins.cs, io.spi.cs.asUInt)
  io.pins.cs.foreach(_.o.ds := driveStrength)
}
