// See LICENSE for license details.
package sifive.blocks.devices.gpio

object GPIOCtrlRegs {
  val value       = 0x00
  val input_en    = 0x04
  val output_en   = 0x08
  val port        = 0x0c
  val pullup_en   = 0x10
  val drive       = 0x14
  val rise_ie     = 0x18
  val rise_ip     = 0x1c
  val fall_ie     = 0x20
  val fall_ip     = 0x24
  val high_ie     = 0x28
  val high_ip     = 0x2c
  val low_ie      = 0x30
  val low_ip      = 0x34
  val iof_en      = 0x38
  val iof_sel     = 0x3c
  val out_xor     = 0x40
}
