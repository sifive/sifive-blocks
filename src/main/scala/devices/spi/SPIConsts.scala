// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._

object SPIProtocol {
  val width = 2
  val Single = UInt(0, width)
  val Dual   = UInt(1, width)
  val Quad   = UInt(2, width)

  val cases = Seq(Single, Dual, Quad)
  def decode(x: UInt): Seq[Bool] = cases.map(_ === x)
}

object SPIDirection {
  val width = 1
  val Rx = UInt(0, width)
  val Tx = UInt(1, width)
}

object SPIEndian {
  val width = 1
  val MSB = UInt(0, width)
  val LSB = UInt(1, width)
}

object SPICSMode {
  val width = 2
  val Auto = UInt(0, width)
  val Hold = UInt(2, width)
  val Off  = UInt(3, width)
}
