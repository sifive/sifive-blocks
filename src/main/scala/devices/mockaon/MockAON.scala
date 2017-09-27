// See LICENSE for license details.
package sifive.blocks.devices.mockaon

import Chisel._
import chisel3.experimental.MultiIOModule
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

import sifive.blocks.util.GenericTimer

case class MockAONParams(
    address: BigInt = BigInt(0x10000000),
    nBackupRegs: Int = 16) {
  def size: Int = 0x1000
  def regBytes: Int = 4
  def wdogOffset: Int = 0
  def rtcOffset: Int = 0x40
  def backupRegOffset: Int = 0x80
  def pmuOffset: Int = 0x100
}

class MockAONPMUIO extends Bundle {
  val vddpaden = Bool(OUTPUT)
  val dwakeup = Bool(INPUT)
}

class MockAONMOffRstIO extends Bundle {
  val hfclkrst = Bool(OUTPUT)
  val corerst = Bool(OUTPUT)
}

trait HasMockAONBundleContents extends Bundle {

  // Output of the Power Management Sequencer
  val moff = new MockAONMOffRstIO

  // This goes out to wrapper
  // to be combined to create aon_rst.
  val wdog_rst = Bool(OUTPUT)

  // This goes out to wrapper
  // and comes back as our clk
  val lfclk = Clock(OUTPUT)

  val pmu = new MockAONPMUIO

  val lfextclk = Clock(INPUT)

  val resetCauses = new ResetCauses().asInput
}

trait HasMockAONModuleContents extends MultiIOModule with HasRegMap {
  val io: HasMockAONBundleContents
  val params: MockAONParams
  val c = params

  // the expectation here is that Chisel's implicit reset is aonrst,
  // which is asynchronous, so don't use synchronous-reset registers.

  val rtc = Module(new RTC)

  val pmu = Module(new PMU(new DevKitPMUConfig))
  io.moff <> pmu.io.control
  io.pmu.vddpaden := pmu.io.control.vddpaden
  pmu.io.wakeup.dwakeup := io.pmu.dwakeup
  pmu.io.wakeup.awakeup := Bool(false)
  pmu.io.wakeup.rtc := rtc.io.ip(0)
  pmu.io.resetCauses := io.resetCauses
  val pmuRegMap = {
    val regs = pmu.io.regs.wakeupProgram ++ pmu.io.regs.sleepProgram ++
      Seq(pmu.io.regs.ie, pmu.io.regs.cause, pmu.io.regs.sleep, pmu.io.regs.key)
    for ((r, i) <- regs.zipWithIndex)
      yield (c.pmuOffset + c.regBytes*i) -> Seq(r.toRegField())
  }
  interrupts(1) := rtc.io.ip(0)

  val wdog = Module(new WatchdogTimer)
  io.wdog_rst := wdog.io.rst
  wdog.io.corerst := pmu.io.control.corerst
  interrupts(0) := wdog.io.ip(0)

  // If there are multiple lfclks to choose from, we can mux them here.
  io.lfclk := io.lfextclk

  val backupRegs = Seq.fill(c.nBackupRegs)(Reg(UInt(width = c.regBytes * 8)))
  val backupRegMap =
    for ((reg, i) <- backupRegs.zipWithIndex)
      yield (c.backupRegOffset + c.regBytes*i) -> Seq(RegField(reg.getWidth, RegReadFn(reg), RegWriteFn(reg)))

  regmap((backupRegMap ++
    GenericTimer.timerRegMap(wdog, c.wdogOffset, c.regBytes) ++
    GenericTimer.timerRegMap(rtc, c.rtcOffset, c.regBytes) ++
    pmuRegMap):_*)

}

class TLMockAON(w: Int, c: MockAONParams)(implicit p: Parameters)
  extends TLRegisterRouter(c.address, "aon", Seq("sifive,aon0"), interrupts = 2, size = c.size, beatBytes = w, concurrency = 1)(
  new TLRegBundle(c, _)    with HasMockAONBundleContents)(
  new TLRegModule(c, _, _) with HasMockAONModuleContents)
