// See LICENSE.SiFive for license details.

package sifive.blocks.devices.chiplink

import Chisel.{defaultCompileOptions => _, _}
import freechips.rocketchip.util.CompileOptions.NotStrictInferReset
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class StuckSnooperNode(uFn: Seq[TLClientPortParameters] => TLClientPortParameters)(implicit valName: ValName) extends TLCustomNode
{
  def resolveStar(iKnown: Int, oKnown: Int, iStars: Int, oStars: Int): (Int, Int) = {
    require (iStars == 0 && oStars == 0, "StuckSnooper node does not support :=* or :*=")
    require (iKnown == 2, "StuckSnooper node expects exactly two inputs")
    require (oKnown == 1, "StuckSnooper node expects exactly one output")
    (0, 0)
  }
  def mapParamsD(n: Int, p: Seq[TLClientPortParameters]): Seq[TLClientPortParameters] = { Seq(uFn(p)) }
  def mapParamsU(n: Int, p: Seq[TLManagerPortParameters]): Seq[TLManagerPortParameters] = { p ++ p }
}



class StuckSnooper(uFn: Seq[TLClientPortParameters] => TLClientPortParameters)(implicit p: Parameters) extends LazyModule
{
  val node = new StuckSnooperNode(uFn)

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val bypass = Bool(INPUT)
      val pending = Bool(OUTPUT)
    })

    val Seq((in0, edgeIn0), (in1, edgeIn1)) = node.in
    val Seq((out, edgeOut)) = node.out


    // We need to be locked to the given bypass direction until all transactions stop
    val bypass = RegInit(io.bypass) // synchronous reset required
    val (flight, next_flight) = edgeOut.inFlight(out)

    io.pending := (flight > 0.U)
    when (next_flight === UInt(0)) { bypass := io.bypass }
    val stall = (bypass =/= io.bypass) && edgeOut.first(out.a)

    in0.a.ready := !stall && out.a.ready &&  bypass
    in1.a.ready := !stall && out.a.ready && !bypass
    out.a.valid := !stall && Mux(bypass, in0.a.valid, in1.a.valid)
    def castA(x: TLBundleA) = { val ret = Wire(out.a.bits); ret <> x; ret }
    out.a.bits := Mux(bypass, castA(in0.a.bits), castA(in1.a.bits))

    out.d.ready := Mux(bypass, in0.d.ready, in1.d.ready)
    in0.d.valid := out.d.valid &&  bypass
    in1.d.valid := out.d.valid && !bypass
    in0.d.bits := out.d.bits
    in1.d.bits := out.d.bits

    //Enable probes to in1 only after it issues an acquire
    val divertprobes = RegInit(true.B)
    divertprobes := divertprobes && ~(in1.a.fire() && (in1.a.bits.opcode === TLMessages.AcquireBlock || in1.a.bits.opcode === TLMessages.AcquirePerm))
    val bypass_c = Wire(Bool()) 
    bypass_c := bypass || divertprobes

    if (edgeOut.manager.anySupportAcquireB && edgeOut.client.anySupportProbe) {
      out.b.ready := Mux(bypass_c, in0.b.ready, in1.b.ready)
      in0.b.valid := out.b.valid &&  bypass_c
      in1.b.valid := out.b.valid && !bypass_c
      in0.b.bits := out.b.bits
      in1.b.bits := out.b.bits

      in0.c.ready := out.c.ready &&  bypass_c
      in1.c.ready := out.c.ready && !bypass_c
      out.c.valid := Mux(bypass_c, in0.c.valid, in1.c.valid)
      def castC(x: TLBundleC) = { val ret = Wire(out.c.bits); ret <> x; ret }
      out.c.bits := Mux(bypass_c, castC(in0.c.bits), castC(in1.c.bits))

      in0.e.ready := out.e.ready &&  bypass_c
      in1.e.ready := out.e.ready && !bypass_c
      out.e.valid := Mux(bypass_c, in0.e.valid, in1.e.valid)
      def castE(x: TLBundleE) = { val ret = Wire(out.e.bits); ret <> x; ret }
      out.e.bits := Mux(bypass_c, castE(in0.e.bits), castE(in1.e.bits))
    } else {
      in0.b.valid := Bool(false)
      in0.c.ready := Bool(true)
      in0.e.ready := Bool(true)

      in1.b.valid := Bool(false)
      in1.c.ready := Bool(true)
      in1.e.ready := Bool(true)

      out.b.ready := Bool(true)
      out.c.valid := Bool(false)
      out.e.valid := Bool(false)
    }
  }
}

/** Synthesizeable unit tests */
import freechips.rocketchip.unittest._

class TLStuckSnooperTester(txns: Int)(implicit p: Parameters) extends LazyModule {
  val fuzz1 = LazyModule(new TLFuzzer(txns))
  val fuzz2 = LazyModule(new TLFuzzer(txns))
  val model1 = LazyModule(new TLRAMModel("StuckSnooper1"))
  val model2 = LazyModule(new TLRAMModel("StuckSnooper2"))
  val mux = LazyModule(new StuckSnooper(uFn = _.head))
  val ram = LazyModule(new TLRAM(AddressSet(0, 0x3ff), beatBytes = 4))
  mux.node := TLFilter(TLFilter.mSelectIntersect(AddressSet( 0, ~16))) := model1.node := fuzz1.node
  mux.node := TLFilter(TLFilter.mSelectIntersect(AddressSet(16, ~16))) := model2.node := fuzz2.node
  ram.node := TLFragmenter(4, 16) := mux.node
  // how to test probe + release?

  lazy val module = new LazyModuleImp(this) with UnitTestModule {
    io.finished := fuzz1.module.io.finished && fuzz2.module.io.finished
    mux.module.io.bypass := LFSR64(Bool(true))(0)
  }
}

class TLStuckSnooperTest(txns: Int = 5000, timeout: Int = 500000)(implicit p: Parameters) extends UnitTest(timeout) {
  val dut = Module(LazyModule(new TLStuckSnooperTester(txns)).module)
  io <> dut.io
}
