// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import chisel3._
import chisel3.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class CAM(keys: Int, dataBits: Int) extends Module
{
  val io = IO(new Bundle {
    // alloc.valid => allocate a key
    // alloc.ready => a key is avilable
    val alloc = Flipped(Decoupled(UInt(dataBits.W)))
    val key   = Output(UInt(log2Ceil(keys).W))
    // free.valid => release the key
    val free  = Flipped(Valid(UInt(log2Ceil(keys).W)))
    val data  = Output(UInt(dataBits.W))
  })

  val free = RegInit(((BigInt(1) << keys) - 1).U(keys.W))
  val data = Mem(keys, UInt(dataBits.W))

  val free_sel = ~(leftOR(free, keys) << 1) & free
  io.key := OHToUInt(free_sel, keys)

  io.alloc.ready := free.orR
  when (io.alloc.fire()) { data.write(io.key, io.alloc.bits) }

  // Support free in same cycle as alloc
  val bypass = io.alloc.fire() && io.free.bits === io.key
  io.data := Mux(bypass, io.alloc.bits, data(io.free.bits))

  // Update CAM usage
  val clr = Mux(io.alloc.fire(), free_sel, 0.U)
  val set = Mux(io.free.valid, UIntToOH(io.free.bits), 0.U)
  free := (free & ~clr) | set
}
