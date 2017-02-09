// See LICENSE for license details.
package sifive.blocks.devices.i2c

// matching Open Cores I2C to re-use Linux driver
// http://lxr.free-electrons.com/source/drivers/i2c/busses/i2c-ocores.c?v=4.6

object I2CCtrlRegs {
  val prescaler_lo = 0x00  // low byte clock prescaler register
  val prescaler_hi = 0x04  // high byte clock prescaler register
  val control      = 0x08  // control register
  val data         = 0x0c  // write: transmit byte, read: receive byte
  val cmd_status   = 0x10  // write: command, read: status
}
