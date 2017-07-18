// See LICENSE for license details.
package sifive.blocks.devices.gpio

import Chisel._

// ------------------------------------------------------------
// SPI, UART, etc are with their
// respective packages,
// This file is for those that don't seem to have a good place
// to put them otherwise.
// ------------------------------------------------------------

import freechips.rocketchip.config._
import freechips.rocketchip.jtag.{JTAGIO}
import sifive.blocks.devices.pinctrl.{Pin, PinCtrl}

class JTAGPins[T <: Pin](pingen: () => T, hasTRSTn: Boolean = true) extends Bundle {

  val TCK         = pingen()
  val TMS         = pingen()
  val TDI         = pingen()
  val TDO        = pingen()
  val TRSTn = if (hasTRSTn) Option(pingen()) else None

  def fromJTAGPort(jtag: JTAGIO): Unit = {
    jtag.TCK  := TCK.inputPin (pue = Bool(true)).asClock
    jtag.TMS  := TMS.inputPin (pue = Bool(true))
    jtag.TDI  := TDI.inputPin(pue = Bool(true))
    jtag.TRSTn.foreach{t => t := TRSTn.get.inputPin(pue = Bool(true))}

    TDO.outputPin(jtag.TDO.data)
    TDO.o.oe := jtag.TDO.driven
  }
}
