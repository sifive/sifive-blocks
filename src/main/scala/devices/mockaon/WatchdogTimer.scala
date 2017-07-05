// See LICENSE for license details.
package sifive.blocks.devices.mockaon

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.util.AsyncResetReg

import sifive.blocks.util.{SlaveRegIF, GenericTimer}

object WatchdogTimer {
  def writeAnyExceptKey(regs: Bundle, keyReg: SlaveRegIF): Bool = {
    regs.elements.values.filter(_ ne keyReg).map({
      case v: Vec[SlaveRegIF] @unchecked => v.map(_.write.valid).reduce(_||_)
      case s: SlaveRegIF => s.write.valid
    }).reduce(_||_)
  }

  val key = 0x51F15E
}

class WatchdogTimer extends GenericTimer {
  protected def countWidth = 31
  protected def cmpWidth = 16
  protected def ncmp = 1
  protected lazy val countAlways = AsyncResetReg(io.regs.cfg.write.bits(12), io.regs.cfg.write.valid && unlocked)(0)
  override protected lazy val countAwake = AsyncResetReg(io.regs.cfg.write.bits(13), io.regs.cfg.write.valid && unlocked)(0)
  protected lazy val countEn = {
    val corerstSynchronized = Reg(next = Reg(next = io.corerst))
    countAlways || (countAwake && !corerstSynchronized)
  }
  override protected lazy val rsten = AsyncResetReg(io.regs.cfg.write.bits(8), io.regs.cfg.write.valid && unlocked)(0)
  protected lazy val ip = RegEnable(io.regs.cfg.write.bits(28) || elapsed(0), (io.regs.cfg.write.valid && unlocked) || elapsed(0))
  override protected lazy val unlocked = {
    val writeAny = WatchdogTimer.writeAnyExceptKey(io.regs, io.regs.key)
    AsyncResetReg(io.regs.key.write.bits === WatchdogTimer.key && !writeAny, io.regs.key.write.valid || writeAny)(0)
  }
  protected lazy val feed = {
    val food = 0xD09F00D
    unlocked && io.regs.feed.write.valid && io.regs.feed.write.bits === food
  }
  lazy val io = new GenericTimerIO {
    val corerst = Bool(INPUT)
    val rst = Bool(OUTPUT)
  }
  io.rst := AsyncResetReg(Bool(true), rsten && elapsed(0))
}

class RTC extends GenericTimer {
  protected def countWidth = 48
  protected def cmpWidth = 32
  protected def ncmp = 1
  protected def countEn = countAlways
  override protected lazy val ip = Reg(next = elapsed(0))
  override protected lazy val zerocmp = Bool(false)
  protected lazy val countAlways = AsyncResetReg(io.regs.cfg.write.bits(12), io.regs.cfg.write.valid && unlocked)(0)
  protected lazy val feed = Bool(false)
  lazy val io = new GenericTimerIO
}
