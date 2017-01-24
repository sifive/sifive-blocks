// See LICENSE for license details.
package sifive.blocks.devices.i2c

import Chisel._
import diplomacy.LazyModule
import rocketchip.{TopNetwork,TopNetworkModule}
import uncore.tilelink2.TLFragmenter

trait PeripheryI2C {
  this: TopNetwork { val i2cConfigs: Seq[I2CConfig] } =>
  val i2cDevices = i2cConfigs.zipWithIndex.map { case (c, i) =>
    val i2c = LazyModule(new TLI2C(c))
    i2c.suggestName(s"i2c$i")
    i2c.node := TLFragmenter(peripheryBusConfig.beatBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := i2c.intnode
    i2c
  }
}

trait PeripheryI2CBundle {
  this: { val i2cConfigs: Seq[I2CConfig] } =>
  val i2cs = Vec(i2cConfigs.size, new I2CPort)
}

trait PeripheryI2CModule {
  this: TopNetworkModule {
    val i2cConfigs: Seq[I2CConfig]
    val outer: PeripheryI2C
    val io: PeripheryI2CBundle
  } =>
  (io.i2cs zip outer.i2cDevices).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}
