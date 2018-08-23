// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class TX(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val c2b_clk  = Clock(OUTPUT)
    val c2b_rst  = Bool(OUTPUT)
    val c2b_send = Bool(OUTPUT)
    val c2b_data = UInt(OUTPUT, info.params.dataBits)
    val a = new AsyncBundle(new DataLayer(info.params), info.params.crossing).flip
    val b = new AsyncBundle(new DataLayer(info.params), info.params.crossing).flip
    val c = new AsyncBundle(new DataLayer(info.params), info.params.crossing).flip
    val d = new AsyncBundle(new DataLayer(info.params), info.params.crossing).flip
    val e = new AsyncBundle(new DataLayer(info.params), info.params.crossing).flip
    val sa = DecoupledIO(new DataLayer(info.params)).flip
    val sb = DecoupledIO(new DataLayer(info.params)).flip
    val sc = DecoupledIO(new DataLayer(info.params)).flip
    val sd = DecoupledIO(new DataLayer(info.params)).flip
    val se = DecoupledIO(new DataLayer(info.params)).flip
    val rxc = new AsyncBundle(new CreditBump(info.params), AsyncQueueParams.singleton()).flip
    val txc = new AsyncBundle(new CreditBump(info.params), AsyncQueueParams.singleton()).flip
  }

  // Currently available credits
  val rx = RegInit(CreditBump(info.params, 0))
  val tx = RegInit(CreditBump(info.params, 0))

  // Constantly pull credits from RX
  val rxInc = FromAsyncBundle(io.rxc)
  val txInc = FromAsyncBundle(io.txc)
  rxInc.ready := Bool(true)
  txInc.ready := Bool(true)

  // Cross the requests (if necessary)
  val sync = info.params.syncTX
  val qa = if (sync) ShiftQueue(io.sa, 2) else FromAsyncBundle(io.a)
  val qb = if (sync) ShiftQueue(io.sb, 2) else FromAsyncBundle(io.b)
  val qc = if (sync) ShiftQueue(io.sc, 2) else FromAsyncBundle(io.c)
  val qd = if (sync) ShiftQueue(io.sd, 2) else FromAsyncBundle(io.d)
  val qe = if (sync) ShiftQueue(io.se, 2) else FromAsyncBundle(io.e)
  private def qX = Seq(qa, qb, qc, qd, qe)

  // Consume TX credits and propagate pre-paid requests
  val ioX = (qX zip (tx.X zip txInc.bits.X)) map { case (q, (credit, gain)) =>
    val first = RegEnable(q.bits.last, Bool(true), q.fire())
    val delta = credit -& q.bits.beats
    val allow = !first || (delta.asSInt >= SInt(0))
    credit := Mux(q.fire() && first, delta, credit) + Mux(txInc.fire(), gain, UInt(0))

    val cq = Module(new ShiftQueue(q.bits.cloneType, 2)) // maybe flow?
    cq.io.enq.bits := q.bits
    cq.io.enq.valid := q.valid && allow
    q.ready := cq.io.enq.ready && allow
    cq.io.deq
  }

  // Prepare RX credit update headers
  val rxQ = Module(new ShiftQueue(new DataLayer(info.params), 2)) // maybe flow?
  val (rxHeader, rxLeft) = rx.toHeader
  rxQ.io.enq.valid := Bool(true)
  rxQ.io.enq.bits.data  := rxHeader
  rxQ.io.enq.bits.last  := Bool(true)
  rxQ.io.enq.bits.beats := UInt(1)
  rx := Mux(rxQ.io.enq.fire(), rxLeft, rx) + Mux(rxInc.fire(), rxInc.bits, CreditBump(info.params, 0))

  // Include the F credit channel in arbitration
  val f = Wire(rxQ.io.deq)
  val ioF = ioX :+ f
  val requests = Cat(ioF.map(_.valid).reverse)
  val lasts = Cat(ioF.map(_.bits.last).reverse)

  // How often should we force transmission of a credit update? sqrt
  val xmitBits = log2Ceil(info.params.Qdepth) / 2
  val xmit = RegInit(UInt(0, width = xmitBits))
  val forceXmit = xmit === UInt(0)
  when (!forceXmit) { xmit := xmit - UInt(1) }
  when (f.fire()) { xmit := ~UInt(0, width = xmitBits) }

  // Flow control for returned credits
  val allowReturn = !ioX.map(_.valid).reduce(_ || _) || forceXmit
  f.bits  := rxQ.io.deq.bits
  f.valid := rxQ.io.deq.valid && allowReturn
  rxQ.io.deq.ready := f.ready && allowReturn

  // Select a channel to transmit from those with data and space
  val first = RegInit(Bool(true))
  val state = Reg(UInt(0, width=6))
  val readys = TLArbiter.roundRobin(6, requests, first)
  val winner = readys & requests
  val grant = Mux(first, winner, state)
  val allowed = Mux(first, readys, state)
  (ioF zip allowed.toBools) foreach { case (beat, sel) => beat.ready := sel }

  val send = Mux(first, rxQ.io.deq.valid, (state & requests) =/= UInt(0))
  assert (send === ((grant & requests) =/= UInt(0)))

  when (send) { first := (grant & lasts).orR }
  when (first) { state := winner }

  // Form the output beat
  io.c2b_clk  := clock
  io.c2b_rst  := AsyncResetReg(Bool(false), clock, reset, true, None)
  io.c2b_send := RegNext(RegNext(send, Bool(false)), Bool(false))
  io.c2b_data := RegNext(Mux1H(RegNext(grant), RegNext(Vec(ioF.map(_.bits.data)))))
}
