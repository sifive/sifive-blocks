// See LICENSE for license details.
package sifive.blocks.devices.uart

object UARTCtrlRegs {
  val txfifo = 0x00
  val rxfifo = 0x04
  val txctrl = 0x08
  val txmark = 0x0a
  val rxctrl = 0x0c
  val rxmark = 0x0e

  val ie     = 0x10
  val ip     = 0x14
  val div    = 0x18
}
