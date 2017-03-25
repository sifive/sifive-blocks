// See LICENSE for license details.
package sifive.blocks.devices.gpio

import Chisel._

// ------------------------------------------------------------
// SPI, UART, etc are with their
// respective packages,
// This file is for those that don't seem to have a good place
// to put them otherwise.
// ------------------------------------------------------------

import config._
import jtag.{JTAGIO}

class JTAGPinsIO(hasTRSTn: Boolean = true) extends Bundle {

  val TCK    = new GPIOPin()
  val TMS    = new GPIOPin()
  val TDI    = new GPIOPin()
  val TDO    = new GPIOPin()
  val TRSTn  = if (hasTRSTn) Option(new GPIOPin()) else None

}

class JTAGGPIOPort(hasTRSTn: Boolean = true)(implicit p: Parameters) extends Module {

  val io = new Bundle {
    // TODO: make this not hard-coded true.
    val jtag = new JTAGIO(hasTRSTn)
    val pins = new JTAGPinsIO(hasTRSTn)
  }

  io.jtag.TCK  := GPIOInputPinCtrl(io.pins.TCK, pue = Bool(true)).asClock
  io.jtag.TMS  := GPIOInputPinCtrl(io.pins.TMS, pue = Bool(true))
  io.jtag.TDI  := GPIOInputPinCtrl(io.pins.TDI, pue = Bool(true))
  io.jtag.TRSTn.foreach{t => t := GPIOInputPinCtrl(io.pins.TRSTn.get, pue = Bool(true))}

  GPIOOutputPinCtrl(io.pins.TDO, io.jtag.TDO.data)
  io.pins.TDO.o.oe := io.jtag.TDO.driven
}
