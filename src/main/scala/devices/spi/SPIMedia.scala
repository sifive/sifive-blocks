// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._

class SPILinkIO(c: SPIParamsBase) extends SPIBundle(c) {
  val tx = Decoupled(Bits(width = c.frameBits))
  val rx = Valid(Bits(width = c.frameBits)).flip

  val cnt = UInt(OUTPUT, c.countBits)
  val fmt = new SPIFormat(c).asOutput
  val cs = new Bundle {
    val set = Bool(OUTPUT)
    val clear = Bool(OUTPUT) // Deactivate CS
    val hold = Bool(OUTPUT) // Supress automatic CS deactivation
  }
  val active = Bool(INPUT)
}

class SPIMedia(c: SPIParamsBase) extends Module {
  val io = new Bundle {
    val port = new SPIPortIO(c)
    val ctrl = new Bundle {
      val sck = new SPIClocking(c).asInput
      val dla = new SPIDelay(c).asInput
      val cs = new SPIChipSelect(c).asInput
      val extradel = new SPIExtraDelay(c).asInput
      val sampledel = new SPISampleDelay(c).asInput
    }
    val link = new SPILinkIO(c).flip
    val mands = Input(Bool())
  }

  val phy = Module(new SPIPhysical(c))
  phy.io.ctrl.sck := io.ctrl.sck
  phy.io.ctrl.fmt := io.link.fmt
  phy.io.ctrl.extradel := io.ctrl.extradel
  phy.io.ctrl.sampledel := io.ctrl.sampledel

  private val op = phy.io.op
  op.valid := Bool(true)
  op.bits.fn := SPIMicroOp.Delay
  op.bits.stb := Bool(false)
  op.bits.cnt := io.link.cnt
  op.bits.data := io.link.tx.bits

  val cs = Reg(io.ctrl.cs)
  val cs_set = Reg(Bool())
  val cs_active = io.ctrl.cs.toggle(io.link.cs.set)
  val cs_update = (cs_active.asUInt =/= cs.dflt.asUInt)

  val clear = Reg(init = Bool(false))
  val cs_assert = Reg(init = Bool(false))
  val cs_deassert = clear || (cs_update && !io.link.cs.hold)

  clear := clear || (io.link.cs.clear && cs_assert)

  val continuous = (io.ctrl.dla.interxfr === UInt(0))

  io.port.sck.o := phy.io.port.sck.o
  io.port.sck.oe := true.B
  io.port.dq <> phy.io.port.dq
  io.port.cs.zip(cs.dflt).map{case (a,b) => a.o := b}
  io.port.cs.map(_.oe := true.B)

  io.link.rx := phy.io.rx
  io.link.tx.ready := Bool(false)
  io.link.active := cs_assert

  val (s_main :: s_interxfr :: s_intercs :: Nil) = Enum(UInt(), 3)
  val state = Reg(init = s_main)

  switch (state) {
    is (s_main) {
      when (cs_assert) {
        when (cs_deassert) {
          op.bits.cnt := io.ctrl.dla.sckcs
          when (op.ready) {
            state := s_intercs
          }
        } .otherwise {
          op.bits.fn := SPIMicroOp.Transfer
          op.bits.stb := Bool(true)

          op.valid := io.link.tx.valid
          io.link.tx.ready := op.ready
          when (op.fire()) {
            state := s_interxfr
          }
        }
      } .elsewhen (io.link.tx.valid) {
        // Assert CS
        op.bits.cnt := io.ctrl.dla.cssck
        when (op.ready) {
          cs_assert := Bool(true)
          cs_set := io.link.cs.set
          cs.dflt := cs_active
        }
      } .otherwise {
        // Idle
        op.bits.cnt := UInt(0)
        op.bits.stb := Bool(true)
        cs := io.ctrl.cs
      }
    }

    is (s_interxfr) {
      // Skip if interxfr delay is zero
      op.valid := !continuous
      op.bits.cnt := io.ctrl.dla.interxfr
      when (op.ready || continuous) {
        state := s_main
      }
    }

    is (s_intercs) {
      // Deassert CS
      op.bits.cnt := io.ctrl.dla.intercs
      op.bits.stb := Bool(true)
      cs_assert := Bool(false)
      clear := Bool(false)
      when (op.ready) {
        cs.dflt := cs.toggle(cs_set)
        state := s_main
      }
    }
  }

  
  if (c.mands) {
    val phySlave = Module(new SPIPhysicalSlave(c))
    phySlave.io.port.dq0 := io.port.dq(0).i
    phySlave.io.port.sck := io.port.sck.i
    phySlave.io.port.cs := io.port.cs(0).i
    phySlave.io.tx.valid := io.link.tx.valid
    phySlave.io.tx.bits := io.link.tx.bits
    phySlave.io.ctrl.sck := io.ctrl.sck
    phySlave.io.ctrl.fmt := io.link.fmt
    phySlave.io.ctrl.extradel := io.ctrl.extradel
    phySlave.io.ctrl.sampledel := io.ctrl.sampledel
    when (io.mands) {
      io.port.sck.oe := false.B
      io.port.cs(0).oe := false.B
      io.port.dq(1).o := phySlave.io.port.dq1
      io.port.dq(1).oe := true.B
      io.link.tx.ready := phySlave.io.tx.ready
      io.link.rx := phySlave.io.rx
      io.link.active := io.port.cs(0).i
    }
  }
}
