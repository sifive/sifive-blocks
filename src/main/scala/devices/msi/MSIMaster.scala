// See LICENSE for license details.

package sifive.blocks.devices.msi

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.leftOR

case class MSITarget(address: BigInt, spacing: Int, number: Int)
{
  require (number >= 0)
  require (address >= 0)
}

class MSIMaster(targets: Seq[MSITarget])(implicit p: Parameters) extends LazyModule
{
  val masterNode = TLClientNode(Seq(TLClientPortParameters(Seq(TLClientParameters("MSI Master", sourceId = IdRange(0,2))))))

  // A terminal interrupt node of flexible number
  val intNode = IntNexusNode(
    sourceFn = { _ => IntSourcePortParameters(Seq(IntSourceParameters(1, Nil))) },
    sinkFn   = { _ => IntSinkPortParameters(Seq(IntSinkParameters())) },
    inputRequiresOutput = false)

  lazy val module = new LazyModuleImp(this) {
    val (io, masterEdge) = masterNode.out(0)
    val interrupts = intNode.in.flatMap { case (i, e) => i.take(e.source.num) }

    // Construct a map of the addresses to update for interrupts
    val targetMap = targets.flatMap { case MSITarget(address, spacing, number) =>
      address until (address+spacing*number) by spacing
    } .map { addr =>
      val m = masterEdge.manager.find(addr)
      require (m.isDefined, s"MSIMaster ${name} was pointed at address 0x${addr}%x which does not exist")
      require (m.get.supportsPutFull.contains(1), s"MSIMaster ${name} requires device ${m.get.name} supportPutFull of 1 byte (${m.get.supportsPutFull})")
      UInt(addr)
    }.take(interrupts.size max 1)

    require (interrupts.size <= targetMap.size, s"MSIMaster ${name} has more interrupts (${interrupts.size}) than addresses to use (${targetMap.size})")
    require (intNode.out.isEmpty, s"MSIMaster ${name} intNode is not a source!")

    val busy    = RegInit(Bool(false))
    val remote  = RegInit(UInt(0, width=interrupts.size max 1))
    val local   = if (interrupts.isEmpty) UInt(0) else Cat(interrupts.reverse)
    val pending = remote ^ local
    val select  = ~(leftOR(pending) << 1) & pending

    io.a.valid := pending.orR && !busy
    io.a.bits := masterEdge.Put(
      fromSource = UInt(0),
      toAddress  = Mux1H(select, targetMap),
      lgSize     = UInt(0),
      data       = (select & local).orR)._2

    // When A is sent, toggle our model of the remote state
    when (io.a.fire()) {
      remote := remote ^ select
      busy   := Bool(true)
    }

    // Sink D messages to clear busy
    io.d.ready := Bool(true)
    when (io.d.fire()) {
      busy := Bool(false)
    }

    // Tie off unused channels
    io.b.ready := Bool(false)
    io.c.valid := Bool(false)
    io.e.valid := Bool(false)
  }
}
