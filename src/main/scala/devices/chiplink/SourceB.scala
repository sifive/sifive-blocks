// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class SourceB(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val b = Decoupled(new TLBundleB(info.edgeIn.bundle))
    val q = Decoupled(UInt(width = info.params.dataBits)).flip
  }

  // Find the optional cache (at most one)
  val cache = info.edgeIn.client.clients.filter(_.supportsProbe).headOption

  // A simple FSM to generate the packet components
  val state = RegInit(UInt(0, width = 2))
  val s_header   = UInt(0, width = 2)
  val s_address0 = UInt(1, width = 2)
  val s_address1 = UInt(2, width = 2)
  val s_data     = UInt(3, width = 2)

  private def hold(key: UInt)(data: UInt) = {
    val enable = state === key
    Mux(enable, data, RegEnable(data, enable))
  }

  // Extract header fields
  val Seq(_, q_opcode, q_param, q_size, _, _) =
    info.decode(io.q.bits).map(hold(s_header) _)

  // Latch address
  val q_address0 = hold(s_address0)(io.q.bits)
  val q_address1 = hold(s_address1)(io.q.bits)

  val (_, q_last) = info.firstlast(io.q, Some(UInt(1)))
  val q_hasData = !q_opcode(2)
  val b_first = RegEnable(state =/= s_data, io.q.fire())

  when (io.q.fire()) {
    switch (state) {
      is (s_header)   { state := s_address0 }
      is (s_address0) { state := s_address1 }
      is (s_address1) { state := Mux(q_hasData, s_data, s_header) }
      is (s_data)     { state := Mux(!q_last,   s_data, s_header) }
    }
  }

  // Feed our preliminary B channel via the Partial Extractor FSM
  val extract = Module(new ParitalExtractor(io.b.bits))
  io.b <> extract.io.o
  val b = extract.io.i
  extract.io.last := q_last

  b.bits.opcode  := q_opcode
  b.bits.param   := q_param
  b.bits.size    := q_size
  b.bits.source  := UInt(cache.map(_.sourceId.start).getOrElse(0))
  b.bits.address := Cat(q_address1, q_address0)
  b.bits.mask    := MaskGen(q_address0, q_size, info.params.dataBytes)
  b.bits.data    := io.q.bits
  b.bits.corrupt := Bool(false)

  val xmit = q_last || state === s_data
  b.valid := io.q.valid &&  xmit
  io.q.ready := b.ready || !xmit
}
