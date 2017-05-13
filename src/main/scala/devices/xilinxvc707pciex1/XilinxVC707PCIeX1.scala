// See LICENSE for license details.
package sifive.blocks.devices.xilinxvc707pciex1

import Chisel._
import config._
import diplomacy._
import uncore.tilelink2._
import uncore.axi4._
import rocketchip._
import sifive.blocks.ip.xilinx.vc707axi_to_pcie_x1.{VC707AXIToPCIeX1, VC707AXIToPCIeX1IOClocksReset, VC707AXIToPCIeX1IOSerial}
import sifive.blocks.ip.xilinx.ibufds_gte2.IBUFDS_GTE2

class XilinxVC707PCIeX1Pads extends Bundle with VC707AXIToPCIeX1IOSerial

class XilinxVC707PCIeX1IO extends Bundle with VC707AXIToPCIeX1IOSerial
                                         with VC707AXIToPCIeX1IOClocksReset {
  val axi_ctl_aresetn = Bool(INPUT)
  val REFCLK_rxp = Bool(INPUT)
  val REFCLK_rxn = Bool(INPUT)
}

class XilinxVC707PCIeX1(implicit p: Parameters) extends LazyModule {
  val slave = TLAsyncInputNode()
  val control = TLAsyncInputNode()
  val master = TLAsyncOutputNode()
  val intnode = IntOutputNode()

  val axi_to_pcie_x1 = LazyModule(new VC707AXIToPCIeX1)

  axi_to_pcie_x1.slave :=
    AXI4Buffer()(
    AXI4UserYanker()(
    AXI4Deinterleaver(p(coreplex.CacheBlockBytes))(
    AXI4IdIndexer(idBits=4)(
    TLToAXI4(beatBytes=8)(
    TLAsyncCrossingSink()(
    slave))))))

  axi_to_pcie_x1.control :=
    AXI4Buffer()(
    AXI4UserYanker()(
    TLToAXI4(beatBytes=4)(
    TLFragmenter(4, p(coreplex.CacheBlockBytes))(
    TLAsyncCrossingSink()(
    control)))))

  master :=
    TLAsyncCrossingSource()(
    TLWidthWidget(8)(
    AXI4ToTL()(
    AXI4UserYanker(capMaxFlight=Some(8))(
    AXI4Fragmenter()(
    axi_to_pcie_x1.master)))))

  intnode := axi_to_pcie_x1.intnode

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val port = new XilinxVC707PCIeX1IO
      val slave_in = slave.bundleIn
      val control_in = control.bundleIn
      val master_out = master.bundleOut
      val interrupt = intnode.bundleOut
    }

    io.port <> axi_to_pcie_x1.module.io.port

    //PCIe Reference Clock
    val ibufds_gte2 = Module(new IBUFDS_GTE2)
    axi_to_pcie_x1.module.io.REFCLK := ibufds_gte2.io.O
    ibufds_gte2.io.CEB := UInt(0)
    ibufds_gte2.io.I := io.port.REFCLK_rxp
    ibufds_gte2.io.IB := io.port.REFCLK_rxn
  }
}
