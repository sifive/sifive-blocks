// See LICENSE for license details.
package sifive.blocks.devices.mockaon

import Chisel._
import Chisel.ImplicitConversions._
import chisel3.experimental.MultiIOModule
import freechips.rocketchip.util.AsyncResetReg
import freechips.rocketchip.regmapper.{RegFieldDesc}

import sifive.blocks.util.{SlaveRegIF, GenericTimer, GenericTimerIO, GenericTimerCfgRegIFC, DefaultGenericTimerCfgDescs}

object WatchdogTimer {
  def writeAnyExceptKey(regs: Bundle, keyReg: SlaveRegIF): Bool = {
    regs.elements.values.filter(_ ne keyReg).map({
      case c: GenericTimerCfgRegIFC => c.anyWriteValid
      case v: Vec[SlaveRegIF] @unchecked => v.map(_.write.valid).reduce(_||_)
      case s: SlaveRegIF => s.write.valid
    }).reduce(_||_)
  }

  val key = 0x51F15E
}

class WatchdogTimer extends MultiIOModule with GenericTimer {
  protected def prefix = "wdog"
  protected def countWidth = 31
  protected def cmpWidth = 16
  protected def ncmp = 1
  protected lazy val countAlways = AsyncResetReg(io.regs.cfg.write.countAlways, io.regs.cfg.write_countAlways && unlocked)(0)
  override protected lazy val countAwake = AsyncResetReg(io.regs.cfg.write.running, io.regs.cfg.write_running && unlocked)(0)
  protected lazy val countEn = {
    val corerstSynchronized = Reg(next = Reg(next = io.corerst))
    countAlways || (countAwake && !corerstSynchronized)
  }
  override protected lazy val rsten = AsyncResetReg(io.regs.cfg.write.sticky, io.regs.cfg.write_sticky && unlocked)(0)
  protected lazy val ip = RegEnable(Vec(Seq(io.regs.cfg.write.ip(0) || elapsed(0))), (io.regs.cfg.write_ip(0) && unlocked) || elapsed(0))
  override protected lazy val unlocked = {
    val writeAny = WatchdogTimer.writeAnyExceptKey(io.regs, io.regs.key)
    AsyncResetReg(io.regs.key.write.bits === WatchdogTimer.key && !writeAny, io.regs.key.write.valid || writeAny)(0)
  }
  protected lazy val feed = {
    val food = 0xD09F00D
    unlocked && io.regs.feed.write.valid && io.regs.feed.write.bits === food
  }

  // The Scala Type-Chekcher seems to have a bug and I get a null pointer during the Scala compilation
  // if I don't do this temporary assignment.
  val tmpStickyDesc =  RegFieldDesc("wdogrsten", "Controls whether the comparator output can set the wdogrst bit and hence cause a full reset.",
      reset = Some(0))

  override protected lazy val cfg_desc = DefaultGenericTimerCfgDescs("wdog", ncmp).copy(
    sticky = tmpStickyDesc,
    deglitch = RegFieldDesc.reserved,
    running = RegFieldDesc("wdogcoreawake", "Increment the watchdog counter if the processor is not asleep", reset=Some(0)),
    center = Seq.fill(ncmp){RegFieldDesc.reserved},
    extra = Seq.fill(ncmp){RegFieldDesc.reserved},
    gang = Seq.fill(ncmp){RegFieldDesc.reserved}
  )

  lazy val io = IO(new GenericTimerIO(regWidth, ncmp, maxcmp, scaleWidth, countWidth, cmpWidth) {
    val corerst = Bool(INPUT)
    val rst = Bool(OUTPUT)
  }
  )
  io.rst := AsyncResetReg(Bool(true), rsten && elapsed(0))
}
