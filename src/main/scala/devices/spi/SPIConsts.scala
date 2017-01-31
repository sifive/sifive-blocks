// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._

object SPIProtocol {
  val width = 2
  def Single = UInt(0, width)
  def Dual   = UInt(1, width)
  def Quad   = UInt(2, width)

  def cases = Seq(Single, Dual, Quad)
  def decode(x: UInt): Seq[Bool] = cases.map(_ === x)
}

object SPIDirection {
  val width = 1
  def Rx = UInt(0, width)
  def Tx = UInt(1, width)
}

object SPIEndian {
  val width = 1
  def MSB = UInt(0, width)
  def LSB = UInt(1, width)
}

object SPICSMode {
  val width = 2
  def Auto = UInt(0, width)
  def Hold = UInt(2, width)
  def Off  = UInt(3, width)
}
