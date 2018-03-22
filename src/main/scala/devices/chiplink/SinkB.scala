// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.tilelink._

class SinkB(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val b = Decoupled(new TLBundleB(info.edgeOut.bundle)).flip
    val q = Decoupled(new DataLayer(info.params))
  }

  // We need a Q because we stall the channel while serializing it's header
  val inject = Module(new PartialInjector(io.b.bits))
  inject.io.i <> Queue(io.b, 1, flow=true)
  inject.io.i_last := info.edgeOut.last(inject.io.i)
  val b = inject.io.o
  val b_last = inject.io.o_last
  val b_hasData = info.edgeOut.hasData(b.bits)
  val b_partial = b.bits.opcode === TLMessages.PutPartialData

  // A simple FSM to generate the packet components
  val state = RegInit(UInt(0, width = 2))
  val s_header   = UInt(0, width = 2)
  val s_address0 = UInt(1, width = 2)
  val s_address1 = UInt(2, width = 2)
  val s_data     = UInt(3, width = 2)

  when (io.q.fire()) {
    switch (state) {
      is (s_header)   { state := s_address0 }
      is (s_address0) { state := s_address1 }
      is (s_address1) { state := Mux(b_hasData, s_data, s_header) }
      is (s_data)     { state := Mux(!b_last,   s_data, s_header) }
    }
  }

  // Construct the header beat
  val header = info.encode(
    format = UInt(1),
    opcode = b.bits.opcode,
    param  = b.bits.param,
    size   = b.bits.size,
    domain = UInt(0), // ChipLink only allows one remote cache, in domain 0
    source = UInt(0))

  assert (!b.valid || b.bits.source === UInt(0))

  // Construct the address beats
  val address0 = b.bits.address
  val address1 = b.bits.address >> 32

  // Frame the output packet
  val isLastState = state === Mux(b_hasData, s_data, s_address1)
  b.ready := io.q.ready && isLastState
  io.q.valid := b.valid
  io.q.bits.last  := b_last && isLastState
  io.q.bits.data  := Vec(header, address0, address1, b.bits.data)(state)
  io.q.bits.beats := Mux(b_hasData, info.size2beats(b.bits.size), UInt(0)) + UInt(3) +
                     Mux(b_partial, info.mask2beats(b.bits.size), UInt(0))
}
