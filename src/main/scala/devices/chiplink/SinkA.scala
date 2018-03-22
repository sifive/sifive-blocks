// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.tilelink._

class SinkA(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val a = Decoupled(new TLBundleA(info.edgeIn.bundle)).flip
    val q = Decoupled(new DataLayer(info.params))
  }

  // Map TileLink sources to ChipLink sources+domain
  val tl2cl = info.sourceMap
  val source = info.mux(tl2cl.mapValues(_.source))
  val domain = info.mux(tl2cl.mapValues(_.domain))

  // We need a Q because we stall the channel while serializing it's header
  val inject = Module(new PartialInjector(io.a.bits))
  inject.io.i <> Queue(io.a, 1, flow=true)
  inject.io.i_last := info.edgeIn.last(inject.io.i)
  val a = inject.io.o
  val a_last = inject.io.o_last
  val a_hasData = info.edgeIn.hasData(a.bits)
  val a_partial = a.bits.opcode === TLMessages.PutPartialData

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
      is (s_address1) { state := Mux(a_hasData, s_data, s_header) }
      is (s_data)     { state := Mux(!a_last,   s_data, s_header) }
    }
  }

  // Construct the header beat
  val header = info.encode(
    format = UInt(0),
    opcode = a.bits.opcode,
    param  = a.bits.param,
    size   = a.bits.size,
    domain = domain(a.bits.source),
    source = source(a.bits.source))

  // Construct the address beats
  val address0 = a.bits.address
  val address1 = a.bits.address >> 32

  // Frame the output packet
  val isLastState = state === Mux(a_hasData, s_data, s_address1)
  a.ready := io.q.ready && isLastState
  io.q.valid := a.valid
  io.q.bits.last  := a_last && isLastState
  io.q.bits.data  := Vec(header, address0, address1, a.bits.data)(state)
  io.q.bits.beats := Mux(a_hasData, info.size2beats(a.bits.size), UInt(0)) + UInt(3) +
                     Mux(a_partial, info.mask2beats(a.bits.size), UInt(0))
}
