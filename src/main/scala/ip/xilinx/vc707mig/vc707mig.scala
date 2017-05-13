// See LICENSE for license details.
package sifive.blocks.ip.xilinx.vc707mig

import Chisel._
import chisel3.experimental.{Analog,attach}
import config._
import junctions._

// IP VLNV: xilinx.com:customize_ip:vc707mig:1.0
// Black Box

trait VC707MIGIODDR extends Bundle {
  val ddr3_addr             = Bits(OUTPUT,14)
  val ddr3_ba               = Bits(OUTPUT,3)
  val ddr3_ras_n            = Bool(OUTPUT)
  val ddr3_cas_n            = Bool(OUTPUT)
  val ddr3_we_n             = Bool(OUTPUT)
  val ddr3_reset_n          = Bool(OUTPUT)
  val ddr3_ck_p             = Bits(OUTPUT,1)
  val ddr3_ck_n             = Bits(OUTPUT,1)
  val ddr3_cke              = Bits(OUTPUT,1)
  val ddr3_cs_n             = Bits(OUTPUT,1)
  val ddr3_dm               = Bits(OUTPUT,8)
  val ddr3_odt              = Bits(OUTPUT,1)

  val ddr3_dq               = Analog(64.W)
  val ddr3_dqs_n            = Analog(8.W)
  val ddr3_dqs_p            = Analog(8.W)
}

//reused directly in io bundle for sifive.blocks.devices.xilinxvc707mig
trait VC707MIGIOClocksReset extends Bundle {
  //inputs
  //"NO_BUFFER" clock source (must be connected to IBUF outside of IP)
  val sys_clk_i             = Bool(INPUT)
  //user interface signals
  val ui_clk                = Clock(OUTPUT)
  val ui_clk_sync_rst       = Bool(OUTPUT)
  val mmcm_locked           = Bool(OUTPUT)
  val aresetn               = Bool(INPUT)
  //misc
  val init_calib_complete   = Bool(OUTPUT)
  val sys_rst               = Bool(INPUT)
}

//scalastyle:off
//turn off linter: blackbox name must match verilog module 
class vc707mig(implicit val p:Parameters) extends BlackBox
{
  val io = new Bundle with VC707MIGIODDR
                      with VC707MIGIOClocksReset {
    // User interface signals
    val app_sr_req            = Bool(INPUT)
    val app_ref_req           = Bool(INPUT)
    val app_zq_req            = Bool(INPUT)
    val app_sr_active         = Bool(OUTPUT)
    val app_ref_ack           = Bool(OUTPUT)
    val app_zq_ack            = Bool(OUTPUT)
    //axi_s
    //slave interface write address ports
    val s_axi_awid            = Bits(INPUT,4)
    val s_axi_awaddr          = Bits(INPUT,30)
    val s_axi_awlen           = Bits(INPUT,8)
    val s_axi_awsize          = Bits(INPUT,3)
    val s_axi_awburst         = Bits(INPUT,2)
    val s_axi_awlock          = Bits(INPUT,1)
    val s_axi_awcache         = Bits(INPUT,4)
    val s_axi_awprot          = Bits(INPUT,3)
    val s_axi_awqos           = Bits(INPUT,4)
    val s_axi_awvalid         = Bool(INPUT)
    val s_axi_awready         = Bool(OUTPUT)
    //slave interface write data ports
    val s_axi_wdata           = Bits(INPUT,64)
    val s_axi_wstrb           = Bits(INPUT,8)
    val s_axi_wlast           = Bool(INPUT)
    val s_axi_wvalid          = Bool(INPUT)
    val s_axi_wready          = Bool(OUTPUT)
    //slave interface write response ports
    val s_axi_bready          = Bool(INPUT)
    val s_axi_bid             = Bits(OUTPUT,4)
    val s_axi_bresp           = Bits(OUTPUT,2)
    val s_axi_bvalid          = Bool(OUTPUT)
    //slave interface read address ports
    val s_axi_arid            = Bits(INPUT,4)
    val s_axi_araddr          = Bits(INPUT,30)
    val s_axi_arlen           = Bits(INPUT,8)
    val s_axi_arsize          = Bits(INPUT,3)
    val s_axi_arburst         = Bits(INPUT,2)
    val s_axi_arlock          = Bits(INPUT,1)
    val s_axi_arcache         = Bits(INPUT,4)
    val s_axi_arprot          = Bits(INPUT,3)
    val s_axi_arqos           = Bits(INPUT,4)
    val s_axi_arvalid         = Bool(INPUT)
    val s_axi_arready         = Bool(OUTPUT)
    //slave interface read data ports
    val s_axi_rready          = Bool(INPUT)
    val s_axi_rid             = Bits(OUTPUT,4)
    val s_axi_rdata           = Bits(OUTPUT,64)
    val s_axi_rresp           = Bits(OUTPUT,2)
    val s_axi_rlast           = Bool(OUTPUT)
    val s_axi_rvalid          = Bool(OUTPUT)
    //misc
    val device_temp           = Bits(OUTPUT,12)
  }
}
//scalastyle:on
