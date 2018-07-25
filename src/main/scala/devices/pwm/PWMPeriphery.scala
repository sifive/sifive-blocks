// See LICENSE for license details.
package sifive.blocks.devices.pwm

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.BaseSubsystem

case object PeripheryPWMKey extends Field[Seq[PWMParams]]

trait HasPeripheryPWM { this: BaseSubsystem =>
  val pwms = p(PeripheryPWMKey).map { ps =>
    PWM.attach(AttachedPWMParams(ps), pbus, ibus.fromSync, None)
  }
  val pwmNodes = pwms.map(_.ioNode.makeSink())
}

trait HasPeripheryPWMBundle {
  val pwm: Seq[PWMPortIO]
}

trait HasPeripheryPWMModuleImp extends LazyModuleImp with HasPeripheryPWMBundle {
  val outer: HasPeripheryPWM
  val pwm = outer.pwmNodes.map(_.makeIO())
}
