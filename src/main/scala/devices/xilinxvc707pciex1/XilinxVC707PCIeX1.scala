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
  val slave = TLInputNode()
  val control = TLInputNode()
  val master = TLOutputNode()
  val intnode = IntSourceNode(1)

  val axi_to_pcie_x1 = LazyModule(new VC707AXIToPCIeX1)
  axi_to_pcie_x1.slave   := TLToAXI4(idBits=4)(slave)
  axi_to_pcie_x1.control := AXI4Fragmenter(lite=true, maxInFlight=4)(TLToAXI4(idBits=0)(control))
  master := TLWidthWidget(8)(AXI4ToTL()(AXI4Fragmenter()(axi_to_pcie_x1.master)))

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val port = new XilinxVC707PCIeX1IO
      val slave_in = slave.bundleIn
      val control_in = control.bundleIn
      val master_out = master.bundleOut
      val interrupt = intnode.bundleOut
    }

    io.port <> axi_to_pcie_x1.module.io.port
    io.interrupt(0)(0) := axi_to_pcie_x1.module.io.interrupt_out

    //PCIe Reference Clock
    val ibufds_gte2 = Module(new IBUFDS_GTE2)
    axi_to_pcie_x1.module.io.REFCLK := ibufds_gte2.io.O
    ibufds_gte2.io.CEB := UInt(0)
    ibufds_gte2.io.I := io.port.REFCLK_rxp
    ibufds_gte2.io.IB := io.port.REFCLK_rxn
  }
}
