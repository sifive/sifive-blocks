// See LICENSE for license details.
package sifive.blocks.util

import Chisel._
import freechips.rocketchip.regmapper._

class SlaveRegIF(private val w: Int, private val desc: Option[RegFieldDesc]) extends Bundle {
  val write = Valid(UInt(width = w)).flip
  val read = UInt(OUTPUT, w)

  def toRegField(dummy: Int = 0): RegField = {
    def writeFn(valid: Bool, data: UInt): Bool = {
      write.valid := valid
      write.bits := data
      Bool(true)
    }
    RegField(w, RegReadFn(read), RegWriteFn((v, d) => writeFn(v, d)), desc)
  }
}
