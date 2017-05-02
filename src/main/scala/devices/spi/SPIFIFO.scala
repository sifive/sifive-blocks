// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._

class SPIFIFOControl(c: SPIParamsBase) extends SPIBundle(c) {
  val fmt = new SPIFormat(c) with HasSPILength
  val cs = new Bundle with HasSPICSMode
  val wm = new SPIWatermark(c)
}

class SPIFIFO(c: SPIParamsBase) extends Module {
  val io = new Bundle {
    val ctrl = new SPIFIFOControl(c).asInput
    val link = new SPIInnerIO(c)
    val tx = Decoupled(Bits(width = c.frameBits)).flip
    val rx = Decoupled(Bits(width = c.frameBits))
    val ip = new SPIInterrupts().asOutput
  }

  val txq = Module(new Queue(io.tx.bits, c.txDepth))
  val rxq = Module(new Queue(io.rx.bits, c.rxDepth))

  txq.io.enq <> io.tx
  io.link.tx <> txq.io.deq

  val fire_tx = io.link.tx.fire()
  val fire_rx = io.link.rx.fire()
  val rxen = Reg(init = Bool(false))

  rxq.io.enq.valid := io.link.rx.valid && rxen
  rxq.io.enq.bits := io.link.rx.bits
  io.rx <> rxq.io.deq

  when (fire_rx) {
    rxen := Bool(false)
  }
  when (fire_tx) {
    rxen := (io.link.fmt.iodir === SPIDirection.Rx)
  }

  val proto = SPIProtocol.decode(io.link.fmt.proto).zipWithIndex
  val cnt_quot = Mux1H(proto.map { case (s, i) => s -> (io.ctrl.fmt.len >> i) })
  val cnt_rmdr = Mux1H(proto.map { case (s, i) => s -> (if (i > 0) io.ctrl.fmt.len(i-1, 0).orR else UInt(0)) })
  io.link.fmt <> io.ctrl.fmt
  io.link.cnt := cnt_quot + cnt_rmdr

  val cs_mode = RegNext(io.ctrl.cs.mode, SPICSMode.Auto)
  val cs_mode_hold = (cs_mode === SPICSMode.Hold)
  val cs_mode_off = (cs_mode === SPICSMode.Off)
  val cs_update = (cs_mode =/= io.ctrl.cs.mode)
  val cs_clear = !(cs_mode_hold || cs_mode_off)

  io.link.cs.set := !cs_mode_off
  io.link.cs.clear := cs_update || (fire_tx && cs_clear)
  io.link.cs.hold := Bool(false)

  io.link.lock := io.link.tx.valid || rxen

  io.ip.txwm := (txq.io.count < io.ctrl.wm.tx)
  io.ip.rxwm := (rxq.io.count > io.ctrl.wm.rx)
}
