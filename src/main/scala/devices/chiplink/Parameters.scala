// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.AsyncQueueParams

case class ChipLinkParams(TLUH: Seq[AddressSet], TLC: Seq[AddressSet], sourceBits: Int = 6, sinkBits: Int = 5, syncTX: Boolean = false, fpgaReset: Boolean = false)
{
  val domains = 8 // hard-wired into chiplink protocol
  require (sourceBits >= log2Ceil(domains))
  require (sinkBits >= 0)
  val sources = 1 << sourceBits
  val sinks = 1 << sinkBits
  val sourcesPerDomain = sources / domains
  val latency = 8 // ChipLink has at least 4 cycles of synchronization per side
  val dataBytes = 4
  val dataBits = dataBytes*8
  val clSourceBits = 16
  val clSinkBits = 16
  val crossing = AsyncQueueParams()
  val Qdepth = 8192 / dataBytes
  val maxXfer = 4096
  val xferBits = log2Ceil(maxXfer)
  val creditBits = 20 // use saturating addition => we can exploit at most 1MB of buffers
  val addressBits = 64
  require (log2Ceil(Qdepth + 1) <= creditBits)

  // Protocol supported operations:
  val noXfer = TransferSizes.none
  val fullXfer = TransferSizes(1, 64) // !!! 4096)
  val acqXfer = TransferSizes(64, 64)
  val atomicXfer = TransferSizes(1, 8)

}

case object ChipLinkKey extends Field[Seq[ChipLinkParams]]

case class TXN(domain: Int, source: Int)
case class ChipLinkInfo(params: ChipLinkParams, edgeIn: TLEdge, edgeOut: TLEdge, errorDev: AddressSet)
{
  // TL source => CL TXN
  val sourceMap: Map[Int, TXN] = {
    var alloc = 1
    val domains = Array.fill(params.domains) { 0 }
    println("ChipLink source mapping CLdomain CLsource <= TLsource:")
    val out = Map() ++ edgeIn.client.clients.flatMap { c =>
      // If the client needs order, pick a domain for it
      val domain = if (c.requestFifo) alloc else 0
      val offset = domains(domain)
      println(s"\t${domain} [${offset}, ${offset + c.sourceId.size}) <= [${c.sourceId.start}, ${c.sourceId.end}):\t${c.name}")
      if (c.requestFifo) {
        alloc = alloc + 1
        if (alloc == params.domains) alloc = 1
      }
      c.sourceId.range.map { id =>
        val source = domains(domain)
        domains(domain) = source + 1
        (id, TXN(domain, source))
      }
    }
    println("")
    out
  }

  def mux(m: Map[Int, Int]): Vec[UInt] = {
    val maxKey = m.keys.max
    val maxVal = m.values.max
    val valBits = log2Up(maxVal + 1)
    val out = Wire(Vec(maxKey + 1, UInt(width = valBits)))
    m.foreach { case (k, v) => out(k) := UInt(v, width = valBits) }
    out
  }

  // Packet format; little-endian
  def encode(format: UInt, opcode: UInt, param: UInt, size: UInt, domain: UInt, source: UInt): UInt = {
    def fmt(x: UInt, w: Int) = (x | UInt(0, width=w))(w-1, 0)
    Cat(
      fmt(source, 16),
      fmt(domain, 3),
      fmt(size,   4),
      fmt(param,  3),
      fmt(opcode, 3),
      fmt(format, 3))
  }

  def decode(x: UInt): Seq[UInt] = {
    val format = x( 2,  0)
    val opcode = x( 5,  3)
    val param  = x( 8,  6)
    val size   = x(12,  9)
    val domain = x(15, 13)
    val source = x(31, 16)
    Seq(format, opcode, param, size, domain, source)
  }

  def size2beats(size: UInt): UInt = {
    val shift = log2Ceil(params.dataBytes)
    Cat(UIntToOH(size|UInt(0, width=4), params.xferBits + 1) >> (shift + 1), size <= UInt(shift))
  }

  def mask2beats(size: UInt): UInt = {
    val shift = log2Ceil(params.dataBytes*8)
    Cat(UIntToOH(size|UInt(0, width=4), params.xferBits + 1) >> (shift + 1), size <= UInt(shift))
  }

  def beats1(x: UInt, forceFormat: Option[UInt] = None): UInt = {
    val Seq(format, opcode, _, size, _, _) = decode(x)
    val beats = size2beats(size)
    val masks = mask2beats(size)
    val grant = opcode === TLMessages.Grant || opcode === TLMessages.GrantData
    val partial = opcode === TLMessages.PutPartialData
    val a = Mux(opcode(2), UInt(0), beats) + UInt(2) + Mux(partial, masks, UInt(0))
    val b = Mux(opcode(2), UInt(0), beats) + UInt(2) + Mux(partial, masks, UInt(0))
    val c = Mux(opcode(0), beats, UInt(0)) + UInt(2)
    val d = Mux(opcode(0), beats, UInt(0)) + grant.asUInt
    val e = UInt(0)
    val f = UInt(0)
    Vec(a, b, c, d, e, f)(forceFormat.getOrElse(format))
  }

  def firstlast(x: DecoupledIO[UInt], forceFormat: Option[UInt] = None): (Bool, Bool) = {
    val count = RegInit(UInt(0))
    val beats = beats1(x.bits, forceFormat)
    val first = count === UInt(0)
    val last  = count === UInt(1) || (first && beats === UInt(0))
    when (x.fire()) { count := Mux(first, beats, count - UInt(1)) }
    (first, last)
  }

  // You can't just unilaterally use error, because this would misalign the mask
  def makeError(legal: Bool, address: UInt): UInt = {
    val alignBits = log2Ceil(errorDev.alignment)
    Cat(
      Mux(legal, address, UInt(errorDev.base))(params.addressBits-1, alignBits),
      address(alignBits-1, 0))
  }
}
