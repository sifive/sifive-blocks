// See LICENSE for license details.
package sifive.blocks.devices.gpio

import Chisel._
import config.Parameters
import regmapper._
import uncore.tilelink2._
import util.AsyncResetRegVec

case class GPIOParams(address: BigInt, width: Int)

// YAGNI: Make the PUE, DS, and
// these also optionally HW controllable.
// This is the base class of things you "always"
// want to control from a HW block.
class GPIOCtrl extends Bundle {
  val oval = Bool()
  val oe   = Bool()
  val ie   = Bool()
}

// This is the actual IOF interface.
// Add a valid bit to indicate whether
// there is something actually connected
// to this.
class GPIOPinIOFCtrl extends GPIOCtrl {
  val valid = Bool()
}

// By default,
object GPIOPinIOFCtrl {
  def apply(): GPIOPinIOFCtrl = {
    val iof = Wire(new GPIOPinIOFCtrl())
    iof.valid := Bool(false)
    iof.oval  := Bool(false)
    iof.oe    := Bool(false)
    iof.ie    := Bool(false)
    iof
  }
}

// This is the control for a physical
// Pad.

class GPIOPinCtrl extends GPIOCtrl {
  val pue  = Bool() // Pull-up Enable
  val ds   = Bool() // Drive Strength
}

object GPIOPinCtrl {
  def apply(): GPIOPinCtrl = {
    val pin = Wire(new GPIOPinCtrl())
    pin.oval := Bool(false)
    pin.oe   := Bool(false)
    pin.pue  := Bool(false)
    pin.ds   := Bool(false)
    pin.ie   := Bool(false)
    pin
  }
}

// Package up the inputs and outputs
// for the IOF
class GPIOPinIOF extends Bundle {
  val i = new Bundle {
    val ival = Bool(INPUT)
  }
  val o = new GPIOPinIOFCtrl().asOutput
}

// Connect both the i and o side of the pin,
// and drive the valid signal for the IOF.
object GPIOPinToIOF {

  def apply (pin: GPIOPin, iof: GPIOPinIOF): Unit = {
    iof <> pin
    iof.o.valid := Bool(true)
  }

}

// Package up the inputs and outputs
// for the Pin
class GPIOPin extends Bundle {
  val i = new Bundle {
    val ival = Bool(INPUT)
  }
  val o = new GPIOPinCtrl().asOutput
}

// This is sort of weird because
// the IOF end up at the RocketChipTop
// level, and we have to do the pinmux
// outside of RocketChipTop.

class GPIOPortIO(c: GPIOParams) extends Bundle {
  val pins = Vec(c.width, new GPIOPin)
  val iof_0 = Vec(c.width, new GPIOPinIOF).flip
  val iof_1 = Vec(c.width, new GPIOPinIOF).flip
}

// It would be better if the IOF were here and
// we could do the pinmux inside.
trait HasGPIOBundleContents extends Bundle {
  val params: GPIOParams
  val port = new GPIOPortIO(params)
}

trait HasGPIOModuleContents extends Module with HasRegMap {
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
  val inSyncReg  = ShiftRegister(inVal, 3)
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
    GPIOCtrlRegs.iof_en    -> Seq(RegField.rwReg(c.width, iofEnReg.io)),
    GPIOCtrlRegs.iof_sel   -> Seq(RegField(c.width, iofSelReg)),
    GPIOCtrlRegs.drive     -> Seq(RegField(c.width, dsReg)),
    GPIOCtrlRegs.input_en  -> Seq(RegField.rwReg(c.width, ieReg.io)),
    GPIOCtrlRegs.out_xor   -> Seq(RegField(c.width, xorReg))

  )

  //--------------------------------------------------
  // Actual Pinmux
  // -------------------------------------------------

  val swPinCtrl = Wire(Vec(c.width, new GPIOPinCtrl()))

  // This strips off the valid.
  val iof0Ctrl = Wire(Vec(c.width, new GPIOCtrl()))
  val iof1Ctrl = Wire(Vec(c.width, new GPIOCtrl()))

  val iofCtrl = Wire(Vec(c.width, new GPIOCtrl()))
  val iofPlusSwPinCtrl = Wire(Vec(c.width, new GPIOPinCtrl()))


  for (pin <- 0 until c.width) {

    // Software Pin Control
    swPinCtrl(pin).pue    := pueReg.io.q(pin)
    swPinCtrl(pin).oval   := portReg(pin)
    swPinCtrl(pin).oe     := oeReg.io.q(pin)
    swPinCtrl(pin).ds     := dsReg(pin)
    swPinCtrl(pin).ie     := ieReg.io.q(pin)

    // Allow SW Override for invalid inputs.
    iof0Ctrl(pin)      <> swPinCtrl(pin)
    when (io.port.iof_0(pin).o.valid) {
      iof0Ctrl(pin)    <> io.port.iof_0(pin).o
    }

    iof1Ctrl(pin)      <> swPinCtrl(pin)
    when (io.port.iof_1(pin).o.valid) {
      iof1Ctrl(pin)    <> io.port.iof_1(pin).o
    }

    // Select IOF 0 vs. IOF 1.
    iofCtrl(pin)       <> Mux(iofSelReg(pin), iof1Ctrl(pin), iof0Ctrl(pin))

    // Allow SW Override for things IOF doesn't control.
    iofPlusSwPinCtrl(pin) <> swPinCtrl(pin)
    iofPlusSwPinCtrl(pin) <> iofCtrl(pin)

    // Final XOR & Pin Control
    val pre_xor: GPIOPinCtrl = Mux(iofEnReg.io.q(pin), iofPlusSwPinCtrl(pin), swPinCtrl(pin))
    io.port.pins(pin).o      := pre_xor
    io.port.pins(pin).o.oval := pre_xor.oval ^ xorReg(pin)

    // Generate Interrupts
    interrupts(pin) := (riseIpReg(pin) & riseIeReg(pin)) |
                         (fallIpReg(pin) & fallIeReg(pin)) |
                         (highIpReg(pin) & highIeReg(pin)) |
                         (lowIpReg(pin) & lowIeReg(pin))

    // Send Value to all consumers
    io.port.iof_0(pin).i.ival := inSyncReg(pin)
    io.port.iof_1(pin).i.ival := inSyncReg(pin)
  }
}

object GPIOOutputPinCtrl {

  def apply( pin: GPIOPin, signal: Bool,
    pue: Bool = Bool(false),
    ds:  Bool = Bool(false),
    ie:  Bool = Bool(false)
  ): Unit = {
    pin.o.oval := signal
    pin.o.oe   := Bool(true)
    pin.o.pue  := pue
    pin.o.ds   := ds
    pin.o.ie   := ie
  }

  def apply(pins: Vec[GPIOPin], signals: Bits,
    pue: Bool, ds:  Bool, ie:  Bool
  ): Unit = {
    for ((signal, pin) <- (signals.toBools zip pins)) {
      apply(pin, signal, pue, ds, ie)
    }
  }

  def apply(pins: Vec[GPIOPin], signals: Bits): Unit = apply(pins, signals,
    Bool(false), Bool(false), Bool(false))

}

object GPIOInputPinCtrl {

  def apply (pin: GPIOPin, pue: Bool = Bool(false)): Bool = {
    pin.o.oval := Bool(false)
    pin.o.oe   := Bool(false)
    pin.o.pue  := pue
    pin.o.ds   := Bool(false)
    pin.o.ie   := Bool(true)

    pin.i.ival
  }

  def apply (pins: Vec[GPIOPin], pue: Bool): Vec[Bool] = {
    val signals = Wire(Vec.fill(pins.size)(Bool(false)))
    for ((signal, pin) <- (signals zip pins)) {
      signal := GPIOInputPinCtrl(pin, pue)
    }
    signals
  }

  def apply (pins: Vec[GPIOPin]): Vec[Bool] = apply(pins, Bool(false))

}

// Magic TL2 Incantation to create a TL2 Slave
class TLGPIO(w: Int, c: GPIOParams)(implicit p: Parameters)
  extends TLRegisterRouter(c.address, "gpio", Seq("sifive,gpio0"), interrupts = c.width, beatBytes = w)(
  new TLRegBundle(c, _)    with HasGPIOBundleContents)(
  new TLRegModule(c, _, _) with HasGPIOModuleContents)
