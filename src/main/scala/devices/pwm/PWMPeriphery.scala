// See LICENSE for license details.
package sifive.blocks.devices.pwm

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.coreplex.{HasPeripheryBus, HasInterruptBus}
import freechips.rocketchip.diplomacy.{LazyModule, LazyMultiIOModuleImp}
import freechips.rocketchip.util.HeterogeneousBag
import sifive.blocks.devices.pinctrl.{Pin}

class PWMPortIO(val c: PWMParams) extends Bundle {
  val port = Vec(c.ncmp, Bool()).asOutput
  override def cloneType: this.type = new PWMPortIO(c).asInstanceOf[this.type]
}

class PWMSignals[T <: Data] (pingen: ()=> T, val c: PWMParams) extends Bundle {

  val pwm: Vec[T] = Vec(c.ncmp, pingen())

  override def cloneType: this.type =
    this.getClass.getConstructors.head.newInstance(pingen, c).asInstanceOf[this.type]
}


class PWMPins[T <: Pin] (pingen: ()=> T, val c: PWMParams) extends PWMSignals[T](pingen, c) {

  override def cloneType: this.type =
    this.getClass.getConstructors.head.newInstance(pingen, c).asInstanceOf[this.type]

  def fromPort(port: PWMPortIO) {
    (pwm zip port.port)  foreach {case (pin, port) =>
      pin.outputPin(port)
    }
  }
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
  val pwm: HeterogeneousBag[PWMPortIO]

}

trait HasPeripheryPWMModuleImp extends LazyMultiIOModuleImp with HasPeripheryPWMBundle {
  val outer: HasPeripheryPWM
  val pwm = IO(HeterogeneousBag(outer.pwmParams.map(new PWMPortIO(_))))

  (pwm zip outer.pwms) foreach { case (io, device) =>
    io.port := device.module.io.gpio
  }
}
