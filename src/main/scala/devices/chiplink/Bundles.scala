// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.util.{rightOR,GenericParameterizedBundle}

class WideDataLayerPortLane(params: ChipLinkParams) extends GenericParameterizedBundle(params) {
  val clk  = Clock(OUTPUT)
  val rst  = Bool(OUTPUT)
  val send = Bool(OUTPUT)
  val data = UInt(OUTPUT, width=params.dataBits)
}

class WideDataLayerPort(params: ChipLinkParams) extends GenericParameterizedBundle(params) {
  val c2b = new WideDataLayerPortLane(params)
  val b2c = new WideDataLayerPortLane(params).flip
}

class DataLayer(params: ChipLinkParams) extends GenericParameterizedBundle(params) {
  val data = UInt(OUTPUT, width=params.dataBits)
  val last = Bool(OUTPUT)
  val beats = UInt(OUTPUT, width=params.xferBits + 1)
}

class CreditBump(params: ChipLinkParams) extends GenericParameterizedBundle(params) {
  val a = UInt(OUTPUT, width = params.creditBits)
  val b = UInt(OUTPUT, width = params.creditBits)
  val c = UInt(OUTPUT, width = params.creditBits)
  val d = UInt(OUTPUT, width = params.creditBits)
  val e = UInt(OUTPUT, width = params.creditBits)
  def X: Seq[UInt] = Seq(a, b, c, d, e)

  // saturating addition
  def +(that: CreditBump): CreditBump = {
    val out = Wire(new CreditBump(params))
    (out.X zip (X zip that.X)) foreach { case (o, (x, y)) =>
      val z = x +& y
      o := Mux((z >> params.creditBits).orR, ~UInt(0, width=params.creditBits), z)
    }
    out
  }

  // Send the MSB of the credits
  def toHeader: (UInt, CreditBump) = {
    def msb(x: UInt) = {
      val mask = rightOR(x) >> 1
      val msbOH = ~(~x | mask)
      val msb = OHToUInt(msbOH << 1, params.creditBits + 1) // 0 = 0, 1 = 1, 2 = 4, 3 = 8, ...
      val pad = (msb | UInt(0, width=5))(4,0)
      (pad, x & mask)
    }
    val (a_msb, a_rest) = msb(a)
    val (b_msb, b_rest) = msb(b)
    val (c_msb, c_rest) = msb(c)
    val (d_msb, d_rest) = msb(d)
    val (e_msb, e_rest) = msb(e)
    val header = Cat(
      e_msb, d_msb, c_msb, b_msb, a_msb,
      UInt(0, width = 4), // padding
      UInt(5, width = 3))

    val out = Wire(new CreditBump(params))
    out.a := a_rest
    out.b := b_rest
    out.c := c_rest
    out.d := d_rest
    out.e := e_rest
    (header, out)
  }
}

object CreditBump {
  def apply(params: ChipLinkParams, x: Int): CreditBump = {
    val v = UInt(x, width = params.creditBits)
    val out = Wire(new CreditBump(params))
    out.X.foreach { _ := v }
    out
  }

  def apply(params: ChipLinkParams, header: UInt): CreditBump = {
    def convert(x: UInt) =
      Mux(x > UInt(params.creditBits),
          ~UInt(0, width = params.creditBits),
          UIntToOH(x, params.creditBits + 1) >> 1)
    val out = Wire(new CreditBump(params))
    out.a := convert(header(11,  7))
    out.b := convert(header(16, 12))
    out.c := convert(header(21, 17))
    out.d := convert(header(26, 22))
    out.e := convert(header(31, 27))
    out
  }
}
