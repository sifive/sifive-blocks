// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._

import freechips.rocketchip.util.PlusArg

class UARTTx(c: UARTParams) extends Module {
  val io = new Bundle {
    val en = Bool(INPUT)
    val in = Decoupled(Bits(width = c.dataBits)).flip
    val out = Bits(OUTPUT, 1)
    val div = UInt(INPUT, c.divisorBits)
    val nstop = UInt(INPUT, log2Up(c.stopBits))
  }

  val prescaler = Reg(init = UInt(0, c.divisorBits))
  val pulse = (prescaler === UInt(0))

  private val n = c.dataBits + 1
  val counter = Reg(init = UInt(0, log2Floor(n + c.stopBits) + 1))
  val shifter = Reg(Bits(width = n))
  val out = Reg(init = Bits(1, 1))
  io.out := out

  val plusarg_tx = PlusArg("uart_tx", 1, "Enable/disable the TX to speed up simulation").orR

  val busy = (counter =/= UInt(0))
  io.in.ready := io.en && !busy
  when (io.in.fire()) {
    printf("UART TX (%x): %c\n", io.in.bits, io.in.bits)
  }
  when (io.in.fire() && plusarg_tx) {
    shifter := Cat(io.in.bits, Bits(0, 1))
    counter := Mux1H((0 until c.stopBits).map(i =>
      (io.nstop === UInt(i)) -> UInt(n + i + 1)))
  }
  when (busy) {
    prescaler := Mux(pulse, io.div, prescaler - UInt(1))
  }
  when (pulse && busy) {
    counter := counter - UInt(1)
    shifter := Cat(Bits(1, 1), shifter >> 1)
    out := shifter(0)
  }
}
