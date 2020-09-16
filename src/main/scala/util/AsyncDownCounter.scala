// See LICENSE for license details.
package sifive.blocks.util

import Chisel._
import chisel3.{BlackBox, RawModule, withClockAndReset}
import freechips.rocketchip.util.{AsyncResetRegVec, AsyncResetReg}

class AsyncDownCounter(clock: Clock, reset: Bool, value: Int)
    extends Module (_clock = clock, _reset = reset) {
  val io = new Bundle {
    val done = Bool(OUTPUT)
  }

  val count_next = Wire(UInt(width = log2Ceil(value)))
  val count = AsyncResetReg(
    updateData = count_next,
    resetData = value,
    name = "count_reg")
  val done_reg = AsyncResetReg(
    updateData = (count === UInt(0)),
    resetData = 0,
    name = "done_reg")

  when (count > UInt(0)) {
    count_next := count - UInt(1)
  } .otherwise {
    count_next := count
  }

  io.done := done_reg
}
