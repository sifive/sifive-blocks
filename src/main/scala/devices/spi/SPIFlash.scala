// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._

class SPIFlashInsn(c: SPIFlashParamsBase) extends SPIBundle(c) {
  val cmd = new Bundle with HasSPIProtocol {
    val code = Bits(width = c.insnCmdBits)
    val en = Bool()
  }
  val addr = new Bundle with HasSPIProtocol {
    val len = UInt(width = c.insnAddrLenBits)
  }
  val pad = new Bundle {
    val code = Bits(width = c.frameBits)
    val cnt = Bits(width = c.insnPadLenBits)
  }
  val data = new Bundle with HasSPIProtocol
}

class SPIFlashControl(c: SPIFlashParamsBase) extends SPIBundle(c) {
  val insn = new SPIFlashInsn(c)
  val fmt = new Bundle with HasSPIEndian
}

object SPIFlashInsn {
  def init(c: SPIFlashParamsBase): SPIFlashInsn = {
    val insn = Wire(new SPIFlashInsn(c))
    insn.cmd.en := Bool(true)
    insn.cmd.code := Bits(0x03)
    insn.cmd.proto := SPIProtocol.Single
    insn.addr.len := UInt(3)
    insn.addr.proto := SPIProtocol.Single
    insn.pad.cnt := UInt(0)
    insn.pad.code := Bits(0)
    insn.data.proto := SPIProtocol.Single
    insn
  }
}

class SPIFlashAddr(c: SPIFlashParamsBase) extends SPIBundle(c) {
  val next = UInt(width = c.insnAddrBits)
  val hold = UInt(width = c.insnAddrBits)
}

class SPIFlashMap(c: SPIFlashParamsBase) extends Module {
  val io = new Bundle {
    val en = Bool(INPUT)
    val ctrl = new SPIFlashControl(c).asInput
    val addr = Decoupled(new SPIFlashAddr(c)).flip
    val data = Decoupled(UInt(width = c.frameBits))
    val link = new SPIInnerIO(c)
  }

  val addr = io.addr.bits.hold + UInt(1)
  val merge = io.link.active && (io.addr.bits.next === addr)

  private val insn = io.ctrl.insn
  io.link.tx.valid := Bool(true)
  io.link.fmt.proto := insn.addr.proto
  io.link.fmt.iodir := SPIDirection.Tx
  io.link.fmt.endian := io.ctrl.fmt.endian
  io.link.cnt := Mux1H(
    SPIProtocol.decode(io.link.fmt.proto).zipWithIndex.map {
      case (s, i) => (s -> UInt(c.frameBits >> i))
    })
  io.link.cs.set := Bool(true)
  io.link.cs.clear := Bool(false)
  io.link.cs.hold := Bool(true)
  io.link.lock := Bool(true)

  io.addr.ready := Bool(false)
  io.data.valid := Bool(false)
  io.data.bits := io.link.rx.bits

  val cnt = Reg(UInt(width = math.max(c.insnPadLenBits, c.insnAddrLenBits)))
  val cnt_en = Wire(init = Bool(false))
  val cnt_cmp = (0 to c.insnAddrBytes).map(cnt === UInt(_))
  val cnt_zero = cnt_cmp(0)
  val cnt_last = cnt_cmp(1) && io.link.tx.ready
  val cnt_done = cnt_last || cnt_zero
  when (cnt_en) {
    io.link.tx.valid := !cnt_zero
    when (io.link.tx.fire()) {
      cnt := cnt - UInt(1)
    }
  }

  val (s_idle :: s_cmd :: s_addr :: s_pad :: s_data_pre :: s_data_post :: s_off :: Nil) = Enum(UInt(), 7)
  val state = Reg(init = s_idle)

  switch (state) {
    is (s_idle) {
      io.link.tx.valid := Bool(false)
      when (io.en) {
        io.addr.ready := Bool(true)
        when (io.addr.valid) {
          when (merge) {
            state := s_data_pre
          } .otherwise {
            state := Mux(insn.cmd.en, s_cmd, s_addr)
            io.link.cs.clear := Bool(true)
          }
        } .otherwise {
          io.link.lock := Bool(false)
        }
      } .otherwise {
        io.addr.ready := Bool(true)
        io.link.lock := Bool(false)
        when (io.addr.valid) {
          state := s_off
        }
      }
    }

    is (s_cmd) {
      io.link.fmt.proto := insn.cmd.proto
      io.link.tx.bits := insn.cmd.code
      when (io.link.tx.ready) {
        state := s_addr
        cnt := insn.addr.len
      }
    }

    is (s_addr) {
      io.link.tx.bits := Mux1H(cnt_cmp.tail.zipWithIndex.map {
        case (s, i) =>
          val n = i * c.frameBits
          val m = n + (c.frameBits - 1)
          s -> io.addr.bits.hold(m, n)
      })

      cnt_en := Bool(true)
      when (cnt_done) {
        state := s_pad
      }
    }

    is (s_pad) {
      io.link.cnt := insn.pad.cnt
      io.link.tx.bits := insn.pad.code
      when (io.link.tx.ready) {
        state := s_data_pre
      }
    }

    is (s_data_pre) {
      io.link.fmt.proto := insn.data.proto
      io.link.fmt.iodir := SPIDirection.Rx
      when (io.link.tx.ready) {
        state := s_data_post
      }
    }

    is (s_data_post) {
      io.link.tx.valid := Bool(false)
      io.data.valid := io.link.rx.valid
      when (io.data.fire()) {
        state := s_idle
      }
    }

    is (s_off) {
      io.data.valid := Bool(true)
      io.data.bits := UInt(0)
      when (io.data.ready) {
        state := s_idle
      }
    }
  }
}
