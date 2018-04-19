// See LICENSE for license details.
package sifive.blocks.devices.mockaon

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.util.AsyncResetReg

import sifive.blocks.util.{SlaveRegIF, GenericTimer}


class RTC extends GenericTimer {
  protected def countWidth = 48
  protected def cmpWidth = 32
  protected def ncmp = 1
  protected def countEn = countAlways
  override protected lazy val ip = Reg(next = elapsed(0))
  override protected lazy val zerocmp = Bool(false)
  protected lazy val countAlways = AsyncResetReg(io.regs.cfg.write.countAlwayys, io.regs.cfg.write_countalways && unlocked)(0)
  protected lazy val feed = Bool(false)
  lazy val io = new GenericTimerIO
}
