// See LICENSE for license details.
package sifive.blocks.devices.mockaon

import Chisel._
import Chisel.ImplicitConversions._
import chisel3.experimental.MultiIOModule
import freechips.rocketchip.util.AsyncResetReg
import freechips.rocketchip.regmapper.RegFieldDesc

import sifive.blocks.util.{SlaveRegIF, GenericTimer, GenericTimerIO, DefaultGenericTimerCfgDescs}

class RTC extends MultiIOModule with GenericTimer {

  protected def prefix = "rtc"
  protected def countWidth = 48
  protected def cmpWidth = 32
  protected def ncmp = 1
  protected def countEn = countAlways
  override protected lazy val ip = RegNext(elapsed)
  override protected lazy val zerocmp = Bool(false)
  protected lazy val countAlways = AsyncResetReg(io.regs.cfg.write.countAlways, io.regs.cfg.write_countAlways && unlocked)(0)
  protected lazy val feed = Bool(false)

  override protected lazy val feed_desc = RegFieldDesc.reserved
  override protected lazy val key_desc = RegFieldDesc.reserved
  override protected lazy val cfg_desc = DefaultGenericTimerCfgDescs("rtc", ncmp).copy(
    sticky = RegFieldDesc.reserved,
    zerocmp = RegFieldDesc.reserved,
    deglitch = RegFieldDesc.reserved,
    running = RegFieldDesc.reserved,
    center = Seq.fill(ncmp){ RegFieldDesc.reserved },
    extra = Seq.fill(ncmp){ RegFieldDesc.reserved },
    gang = Seq.fill(ncmp){ RegFieldDesc.reserved }
  )

  lazy val io = IO(new GenericTimerIO(regWidth, ncmp, maxcmp, scaleWidth, countWidth, cmpWidth))

}
