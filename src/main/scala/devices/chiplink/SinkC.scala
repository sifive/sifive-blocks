// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.tilelink._

class SinkC(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val c = Decoupled(new TLBundleC(info.edgeIn.bundle)).flip
    val q = Decoupled(new DataLayer(info.params))
  }

  // Map TileLink sources to ChipLink sources+domain
  val tl2cl = info.sourceMap
  val source = info.mux(tl2cl.mapValues(_.source))
  val domain = info.mux(tl2cl.mapValues(_.domain))

  // We need a Q because we stall the channel while serializing it's header
  val c = Queue(io.c, 1, flow=true)
  val c_last = info.edgeIn.last(c)
  val c_hasData = info.edgeIn.hasData(c.bits)
  val c_release = c.bits.opcode === TLMessages.Release || c.bits.opcode === TLMessages.ReleaseData

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
      is (s_address1) { state := Mux(c_hasData, s_data, s_header) }
      is (s_data)     { state := Mux(!c_last,   s_data, s_header) }
    }
  }

  // Construct the header beat
  val header = info.encode(
    format = UInt(2),
    opcode = c.bits.opcode,
    param  = c.bits.param,
    size   = c.bits.size,
    domain = UInt(0), // only caches (unordered) can release
    source = Mux(c_release, source(c.bits.source), UInt(0)))

  assert (!c.valid || domain(c.bits.source) === UInt(0))

  // Construct the address beats
  val address0 = c.bits.address
  val address1 = c.bits.address >> 32

  // Frame the output packet
  val isLastState = state === Mux(c_hasData, s_data, s_address1)
  c.ready := io.q.ready && isLastState
  io.q.valid := c.valid
  io.q.bits.last  := c_last && isLastState
  io.q.bits.data  := Vec(header, address0, address1, c.bits.data)(state)
  io.q.bits.beats := Mux(c_hasData, info.size2beats(c.bits.size), UInt(0)) + UInt(3)
}
