// See LICENSE for license details.
package sifive.blocks.devices.mockaon

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.util._
import sifive.blocks.util.SRLatch

import sifive.blocks.util.{SlaveRegIF}

class WakeupCauses extends Bundle {
  val awakeup = Bool()
  val dwakeup = Bool()
  val rtc = Bool()
  val reset = Bool()
}

class ResetCauses extends Bundle {
  val wdogrst = Bool()
  val erst = Bool()
  val porrst = Bool()
}

class PMUSignals extends Bundle {
  val hfclkrst = Bool()
  val corerst = Bool()
  val reserved1 = Bool()
  val vddpaden = Bool()
  val reserved0 = Bool()
}

class PMUInstruction extends Bundle {
  val sigs = new PMUSignals
  val dt = UInt(width = 4)
}

class PMUConfig(wakeupProgramIn: Seq[Int],
                sleepProgramIn: Seq[Int]) {
  val programLength = 8
  val nWakeupCauses = new WakeupCauses().elements.size
  val wakeupProgram = wakeupProgramIn.padTo(programLength, wakeupProgramIn.last)
  val sleepProgram = sleepProgramIn.padTo(programLength, sleepProgramIn.last)
  require(wakeupProgram.length == programLength)
  require(sleepProgram.length == programLength)
}

class DevKitPMUConfig extends PMUConfig( // TODO
  Seq(0x1f0, 0x0f8, 0x030),
  Seq(0x0f0, 0x1f0, 0x1d0, 0x1c0))

class PMURegs(c: PMUConfig) extends Bundle {
  val ie = new SlaveRegIF(c.nWakeupCauses)
  val cause = new SlaveRegIF(32)
  val sleep = new SlaveRegIF(32)
  val key = new SlaveRegIF(32)
  val wakeupProgram = Vec(c.programLength, new SlaveRegIF(32))
  val sleepProgram = Vec(c.programLength, new SlaveRegIF(32))
}

class PMUCore(c: PMUConfig)(resetIn: Bool) extends Module(_reset = resetIn) {
  val io = new Bundle {
    val wakeup = new WakeupCauses().asInput
    val control = Valid(new PMUSignals)
    val resetCause = UInt(INPUT, log2Ceil(new ResetCauses().getWidth))
    val regs = new PMURegs(c)
  }

  val run = Reg(init = Bool(true))
  val awake = Reg(init = Bool(true))
  val unlocked = {
    val writeAny = WatchdogTimer.writeAnyExceptKey(io.regs, io.regs.key)
    RegEnable(io.regs.key.write.bits === WatchdogTimer.key && !writeAny, Bool(false), io.regs.key.write.valid || writeAny)
  }
  val wantSleep = RegEnable(Bool(true), Bool(false), io.regs.sleep.write.valid && unlocked)
  val pc = Reg(init = UInt(0, log2Ceil(c.programLength)))
  val wakeupCause = Reg(init = UInt(0, log2Ceil(c.nWakeupCauses)))
  val ie = RegEnable(io.regs.ie.write.bits, io.regs.ie.write.valid && unlocked) | 1 /* POR always enabled */

  val insnWidth = new PMUInstruction().getWidth
  val wakeupProgram = c.wakeupProgram.map(v => Reg(init = UInt(v, insnWidth)))
  val sleepProgram = c.sleepProgram.map(v => Reg(init = UInt(v, insnWidth)))
  val insnBits = Mux(awake, wakeupProgram(pc), sleepProgram(pc))
  val insn = new PMUInstruction().fromBits(insnBits)

  val count = Reg(init = UInt(0, 1 << insn.dt.getWidth))
  val tick = (count ^ (count + 1))(insn.dt)
  val npc = pc +& 1
  val last = npc >= c.programLength
  io.control.valid := run && !last && tick
  io.control.bits := insn.sigs

  when (run) {
    count := count + 1
    when (tick) {
      count := 0

      require(isPow2(c.programLength))
      run := !last
      pc := npc
    }
  }.otherwise {
    val maskedWakeupCauses = ie & io.wakeup.asUInt
    when (!awake && maskedWakeupCauses.orR) {
      run := true
      awake := true
      wakeupCause := PriorityEncoder(maskedWakeupCauses)
    }
    when (awake && wantSleep) {
      run := true
      awake := false
      wantSleep := false
    }
  }

  io.regs.cause.read := wakeupCause | (io.resetCause << 8)
  io.regs.ie.read := ie
  io.regs.key.read := unlocked
  io.regs.sleep.read := 0

  for ((port, reg) <- (io.regs.wakeupProgram ++ io.regs.sleepProgram) zip (wakeupProgram ++ sleepProgram)) {
    port.read := reg
    when (port.write.valid && unlocked) { reg := port.write.bits }
  }
}

class PMU(val c: PMUConfig) extends Module {
  val io = new Bundle {
    val wakeup = new WakeupCauses().asInput
    val control = new PMUSignals().asOutput
    val regs = new PMURegs(c)
    val resetCauses = new ResetCauses().asInput
  }

  val coreReset = Reg(next = Reg(next = reset))
  val core = Module(new PMUCore(c)(resetIn = coreReset))

  io <> core.io
  core.io.wakeup.reset := false // this is implied by resetting the PMU

  // during aonrst, hold all control signals high
  val latch = ~AsyncResetReg(~core.io.control.bits.asUInt, core.io.control.valid)
  io.control := io.control.fromBits(latch)

  core.io.resetCause := {
    val cause = io.resetCauses.asUInt
    val latches = for (i <- 0 until cause.getWidth) yield {
      val latch = Module(new SRLatch)
      latch.io.set := cause(i)
      latch.io.reset := (0 until cause.getWidth).filter(_ != i).map(cause(_)).reduce(_||_)
      latch.io.q
    }
    OHToUInt(latches)
  }
}
