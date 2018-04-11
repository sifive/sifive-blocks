// See LICENSE for license details.
package sifive.blocks.util

import Chisel._
import freechips.rocketchip.regmapper._

// MSB indicates full status
object NonBlockingEnqueue {
  def apply(enq: DecoupledIO[UInt], regWidth: Int = 32): Seq[RegField] = {
    val enqWidth = enq.bits.getWidth
    val quash = Wire(Bool())
    require(enqWidth > 0)
    require(regWidth > enqWidth)
    Seq(
      RegField(enqWidth,
        RegReadFn(UInt(0)),
        RegWriteFn((valid, data) => {
          enq.valid := valid && !quash
          enq.bits := data
          Bool(true)
        }), RegFieldDesc("data","Transmit data")),
      RegField(regWidth - enqWidth - 1),
      RegField(1,
        !enq.ready,
        RegWriteFn((valid, data) =>  {
          quash := valid && data(0)
          Bool(true)
        }), RegFieldDesc("full","Transmit FIFO full", volatile=true)))
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
        }), RegFieldDesc("data","Receive data", volatile=true)),
      RegField(regWidth - deqWidth - 1),
      RegField.r(1, !deq.valid,
                 RegFieldDesc("empty","Receive FIFO empty", volatile=true)))
  }
}
