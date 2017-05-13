// See LICENSE for license details.
package sifive.blocks.devices.xilinxvc707mig

import Chisel._
import chisel3.experimental.{Analog,attach}
import config._
import diplomacy._
import uncore.tilelink2._
import uncore.axi4._
import rocketchip._
import sifive.blocks.ip.xilinx.vc707mig.{VC707MIGIOClocksReset, VC707MIGIODDR, vc707mig}

trait HasXilinxVC707MIGParameters {
}

class XilinxVC707MIGPads extends Bundle with VC707MIGIODDR

class XilinxVC707MIGIO extends Bundle with VC707MIGIODDR
                                      with VC707MIGIOClocksReset

class XilinxVC707MIG(implicit p: Parameters) extends LazyModule with HasXilinxVC707MIGParameters {
  val device = new MemoryDevice
  val node = TLInputNode()
  val axi4 = AXI4InternalOutputNode(Seq(AXI4SlavePortParameters(
    slaves = Seq(AXI4SlaveParameters(
      address = Seq(AddressSet(p(ExtMem).base, p(ExtMem).size-1)),
      resources     = device.reg,
      regionType    = RegionType.UNCACHED,
      executable    = true,
      supportsWrite = TransferSizes(1, 256*8),
      supportsRead  = TransferSizes(1, 256*8))),
    beatBytes = 8)))

  val xing    = LazyModule(new TLAsyncCrossing)
  val toaxi4  = LazyModule(new TLToAXI4(beatBytes = 8))
  val indexer = LazyModule(new AXI4IdIndexer(idBits = 4))
  val deint   = LazyModule(new AXI4Deinterleaver(p(coreplex.CacheBlockBytes)))
  val yank    = LazyModule(new AXI4UserYanker)
  val buffer  = LazyModule(new AXI4Buffer)

  xing.node := node
  val monitor = (toaxi4.node := xing.node)
  axi4 := buffer.node
  buffer.node := yank.node
  yank.node := deint.node
  deint.node := indexer.node
  indexer.node := toaxi4.node

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val port = new XilinxVC707MIGIO
      val tl = node.bundleIn
    }

    //MIG black box instantiation
    val blackbox = Module(new vc707mig)

    //pins to top level

    //inouts
    attach(io.port.ddr3_dq,blackbox.io.ddr3_dq)
    attach(io.port.ddr3_dqs_n,blackbox.io.ddr3_dqs_n)
    attach(io.port.ddr3_dqs_p,blackbox.io.ddr3_dqs_p)

    //outputs
    io.port.ddr3_addr         := blackbox.io.ddr3_addr
    io.port.ddr3_ba           := blackbox.io.ddr3_ba
    io.port.ddr3_ras_n        := blackbox.io.ddr3_ras_n
    io.port.ddr3_cas_n        := blackbox.io.ddr3_cas_n
    io.port.ddr3_we_n         := blackbox.io.ddr3_we_n
    io.port.ddr3_reset_n      := blackbox.io.ddr3_reset_n
    io.port.ddr3_ck_p         := blackbox.io.ddr3_ck_p
    io.port.ddr3_ck_n         := blackbox.io.ddr3_ck_n
    io.port.ddr3_cke          := blackbox.io.ddr3_cke
    io.port.ddr3_cs_n         := blackbox.io.ddr3_cs_n
    io.port.ddr3_dm           := blackbox.io.ddr3_dm
    io.port.ddr3_odt          := blackbox.io.ddr3_odt

    //inputs
    //NO_BUFFER clock
    blackbox.io.sys_clk_i     := io.port.sys_clk_i

    //user interface signals
    val axi_async = axi4.bundleIn(0)
    xing.module.io.in_clock := clock
    xing.module.io.in_reset := reset
    xing.module.io.out_clock := blackbox.io.ui_clk
    xing.module.io.out_reset := blackbox.io.ui_clk_sync_rst
    (Seq(toaxi4, indexer, deint, yank, buffer) ++ monitor) foreach { lm =>
      lm.module.clock := blackbox.io.ui_clk
      lm.module.reset := blackbox.io.ui_clk_sync_rst
    }

    io.port.ui_clk            := blackbox.io.ui_clk
    io.port.ui_clk_sync_rst   := blackbox.io.ui_clk_sync_rst
    io.port.mmcm_locked       := blackbox.io.mmcm_locked
    blackbox.io.aresetn       := io.port.aresetn
    blackbox.io.app_sr_req    := Bool(false)
    blackbox.io.app_ref_req   := Bool(false)
    blackbox.io.app_zq_req    := Bool(false)
    //app_sr_active           := unconnected
    //app_ref_ack             := unconnected
    //app_zq_ack              := unconnected

    //slave AXI interface write address ports
    blackbox.io.s_axi_awid    := axi_async.aw.bits.id
    blackbox.io.s_axi_awaddr  := axi_async.aw.bits.addr //truncation ??
    blackbox.io.s_axi_awlen   := axi_async.aw.bits.len
    blackbox.io.s_axi_awsize  := axi_async.aw.bits.size
    blackbox.io.s_axi_awburst := axi_async.aw.bits.burst
    blackbox.io.s_axi_awlock  := axi_async.aw.bits.lock
    blackbox.io.s_axi_awcache := UInt("b0011")
    blackbox.io.s_axi_awprot  := axi_async.aw.bits.prot
    blackbox.io.s_axi_awqos   := axi_async.aw.bits.qos
    blackbox.io.s_axi_awvalid := axi_async.aw.valid
    axi_async.aw.ready        := blackbox.io.s_axi_awready

    //slave interface write data ports
    blackbox.io.s_axi_wdata   := axi_async.w.bits.data
    blackbox.io.s_axi_wstrb   := axi_async.w.bits.strb
    blackbox.io.s_axi_wlast   := axi_async.w.bits.last
    blackbox.io.s_axi_wvalid  := axi_async.w.valid
    axi_async.w.ready         := blackbox.io.s_axi_wready

    //slave interface write response
    blackbox.io.s_axi_bready  := axi_async.b.ready
    axi_async.b.bits.id       := blackbox.io.s_axi_bid
    axi_async.b.bits.resp     := blackbox.io.s_axi_bresp
    axi_async.b.valid         := blackbox.io.s_axi_bvalid

    //slave AXI interface read address ports
    blackbox.io.s_axi_arid    := axi_async.ar.bits.id
    blackbox.io.s_axi_araddr  := axi_async.ar.bits.addr //truncation ??
    blackbox.io.s_axi_arlen   := axi_async.ar.bits.len
    blackbox.io.s_axi_arsize  := axi_async.ar.bits.size
    blackbox.io.s_axi_arburst := axi_async.ar.bits.burst
    blackbox.io.s_axi_arlock  := axi_async.ar.bits.lock
    blackbox.io.s_axi_arcache := UInt("b0011")
    blackbox.io.s_axi_arprot  := axi_async.ar.bits.prot
    blackbox.io.s_axi_arqos   := axi_async.ar.bits.qos
    blackbox.io.s_axi_arvalid := axi_async.ar.valid
    axi_async.ar.ready        := blackbox.io.s_axi_arready

    //slace AXI interface read data ports
    blackbox.io.s_axi_rready  := axi_async.r.ready
    axi_async.r.bits.id       := blackbox.io.s_axi_rid
    axi_async.r.bits.data     := blackbox.io.s_axi_rdata
    axi_async.r.bits.resp     := blackbox.io.s_axi_rresp
    axi_async.r.bits.last     := blackbox.io.s_axi_rlast
    axi_async.r.valid         := blackbox.io.s_axi_rvalid

    //misc
    io.port.init_calib_complete := blackbox.io.init_calib_complete
    blackbox.io.sys_rst       :=io.port.sys_rst
    //mig.device_temp         :- unconnceted
  }
}
