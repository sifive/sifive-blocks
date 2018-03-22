// See LICENSE for license details.
package sifive.blocks.devices.chiplink

import Chisel._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink.TLBusBypass
import freechips.rocketchip.util._

class ChipLink(val params: ChipLinkParams)(implicit p: Parameters) extends LazyModule() {

  val device = new SimpleBus("chiplink", Seq("sifive,chiplink"))

  private def maybeManager(x: Seq[AddressSet], f: Seq[AddressSet] => TLManagerParameters) =
    if (x.isEmpty) Nil else Seq(f(x))

  private val slaveNode = TLManagerNode(Seq(TLManagerPortParameters(
    managers =
      maybeManager(params.TLUH, a => TLManagerParameters(
        address            = a,
        resources          = device.ranges,
        regionType         = RegionType.GET_EFFECTS,
        executable         = true,
        supportsArithmetic = params.atomicXfer,
        supportsLogical    = params.atomicXfer,
        supportsGet        = params.fullXfer,
        supportsPutFull    = params.fullXfer,
        supportsPutPartial = params.fullXfer,
        supportsHint       = params.fullXfer,
        fifoId             = Some(0))) ++
      maybeManager(params.TLC, a => TLManagerParameters(
        address            = a,
        resources          = device.ranges,
        regionType         = RegionType.TRACKED,
        executable         = true,
        supportsAcquireT   = params.acqXfer,
        supportsAcquireB   = params.acqXfer,
        supportsArithmetic = params.atomicXfer,
        supportsLogical    = params.atomicXfer,
        supportsGet        = params.fullXfer,
        supportsPutFull    = params.fullXfer,
        supportsPutPartial = params.fullXfer,
        supportsHint       = params.fullXfer,
        fifoId             = Some(0))),
    beatBytes  = 4,
    endSinkId  = params.sinks,
    minLatency = params.latency)))

  // Masters 1+ require order; Master 0 is unordered and may cache
  private val masterNode = TLClientNode(Seq(TLClientPortParameters(
    clients = Seq.tabulate(params.domains) { i =>
      TLClientParameters(
        name          = "ChipLink Domain #" + i,
        sourceId      = IdRange(i*params.sourcesPerDomain, (i + 1)*params.sourcesPerDomain),
        requestFifo   = i > 0,
        supportsProbe = if (i == 0) params.fullXfer else params.noXfer) },
    minLatency = params.latency)))

  private val bypass = LazyModule(new TLBusBypass(beatBytes = 4))
  slaveNode := bypass.node

  val node = NodeHandle(bypass.node, masterNode)

  // Exported memory map. Used when connecting VIP
  lazy val managers = masterNode.edges.out(0).manager.managers
  lazy val mmap = {
    val (tlc, tluh) = managers.partition(_.supportsAcquireB)
    params.copy(
      TLUH = AddressSet.unify(tluh.flatMap(_.address)),
      TLC  = AddressSet.unify(tlc.flatMap(_.address)))
  }

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val port = new WideDataLayerPort(params)
      val bypass = Bool(OUTPUT)
      // These are fed to port.c2b.{clk,rst} -- must be specified by creator
      val c2b_clk = Clock(INPUT)
      val c2b_rst = Bool(INPUT)
    })

    // Ensure downstream devices support our requirements
    val (in,  edgeIn)  = slaveNode.in(0)
    val (out, edgeOut) = masterNode.out(0)

    require (edgeIn.manager.beatBytes == 4)
    edgeOut.manager.requireFifo()

    edgeOut.manager.managers.foreach { m =>
      require (m.supportsGet.contains(params.fullXfer),
        s"ChipLink requires ${m.name} support ${params.fullXfer} Get, not ${m.supportsGet}")
      if (m.supportsPutFull) {
        require (m.supportsPutFull.contains(params.fullXfer),
          s"ChipLink requires ${m.name} support ${params.fullXfer} PutFill, not ${m.supportsPutFull}")
        // !!! argh. AHB devices can't: require (m.supportsPutPartial.contains(params.fullXfer),
        //  s"ChipLink requires ${m.name} support ${params.fullXfer} PutPartial not ${m.supportsPutPartial}")
        require (m.supportsArithmetic.contains(params.atomicXfer),
          s"ChipLink requires ${m.name} support ${params.atomicXfer} Arithmetic, not ${m.supportsArithmetic}")
        require (m.supportsLogical.contains(params.atomicXfer),
          s"ChipLink requires ${m.name} support ${params.atomicXfer} Logical, not ${m.supportsLogical}")
      }
      require (m.supportsHint.contains(params.fullXfer),
        s"ChipLink requires ${m.name} support ${params.fullXfer} Hint, not ${m.supportsHint}")
      require (!m.supportsAcquireT || m.supportsAcquireT.contains(params.acqXfer),
        s"ChipLink requires ${m.name} support ${params.acqXfer} AcquireT, not ${m.supportsAcquireT}")
      require (!m.supportsAcquireB || m.supportsAcquireB.contains(params.acqXfer),
        s"ChipLink requires ${m.name} support ${params.acqXfer} AcquireB, not ${m.supportsAcquireB}")
      require (!m.supportsAcquireB || !m.supportsPutFull || m.supportsAcquireT,
        s"ChipLink requires ${m.name} to support AcquireT if it supports Put and AcquireB")
    }

    // Anything that is optional, must be supported by the error device (for redirect)
    val errorDevs = edgeOut.manager.managers.filter(_.nodePath.last.lazyModule.className == "TLError")
    require (!errorDevs.isEmpty, "There is no TLError reachable from ChipLink. One must be instantiated.")
    val errorDev = errorDevs.head
    require (errorDev.supportsPutFull.contains(params.fullXfer),
      s"ChipLink requires ${errorDev.name} support ${params.fullXfer} PutFill, not ${errorDev.supportsPutFull}")
    require (errorDev.supportsPutPartial.contains(params.fullXfer),
      s"ChipLink requires ${errorDev.name} support ${params.fullXfer} PutPartial not ${errorDev.supportsPutPartial}")
    require (errorDev.supportsArithmetic.contains(params.atomicXfer),
      s"ChipLink requires ${errorDev.name} support ${params.atomicXfer} Arithmetic, not ${errorDev.supportsArithmetic}")
    require (errorDev.supportsLogical.contains(params.atomicXfer),
      s"ChipLink requires ${errorDev.name} support ${params.atomicXfer} Logical, not ${errorDev.supportsLogical}")
    require (errorDev.supportsAcquireT.contains(params.acqXfer),
      s"ChipLink requires ${errorDev.name} support ${params.acqXfer} AcquireT, not ${errorDev.supportsAcquireT}")

    // At most one cache can master ChipLink
    require (edgeIn.client.clients.filter(_.supportsProbe).size <= 1,
      s"ChipLink supports at most one caching master, ${edgeIn.client.clients.filter(_.supportsProbe).map(_.name)}")

    // Construct the info needed by all submodules
    val info = ChipLinkInfo(params, edgeIn, edgeOut, errorDevs.head.address.head.base)

    val sinkA = Module(new SinkA(info))
    val sinkB = Module(new SinkB(info))
    val sinkC = Module(new SinkC(info))
    val sinkD = Module(new SinkD(info))
    val sinkE = Module(new SinkE(info))
    val sourceA = Module(new SourceA(info))
    val sourceB = Module(new SourceB(info))
    val sourceC = Module(new SourceC(info))
    val sourceD = Module(new SourceD(info))
    val sourceE = Module(new SourceE(info))

    val rx = Module(new RX(info))
    rx.clock := io.port.b2c.clk
    rx.reset := io.port.b2c.rst
    rx.io.b2c_data := io.port.b2c.data
    rx.io.b2c_send := io.port.b2c.send
    out.a <> sourceA.io.a
    in .b <> sourceB.io.b
    out.c <> sourceC.io.c
    in .d <> sourceD.io.d
    out.e <> sourceE.io.e
    sourceA.io.q <> FromAsyncBundle(rx.io.a)
    sourceB.io.q <> FromAsyncBundle(rx.io.b)
    sourceC.io.q <> FromAsyncBundle(rx.io.c)
    sourceD.io.q <> FromAsyncBundle(rx.io.d)
    sourceE.io.q <> FromAsyncBundle(rx.io.e)

    val tx = Module(new TX(info))
    io.port.c2b.data := tx.io.c2b_data
    io.port.c2b.send := tx.io.c2b_send
    sinkA.io.a <> in .a
    sinkB.io.b <> out.b
    sinkC.io.c <> in .c
    sinkD.io.d <> out.d
    sinkE.io.e <> in .e
    if (params.syncTX) {
      tx.io.sa <> sinkA.io.q
      tx.io.sb <> sinkB.io.q
      tx.io.sc <> sinkC.io.q
      tx.io.sd <> sinkD.io.q
      tx.io.se <> sinkE.io.q
    } else {
      tx.clock := io.port.c2b.clk
      tx.reset := io.port.c2b.rst
      tx.io.a <> ToAsyncBundle(sinkA.io.q, params.crossingDepth)
      tx.io.b <> ToAsyncBundle(sinkB.io.q, params.crossingDepth)
      tx.io.c <> ToAsyncBundle(sinkC.io.q, params.crossingDepth)
      tx.io.d <> ToAsyncBundle(sinkD.io.q, params.crossingDepth)
      tx.io.e <> ToAsyncBundle(sinkE.io.q, params.crossingDepth)
    }

    // Pass credits from RX to TX
    tx.io.rxc <> rx.io.rxc
    tx.io.txc <> rx.io.txc

    // Connect the CAM source pools
    sinkD.io.a_clSource := sourceA.io.d_clSource
    sourceA.io.d_tlSource := sinkD.io.a_tlSource
    sinkD.io.c_clSource := sourceC.io.d_clSource
    sourceC.io.d_tlSource := sinkD.io.c_tlSource
    sourceD.io.e_tlSink := sinkE.io.d_tlSink
    sinkE.io.d_clSink := sourceD.io.e_clSink

    // Create the TX clock domain from input
    io.port.c2b.clk := io.c2b_clk
    io.port.c2b.rst := io.c2b_rst

    // Disable ChipLink while RX+TX are in reset
    val do_bypass = ResetCatchAndSync(clock, rx.reset) || ResetCatchAndSync(clock, tx.reset)
    bypass.module.io.bypass := do_bypass
    io.bypass := do_bypass
  }
}
