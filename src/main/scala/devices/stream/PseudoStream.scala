// See LICENSE for license details.
package sifive.blocks.devices.stream

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import sifive.blocks.util.{NonBlockingEnqueue, NonBlockingDequeue}

case class PseudoStreamParams(
    address: BigInt,
    nChannels: Int = 1,
    dataBits: Int = 32) {
  require(dataBits <= 63)
}

class PseudoStreamChannelIO(val params: PseudoStreamParams) extends Bundle {
  val txq = Decoupled(UInt(width = params.dataBits))
  val rxq = Decoupled(UInt(width = params.dataBits)).flip
}

class PseudoStreamPortIO(val params: PseudoStreamParams) extends Bundle {
  val channel = Vec(params.nChannels, new PseudoStreamChannelIO(params))
}

abstract class PseudoStream(busWidthBytes: Int, val params: PseudoStreamParams)(implicit p: Parameters)
    extends IORegisterRouter(
      RegisterRouterParams(
        name = "stream",
        compat = Seq("sifive,stream0"),
        base = params.address,
        size = 1 << log2Up(4096 * params.nChannels),
        beatBytes = busWidthBytes),
      new PseudoStreamPortIO(params)) {
  lazy val module = new LazyModuleImp(this) {

  val nbports = Wire(Vec(params.nChannels, new PseudoStreamChannelIO(params)))
  val bports = Wire(Vec(params.nChannels, new PseudoStreamChannelIO(params)))

  regmap(
    List.tabulate(params.nChannels)(idx => List(
      PseudoStreamCtrlRegs.txfifo + 4096 * idx -> NonBlockingEnqueue(nbports(idx).txq, 64),
      PseudoStreamCtrlRegs.rxfifo + 4096 * idx -> NonBlockingDequeue(nbports(idx).rxq, 64),
      PseudoStreamCtrlRegs.txfifob + 4096 * idx -> Seq(
        RegField.w(params.dataBits, bports(idx).txq, RegFieldDesc("txfifob", "blocking txfifo interface"))),
      PseudoStreamCtrlRegs.rxfifob + 4096 * idx -> Seq(
        RegField.r(params.dataBits, bports(idx).rxq, RegFieldDesc("rxfifob", "blocking rxfifo interface")))
    )).flatten:_*)

  (nbports zip bports).zipWithIndex.map { case ((nb, b), idx) =>
    val txq_arb = Module(new Arbiter(UInt(width = params.dataBits), 2))
    txq_arb.io.in(0) <> nb.txq
    txq_arb.io.in(1) <> b.txq
    port.channel(idx).txq <> txq_arb.io.out

    nb.rxq.valid := port.channel(idx).rxq.valid
    nb.rxq.bits := port.channel(idx).rxq.bits
    b.rxq.valid := port.channel(idx).rxq.valid
    b.rxq.bits := port.channel(idx).rxq.bits
    port.channel(idx).rxq.ready := nb.rxq.ready || b.rxq.ready
  }
}}

class TLPseudoStream(busWidthBytes: Int, params: PseudoStreamParams)(implicit p: Parameters)
  extends PseudoStream(busWidthBytes, params) with HasTLControlRegMap

case class PseudoStreamAttachParams(
  stream: PseudoStreamParams,
  controlBus: TLBusWrapper,
  controlXType: ClockCrossingType = NoCrossing,
  mclock: Option[ModuleValue[Clock]] = None,
  mreset: Option[ModuleValue[Bool]] = None)
  (implicit val p: Parameters)

object PseudoStream {
  val nextId = { var i = -1; () => { i += 1; i} }

  def attach(params: PseudoStreamAttachParams): TLPseudoStream = {
    implicit val p = params.p
    val name = s"stream_${nextId()}"
    val cbus =  params.controlBus
    val stream = LazyModule(new TLPseudoStream(cbus.beatBytes, params.stream))
    stream.suggestName(name)

    cbus.coupleTo(s"slave_named_$name") {
      (stream.controlXing(params.controlXType)
        := TLFragmenter(cbus.beatBytes, cbus.blockBytes)
        := TLBuffer(BufferParams.flow) := _)
    }
    InModuleBody { stream.module.clock := params.mclock.map(_.getWrappedValue).getOrElse(cbus.module.clock) }
    InModuleBody { stream.module.reset := params.mreset.map(_.getWrappedValue).getOrElse(cbus.module.reset) }

    stream
  }

  def attachAndMakePort(params: PseudoStreamAttachParams): ModuleValue[PseudoStreamPortIO] = {
    val stream = attach(params)
    val streamNode = stream.ioNode.makeSink()(params.p)
    InModuleBody { streamNode.makeIO()(ValName(stream.name)) }
  }

  def tieoff(port: PseudoStreamPortIO) {
    port.channel.foreach { s =>
      s.txq.ready := true.B
      s.rxq.valid := false.B
    }
  }

  def loopback(port: PseudoStreamPortIO, clock: Clock) {
    port.channel.foreach { s =>
      val q = Module(new Queue(s.txq.bits, 2))
      q.clock := clock
      q.io.enq <> s.txq
      s.rxq <> q.io.deq
    }
  }
}
