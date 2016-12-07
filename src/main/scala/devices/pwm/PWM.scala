// See LICENSE for license details.
package sifive.blocks.devices.pwm

import Chisel._
import Chisel.ImplicitConversions._
import config._
import regmapper._
import rocketchip.PeripheryBusConfig
import uncore.tilelink2._
import util._

import sifive.blocks.util.GenericTimer

// Core PWM Functionality  & Register Interface

class PWM(val ncmp: Int = 4, val cmpWidth: Int = 16)(implicit p: Parameters) extends GenericTimer {
  protected def countWidth = ((1 << scaleWidth) - 1) + cmpWidth
  protected lazy val countAlways = RegEnable(io.regs.cfg.write.bits(12), Bool(false), io.regs.cfg.write.valid && unlocked)
  protected lazy val feed = count.carryOut(scale + UInt(cmpWidth))
  protected lazy val countEn = Wire(Bool())
  override protected lazy val oneShot = RegEnable(io.regs.cfg.write.bits(13) && !countReset, Bool(false), (io.regs.cfg.write.valid && unlocked) || countReset)
  override protected lazy val center = RegEnable(io.regs.cfg.write.bits(16 + ncmp - 1, 16), io.regs.cfg.write.valid && unlocked)
  override protected lazy val gang = RegEnable(io.regs.cfg.write.bits(24 + ncmp - 1, 24), io.regs.cfg.write.valid && unlocked)
  override protected lazy val deglitch = RegEnable(io.regs.cfg.write.bits(10), io.regs.cfg.write.valid && unlocked)(0)
  override protected lazy val sticky = RegEnable(io.regs.cfg.write.bits(8), io.regs.cfg.write.valid && unlocked)(0)
  override protected lazy val ip = {
    val doSticky = Reg(next = (deglitch && !countReset) || sticky)
    val sel = ((0 until ncmp).map(i => s(cmpWidth-1) && center(i))).asUInt
    val reg = Reg(UInt(width = ncmp))
    reg := (sel & elapsed.asUInt) | (~sel & (elapsed.asUInt | (Fill(ncmp, doSticky) & reg)))
    when (io.regs.cfg.write.valid && unlocked) { reg := io.regs.cfg.write.bits(28 + ncmp - 1, 28) }
    reg
  }
  lazy val io = new GenericTimerIO {
    val gpio = Vec(ncmp, Bool()).asOutput
  }
  io.gpio := io.gpio.fromBits(ip & ~(gang & Cat(ip(0), ip >> 1)))
  countEn := countAlways || oneShot
}

case class PWMConfig(
  address: BigInt,
  size: Int = 0x1000,
  regBytes: Int = 4,
  ncmp: Int = 4,
  cmpWidth: Int = 16)
{
  val bc = new PWMBundleConfig(ncmp)
}

case class PWMBundleConfig(
  ncmp: Int)
{
  def union(that: PWMBundleConfig): PWMBundleConfig =
    PWMBundleConfig(scala.math.max(ncmp, that.ncmp))
}

trait HasPWMParameters {
  implicit val p: Parameters
  val params: PWMConfig
  val c = params
}

trait PWMBundle extends Bundle with HasPWMParameters {
  val gpio = Vec(c.ncmp, Bool()).asOutput
}

trait PWMModule extends Module with HasRegMap with HasPWMParameters {
  val io: PWMBundle

  val pwm = Module(new PWM(c.ncmp, c.cmpWidth))

  interrupts := pwm.io.ip
  io.gpio := pwm.io.gpio

  regmap((GenericTimer.timerRegMap(pwm, 0, c.regBytes)):_*)
}

class TLPWM(c: PWMConfig)(implicit p: Parameters)
  extends TLRegisterRouter(c.address, interrupts = c.ncmp, size = c.size, beatBytes = p(PeripheryBusConfig).beatBytes)(
  new TLRegBundle(c, _)    with PWMBundle)(
  new TLRegModule(c, _, _) with PWMModule)
