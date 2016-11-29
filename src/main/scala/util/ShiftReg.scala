// See LICENSE for license details.
package sifive.blocks.util

import Chisel._

object ShiftRegisterInit {
  def apply[T <: Data](in: T, n: Int, init: T): T =
    (0 until n).foldLeft(in) {
      case (next, _) => Reg(next, next = next, init = init)
    }
}
