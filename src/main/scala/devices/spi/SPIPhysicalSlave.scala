// See LICENSE for license details.
package sifive.blocks.devices.spi

import chisel3._
import chisel3.util._

class SPIPortSlaveIO(c: SPIParamsBase) extends SPIBundle(c) {
  val sck = Input(Bool())
  val dq0 = Input(Bool())
  val dq1 = Output(Bool())
  val cs = Input(Bool())
}


class SPIPhysicalSlave(c: SPIParamsBase) extends Module {
  val io = IO(new SPIBundle(c) {
    val port = new SPIPortSlaveIO(c)
    val ctrl = Input(new SPIPhyControl(c))
    val tx = Flipped(Decoupled(UInt(c.frameBits.W)))
    val rx = Valid(UInt(c.frameBits.W))
  })

  val pol = io.ctrl.sck.pol 
  val pha = io.ctrl.sck.pha

  val enable = !io.port.cs

  val s0_sck = io.port.sck
  val s1_sck = RegNext(io.port.sck)
  val risingEdge = s0_sck && !s1_sck
  val fallingEdge = !s0_sck && s1_sck
  val cinv = pol ^ pha
  val sample = (risingEdge && !cinv) || (fallingEdge && cinv)
  val setup = (risingEdge && cinv) || (fallingEdge && !cinv)

  val scnt = RegInit(0.U(c.countBits.W))

  val txd = RegInit(false.B)
  val buffer = RegInit(0.U(c.frameBits.W))

  private def convert(data: UInt, fmt: SPIFormat) =
    Mux(fmt.endian === SPIEndian.MSB, data, Cat(data.toBools))

  io.tx.ready := false.B
  io.rx.valid := false.B
  io.rx.bits := Cat(buffer, io.port.dq0)
  io.port.dq1 := txd
  when (enable && (scnt === 0.U)) {
    val buffer_in = convert(io.tx.bits, io.ctrl.fmt)
    buffer := buffer_in
    scnt := c.frameBits.U 
    txd := buffer_in(c.frameBits-1)
    io.tx.ready := true.B
  }

  when (sample && enable) {
    buffer := Cat(buffer, io.port.dq0)
    scnt := scnt - 1.U
  }

  when (setup && enable) {
    io.port.dq1 := buffer(c.frameBits-1)
    txd := buffer(c.frameBits-1)
  }

  when (sample && (scnt === 1.U)) {
    io.rx.valid := true.B
  }
}
