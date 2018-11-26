// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._

abstract class SPIBundle(val c: SPIParamsBase) extends Bundle

class SPIDataIO extends Bundle {
  val i = Bool(INPUT)
  val o = Bool(OUTPUT)
  val oe = Bool(OUTPUT)
}

class SPIPortIO(c: SPIParamsBase) extends SPIBundle(c) {
  val sck = Bool(OUTPUT)
  val dq = Vec(4, new SPIDataIO)
  val cs = Vec(c.csWidth, Bool(OUTPUT))
}

trait HasSPIProtocol {
  val proto = Bits(width = SPIProtocol.width)
}
trait HasSPIEndian {
  val endian = Bits(width = SPIEndian.width)
}
class SPIFormat(c: SPIParamsBase) extends SPIBundle(c)
    with HasSPIProtocol
    with HasSPIEndian {
  val iodir = Bits(width = SPIDirection.width)
}

trait HasSPILength extends SPIBundle {
  val len = UInt(width = c.lengthBits)
}

class SPIClocking(c: SPIParamsBase) extends SPIBundle(c) {
  val div = UInt(width = c.divisorBits)
  val pol = Bool()
  val pha = Bool()
}

class SPIChipSelect(c: SPIParamsBase) extends SPIBundle(c) {
  val id = UInt(width = c.csIdBits)
  val dflt = Vec(c.csWidth, Bool())

  def toggle(en: Bool): Vec[Bool] = {
    val mask = en << id
    val out = Cat(dflt.reverse) ^ mask
    Vec.tabulate(c.csWidth)(out(_))
  }
}

trait HasSPICSMode {
  val mode = Bits(width = SPICSMode.width)
}

class SPIDelay(c: SPIParamsBase) extends SPIBundle(c) {
  val cssck = UInt(width = c.delayBits)
  val sckcs = UInt(width = c.delayBits)
  val intercs = UInt(width = c.delayBits)
  val interxfr = UInt(width = c.delayBits)
}

class SPIWatermark(c: SPIParamsBase) extends SPIBundle(c) {
  val tx = UInt(width = c.txDepthBits)
  val rx = UInt(width = c.rxDepthBits)
}

class SPIControl(c: SPIParamsBase) extends SPIBundle(c) {
  val fmt = new SPIFormat(c) with HasSPILength
  val sck = new SPIClocking(c)
  val cs = new SPIChipSelect(c) with HasSPICSMode
  val dla = new SPIDelay(c)
  val wm = new SPIWatermark(c)
  val extradel = new SPIExtraDelay(c)
  val sampledel = new SPISampleDelay(c)
}

object SPIControl {
  def init(c: SPIParamsBase): SPIControl = {
    val ctrl = Wire(new SPIControl(c))
    ctrl.fmt.proto := SPIProtocol.Single
    ctrl.fmt.iodir := SPIDirection.Rx
    ctrl.fmt.endian := SPIEndian.MSB
    ctrl.fmt.len := UInt(math.min(c.frameBits, 8))
    ctrl.sck.div := UInt(3)
    ctrl.sck.pol := Bool(false)
    ctrl.sck.pha := Bool(false)
    ctrl.cs.id := UInt(0)
    ctrl.cs.dflt.foreach { _ := Bool(true) }
    ctrl.cs.mode := SPICSMode.Auto
    ctrl.dla.cssck := UInt(1)
    ctrl.dla.sckcs := UInt(1)
    ctrl.dla.intercs := UInt(1)
    ctrl.dla.interxfr := UInt(0)
    ctrl.wm.tx := UInt(0)
    ctrl.wm.rx := UInt(0)
    ctrl.extradel.coarse := UInt(0)
    ctrl.extradel.fine := UInt(0)
    ctrl.sampledel.sd := UInt(c.defaultSampleDel)
    ctrl
  }
}

class SPIInterrupts extends Bundle {
  val txwm = Bool()
  val rxwm = Bool()
}
