// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel.{defaultCompileOptions => _, _}
import freechips.rocketchip.util.CompileOptions.NotStrictInferReset
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util.HeterogeneousBag
import freechips.rocketchip.diplomaticobjectmodel.model.{OMComponent, OMRegister}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalModuleTree, LogicalTreeNode}
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import sifive.blocks.util._

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
    fineDelayBits: Int = 0,
    sampleDelayBits: Int = 5,
    defaultSampleDel: Int = 3,
    oeDisableDummy: Boolean = false
    )
  extends SPIFlashParamsBase with DeviceParams {
  val frameBits = 8
  val insnAddrBytes = 4
  val insnPadLenBits = 4

  require(insnPadLenBits <= delayBits)
  require((fineDelayBits == 0) | (fineDelayBits == 5), s"Require fine delay bits to be 0 or 5 and not $fineDelayBits")
  require(sampleDelayBits >= 0)
  require(defaultSampleDel >= 0)
}

class SPIFlashTopModule(c: SPIFlashParamsBase, outer: TLSPIFlashBase)
  extends SPITopModule(c, outer) {

  val flash = Module(new SPIFlashMap(c))
  val arb = Module(new SPIArbiter(c, 2))

  private val (f, _) = outer.fnode.in(0)
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

  f.d.bits := outer.fnode.edges.in.head.AccessAck(a, flash.io.data.bits)
  f.d.valid := flash.io.data.valid
  flash.io.data.ready := f.d.ready

  val insn = Reg(init = SPIFlashInsn.init(c))
  val flash_en = Reg(init = Bool(true))

  flash.io.ctrl.insn := insn
  flash.io.ctrl.fmt <> ctrl.fmt
  flash.io.en := flash_en
  arb.io.sel := !flash_en

  val regmapFlash = Seq(
    SPICRs.insnmode -> Seq(RegField(1, flash_en,
                           RegFieldDesc("flash_en", "SPIFlash mode select", reset=Some(1)))),
    // Note that these are all in the 'ffmt' group, but are defined with seperate calls
    // because the addresses are actually byte addresses. This makes it easy to align
    // them to byte boundaries without explicitly having to add padding.
    SPICRs.insnfmt -> RegFieldGroup("ffmt", Some("SPIFlash read instruction format"), Seq(
      RegField(1, insn.cmd.en,
        RegFieldDesc("cmd_en", "Enable sending of command", reset=Some(1))),
      RegField(c.insnAddrLenBits, insn.addr.len,
        RegFieldDesc("addr_len", "Number of address bytes", reset=Some(3))),
      RegField(c.insnPadLenBits, insn.pad.cnt,
        RegFieldDesc("pad_cnt", "Number of dummy cycles", reset=Some(0))))),
    SPICRs.insnproto -> RegFieldGroup("ffmt", None, Seq(
      RegField(SPIProtocol.width, insn.cmd.proto,
        RegFieldDesc("cmd_proto", "Protocol for transmitting command", reset=Some(0))),
      RegField(SPIProtocol.width, insn.addr.proto,
        RegFieldDesc("addr_proto", "Protocol for transmitting address and padding", reset=Some(0))),
      RegField(SPIProtocol.width, insn.data.proto,
        RegFieldDesc("data_proto", "Protocol for transmitting receiving data", reset=Some(0))))),
    SPICRs.insncmd -> RegFieldGroup("ffmt", None, Seq(
      RegField(c.insnCmdBits, insn.cmd.code,
        RegFieldDesc("cmd_code", "Value of command byte", reset=Some(3))))),
    SPICRs.insnpad -> RegFieldGroup("ffmt", None, Seq(
      RegField(c.frameBits, insn.pad.code,
        RegFieldDesc("pad_code", "First 8 bits to transmit during dummy cycles", reset=Some(0)))))
  )
}

abstract class TLSPIFlashBase(w: Int, c: SPIFlashParamsBase)(implicit p: Parameters) extends TLSPIBase(w,c)(p) {
  require(isPow2(c.fSize))
  val fnode = TLManagerNode(Seq(TLSlavePortParameters.v1(
    managers = Seq(TLSlaveParameters.v1(
      address     = Seq(AddressSet(c.fAddress, c.fSize-1)),
      resources   = device.reg("mem"),
      regionType  = RegionType.UNCACHED,
      executable  = true,
      supportsGet = TransferSizes(1, 1),
      fifoId      = Some(0))),
    beatBytes = 1)))
  val memXing = this.crossIn(fnode)
}

class TLSPIFlash(w: Int, c: SPIFlashParams)(implicit p: Parameters)
    extends TLSPIFlashBase(w,c)(p)
    with HasTLControlRegMap {
  lazy val module = new SPIFlashTopModule(c, this) {

    arb.io.inner(0) <> flash.io.link
    arb.io.inner(1) <> fifo.io.link
    mac.io.link <> arb.io.outer

    val totalMapping = (regmapBase ++ regmapFlash)
    regmap(totalMapping:_*)
    val omRegMap = OMRegister.convert(totalMapping:_*)
  }

  val logicalTreeNode = new LogicalTreeNode(() => Some(device)) {
    def getOMComponents(resourceBindings: ResourceBindings, children: Seq[OMComponent] = Nil): Seq[OMComponent] = {
      // Get all the memory regions, but don't associate a register map to any of them yet
      val diplomaticRegions = DiplomaticObjectModelAddressing.getOMMemoryRegions("SPIXIP", resourceBindings/*, Some(module.omRegMap)*/)
      // The regmap goes with the "control" region so add it and don't alter the others.
      require(diplomaticRegions.exists(_.description == "control"),
        "There should be a memory region with description \"control\" to connect the regmap to")
      val memoryRegions = diplomaticRegions.map{ memRegion =>
        if (memRegion.description == "control") { memRegion.copy(registerMap = Some(module.omRegMap)) } else {memRegion}
      }

      Seq(
        OMSPIXIP(
          rxDepth = c.rxDepth,
          txDepth = c.txDepth,
          csWidthBits = c.csWidth,
          frameBits = c.frameBits,
          delayBits = c.delayBits,
          divisorBits = c.divisorBits,
          coarseDelayBits = c.divisorBits,
          fineDelayBits = c.fineDelayBits,
          sampleDelayBits = c.sampleDelayBits,
          defaultSampleDelay = c.defaultSampleDel,
          instructionAddressBytes = c.insnAddrBytes,
          instructionPadLengthBits = c.insnPadLenBits,
          memMapAddressBase = c.fAddress,
          memMapAddressSizeBytes = c.fSize,
          memoryRegions = memoryRegions,
          interrupts = DiplomaticObjectModelAddressing.describeGlobalInterrupts(device.describe(resourceBindings).name, resourceBindings)
        )
      )
    }
  }


}
