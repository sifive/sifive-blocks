// See LICENSE for license details.
package sifive.blocks.devices.i2c

import Chisel._
import config._
import regmapper._
import uncore.tilelink2._
import rocketchip.PeripheryBusConfig
import util.AsyncResetRegVec
import sifive.blocks.devices.gpio.{GPIOPinCtrl}

case class I2CConfig(address: BigInt)

trait HasI2CParameters {
  implicit val p: Parameters
  val params: I2CConfig
  val c = params
}

class I2CPin extends Bundle {
  val in  = Bool(INPUT)
  val out = Bool(OUTPUT)
  val oe  = Bool(OUTPUT)
}

class I2CPort extends Bundle {
  val scl = new I2CPin
  val sda = new I2CPin
}

trait I2CBundle extends Bundle with HasI2CParameters {
  val port = new I2CPort
}

trait I2CModule extends Module with HasI2CParameters with HasRegMap {
  val io: I2CBundle

  val prescaler_lo = Reg(UInt(8.W))  // low byte clock prescaler register
  val prescaler_hi = Reg(UInt(8.W))  // high byte clock prescaler register
  val control      = Reg(UInt(8.W))  // control register
  val data         = Reg(UInt(8.W))  // write: transmit byte, read: receive byte
  val cmd_status   = Reg(UInt(8.W))  // write: command, read: status

  // Note that these are out of order.
  regmap(
    I2CCtrlRegs.prescaler_lo -> Seq(RegField(8, prescaler_lo)),
    I2CCtrlRegs.prescaler_hi -> Seq(RegField(8, prescaler_hi)),
    I2CCtrlRegs.control      -> Seq(RegField(8, control)),
    I2CCtrlRegs.data         -> Seq(RegField(8, data)),
    I2CCtrlRegs.cmd_status   -> Seq(RegField(8, cmd_status))
  )
}

// Magic TL2 Incantation to create a TL2 Slave
class TLI2C(c: I2CConfig)(implicit p: Parameters)
  extends TLRegisterRouter(c.address, interrupts = 1, beatBytes = p(PeripheryBusConfig).beatBytes)(
  new TLRegBundle(c, _)    with I2CBundle)(
  new TLRegModule(c, _, _) with I2CModule)
