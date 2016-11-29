// See LICENSE for license details.
package sifive.blocks.util

import Chisel._
import regmapper._

// MSB indicates full status
object NonBlockingEnqueue {
  def apply(enq: DecoupledIO[UInt], regWidth: Int = 32): Seq[RegField] = {
    val enqWidth = enq.bits.getWidth
    require(enqWidth > 0)
    require(regWidth > enqWidth)
    Seq(
      RegField(enqWidth,
        RegReadFn(UInt(0)),
        RegWriteFn((valid, data) => {
          enq.valid := valid
          enq.bits := data
          Bool(true)
        })),
      RegField(regWidth - enqWidth - 1),
      RegField.r(1, !enq.ready))
  }
}

// MSB indicates empty status
object NonBlockingDequeue {
  def apply(deq: DecoupledIO[UInt], regWidth: Int = 32): Seq[RegField] = {
    val deqWidth = deq.bits.getWidth
    require(deqWidth > 0)
    require(regWidth > deqWidth)
    Seq(
      RegField.r(deqWidth,
        RegReadFn(ready => {
          deq.ready := ready
          (Bool(true), deq.bits)
        })),
      RegField(regWidth - deqWidth - 1),
      RegField.r(1, !deq.valid))
  }
}
