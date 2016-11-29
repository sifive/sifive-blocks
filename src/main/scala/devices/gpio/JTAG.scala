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
import junctions.{JTAGIO}

class JTAGPinsIO extends Bundle {

  val TCK    = new GPIOPin()
  val TMS    = new GPIOPin()
  val TDI    = new GPIOPin()
  val TDO    = new GPIOPin()
  val TRST_n = new GPIOPin()

}

class JTAGGPIOPort(drvTdo: Boolean = false)(implicit p: Parameters) extends Module {

  val io = new Bundle {
    val jtag = new JTAGIO(drvTdo)
    val pins = new JTAGPinsIO()
  }

  io.jtag.TCK  := GPIOInputPinCtrl(io.pins.TCK, pue = Bool(true)).asClock
  io.jtag.TMS  := GPIOInputPinCtrl(io.pins.TMS, pue = Bool(true))
  io.jtag.TDI  := GPIOInputPinCtrl(io.pins.TDI, pue = Bool(true))
  io.jtag.TRST := ~GPIOInputPinCtrl(io.pins.TRST_n, pue = Bool(true))

  GPIOOutputPinCtrl(io.pins.TDO, io.jtag.TDO)
  if (drvTdo) {
    io.pins.TDO.o.oe := io.jtag.DRV_TDO.get
  }

}
