// See LICENSE for license details.
package sifive.blocks.devices.pwm

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
import freechips.rocketchip.util.HeterogeneousBag
import sifive.blocks.devices.pinctrl.{Pin}

class PWMPortIO(val c: PWMParams) extends Bundle {
  val port = Vec(c.ncmp, Bool()).asOutput
}


case object PeripheryPWMKey extends Field[Seq[PWMParams]]

trait HasPeripheryPWM { this: BaseSubsystem =>
  val pwmParams = p(PeripheryPWMKey)
  val pwms = pwmParams.zipWithIndex.map { case(params, i) =>
    val name = Some(s"pwm_$i")
    val pwm = LazyModule(new TLPWM(pbus.beatBytes, params)).suggestName(name)
    pbus.toVariableWidthSlave(name) { pwm.node }
    ibus.fromSync := pwm.intnode
    pwm
  }
}

trait HasPeripheryPWMBundle {
  val pwm: HeterogeneousBag[PWMPortIO]

}

trait HasPeripheryPWMModuleImp extends LazyModuleImp with HasPeripheryPWMBundle {
  val outer: HasPeripheryPWM
  val pwm = IO(HeterogeneousBag(outer.pwmParams.map(new PWMPortIO(_))))

  (pwm zip outer.pwms) foreach { case (io, device) =>
    io.port := device.module.io.gpio
  }
}
