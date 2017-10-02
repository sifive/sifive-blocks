// See LICENSE for license details.
package sifive.blocks.devices.pwm

import Chisel._
import chisel3.experimental.MultiIOModule
import Chisel.ImplicitConversions._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import sifive.blocks.util.GenericTimer

// Core PWM Functionality  & Register Interface

class PWM(val ncmp: Int = 4, val cmpWidth: Int = 16) extends GenericTimer {
  protected def countWidth = ((1 << scaleWidth) - 1) + cmpWidth
  protected lazy val countAlways = RegEnable(io.regs.cfg.write.bits(12), Bool(false), io.regs.cfg.write.valid && unlocked)
  protected lazy val feed = count.carryOut(scale + UInt(cmpWidth))
  protected lazy val countEn = Wire(Bool())
  override protected lazy val oneShot = RegEnable(io.regs.cfg.write.bits(13) && !countReset, Bool(false), (io.regs.cfg.write.valid && unlocked) || countReset)
  override protected lazy val extra  = RegEnable(io.regs.cfg.write.bits(20 + ncmp - 1, 20), init = 0.U, enable = io.regs.cfg.write.valid && unlocked)
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

  val invert = extra

  io.gpio := io.gpio.fromBits((ip & ~(gang & Cat(ip(0), ip >> 1))) ^ invert)
  countEn := countAlways || oneShot
}

case class PWMParams(
  address: BigInt,
  size: Int = 0x1000,
  regBytes: Int = 4,
  ncmp: Int = 4,
  cmpWidth: Int = 16)

trait HasPWMBundleContents extends Bundle {
  def params: PWMParams
  val gpio = Vec(params.ncmp, Bool()).asOutput
}

trait HasPWMModuleContents extends MultiIOModule with HasRegMap {
  val io: HasPWMBundleContents
  val params: PWMParams

  val pwm = Module(new PWM(params.ncmp, params.cmpWidth))

  interrupts := pwm.io.ip
  io.gpio := pwm.io.gpio

  regmap((GenericTimer.timerRegMap(pwm, 0, params.regBytes)):_*)
}

class TLPWM(w: Int, c: PWMParams)(implicit p: Parameters)
  extends TLRegisterRouter(c.address, "pwm", Seq("sifive,pwm0"), interrupts = c.ncmp, size = c.size, beatBytes = w)(
  new TLRegBundle(c, _)    with HasPWMBundleContents)(
  new TLRegModule(c, _, _) with HasPWMModuleContents)
