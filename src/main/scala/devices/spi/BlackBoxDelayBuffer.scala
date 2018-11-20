package sifive.blocks.devices.spi

import Chisel._
import freechips.rocketchip.util.ShiftRegInit
import chisel3.experimental._ 

class BlackBoxDelayBuffer extends BlackBox {
  val io = IO(new Bundle() {
  val in = UInt(INPUT,1.W) 
  val sel = UInt(INPUT,5.W)
  val out = UInt(OUTPUT, 1.W)
  val mux_out = UInt(OUTPUT, 1.W)
  })
}
