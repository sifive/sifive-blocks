// See LICENSE for license details.
package sifive.blocks.devices.pwm

import Chisel._
import config.Field
import diplomacy.LazyModule
import rocketchip.{
  HasTopLevelNetworks,
  HasTopLevelNetworksBundle,
  HasTopLevelNetworksModule
}
import uncore.tilelink2.TLFragmenter
import util.HeterogeneousBag

import sifive.blocks.devices.gpio._

class PWMPortIO(c: PWMParams) extends Bundle {
  val port = Vec(c.ncmp, Bool()).asOutput
  override def cloneType: this.type = new PWMPortIO(c).asInstanceOf[this.type]
}

class PWMPinsIO(c: PWMParams) extends Bundle {
  val pwm = Vec(c.ncmp, new GPIOPin)
}

class PWMGPIOPort(c: PWMParams) extends Module {
  val io = new Bundle {
    val pwm = new PWMPortIO(c).flip()
    val pins = new PWMPinsIO(c)
  }

  GPIOOutputPinCtrl(io.pins.pwm, io.pwm.port.asUInt)
}

case object PeripheryPWMKey extends Field[Seq[PWMParams]]

trait HasPeripheryPWM extends HasTopLevelNetworks {
  val pwmParams = p(PeripheryPWMKey)
  val pwms = pwmParams map { params =>
    val pwm = LazyModule(new TLPWM(peripheryBusBytes, params))
    pwm.node := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := pwm.intnode
    pwm
  }
}

trait HasPeripheryPWMBundle extends HasTopLevelNetworksBundle {
  val outer: HasPeripheryPWM
  val pwms = HeterogeneousBag(outer.pwmParams.map(new PWMPortIO(_)))
}

trait HasPeripheryPWMModule extends HasTopLevelNetworksModule {
  val outer: HasPeripheryPWM
  val io: HasPeripheryPWMBundle

  (io.pwms zip outer.pwms) foreach { case (io, device) =>
    io.port := device.module.io.gpio
  }
}
