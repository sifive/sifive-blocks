// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class CAM(keys: Int, dataBits: Int) extends Module
{
  val io = new Bundle {
    // alloc.valid => allocate a key
    // alloc.ready => a key is avilable
    val alloc = Decoupled(UInt(width = dataBits)).flip
    val key   = UInt(OUTPUT, width = log2Ceil(keys))
    // free.valid => release the key
    val free  = Valid(UInt(width = log2Ceil(keys))).flip
    val data  = UInt(OUTPUT, width = dataBits)
  }

  val free = RegInit(UInt((BigInt(1) << keys) - 1, width = keys))
  val data = Mem(keys, UInt(width = dataBits))

  val free_sel = ~(leftOR(free, keys) << 1) & free
  io.key := OHToUInt(free_sel, keys)

  io.alloc.ready := free.orR
  when (io.alloc.fire()) { data.write(io.key, io.alloc.bits) }

  // Support free in same cycle as alloc
  val bypass = io.alloc.fire() && io.free.bits === io.key
  io.data := Mux(bypass, io.alloc.bits, data(io.free.bits))

  // Update CAM usage
  val clr = Mux(io.alloc.fire(), free_sel, UInt(0))
  val set = Mux(io.free.valid, UIntToOH(io.free.bits), UInt(0))
  free := (free & ~clr) | set
}
