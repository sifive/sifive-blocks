// See LICENSE for license details.
package sifive.blocks.devices.gpio

object GPIOCtrlRegs {
  val value       = 0x00
  val input_en    = 0x08
  val output_en   = 0x10
  val port        = 0x18
  val pullup_en   = 0x20
  val drive       = 0x28
  val rise_ie     = 0x30
  val rise_ip     = 0x38
  val fall_ie     = 0x40
  val fall_ip     = 0x48
  val high_ie     = 0x50
  val high_ip     = 0x58
  val low_ie      = 0x60
  val low_ip      = 0x68
  val iof_en      = 0x70
  val iof_sel     = 0x78
  val out_xor     = 0x80
  val passthru_high_ie = 0x88
  val passthru_low_ie  = 0x90
}
