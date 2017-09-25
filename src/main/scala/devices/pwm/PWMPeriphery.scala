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
