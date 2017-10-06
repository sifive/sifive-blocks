//See LICENSE for license details

package sifive.blocks.devices.pinctrl

import Chisel._

// This is the base class of things you "always"
// want to control from a HW block.
class PinCtrl extends Bundle {
  val oval = Bool()
  val oe   = Bool()
  val ie   = Bool()
}

// Package up the inputs and outputs
// for the Pin
abstract class Pin extends Bundle {
  val i = new Bundle {
    val ival = Bool(INPUT)
  }
  val o: PinCtrl

  // Must be defined by the subclasses
  def default(): Unit
  def inputPin(pue: Bool = Bool(false)): Bool
  def outputPin(signal: Bool,
    pue: Bool = Bool(false),
    ds: Bool = Bool(false),
    ie: Bool = Bool(false)
  ): Unit
  
}


////////////////////////////////////////////////////////////////////////////////////

class BasePin extends Pin() {
  val o = new PinCtrl().asOutput

  def default(): Unit = {
    this.o.oval := Bool(false)
    this.o.oe   := Bool(false)
    this.o.ie   := Bool(false)
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

/////////////////////////////////////////////////////////////////////////
class EnhancedPinCtrl extends PinCtrl {
  val pue = Bool()
  val ds = Bool()
}

class EnhancedPin extends Pin() {

  val o = new EnhancedPinCtrl().asOutput

  def default(): Unit = {
    this.o.oval := Bool(false)
    this.o.oe   := Bool(false)
    this.o.ie   := Bool(false)
    this.o.ds   := Bool(false)
    this.o.pue  := Bool(false)
  }

  def inputPin(pue: Bool = Bool(false)): Bool = {
    this.o.oval := Bool(false)
    this.o.oe   := Bool(false)
    this.o.pue  := pue
    this.o.ds   := Bool(false)
    this.o.ie   := Bool(true)

    this.i.ival
  }

  def outputPin(signal: Bool,
    pue: Bool = Bool(false),
    ds: Bool = Bool(false),
    ie: Bool = Bool(false)
  ): Unit = {
    this.o.oval := signal
    this.o.oe   := Bool(true)
    this.o.pue  := pue
    this.o.ds   := ds
    this.o.ie   := ie
  }

  def toBasePin(): BasePin = {

    val base_pin = Wire(new BasePin())
    base_pin <> this
    base_pin
  }
}
