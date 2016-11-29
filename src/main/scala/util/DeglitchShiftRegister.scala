// See LICENSE for license details.
package sifive.blocks.util

import Chisel._

//Allows us to specify a different clock for a shift register
// and to force input to be high for > 1 cycle.
class DeglitchShiftRegister(shift: Int) extends Module {
  val io = new Bundle {
    val d = Bool(INPUT)
    val q = Bool(OUTPUT)
  }
  val sync = ShiftRegister(io.d, shift)
  val last = ShiftRegister(sync, 1)
  io.q := sync & last
}

object DeglitchShiftRegister {
  def apply (shift: Int, d: Bool, clock: Clock,
    name: Option[String] = None): Bool = {
    val deglitch = Module (new DeglitchShiftRegister(shift))
    name.foreach(deglitch.suggestName(_))
    deglitch.clock := clock
    deglitch.reset := Bool(false)
    deglitch.io.d := d
    deglitch.io.q
  }
}
