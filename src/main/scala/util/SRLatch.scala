// See LICENSE for license details.
package sifive.blocks.util

import Chisel._

class SRLatch extends BlackBox {
  val io = new Bundle {
    val set = Bool(INPUT)
    val reset = Bool(INPUT)
    val q = Bool(OUTPUT)
  }
}
