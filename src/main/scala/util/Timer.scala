// See LICENSE for license details.
package sifive.blocks.util

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.util.WideCounter

class SlaveRegIF(w: Int) extends Bundle {
  val write = Valid(UInt(width = w)).flip
  val read = UInt(OUTPUT, w)

  override def cloneType: this.type = new SlaveRegIF(w).asInstanceOf[this.type]

  def toRegField(dummy: Int = 0): RegField = {
    def writeFn(valid: Bool, data: UInt): Bool = {
      write.valid := valid
      write.bits := data
      Bool(true)
    }
    RegField(w, RegReadFn(read), RegWriteFn((v, d) => writeFn(v, d)))
  }
}


abstract class GenericTimer extends Module {
  protected def countWidth: Int
  protected def cmpWidth: Int
  protected def ncmp: Int
  protected def countAlways: Bool
  protected def countEn: Bool
  protected def feed: Bool
  protected def ip: UInt
  protected def countAwake: Bool = Bool(false)
  protected def unlocked: Bool = Bool(true)
  protected def rsten: Bool = Bool(false)
  protected def deglitch: Bool = Bool(false)
  protected def sticky: Bool = Bool(false)
  protected def oneShot: Bool = Bool(false)
  protected def center: UInt = UInt(0)
  protected def gang: UInt = UInt(0)
  protected val scaleWidth = 4
  protected val regWidth = 32
  val maxcmp = 4
  require(ncmp <= maxcmp)

  class GenericTimerIO extends Bundle {
    val regs = new Bundle {
      val cfg = new SlaveRegIF(regWidth)
      val countLo = new SlaveRegIF(regWidth)
      val countHi = new SlaveRegIF(regWidth)
      val s = new SlaveRegIF(cmpWidth)
      val cmp = Vec(ncmp, new SlaveRegIF(cmpWidth))
      val feed = new SlaveRegIF(regWidth)
      val key = new SlaveRegIF(regWidth)
    }
    val ip = Vec(ncmp, Bool()).asOutput
  }

  def io: GenericTimerIO

  protected val scale = RegEnable(io.regs.cfg.write.bits(scaleWidth-1, 0), io.regs.cfg.write.valid && unlocked)
  protected lazy val zerocmp = RegEnable(io.regs.cfg.write.bits(9), io.regs.cfg.write.valid && unlocked)
  protected val cmp = io.regs.cmp.map(c => RegEnable(c.write.bits, c.write.valid && unlocked))

  protected val count = WideCounter(countWidth, countEn, reset = false)
  when (io.regs.countLo.write.valid && unlocked) { count := Cat(count >> regWidth, io.regs.countLo.write.bits) }
  if (countWidth > regWidth) when (io.regs.countHi.write.valid && unlocked) { count := Cat(io.regs.countHi.write.bits, count(regWidth-1, 0)) }

  // generate periodic interrupt
  protected val s = (count >> scale)(cmpWidth-1, 0)
  // reset counter when fed or elapsed
  protected val elapsed =
    for (i <- 0 until ncmp)
      yield Mux(s(cmpWidth-1) && center(i), ~s, s) >= cmp(i)
  protected val countReset = feed || (zerocmp && elapsed(0))
  when (countReset) { count := 0 }

  io.regs.cfg.read := Cat(ip, gang | UInt(0, maxcmp), UInt(0, maxcmp), center | UInt(0, maxcmp),
                          UInt(0, 2), countAwake || oneShot, countAlways, UInt(0, 1), deglitch, zerocmp, rsten || sticky, UInt(0, 8-scaleWidth), scale)
  io.regs.countLo.read := count
  io.regs.countHi.read := count >> regWidth
  io.regs.s.read := s
  (io.regs.cmp zip cmp) map { case (r, c) => r.read := c }
  io.regs.feed.read := 0
  io.regs.key.read := unlocked
  io.ip := io.ip.fromBits(ip)
}


object GenericTimer {
  def timerRegMap(t: GenericTimer, offset: Int, regBytes: Int): Seq[(Int, Seq[RegField])] = {
    val regs = Seq(
      0 -> t.io.regs.cfg,
      2 -> t.io.regs.countLo,
      3 -> t.io.regs.countHi,
      4 -> t.io.regs.s,
      6 -> t.io.regs.feed,
      7 -> t.io.regs.key)
    val cmpRegs = t.io.regs.cmp.zipWithIndex map { case (r, i) => (8 + i) -> r }
    for ((i, r) <- (regs ++ cmpRegs))
      yield (offset + regBytes*i) -> Seq(r.toRegField())
  }
}
