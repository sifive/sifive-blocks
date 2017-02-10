// See LICENSE for license details.
package sifive.blocks.devices.pwm

import Chisel._
import config._
import diplomacy.LazyModule
import rocketchip.{TopNetwork,TopNetworkModule}
import uncore.tilelink2.TLFragmenter

import sifive.blocks.devices.gpio._

class PWMPortIO(c: PWMBundleConfig)(implicit p: Parameters) extends Bundle {
  val port = Vec(c.ncmp, Bool()).asOutput
  override def cloneType: this.type = new PWMPortIO(c).asInstanceOf[this.type]
}

class PWMPinsIO(c: PWMBundleConfig)(implicit p: Parameters) extends Bundle {
  val pwm = Vec(c.ncmp, new GPIOPin)
}

class PWMGPIOPort(c: PWMBundleConfig)(implicit p: Parameters) extends Module {
  val io = new Bundle {
    val pwm = new PWMPortIO(c).flip()
    val pins = new PWMPinsIO(c)
  }

  GPIOOutputPinCtrl(io.pins.pwm, io.pwm.port.asUInt)
}

trait PeripheryPWM {
  this: TopNetwork { val pwmConfigs: Seq[PWMConfig] } =>

  val pwm = (pwmConfigs.zipWithIndex) map { case (c, i) =>
    val pwm = LazyModule(new TLPWM(c))
    pwm.node := TLFragmenter(peripheryBusConfig.beatBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := pwm.intnode
    pwm
  }
}

trait PeripheryPWMBundle {
  this: {
    val p: Parameters
    val pwmConfigs: Seq[PWMConfig]
  } =>
  val pwm_bc = pwmConfigs.map(_.bc).reduce(_.union(_))
  val pwms = Vec(pwmConfigs.size, new PWMPortIO(pwm_bc)(p))
}

trait PeripheryPWMModule {
  this: TopNetworkModule {
    val outer: PeripheryPWM
    val io: PeripheryPWMBundle
  } =>
  (io.pwms.zipWithIndex zip outer.pwm) foreach { case ((io, i), device) =>
    io.port := device.module.io.gpio
  }
}
