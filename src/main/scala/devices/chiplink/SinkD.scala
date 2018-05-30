// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.tilelink._

class SinkD(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val d = Decoupled(new TLBundleD(info.edgeOut.bundle)).flip
    val q = Decoupled(new DataLayer(info.params))
    val a_tlSource = Valid(UInt(width = info.params.sourceBits))
    val a_clSource = UInt(INPUT, width = info.params.clSourceBits)
    val c_tlSource = Valid(UInt(width = info.params.sourceBits))
    val c_clSource = UInt(INPUT, width = info.params.clSourceBits)
  }

  // The FSM states
  val state = RegInit(UInt(0, width = 2))
  val s_header   = UInt(0, width = 2)
  val s_sink     = UInt(1, width = 2)
  val s_data     = UInt(2, width = 2)

  // We need a Q because we stall the channel while serializing it's header
  val d = Queue(io.d, 1, flow=true)
  val d_last = info.edgeOut.last(d)
  val d_hasData = info.edgeOut.hasData(d.bits)
  val d_grant = d.bits.opcode === TLMessages.Grant || d.bits.opcode === TLMessages.GrantData

  when (io.q.fire()) {
    switch (state) {
      is (s_header)   { state := Mux(d_grant, s_sink, Mux(d_hasData, s_data, s_header)) }
      is (s_sink)     { state := Mux(d_hasData, s_data, s_header) }
      is (s_data)     { state := Mux(d_last, s_header, s_data) }
    }
  }

  // Release the TL source
  val relack = d.bits.opcode === TLMessages.ReleaseAck
  io.a_tlSource.valid := io.q.fire() && state === s_header && !relack
  io.a_tlSource.bits := d.bits.source
  io.c_tlSource.valid := io.q.fire() && state === s_header &&  relack
  io.c_tlSource.bits := d.bits.source

  // Construct the header beat
  val header = info.encode(
    format = UInt(3),
    opcode = d.bits.opcode,
    param  = Cat(d.bits.denied, d.bits.param),
    size   = d.bits.size,
    domain = d.bits.source >> log2Ceil(info.params.sourcesPerDomain),
    source = Mux(relack, io.c_clSource, io.a_clSource))

  val isLastState = state === Mux(d_hasData, s_data, Mux(d_grant, s_sink, s_header))
  d.ready := io.q.ready && isLastState
  io.q.valid := d.valid
  io.q.bits.last  := d_last && isLastState
  io.q.bits.data  := Vec(header, d.bits.sink, d.bits.data)(state)
  io.q.bits.beats := Mux(d_hasData, info.size2beats(d.bits.size), UInt(0)) + UInt(1) + d_grant.asUInt
}
