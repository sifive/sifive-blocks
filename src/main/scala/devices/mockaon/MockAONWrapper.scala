// See LICENSE for license details.
package sifive.blocks.devices.mockaon

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import sifive.blocks.devices.pinctrl.{EnhancedPin}
import sifive.blocks.util.{DeglitchShiftRegister, ResetCatchAndSync}

/* The wrapper handles the Clock and Reset Generation for The AON block itself,
 and instantiates real pad controls (aka pull-ups)*/

class MockAONWrapperPMUIO extends Bundle {
  val dwakeup_n = new EnhancedPin()
  val vddpaden = new  EnhancedPin()
}

class MockAONWrapperPadsIO extends Bundle {
  val erst_n   = new EnhancedPin()
  val lfextclk = new EnhancedPin()
  val pmu = new MockAONWrapperPMUIO()
}

class MockAONWrapperBundle extends Bundle {
  val pads = new MockAONWrapperPadsIO()
  val rsts = new MockAONMOffRstIO()
}

class MockAONWrapper(w: Int, c: MockAONParams)(implicit p: Parameters) extends LazyModule {

  val node = TLAsyncInputNode()
  val intnode = IntOutputNode()
  val aon = LazyModule(new TLMockAON(w, c))

  // We only need to isolate the signals
  // coming from MOFF to AON,
  // since AON is never off while MOFF is on.
  // The MOFF is on the "in" side of the Isolation.
  // AON is on the "out" side of the Isolation.

  def isoOut(iso: Bool, x: UInt): UInt = IsoZero(iso, x)
  def isoIn(iso: Bool, x: UInt): UInt = x
  val isolation = LazyModule(new TLIsolation(fOut = isoOut, fIn = isoIn))
  val crossing = LazyModule(new TLAsyncCrossingSink(depth = 1))

  isolation.node := node
  crossing.node := isolation.node
  val crossing_monitor = (aon.node := crossing.node)

  // crossing lives outside in Periphery
  intnode := aon.intnode

  lazy val module = new LazyModuleImp(this) {
    val io = new MockAONWrapperBundle {
      val in = node.bundleIn
      val ip = intnode.bundleOut
      val rtc  = Clock(OUTPUT)
      val ndreset = Bool(INPUT)
    }

    val aon_io = aon.module.io
    val pads = io.pads

    // -----------------------------------------------
    // Generation of aonrst
    // -----------------------------------------------

    // ERST
    val erst = ~pads.erst_n.inputPin(pue = Bool(true))
    aon_io.resetCauses.erst := erst
    aon_io.resetCauses.wdogrst := aon_io.wdog_rst

    // PORRST
    val porrst = Bool(false) // TODO
    aon_io.resetCauses.porrst := porrst

    //--------------------------------------------------
    // Drive "Mostly Off" Reset Signals (these
    // are synchronized inside MOFF as needed)
    //--------------------------------------------------

    io.rsts.hfclkrst := aon_io.moff.hfclkrst
    io.rsts.corerst  := aon_io.moff.corerst

    //--------------------------------------------------
    // Generate the LFCLK input to AON
    // This is the same clock that is driven to this
    // block as 'clock'.
    //--------------------------------------------------

    // LFCLK Override
    // Note that the actual mux lives inside AON itself.
    // Therefore, the lfclk which comes out of AON is the
    // true clock that AON and AONWrapper are running off of.
    val lfextclk = pads.lfextclk.inputPin(pue=Bool(true))
    aon_io.lfextclk := lfextclk.asClock

    // Drive AON's clock and Reset
    val lfclk = aon_io.lfclk

    val aonrst_catch = Module (new ResetCatchAndSync(3))
    aonrst_catch.reset := erst | aon_io.wdog_rst | io.ndreset
    aonrst_catch.clock := lfclk
    aon.module.reset := aonrst_catch.io.sync_reset

    aon.module.clock := lfclk

    //--------------------------------------------------
    // TL2 Register Access Interface
    //--------------------------------------------------

    // Safely cross TL2 into AON Domain
    // Ensure that both are reset and clocked
    // at the same time.
    // Note that aon.moff.corerst is synchronous
    // to aon.module.clock, so this is safe.
    val crossing_slave_reset  = ResetCatchAndSync(lfclk,
      aon.module.io.moff.corerst | aon.module.reset)

    crossing.module.clock := lfclk
    crossing.module.reset := crossing_slave_reset

    crossing_monitor.foreach { lm =>
      lm.module.clock := lfclk
      lm.module.reset := crossing_slave_reset
    }

    // Note that aon.moff.corerst is synchronous
    // to aon.module.clock, so this is safe.
    isolation.module.io.iso_out := aon.module.io.moff.corerst
    isolation.module.io.iso_in  := Bool(true)

    //--------------------------------------------------
    // PMU <--> pads Interface
    //--------------------------------------------------

    val dwakeup_n_async = pads.pmu.dwakeup_n.inputPin(pue=Bool(true))

    val dwakeup_deglitch = Module (new DeglitchShiftRegister(3))
    dwakeup_deglitch.clock := lfclk
    dwakeup_deglitch.io.d := ~dwakeup_n_async
    aon.module.io.pmu.dwakeup := dwakeup_deglitch.io.q

    pads.pmu.vddpaden.outputPin(aon.module.io.pmu.vddpaden)

    //--------------------------------------------------
    // Connect signals to MOFF
    //--------------------------------------------------

    io.rtc := aon_io.lfclk
  }

}

// -----------------------------------------------
// Isolation Cells
// -----------------------------------------------

class IsoZero extends Module {
  val io = new Bundle {
    val in = Bool(INPUT)
    val iso = Bool(INPUT)
    val out = Bool(OUTPUT)
  }
  io.out := io.in & ~io.iso
}

object IsoZero {
  def apply (iso: Bool, in: UInt): UInt = {

    val w = in.getWidth
    val isos: List[IsoZero] = List.tabulate(in.getWidth)(
      x => Module(new IsoZero).suggestName(s"iso_$x")
    )
    for ((z, i) <- isos.zipWithIndex) {
      z.io.in := in(i)
      z.io.iso := iso
    }
    isos.map(_.io.out).asUInt
  }
}
