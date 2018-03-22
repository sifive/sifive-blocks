// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.tilelink._

class SinkE(info: ChipLinkInfo) extends Module
{
  val io = new Bundle {
    val e = Decoupled(new TLBundleE(info.edgeIn.bundle)).flip
    val q = Decoupled(new DataLayer(info.params))
    // Find the sink from D
    val d_tlSink = Valid(UInt(width = info.params.sinkBits))
    val d_clSink = UInt(INPUT, width = info.params.clSinkBits)
  }

  io.d_tlSink.valid := io.e.fire()
  io.d_tlSink.bits := io.e.bits.sink

  val header = info.encode(
    format = UInt(4),
    opcode = UInt(0),
    param  = UInt(0),
    size   = UInt(0),
    domain = UInt(0),
    source = io.d_clSink)

  io.e.ready := io.q.ready
  io.q.valid := io.e.valid
  io.q.bits.last  := Bool(true)
  io.q.bits.data  := header
  io.q.bits.beats := UInt(1)
}
