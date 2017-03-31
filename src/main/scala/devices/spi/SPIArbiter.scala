// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._

class SPIInnerIO(c: SPIParamsBase) extends SPILinkIO(c) {
  val lock = Bool(OUTPUT)
}

class SPIArbiter(c: SPIParamsBase, n: Int) extends Module {
  val io = new Bundle {
    val inner = Vec(n, new SPIInnerIO(c)).flip
    val outer = new SPILinkIO(c)
    val sel = UInt(INPUT, log2Up(n))
  }

  val sel = Reg(init = Vec(Bool(true) +: Seq.fill(n-1)(Bool(false))))

  io.outer.tx.valid := Mux1H(sel, io.inner.map(_.tx.valid))
  io.outer.tx.bits := Mux1H(sel, io.inner.map(_.tx.bits))
  io.outer.cnt := Mux1H(sel, io.inner.map(_.cnt))
  io.outer.fmt := Mux1H(sel, io.inner.map(_.fmt))
  // Workaround for overzealous combinational loop detection
  io.outer.cs := Mux(sel(0), io.inner(0).cs, io.inner(1).cs)
  require(n == 2, "SPIArbiter currently only supports 2 clients")

  (io.inner zip sel).foreach { case (inner, s) =>
    inner.tx.ready := io.outer.tx.ready && s
    inner.rx.valid := io.outer.rx.valid && s
    inner.rx.bits := io.outer.rx.bits
    inner.active := io.outer.active && s
  }

  val nsel = Vec.tabulate(n)(io.sel === UInt(_))
  val lock = Mux1H(sel, io.inner.map(_.lock))
  when (!lock) {
    sel := nsel
    when (sel.asUInt =/= nsel.asUInt) {
      io.outer.cs.clear := Bool(true)
    }
  }
}
