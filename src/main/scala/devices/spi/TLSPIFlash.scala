// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._
import config._
import diplomacy._
import regmapper._
import uncore.tilelink2._

trait SPIFlashParamsBase extends SPIParamsBase {
  val fAddress: BigInt
  val fSize: BigInt

  val insnAddrBytes: Int
  val insnPadLenBits: Int
  lazy val insnCmdBits = frameBits
  lazy val insnAddrBits = insnAddrBytes * frameBits
  lazy val insnAddrLenBits = log2Floor(insnAddrBytes) + 1
}

case class SPIFlashParams(
    rAddress: BigInt,
    fAddress: BigInt,
    rSize: BigInt = 0x1000,
    fSize: BigInt = 0x20000000,
    rxDepth: Int = 8,
    txDepth: Int = 8,
    csWidth: Int = 1,
    delayBits: Int = 8,
    divisorBits: Int = 12,
    sampleDelay: Int = 2)
  extends SPIFlashParamsBase {
  val frameBits = 8
  val insnAddrBytes = 4
  val insnPadLenBits = 4

  require(insnPadLenBits <= delayBits)
  require(sampleDelay >= 0)
}

class SPIFlashTopBundle(i: util.HeterogeneousBag[Vec[Bool]], r: util.HeterogeneousBag[TLBundle], val f: util.HeterogeneousBag[TLBundle]) extends SPITopBundle(i, r)

class SPIFlashTopModule[B <: SPIFlashTopBundle]
    (c: SPIFlashParamsBase, bundle: => B, outer: TLSPIFlashBase)
  extends SPITopModule(c, bundle, outer) {

  val flash = Module(new SPIFlashMap(c))
  val arb = Module(new SPIArbiter(c, 2))

  private val f = io.tl.f.head
  // Tie unused channels
  f.b.valid := Bool(false)
  f.c.ready := Bool(true)
  f.e.ready := Bool(true)

  val a = Reg(f.a.bits)
  val a_msb = log2Ceil(c.fSize) - 1

  when (f.a.fire()) {
    a := f.a.bits
  }

  flash.io.addr.bits.next := f.a.bits.address(a_msb, 0)
  flash.io.addr.bits.hold := a.address(a_msb, 0)
  flash.io.addr.valid := f.a.valid
  f.a.ready := flash.io.addr.ready

  f.d.bits := outer.fnode.edgesIn.head.AccessAck(a, UInt(0), flash.io.data.bits)
  f.d.valid := flash.io.data.valid
  flash.io.data.ready := f.d.ready

  val insn = Reg(init = SPIFlashInsn.init(c))
  val flash_en = Reg(init = Bool(true))

  flash.io.ctrl.insn := insn
  flash.io.ctrl.fmt <> ctrl.fmt
  flash.io.en := flash_en
  arb.io.sel := !flash_en

  protected val regmapFlash = Seq(
    SPICRs.insnmode -> Seq(RegField(1, flash_en)),
    SPICRs.insnfmt -> Seq(
      RegField(1, insn.cmd.en),
      RegField(c.insnAddrLenBits, insn.addr.len),
      RegField(c.insnPadLenBits, insn.pad.cnt)),
    SPICRs.insnproto -> Seq(
      RegField(SPIProtocol.width, insn.cmd.proto),
      RegField(SPIProtocol.width, insn.addr.proto),
      RegField(SPIProtocol.width, insn.data.proto)),
    SPICRs.insncmd -> Seq(RegField(c.insnCmdBits, insn.cmd.code)),
    SPICRs.insnpad -> Seq(RegField(c.frameBits, insn.pad.code)))
}

abstract class TLSPIFlashBase(w: Int, c: SPIFlashParamsBase)(implicit p: Parameters) extends TLSPIBase(w,c)(p) {
  require(isPow2(c.fSize))
  val fnode = TLManagerNode(1, TLManagerParameters(
    address     = Seq(AddressSet(c.fAddress, c.fSize-1)),
    resources   = Seq(Resource(device, "ranges")),
    regionType  = RegionType.UNCACHED,
    executable  = true,
    supportsGet = TransferSizes(1, 1),
    fifoId      = Some(0)))
}

class TLSPIFlash(w: Int, c: SPIFlashParams)(implicit p: Parameters) extends TLSPIFlashBase(w,c)(p) {
  lazy val module = new SPIFlashTopModule(c,
    new SPIFlashTopBundle(intnode.bundleOut, rnode.bundleIn, fnode.bundleIn), this) {

    arb.io.inner(0) <> flash.io.link
    arb.io.inner(1) <> fifo.io.link
    mac.io.link <> arb.io.outer

    rnode.regmap(regmapBase ++ regmapFlash:_*)
  }
}
