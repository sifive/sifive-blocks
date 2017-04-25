/////////////////////////////////////////////////////////////////////
////                                                             ////
////  WISHBONE revB.2 compliant I2C Master controller Top-level  ////
////                                                             ////
////                                                             ////
////  Author: Richard Herveille                                  ////
////          richard@asics.ws                                   ////
////          www.asics.ws                                       ////
////                                                             ////
////  Downloaded from: http://www.opencores.org/projects/i2c/    ////
////                                                             ////
/////////////////////////////////////////////////////////////////////
////                                                             ////
//// Copyright (C) 2001 Richard Herveille                        ////
////                    richard@asics.ws                         ////
////                                                             ////
//// This source file may be used and distributed without        ////
//// restriction provided that this copyright statement is not   ////
//// removed from the file and that any derivative work contains ////
//// the original copyright notice and the associated disclaimer.////
////                                                             ////
////     THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY     ////
//// EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED   ////
//// TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS   ////
//// FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL THE AUTHOR      ////
//// OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,         ////
//// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES    ////
//// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE   ////
//// GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR        ////
//// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF  ////
//// LIABILITY, WHETHER IN  CONTRACT, STRICT LIABILITY, OR TORT  ////
//// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT  ////
//// OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE         ////
//// POSSIBILITY OF SUCH DAMAGE.                                 ////
////                                                             ////
/////////////////////////////////////////////////////////////////////

// This code was re-written in Chisel by SiFive, Inc.
// See LICENSE for license details.
// WISHBONE interface replaced by Tilelink2

package sifive.blocks.devices.i2c

import Chisel._
import config._
import regmapper._
import uncore.tilelink2._
import util.{AsyncResetRegVec, Majority}
import sifive.blocks.devices.gpio.{GPIOPinCtrl}

case class I2CParams(address: BigInt)

class I2CPin extends Bundle {
  val in  = Bool(INPUT)
  val out = Bool(OUTPUT)
  val oe  = Bool(OUTPUT)
}

class I2CPort extends Bundle {
  val scl = new I2CPin
  val sda = new I2CPin
}

trait HasI2CBundleContents extends Bundle {
  val port = new I2CPort
}

trait HasI2CModuleContents extends Module with HasRegMap {
  val io: HasI2CBundleContents
  val params: I2CParams

  val I2C_CMD_NOP   = UInt(0x00)
  val I2C_CMD_START = UInt(0x01)
  val I2C_CMD_STOP  = UInt(0x02)
  val I2C_CMD_WRITE = UInt(0x04)
  val I2C_CMD_READ  = UInt(0x08)

  class PrescalerBundle extends Bundle{
    val hi = UInt(8.W)
    val lo = UInt(8.W)
  }

  class ControlBundle extends Bundle{
    val coreEn             = Bool()
    val intEn              = Bool()
    val reserved           = UInt(6.W)
  }

  class CommandBundle extends Bundle{
    val start              = Bool()
    val stop               = Bool()
    val read               = Bool()
    val write              = Bool()
    val ack                = Bool()
    val reserved           = UInt(2.W)
    val irqAck             = Bool()
  }

  class StatusBundle extends Bundle{
    val receivedAck        = Bool()    // received aknowledge from slave
    val busy               = Bool()
    val arbLost            = Bool()
    val reserved           = UInt(3.W)
    val transferInProgress = Bool()
    val irqFlag            = Bool()
  }

  // control state visible to SW/driver
  val prescaler    = Reg(init = (new PrescalerBundle).fromBits(0xFFFF.U))
  val control      = Reg(init = (new ControlBundle).fromBits(0.U))
  val transmitData = Reg(init = UInt(0, 8.W))
  val receivedData = Reg(init = UInt(0, 8.W))
  val cmd          = Reg(init = (new CommandBundle).fromBits(0.U))
  val status       = Reg(init = (new StatusBundle).fromBits(0.U))


  //////// Bit level ////////

  io.port.scl.out := false.B                           // i2c clock line output
  io.port.sda.out := false.B                           // i2c data line output

  // filter SCL and SDA signals; (attempt to) remove glitches
  val filterCnt = Reg(init = UInt(0, 14.W))
  when ( !control.coreEn ) {
    filterCnt := 0.U
  } .elsewhen (!(filterCnt.orR)) {
    filterCnt := Cat(prescaler.hi, prescaler.lo) >> 2  //16x I2C bus frequency
  } .otherwise {
    filterCnt := filterCnt - 1.U
  }

  val fSCL      = Reg(init = UInt(0x7, 3.W))
  val fSDA      = Reg(init = UInt(0x7, 3.W))
  when (!(filterCnt.orR)) {
    fSCL := Cat(fSCL, io.port.scl.in)
    fSDA := Cat(fSDA, io.port.sda.in)
  }

  val sSCL      = Reg(init = true.B, next = Majority(fSCL))
  val sSDA      = Reg(init = true.B, next = Majority(fSDA))

  val dSCL      = Reg(init = true.B, next = sSCL)
  val dSDA      = Reg(init = true.B, next = sSDA)

  val dSCLOen   = Reg(next = io.port.scl.oe) // delayed scl_oen

  // detect start condition => detect falling edge on SDA while SCL is high
  // detect stop  condition => detect rising  edge on SDA while SCL is high
  val startCond = Reg(init = false.B, next = !sSDA &&  dSDA && sSCL)
  val stopCond  = Reg(init = false.B, next =  sSDA && !dSDA && sSCL)

  // master drives SCL high, but another master pulls it low
  // master start counting down its low cycle now (clock synchronization)
  val sclSync   = dSCL && !sSCL && io.port.scl.oe

  // slave_wait is asserted when master wants to drive SCL high, but the slave pulls it low
  // slave_wait remains asserted until the slave releases SCL
  val slaveWait = Reg(init = false.B)
  slaveWait := (io.port.scl.oe && !dSCLOen && !sSCL) || (slaveWait && !sSCL)

  val clkEn     = Reg(init = true.B)     // clock generation signals
  val cnt       = Reg(init = UInt(0, 16.W))  // clock divider counter (synthesis)

  // generate clk enable signal
  when (!(cnt.orR) || !control.coreEn || sclSync ) {
    cnt   := Cat(prescaler.hi, prescaler.lo)
    clkEn := true.B
  }
  .elsewhen (slaveWait) {
    clkEn := false.B
  }
  .otherwise {
    cnt   := cnt - 1.U
    clkEn := false.B
  }

  val sclOen     = Reg(init = true.B)
  io.port.scl.oe := !sclOen

  val sdaOen     = Reg(init = true.B)
  io.port.sda.oe := !sdaOen

  val sdaChk     = Reg(init = false.B)       // check SDA output (Multi-master arbitration)

  val transmitBit = Reg(init = false.B)
  val receivedBit = Reg(Bool())
  when (sSCL && !dSCL) {
    receivedBit := sSDA
  }

  val bitCmd      = Reg(init = UInt(0, 4.W)) // command (from byte controller)
  val bitCmdStop  = Reg(init = false.B)
  when (clkEn) {
    bitCmdStop := bitCmd === I2C_CMD_STOP
  }
  val bitCmdAck   = Reg(init = false.B)

  val (s_bit_idle ::
       s_bit_start_a :: s_bit_start_b :: s_bit_start_c :: s_bit_start_d :: s_bit_start_e ::
       s_bit_stop_a  :: s_bit_stop_b  :: s_bit_stop_c  :: s_bit_stop_d  ::
       s_bit_rd_a    :: s_bit_rd_b    :: s_bit_rd_c    :: s_bit_rd_d    ::
       s_bit_wr_a    :: s_bit_wr_b    :: s_bit_wr_c    :: s_bit_wr_d    :: Nil) = Enum(UInt(), 18)
  val bitState    = Reg(init = s_bit_idle)

  val arbLost     = Reg(init = false.B, next = (sdaChk && !sSDA && sdaOen) | ((bitState === s_bit_idle) && stopCond && !bitCmdStop))

  // bit FSM
  when (arbLost) {
    bitState  := s_bit_idle
    bitCmdAck := false.B
    sclOen    := true.B
    sdaOen    := true.B
    sdaChk    := false.B
  }
  .otherwise {
    bitCmdAck := false.B

    when (clkEn) {
      switch (bitState) {
        is (s_bit_idle) {
          switch (bitCmd) {
            is (I2C_CMD_START) { bitState := s_bit_start_a }
            is (I2C_CMD_STOP)  { bitState := s_bit_stop_a  }
            is (I2C_CMD_WRITE) { bitState := s_bit_wr_a    }
            is (I2C_CMD_READ)  { bitState := s_bit_rd_a    }
          }
          sdaChk := false.B
        }

        is (s_bit_start_a) {
          bitState  := s_bit_start_b
          sclOen    := sclOen
          sdaOen    := true.B
          sdaChk    := false.B
        }
        is (s_bit_start_b) {
          bitState  := s_bit_start_c
          sclOen    := true.B
          sdaOen    := true.B
          sdaChk    := false.B
        }
        is (s_bit_start_c) {
          bitState  := s_bit_start_d
          sclOen    := true.B
          sdaOen    := false.B
          sdaChk    := false.B
        }
        is (s_bit_start_d) {
          bitState  := s_bit_start_e
          sclOen    := true.B
          sdaOen    := false.B
          sdaChk    := false.B
        }
        is (s_bit_start_e) {
          bitState  := s_bit_idle
          bitCmdAck := true.B
          sclOen    := false.B
          sdaOen    := false.B
          sdaChk    := false.B
        }

        is (s_bit_stop_a) {
          bitState  := s_bit_stop_b
          sclOen    := false.B
          sdaOen    := false.B
          sdaChk    := false.B
        }
        is (s_bit_stop_b) {
          bitState  := s_bit_stop_c
          sclOen    := true.B
          sdaOen    := false.B
          sdaChk    := false.B
        }
        is (s_bit_stop_c) {
          bitState  := s_bit_stop_d
          sclOen    := true.B
          sdaOen    := false.B
          sdaChk    := false.B
        }
        is (s_bit_stop_d) {
          bitState  := s_bit_idle
          bitCmdAck := true.B
          sclOen    := true.B
          sdaOen    := true.B
          sdaChk    := false.B
        }

        is (s_bit_rd_a) {
          bitState  := s_bit_rd_b
          sclOen    := false.B
          sdaOen    := true.B
          sdaChk    := false.B
        }
        is (s_bit_rd_b) {
          bitState  := s_bit_rd_c
          sclOen    := true.B
          sdaOen    := true.B
          sdaChk    := false.B
        }
        is (s_bit_rd_c) {
          bitState  := s_bit_rd_d
          sclOen    := true.B
          sdaOen    := true.B
          sdaChk    := false.B
        }
        is (s_bit_rd_d) {
          bitState  := s_bit_idle
          bitCmdAck := true.B
          sclOen    := false.B
          sdaOen    := true.B
          sdaChk    := false.B
        }

        is (s_bit_wr_a) {
          bitState  := s_bit_wr_b
          sclOen    := false.B
          sdaOen    := transmitBit
          sdaChk    := false.B
        }
        is (s_bit_wr_b) {
          bitState  := s_bit_wr_c
          sclOen    := true.B
          sdaOen    := transmitBit
          sdaChk    := false.B
        }
        is (s_bit_wr_c) {
          bitState  := s_bit_wr_d
          sclOen    := true.B
          sdaOen    := transmitBit
          sdaChk    := true.B
        }
        is (s_bit_wr_d) {
          bitState  := s_bit_idle
          bitCmdAck := true.B
          sclOen    := false.B
          sdaOen    := transmitBit
          sdaChk    := false.B
        }
      }
    }
  }


  //////// Byte level ///////
  val load        = Reg(init = false.B)                         // load shift register
  val shift       = Reg(init = false.B)                         // shift shift register
  val cmdAck      = Reg(init = false.B)                         // also done
  val receivedAck = Reg(init = false.B)                         // from I2C slave
  val go          = (cmd.read | cmd.write | cmd.stop) & !cmdAck

  val bitCnt      = Reg(init = UInt(0, 3.W))
  when (load) {
    bitCnt := 0x7.U
  }
  .elsewhen (shift) {
    bitCnt := bitCnt - 1.U
  }
  val bitCntDone  = !(bitCnt.orR)

  // receivedData is used as shift register directly
  when (load) {
    receivedData := transmitData
  }
  .elsewhen (shift) {
    receivedData := Cat(receivedData, receivedBit)
  }

  val (s_byte_idle :: s_byte_start :: s_byte_read :: s_byte_write :: s_byte_ack :: s_byte_stop :: Nil) = Enum(UInt(), 6)
  val byteState   = Reg(init = s_byte_idle)

  when (arbLost) {
    bitCmd      := I2C_CMD_NOP
    transmitBit := false.B
    shift       := false.B
    load        := false.B
    cmdAck      := false.B
    byteState   := s_byte_idle
    receivedAck := false.B
  }
  .otherwise {
    transmitBit := receivedData(7)
    shift       := false.B
    load        := false.B
    cmdAck      := false.B

    switch (byteState) {
      is (s_byte_idle) {
        when (go) {
          when (cmd.start) {
            byteState := s_byte_start
            bitCmd    := I2C_CMD_START
          }
          .elsewhen (cmd.read) {
            byteState := s_byte_read
            bitCmd    := I2C_CMD_READ
          }
          .elsewhen (cmd.write) {
            byteState := s_byte_write
            bitCmd    := I2C_CMD_WRITE
          }
          .otherwise { // stop
            byteState := s_byte_stop
            bitCmd    := I2C_CMD_STOP
          }

          load        := true.B
        }
      }
      is (s_byte_start) {
        when (bitCmdAck) {
          when (cmd.read) {
            byteState := s_byte_read
            bitCmd    := I2C_CMD_READ
          }
          .otherwise {
            byteState := s_byte_write
            bitCmd    := I2C_CMD_WRITE
          }

          load        := true.B
        }
      }
      is (s_byte_write) {
        when (bitCmdAck) {
          when (bitCntDone) {
            byteState := s_byte_ack
            bitCmd    := I2C_CMD_READ
          }
          .otherwise {
            byteState := s_byte_write
            bitCmd    := I2C_CMD_WRITE
            shift     := true.B
          }
        }
      }
      is (s_byte_read) {
        when (bitCmdAck) {
          when (bitCntDone) {
            byteState := s_byte_ack
            bitCmd    := I2C_CMD_WRITE
          }
          .otherwise {
            byteState := s_byte_read
            bitCmd    := I2C_CMD_READ
          }

          shift       := true.B
          transmitBit := cmd.ack
        }
      }
      is (s_byte_ack) {
        when (bitCmdAck) {
          when (cmd.stop) {
            byteState := s_byte_stop
            bitCmd    := I2C_CMD_STOP
          }
          .otherwise {
            byteState := s_byte_idle
            bitCmd    := I2C_CMD_NOP

	    // generate command acknowledge signal
            cmdAck    := true.B
          }

	  // assign ack_out output to bit_controller_rxd (contains last received bit)
          receivedAck := receivedBit

          transmitBit := true.B
        }
        .otherwise {
          transmitBit := cmd.ack
        }
      }
      is (s_byte_stop) {
        when (bitCmdAck) {
          byteState := s_byte_idle
          bitCmd    := I2C_CMD_NOP

	  // assign ack_out output to bit_controller_rxd (contains last received bit)
          cmdAck    := true.B
        }
      }
    }
  }


  //////// Top level ////////

  // hack: b/c the same register offset is used to write cmd and read status
  val nextCmd = Wire(UInt(8.W))
  nextCmd := cmd.asUInt
  cmd := (new CommandBundle).fromBits(nextCmd)

  when (cmdAck || arbLost) {
    cmd.start := false.B    // clear command bits when done
    cmd.stop  := false.B    // or when aribitration lost
    cmd.read  := false.B
    cmd.write := false.B
  }
  cmd.irqAck  := false.B    // clear IRQ_ACK bit (essentially 1 cycle pulse b/c it is overwritten by regmap below)

  status.receivedAck := receivedAck
  when (stopCond) {
    status.busy             := false.B
  }
  .elsewhen (startCond) {
    status.busy             := true.B
  }

  when (arbLost) {
    status.arbLost          := true.B
  }
  .elsewhen (cmd.start) {
    status.arbLost          := false.B
  }
  status.transferInProgress := cmd.read || cmd.write
  status.irqFlag            := (cmdAck || arbLost || status.irqFlag) && !cmd.irqAck


  val statusReadReady = Reg(init = true.B)
  when (!statusReadReady) {
    statusReadReady := true.B
  }

  // statusReadReady,
  regmap(
    I2CCtrlRegs.prescaler_lo -> Seq(RegField(8, prescaler.lo)),
    I2CCtrlRegs.prescaler_hi -> Seq(RegField(8, prescaler.hi)),
    I2CCtrlRegs.control      -> control.elements.map{ case(name, e) => RegField(e.getWidth, e.asInstanceOf[UInt]) }.toSeq,
    I2CCtrlRegs.data         -> Seq(RegField(8, r = RegReadFn(receivedData),  w = RegWriteFn(transmitData))),
    I2CCtrlRegs.cmd_status   -> Seq(RegField(8, r = RegReadFn{ ready =>
                                                               (statusReadReady, status.asUInt)
                                                             },
                                                w = RegWriteFn((valid, data) => {
                                                               when (valid) {
                                                                 statusReadReady := false.B
                                                                 nextCmd := data
                                                             }
                                                             true.B
                                                }
                                                )))
  )

  // tie off unused bits
  control.reserved := 0.U
  cmd.reserved     := 0.U
  status.reserved  := 0.U

  interrupts(0) := status.irqFlag & control.intEn
}

// Magic TL2 Incantation to create a TL2 Slave
class TLI2C(w: Int, c: I2CParams)(implicit p: Parameters)
  extends TLRegisterRouter(c.address, "i2c", Seq("sifive,i2c0"), interrupts = 1, beatBytes = w)(
  new TLRegBundle(c, _)    with HasI2CBundleContents)(
  new TLRegModule(c, _, _) with HasI2CModuleContents)
