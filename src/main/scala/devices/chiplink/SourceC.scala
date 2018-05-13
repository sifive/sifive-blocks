// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class SourceC(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val c = Decoupled(new TLBundleC(info.edgeOut.bundle))
    val q = Decoupled(UInt(width = info.params.dataBits)).flip
    // Used by D to find the txn
    val d_tlSource = Valid(UInt(width = info.params.sourceBits)).flip
    val d_clSource = UInt(OUTPUT, width = info.params.clSourceBits)
  }

  // CAM of sources used for release
  val cam = Module(new CAM(info.params.sourcesPerDomain, info.params.clSourceBits))

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
  val Seq(_, q_opcode, q_param, q_size, _, q_source) =
    info.decode(io.q.bits).map(hold(s_header) _)

  // Latch address
  val q_address0 = hold(s_address0)(io.q.bits)
  val q_address1 = hold(s_address1)(io.q.bits)

  val (_, q_last) = info.firstlast(io.q, Some(UInt(2)))
  val q_hasData = q_opcode(0)
  val c_first = RegEnable(state =/= s_data, io.q.fire())

  when (io.q.fire()) {
    switch (state) {
      is (s_header)   { state := s_address0 }
      is (s_address0) { state := s_address1 }
      is (s_address1) { state := Mux(q_hasData, s_data, s_header) }
      is (s_data)     { state := Mux(!q_last,   s_data, s_header) }
    }
  }

  // Determine if the request is legal. If not, route to error device.
  val q_address = Cat(q_address1, q_address0)
  val exists = info.edgeOut.manager.containsSafe(q_address)
  private def writeable(m: TLManagerParameters): Boolean = if (m.supportsAcquireB) m.supportsAcquireT else m.supportsPutFull
  private def acquireable(m: TLManagerParameters): Boolean = m.supportsAcquireB || m.supportsAcquireT
  private def toBool(x: Boolean) = Bool(x)
  val writeOk = info.edgeOut.manager.fastProperty(q_address, writeable, toBool)
  val acquireOk = info.edgeOut.manager.fastProperty(q_address, acquireable, toBool)
  val q_legal = exists && (!q_hasData || writeOk) && acquireOk

  // Look for an available source in the correct domain
  val q_release = q_opcode === TLMessages.Release || q_opcode === TLMessages.ReleaseData
  val source_ok = !q_release || cam.io.alloc.ready
  val source    = cam.io.key holdUnless c_first

  io.c.bits.opcode  := q_opcode
  io.c.bits.param   := q_param
  io.c.bits.size    := q_size
  io.c.bits.source  := Mux(q_release, source, UInt(0)) // always domain 0
  io.c.bits.address := info.makeError(q_legal, q_address)
  io.c.bits.data    := io.q.bits
  io.c.bits.corrupt := Bool(false)

  val stall = c_first && !source_ok
  val xmit = q_last || state === s_data
  io.c.valid := (io.q.valid && !stall) &&  xmit
  io.q.ready := (io.c.ready && !stall) || !xmit
  cam.io.alloc.valid := q_release && c_first && xmit && io.q.valid && io.c.ready
  cam.io.alloc.bits  := q_source

  // Free the CAM entries
  io.d_clSource := cam.io.data
  cam.io.free := io.d_tlSource
}
