// See LICENSE for license details.
package sifive.blocks.devices.pwm

import Chisel._
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.BaseSubsystem

case object PeripheryPWMKey extends Field[Seq[PWMParams]](Nil)

trait HasPeripheryPWM { this: BaseSubsystem =>
  val pwmNodes = p(PeripheryPWMKey).map { ps =>
    PWMAttachParams(ps).attachTo(this).ioNode.makeSink 
  }
}

trait HasPeripheryPWMBundle {
  val pwm: Seq[PWMPortIO]
}

trait HasPeripheryPWMModuleImp extends LazyModuleImp with HasPeripheryPWMBundle {
  val outer: HasPeripheryPWM
  val pwm  = outer.pwmNodes.zipWithIndex.map  { case(n,i) => n.makeIO()(ValName(s"pwm_$i")) }
}
