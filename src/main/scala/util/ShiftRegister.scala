// See LICENSE for license details.
package sifive.blocks.util

import Chisel._

object ShiftRegisterInit {
  def apply[T <: Data](in: T, n: Int, init: T): T =
    (0 until n).foldLeft(in) {
      case (next, _) => Reg(next, next = next, init = init)
    }
}


// Similar to the Chisel ShiftRegister but allows the user to suggest a
// name to the registers within the module that get instantiated
object ShiftRegister
{
  /** Returns the n-cycle delayed version of the input signal.
    *
    * @param in input to delay
    * @param n number of cycles to delay
    * @param en enable the shift
    * @param name set the elaborated name of the registers.
    */
  def apply[T <: Chisel.Data](in: T, n: Int, en: Chisel.Bool = Chisel.Bool(true), name: Option[String] = None): T = {
    // The order of tests reflects the expected use cases.
    if (n != 0) {
      val r = Chisel.RegEnable(apply(in, n-1, en, name), en)
      if (name.isDefined) r.suggestName(s"${name.get}_sync_${n-1}")
      r
    } else {
      in
    }
  }

  /** Returns the n-cycle delayed version of the input signal with reset initialization.
    *
    * @param in input to delay
    * @param n number of cycles to delay
    * @param resetData reset value for each register in the shift
    * @param en enable the shift
    * @param name set the elaborated name of the registers.
    */
  def apply[T <: Chisel.Data](in: T, n: Int, resetData: T, en: Chisel.Bool, name: Option[String]): T = {
    // The order of tests reflects the expected use cases.
    if (n != 0) {
      val r = Chisel.RegEnable(apply(in, n-1, resetData, en, name), resetData, en)
      if (name.isDefined) r.suggestName(s"${name.get}_sync_${n-1}")
      r
    } else {
      in
    }
  }
}
