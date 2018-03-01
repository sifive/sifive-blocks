// See LICENSE for license details.
package sifive.blocks.devices.gpio

import Chisel._
import chisel3.experimental.MultiIOModule
import sifive.blocks.devices.pinctrl.{PinCtrl, Pin, BasePin, EnhancedPin, EnhancedPinCtrl}
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.util.SynchronizerShiftReg
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.AsyncResetRegVec

case class GPIOParams(address: BigInt, width: Int, includeIOF: Boolean = false)

// This is the actual IOF interface.pa
// Add a valid bit to indicate whether
// there is something actually connected
// to this.
class IOFCtrl extends PinCtrl {
  val valid = Bool()
}

// By default,
object IOFCtrl {
  def apply(): IOFCtrl = {
    val iof = Wire(new IOFCtrl())
    iof.valid := Bool(false)
    iof.oval  := Bool(false)
    iof.oe    := Bool(false)
    iof.ie    := Bool(false)
    iof
  }
}

// Package up the inputs and outputs
// for the IOF
class IOFPin extends Pin {
  val o  = new IOFCtrl().asOutput

  def default(): Unit = {
    this.o.oval  := Bool(false)
    this.o.oe    := Bool(false)
    this.o.ie    := Bool(false)
    this.o.valid := Bool(false)
  }

  def inputPin(pue: Bool = Bool(false) /*ignored*/): Bool = {
    this.o.oval := Bool(false)
    this.o.oe   := Bool(false)
    this.o.ie   := Bool(true)
    this.i.ival
  }
  def outputPin(signal: Bool,
    pue: Bool = Bool(false), /*ignored*/
    ds: Bool = Bool(false), /*ignored*/
    ie: Bool = Bool(false)
  ): Unit = {
    this.o.oval := signal
    this.o.oe   := Bool(true)
    this.o.ie   := ie
  }
}

// Connect both the i and o side of the pin,
// and drive the valid signal for the IOF.
object BasePinToIOF {
  def apply(pin: BasePin, iof: IOFPin): Unit = {
    iof <> pin
    iof.o.valid := Bool(true)
  }
}

// This is sort of weird because
// the IOF end up at the RocketChipTop
// level, and we have to do the pinmux
// outside of RocketChipTop.

class GPIOPortIO(private val c: GPIOParams) extends Bundle {
  val pins = Vec(c.width, new EnhancedPin())
  val iof_0 = if (c.includeIOF) Some(Vec(c.width, new IOFPin).flip) else None
  val iof_1 = if (c.includeIOF) Some(Vec(c.width, new IOFPin).flip) else None
}

// It would be better if the IOF were here and
// we could do the pinmux inside.
trait HasGPIOBundleContents extends Bundle {
  def params: GPIOParams
  val port = new GPIOPortIO(params)
}

trait HasGPIOModuleContents extends MultiIOModule with HasRegMap {
  val io: HasGPIOBundleContents
  val params: GPIOParams
  val c = params

  //--------------------------------------------------
  // CSR Declarations
  // -------------------------------------------------

  // SW Control only.
  val portReg = Reg(init = UInt(0, c.width))

  val oeReg  = Module(new AsyncResetRegVec(c.width, 0))
  val pueReg = Module(new AsyncResetRegVec(c.width, 0))
  val dsReg  = Reg(init = UInt(0, c.width))
  val ieReg  = Module(new AsyncResetRegVec(c.width, 0))

  // Synchronize Input to get valueReg
  val inVal = Wire(UInt(0, width=c.width))
  inVal := Vec(io.port.pins.map(_.i.ival)).asUInt
  val inSyncReg  = SynchronizerShiftReg(inVal, 3, Some("inSyncReg"))
  val valueReg   = Reg(init = UInt(0, c.width), next = inSyncReg)

  // Interrupt Configuration
  val highIeReg = Reg(init = UInt(0, c.width))
  val lowIeReg  = Reg(init = UInt(0, c.width))
  val riseIeReg = Reg(init = UInt(0, c.width))
  val fallIeReg = Reg(init = UInt(0, c.width))
  val highIpReg = Reg(init = UInt(0, c.width))
  val lowIpReg  = Reg(init = UInt(0, c.width))
  val riseIpReg = Reg(init = UInt(0, c.width))
  val fallIpReg = Reg(init = UInt(0, c.width))

  // HW IO Function
  val iofEnReg  = Module(new AsyncResetRegVec(c.width, 0))
  val iofSelReg = Reg(init = UInt(0, c.width))
  
  // Invert Output
  val xorReg    = Reg(init = UInt(0, c.width))

  //--------------------------------------------------
  // CSR Access Logic (most of this section is boilerplate)
  // -------------------------------------------------

  val rise = ~valueReg & inSyncReg;
  val fall = valueReg & ~inSyncReg;

  val iofEnFields =  if (c.includeIOF) (Seq(RegField.rwReg(c.width, iofEnReg.io))) else (Seq(RegField(c.width)))
  val iofSelFields = if (c.includeIOF) (Seq(RegField(c.width, iofSelReg))) else (Seq(RegField(c.width)))


  // Note that these are out of order.
  regmap(
    GPIOCtrlRegs.value     -> Seq(RegField.r(c.width, valueReg)),
    GPIOCtrlRegs.output_en -> Seq(RegField.rwReg(c.width, oeReg.io)),
    GPIOCtrlRegs.rise_ie   -> Seq(RegField(c.width, riseIeReg)),
    GPIOCtrlRegs.rise_ip   -> Seq(RegField.w1ToClear(c.width, riseIpReg, rise)),
    GPIOCtrlRegs.fall_ie   -> Seq(RegField(c.width, fallIeReg)),
    GPIOCtrlRegs.fall_ip   -> Seq(RegField.w1ToClear(c.width, fallIpReg, fall)),
    GPIOCtrlRegs.high_ie   -> Seq(RegField(c.width, highIeReg)),
    GPIOCtrlRegs.high_ip   -> Seq(RegField.w1ToClear(c.width, highIpReg, valueReg)),
    GPIOCtrlRegs.low_ie    -> Seq(RegField(c.width, lowIeReg)),
    GPIOCtrlRegs.low_ip    -> Seq(RegField.w1ToClear(c.width,lowIpReg, ~valueReg)),
    GPIOCtrlRegs.port      -> Seq(RegField(c.width, portReg)),
    GPIOCtrlRegs.pullup_en -> Seq(RegField.rwReg(c.width, pueReg.io)),
    GPIOCtrlRegs.iof_en    -> iofEnFields,
    GPIOCtrlRegs.iof_sel   -> iofSelFields,
    GPIOCtrlRegs.drive     -> Seq(RegField(c.width, dsReg)),
    GPIOCtrlRegs.input_en  -> Seq(RegField.rwReg(c.width, ieReg.io)),
    GPIOCtrlRegs.out_xor   -> Seq(RegField(c.width, xorReg))

  )

  //--------------------------------------------------
  // Actual Pinmux
  // -------------------------------------------------

  val swPinCtrl = Wire(Vec(c.width, new EnhancedPinCtrl()))

  // This strips off the valid.
  val iof0Ctrl = Wire(Vec(c.width, new IOFCtrl()))
  val iof1Ctrl = Wire(Vec(c.width, new IOFCtrl()))

  val iofCtrl = Wire(Vec(c.width, new IOFCtrl()))
  val iofPlusSwPinCtrl = Wire(Vec(c.width, new EnhancedPinCtrl()))

  for (pin <- 0 until c.width) {

    // Software Pin Control
    swPinCtrl(pin).pue    := pueReg.io.q(pin)
    swPinCtrl(pin).oval   := portReg(pin)
    swPinCtrl(pin).oe     := oeReg.io.q(pin)
    swPinCtrl(pin).ds     := dsReg(pin)
    swPinCtrl(pin).ie     := ieReg.io.q(pin)

    val pre_xor = Wire(new EnhancedPinCtrl())

    if (c.includeIOF) {
      // Allow SW Override for invalid inputs.
      iof0Ctrl(pin)      <> swPinCtrl(pin)
      when (io.port.iof_0.get(pin).o.valid) {
        iof0Ctrl(pin)    <> io.port.iof_0.get(pin).o
      }

      iof1Ctrl(pin)      <> swPinCtrl(pin)
      when (io.port.iof_1.get(pin).o.valid) {
        iof1Ctrl(pin)    <> io.port.iof_1.get(pin).o
      }

      // Select IOF 0 vs. IOF 1.
      iofCtrl(pin)       <> Mux(iofSelReg(pin), iof1Ctrl(pin), iof0Ctrl(pin))

      // Allow SW Override for things IOF doesn't control.
      iofPlusSwPinCtrl(pin) <> swPinCtrl(pin)
      iofPlusSwPinCtrl(pin) <> iofCtrl(pin)
   
      // Final XOR & Pin Control
      pre_xor  := Mux(iofEnReg.io.q(pin), iofPlusSwPinCtrl(pin), swPinCtrl(pin))
    } else {
      pre_xor := swPinCtrl(pin)
    }

    io.port.pins(pin).o      := pre_xor
    io.port.pins(pin).o.oval := pre_xor.oval ^ xorReg(pin)

    // Generate Interrupts
    interrupts(pin) := (riseIpReg(pin) & riseIeReg(pin)) |
                         (fallIpReg(pin) & fallIeReg(pin)) |
                         (highIpReg(pin) & highIeReg(pin)) |
                         (lowIpReg(pin) & lowIeReg(pin))

    if (c.includeIOF) {
      // Send Value to all consumers
      io.port.iof_0.get(pin).i.ival := inSyncReg(pin)
      io.port.iof_1.get(pin).i.ival := inSyncReg(pin)
    }
  }
}

// Magic TL2 Incantation to create a TL2 Slave
class TLGPIO(w: Int, c: GPIOParams)(implicit p: Parameters)
  extends TLRegisterRouter(c.address, "gpio", Seq("sifive,gpio0"), interrupts = c.width, beatBytes = w)(
  new TLRegBundle(c, _)    with HasGPIOBundleContents)(
  new TLRegModule(c, _, _) with HasGPIOModuleContents)
