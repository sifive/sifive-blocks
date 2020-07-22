// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel.{defaultCompileOptions => _, _}
import freechips.rocketchip.util.CompileOptions.NotStrictInferReset

import freechips.rocketchip.util._

class UARTTx(c: UARTParams) extends Module {
  val io = new Bundle {
    val en = Bool(INPUT)
    val in = Decoupled(Bits(width = c.dataBits)).flip
    val out = Bits(OUTPUT, 1)
    val div = UInt(INPUT, c.divisorBits)
    val nstop = UInt(INPUT, log2Up(c.stopBits))
    val tx_busy = Bool(OUTPUT)
    val enparity = c.includeParity.option(Bool(INPUT))
    val parity = c.includeParity.option(Bool(INPUT))
    val data8or9 = (c.dataBits == 9).option(Bool(INPUT))
    val cts_n = c.includeFourWire.option(Bool(INPUT))
  }

  val prescaler = Reg(init = UInt(0, c.divisorBits))
  val pulse = (prescaler === UInt(0))

  private val n = c.dataBits + 1 + c.includeParity.toInt
  val counter = Reg(init = UInt(0, log2Floor(n + c.stopBits) + 1))
  val shifter = Reg(Bits(width = n))
  val out = Reg(init = Bits(1, 1))
  io.out := out

  val plusarg_tx = PlusArg("uart_tx", 1, "Enable/disable the TX to speed up simulation").orR

  val busy = (counter =/= UInt(0))
  io.in.ready := io.en && !busy
  io.tx_busy := busy
  when (io.in.fire()) {
    printf("UART TX (%x): %c\n", io.in.bits, io.in.bits)
  }
  when (io.in.fire() && plusarg_tx) {
    if (c.includeParity) {
      val includebit9 = if (c.dataBits == 9) Mux(io.data8or9.get, Bool(false), io.in.bits(8)) else Bool(false)
      val parity = Mux(io.enparity.get, includebit9 ^ io.in.bits(7,0).asBools.reduce(_ ^ _) ^ io.parity.get, Bool(true))
      val paritywithbit9 = if (c.dataBits == 9) Mux(io.data8or9.get, Cat(1.U(1.W), parity), Cat(parity, io.in.bits(8))) 
                           else Cat(1.U(1.W), parity)
      shifter := Cat(paritywithbit9, io.in.bits(7,0), Bits(0, 1))
      counter := Mux1H((0 until c.stopBits).map(i =>
        (io.nstop === UInt(i)) -> UInt(n + i + 1))) - (!io.enparity.get).asUInt - io.data8or9.getOrElse(0.U)
      // n = max number of databits configured at elaboration + start bit + parity bit 
      // n + i + 1 = n + stop bits + pad bit(when counter === 0 no bit is transmitted)
      // n + i + 1 - 8_bit_mode(if c.dataBits == 9) - parity_disabled_at_runtime
    }
    else {
      val bit9 = if (c.dataBits == 9) Mux(io.data8or9.get, 1.U(1.W), io.in.bits(8)) else 1.U(1.W)
      shifter := Cat(bit9, io.in.bits(7,0), Bits(0, 1))
      counter := Mux1H((0 until c.stopBits).map(i =>
        (io.nstop === UInt(i)) -> UInt(n + i + 1))) - io.data8or9.getOrElse(0.U)
    }
  }
  when (busy) {
    prescaler := Mux(pulse || io.cts_n.getOrElse(false.B), io.div, prescaler - UInt(1))
  }
  when (pulse && busy) {
    counter := counter - UInt(1)
    shifter := Cat(Bits(1, 1), shifter >> 1)
    out := shifter(0)
  }
}
