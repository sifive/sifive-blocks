// See LICENSE for license details.
package sifive.blocks.devices.jtag

import Chisel._

// ------------------------------------------------------------
// SPI, UART, etc are with their respective packages,
// JTAG doesn't really correspond directly to a device, but it does
// define pins as those devices do.
// ------------------------------------------------------------

import freechips.rocketchip.config._
import freechips.rocketchip.jtag.{JTAGIO}
import sifive.blocks.devices.pinctrl.{Pin, PinCtrl}

class JTAGSignals[T <: Data](pingen: () => T, hasTRSTn: Boolean = true) extends Bundle {
  val TCK         = pingen()
  val TMS         = pingen()
  val TDI         = pingen()
  val TDO        = pingen()
  val TRSTn = if (hasTRSTn) Option(pingen()) else None
}

class JTAGPins[T <: Pin](pingen: () => T, hasTRSTn: Boolean = true) extends JTAGSignals[T](pingen, hasTRSTn)

object JTAGPinsFromPort {

  def apply[T <: Pin] (pins: JTAGSignals[T], jtag: JTAGIO): Unit = {
    jtag.TCK  := pins.TCK.inputPin (pue = Bool(true)).asClock
    jtag.TMS  := pins.TMS.inputPin (pue = Bool(true))
    jtag.TDI  := pins.TDI.inputPin(pue = Bool(true))
    jtag.TRSTn.foreach{t => t := pins.TRSTn.get.inputPin(pue = Bool(true))}

    pins.TDO.outputPin(jtag.TDO.data)
    pins.TDO.o.oe := jtag.TDO.driven
  }
}
