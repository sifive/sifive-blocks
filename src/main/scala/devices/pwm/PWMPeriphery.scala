// See LICENSE for license details.
package sifive.blocks.devices.pwm

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.coreplex.{HasPeripheryBus, HasInterruptBus}
import freechips.rocketchip.diplomacy.{LazyModule, LazyMultiIOModuleImp}
import freechips.rocketchip.util.HeterogeneousBag
import sifive.blocks.devices.gpio._

class PWMPortIO(val c: PWMParams) extends Bundle {
  val port = Vec(c.ncmp, Bool()).asOutput
  override def cloneType: this.type = new PWMPortIO(c).asInstanceOf[this.type]
}

class PWMPinsIO(val c: PWMParams) extends Bundle {
  val pwm = Vec(c.ncmp, new GPIOPin)
}

class PWMGPIOPort(val c: PWMParams) extends Module {
  val io = new Bundle {
    val pwm = new PWMPortIO(c).flip()
    val pins = new PWMPinsIO(c)
  }

  GPIOOutputPinCtrl(io.pins.pwm, io.pwm.port.asUInt)
}

case object PeripheryPWMKey extends Field[Seq[PWMParams]]

trait HasPeripheryPWM extends HasPeripheryBus with HasInterruptBus {
  val pwmParams = p(PeripheryPWMKey)
  val pwms = pwmParams map { params =>
    val pwm = LazyModule(new TLPWM(pbus.beatBytes, params))
    pwm.node := pbus.toVariableWidthSlaves
    ibus.fromSync := pwm.intnode
    pwm
  }
}

trait HasPeripheryPWMBundle {
  val pwms: HeterogeneousBag[PWMPortIO]

  def PWMtoGPIOPins(dummy: Int = 1): Seq[PWMPinsIO] = pwms.map { p =>
    val pins = Module(new PWMGPIOPort(p.c))
    pins.io.pwm <> p
    pins.io.pins
  }
}

trait HasPeripheryPWMModuleImp extends LazyMultiIOModuleImp with HasPeripheryPWMBundle {
  val outer: HasPeripheryPWM
  val pwms = IO(HeterogeneousBag(outer.pwmParams.map(new PWMPortIO(_))))

  (pwms zip outer.pwms) foreach { case (io, device) =>
    io.port := device.module.io.gpio
  }
}
