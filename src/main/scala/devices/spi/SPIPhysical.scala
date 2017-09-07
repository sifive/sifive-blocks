// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import freechips.rocketchip.util.ShiftRegInit

class SPIMicroOp(c: SPIParamsBase) extends SPIBundle(c) {
  val fn = Bits(width = 1)
  val stb = Bool()
  val cnt = UInt(width = c.countBits)
  val data = Bits(width = c.frameBits)
}

object SPIMicroOp {
  def Transfer = UInt(0, 1)
  def Delay    = UInt(1, 1)
}

class SPIPhyControl(c: SPIParamsBase) extends SPIBundle(c) {
  val sck = new SPIClocking(c)
  val fmt = new SPIFormat(c)
}

class SPIPhysical(c: SPIParamsBase) extends Module {
  val io = new SPIBundle(c) {
    val port = new SPIPortIO(c)
    val ctrl = new SPIPhyControl(c).asInput
    val op = Decoupled(new SPIMicroOp(c)).flip
    val rx = Valid(Bits(width = c.frameBits))
  }

  private val op = io.op.bits
  val ctrl = Reg(io.ctrl)
  val proto = SPIProtocol.decode(ctrl.fmt.proto)

  val accept = Wire(init = Bool(false))
  val sample = Wire(init = Bool(false))
  val setup = Wire(init = Bool(false))
  val last = Wire(init = Bool(false))
  // Delayed versions
  val setup_d = Reg(next = setup)
  val sample_d = ShiftRegInit(sample, c.sampleDelay, init = Bool(false))
  val last_d = ShiftRegInit(last, c.sampleDelay, init = Bool(false))

  val scnt = Reg(init = UInt(0, c.countBits))
  val tcnt = Reg(io.ctrl.sck.div)

  val stop = (scnt === UInt(0))
  val beat = (tcnt === UInt(0))
  val decr = Mux(beat, scnt, tcnt) - UInt(1)
  val sched = Wire(init = beat)
  tcnt := Mux(sched, ctrl.sck.div, decr)

  val sck = Reg(Bool())
  val cref = Reg(init = Bool(true))
  val cinv = ctrl.sck.pha ^ ctrl.sck.pol

  private def convert(data: UInt, fmt: SPIFormat) =
    Mux(fmt.endian === SPIEndian.MSB, data, Cat(data.toBools))

  val rxd = Cat(io.port.dq.reverse.map(_.i))
  val samples = Seq(rxd(1), rxd(1, 0), rxd)

  val buffer = Reg(op.data)
  val buffer_in = convert(io.op.bits.data, io.ctrl.fmt)
  val shift = if (c.sampleDelay > 0) setup_d || (sample_d && stop) else sample_d
  buffer := Mux1H(proto, samples.zipWithIndex.map { case (data, i) =>
    val n = 1 << i
    val m = c.frameBits -1
    Cat(Mux(shift, buffer(m-n, 0), buffer(m, n)),
        Mux(sample_d, data, buffer(n-1, 0)))
  })

  private def upper(x: UInt, n: Int) = x(c.frameBits-1, c.frameBits-n)

  val txd = Reg(init = Bits(0, io.port.dq.size))
  val txd_in = Mux(accept, upper(buffer_in, 4), upper(buffer, 4))
  val txd_sel = SPIProtocol.decode(Mux(accept, io.ctrl.fmt.proto, ctrl.fmt.proto))
  val txd_shf = (0 until txd_sel.size).map(i => txd_in(3, 4-(1<<i)))
  when (setup) {
    txd := Mux1H(txd_sel, txd_shf)
  }

  val tx = (ctrl.fmt.iodir === SPIDirection.Tx)
  val txen_in = (proto.head +: proto.tail.map(_ && tx)).scanRight(Bool(false))(_ || _).init
  val txen = txen_in :+ txen_in.last

  io.port.sck := sck
  io.port.cs := Vec.fill(io.port.cs.size)(Bool(true)) // dummy
  (io.port.dq zip (txd.toBools zip txen)).foreach {
    case (dq, (o, oe)) =>
      dq.o := o
      dq.oe := oe
  }
  io.op.ready := Bool(false)

  val done = Reg(init = Bool(true))
  done := done || last_d

  io.rx.valid := done
  io.rx.bits := convert(buffer, ctrl.fmt)

  val xfr = Reg(Bool())

  when (stop) {
    sched := Bool(true)
    accept := Bool(true)
  } .otherwise {
    when (beat) {
      cref := !cref
      when (xfr) {
        sck := cref ^ cinv
        sample := cref
        setup := !cref
      }
      when (!cref) {
        scnt := decr
      }
    }
  }

  when (scnt === UInt(1)) {
    last := beat && cref && xfr // Final sample
    when (beat && !cref) { // Final shift
      accept := Bool(true)
      setup := Bool(false)
      sck := ctrl.sck.pol
    }
  }

  when (accept && done) {
    io.op.ready := Bool(true)
    when (io.op.valid) {
      scnt := op.cnt
      when (op.stb) {
        ctrl.fmt := io.ctrl.fmt
      }

      xfr := Bool(false)
      switch (op.fn) {
        is (SPIMicroOp.Transfer) {
          buffer := buffer_in
          sck := cinv
          setup := Bool(true)
          done := (op.cnt === UInt(0))
          xfr := Bool(true)
        }
        is (SPIMicroOp.Delay) {
          when (op.stb) {
            sck := io.ctrl.sck.pol
            ctrl.sck := io.ctrl.sck
          }
        }
      }
    }
  }
}
