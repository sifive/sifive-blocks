// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class TX(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val c2b_send = Bool(OUTPUT)
    val c2b_data = UInt(OUTPUT, info.params.dataBits)
    val a = new AsyncBundle(info.params.crossingDepth, new DataLayer(info.params)).flip
    val b = new AsyncBundle(info.params.crossingDepth, new DataLayer(info.params)).flip
    val c = new AsyncBundle(info.params.crossingDepth, new DataLayer(info.params)).flip
    val d = new AsyncBundle(info.params.crossingDepth, new DataLayer(info.params)).flip
    val e = new AsyncBundle(info.params.crossingDepth, new DataLayer(info.params)).flip
    val sa = DecoupledIO(new DataLayer(info.params)).flip
    val sb = DecoupledIO(new DataLayer(info.params)).flip
    val sc = DecoupledIO(new DataLayer(info.params)).flip
    val sd = DecoupledIO(new DataLayer(info.params)).flip
    val se = DecoupledIO(new DataLayer(info.params)).flip
    val rxc = new AsyncBundle(1, new CreditBump(info.params)).flip
    val txc = new AsyncBundle(1, new CreditBump(info.params)).flip
  }

  // Currently available credits
  val rx = RegInit(CreditBump(info.params, 0))
  val tx = RegInit(CreditBump(info.params, 0))
  val first = RegInit(Bool(true))

  // Constantly pull credits from RX
  val rxInc = FromAsyncBundle(io.rxc)
  val txInc = FromAsyncBundle(io.txc)
  rxInc.ready := Bool(true)
  txInc.ready := Bool(true)

  // Cross the requests (if necessary)
  val sync = info.params.syncTX
  val a = if (sync) ShiftQueue(io.sa, 2) else FromAsyncBundle(io.a)
  val b = if (sync) ShiftQueue(io.sb, 2) else FromAsyncBundle(io.b)
  val c = if (sync) ShiftQueue(io.sc, 2) else FromAsyncBundle(io.c)
  val d = if (sync) ShiftQueue(io.sd, 2) else FromAsyncBundle(io.d)
  val e = if (sync) ShiftQueue(io.se, 2) else FromAsyncBundle(io.e)

  private def ioX = Seq(a, b, c, d, e)
  val validABCDE = Cat(ioX.map(_.valid).reverse)

  // Calculate if the packet will fit
  val txDec = CreditBump(info.params, 0)
  val spaceABCDE = Cat(((tx.X zip txDec.X) zip ioX) .map { case ((credit, reduce), beat) =>
    val delta = credit -& beat.bits.beats
    reduce := Mux(beat.fire() && first, delta, credit)
    delta.asSInt >= SInt(0)
  }.reverse)
  val requestABCDE = validABCDE & spaceABCDE

  // How often should we force transmission of a credit update? sqrt
  val xmitBits = log2Ceil(info.params.Qdepth) / 2
  val xmit = RegInit(UInt(0, width = xmitBits))
  val forceXmit = xmit === UInt(0)

  // Frame an update of the RX credits
  val (header, rxLeft) = rx.toHeader
  val f = Wire(Decoupled(new DataLayer(info.params)))
  f.valid := requestABCDE === UInt(0) || forceXmit
  f.bits.data  := header
  f.bits.last  := Bool(true)
  f.bits.beats := UInt(1)

  when (!forceXmit) { xmit := xmit - UInt(1) }
  when (f.fire()) { xmit := ~UInt(0, width = xmitBits) }

  // Include the F credit channel in arbitration
  val ioF = ioX :+ f
  val space = Cat(UInt(1), spaceABCDE)
  val request = Cat(f.valid, requestABCDE)
  val valid = Cat(f.valid, validABCDE)

  // Select a channel to transmit from those with data and space
  val lasts = Cat(ioF.map(_.bits.last).reverse)
  val readys = TLArbiter.roundRobin(6, request, first)
  val winner = readys & request
  val state = RegInit(UInt(0, width=6))
  val grant = Mux(first, winner, state)
  val allowed = Mux(first, readys & space, state)
  (ioF zip allowed.toBools) foreach { case (beat, sel) => beat.ready := sel }

  state := grant
  first := (grant & lasts).orR

  // Form the output beat
  io.c2b_send := RegNext(RegNext(first || (state & valid) =/= UInt(0), Bool(false)), Bool(false))
  io.c2b_data := RegNext(Mux1H(RegNext(grant), RegNext(Vec(ioF.map(_.bits.data)))))

  // Update the credit trackers
  rx := Mux(f.fire(), rxLeft, rx) + Mux(rxInc.fire(), rxInc.bits, CreditBump(info.params, 0))
  tx := txDec                     + Mux(txInc.fire(), txInc.bits, CreditBump(info.params, 0))
}
