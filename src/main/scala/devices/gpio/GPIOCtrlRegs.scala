// See LICENSE for license details.
package sifive.blocks.devices.gpio

object GPIOCtrlRegs {
  def value(implicit mode64: Boolean)             = if(mode64) 0x00 else 0x00 
  def input_en(implicit mode64: Boolean)          = if(mode64) 0x08 else 0x04
  def output_en(implicit mode64: Boolean)         = if(mode64) 0x10 else 0x08
  def port(implicit mode64: Boolean)              = if(mode64) 0x18 else 0x0c
  def pullup_en(implicit mode64: Boolean)         = if(mode64) 0x20 else 0x10
  def drive(implicit mode64: Boolean)             = if(mode64) 0x28 else 0x14
  def rise_ie(implicit mode64: Boolean)           = if(mode64) 0x30 else 0x18
  def rise_ip(implicit mode64: Boolean)           = if(mode64) 0x38 else 0x1c
  def fall_ie(implicit mode64: Boolean)           = if(mode64) 0x40 else 0x20
  def fall_ip(implicit mode64: Boolean)           = if(mode64) 0x48 else 0x24
  def high_ie(implicit mode64: Boolean)           = if(mode64) 0x50 else 0x28
  def high_ip(implicit mode64: Boolean)           = if(mode64) 0x58 else 0x2c
  def low_ie(implicit mode64: Boolean)            = if(mode64) 0x60 else 0x30
  def low_ip(implicit mode64: Boolean)            = if(mode64) 0x68 else 0x34
  def iof_en(implicit mode64: Boolean)            = if(mode64) 0x70 else 0x38
  def iof_sel(implicit mode64: Boolean)           = if(mode64) 0x78 else 0x3c
  def out_xor(implicit mode64: Boolean)           = if(mode64) 0x80 else 0x40
  def passthru_high_ie(implicit mode64: Boolean)  = if(mode64) 0x88 else 0x44
  def passthru_low_ie(implicit mode64: Boolean)   = if(mode64) 0x90 else 0x48
}
