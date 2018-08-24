// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class RX(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val b2c_send = Bool(INPUT)
    val b2c_data = UInt(INPUT, info.params.dataBits)
    val a = new AsyncBundle(UInt(width = info.params.dataBits), info.params.crossing)
    val b = new AsyncBundle(UInt(width = info.params.dataBits), info.params.crossing)
    val c = new AsyncBundle(UInt(width = info.params.dataBits), info.params.crossing)
    val d = new AsyncBundle(UInt(width = info.params.dataBits), info.params.crossing)
    val e = new AsyncBundle(UInt(width = info.params.dataBits), info.params.crossing)
    val rxc = new AsyncBundle(new CreditBump(info.params), AsyncQueueParams.singleton())
    val txc = new AsyncBundle(new CreditBump(info.params), AsyncQueueParams.singleton())
  }

  // Immediately register our input data
  val b2c_data = RegNext(RegNext(io.b2c_data))
  val b2c_send = RegNext(RegNext(io.b2c_send), Bool(false))
  // b2c_send is NOT cleared on the first RegNext because this module's reset has a flop on it

  // Fit b2c into the firstlast API
  val beat = Wire(Decoupled(UInt(width = info.params.dataBits)))
  beat.bits  := b2c_data
  beat.valid := b2c_send
  beat.ready := Bool(true)

  // Select the correct HellaQueue for the request
  val (first, _) = info.firstlast(beat)
  val formatBits  = beat.bits(2, 0)
  val formatValid = beat.fire() && first
  val format = Mux(formatValid, formatBits, RegEnable(formatBits, formatValid))
  val formatOH = UIntToOH(format)

  // Create the receiver buffers
  val hqa = Module(new HellaQueue(info.params.Qdepth)(beat.bits))
  val hqb = Module(new HellaQueue(info.params.Qdepth)(beat.bits))
  val hqc = Module(new HellaQueue(info.params.Qdepth)(beat.bits))
  val hqd = Module(new HellaQueue(info.params.Qdepth)(beat.bits))
  val hqe = Module(new HellaQueue(info.params.Qdepth)(beat.bits))

  // Use these to save some typing; function to prevent renaming
  private def hqX = Seq(hqa, hqb, hqc, hqd, hqe)
  private def ioX = Seq(io.a, io.b, io.c, io.d, io.e)

  // Enqueue to the HellaQueues
  (formatOH.toBools zip hqX) foreach { case (sel, hq) =>
    hq.io.enq.valid := beat.valid && sel
    hq.io.enq.bits := beat.bits
    assert (!hq.io.enq.valid || hq.io.enq.ready) // overrun impossible
  }

  // Send HellaQueue output to their respective FSMs
  (hqX zip ioX) foreach { case (hq, io) =>
    io <> ToAsyncBundle(hq.io.deq, info.params.crossing)
  }

  // Credits we need to hand-off to the TX FSM
  val tx = RegInit(CreditBump(info.params, 0))
  val rx = RegInit(CreditBump(info.params, info.params.Qdepth))

  // Constantly transmit credit updates
  val txOut = Wire(Decoupled(new CreditBump(info.params)))
  val rxOut = Wire(Decoupled(new CreditBump(info.params)))
  txOut.valid := Bool(true)
  rxOut.valid := Bool(true)
  txOut.bits := tx
  rxOut.bits := rx
  io.txc <> ToAsyncBundle(txOut, AsyncQueueParams.singleton())
  io.rxc <> ToAsyncBundle(rxOut, AsyncQueueParams.singleton())

  // Generate new RX credits as the HellaQueues drain
  val rxInc = Wire(new CreditBump(info.params))
  (hqX zip rxInc.X) foreach { case (hq, inc) =>
    inc := hq.io.deq.fire().asUInt
  }

  // Generate new TX credits as we receive F-format messages
  val txInc = Mux(beat.valid && formatOH(5), CreditBump(info.params, beat.bits), CreditBump(info.params, 0))

  // As we hand-over credits, reset the counters
  tx := tx + txInc
  rx := rx + rxInc
  when (txOut.fire()) { tx := txInc }
  when (rxOut.fire()) { rx := rxInc }
}
