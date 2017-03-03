// See LICENSE for license details.
package sifive.blocks.devices.i2c

import Chisel._
import config.Field
import diplomacy.LazyModule
import rocketchip.{HasTopLevelNetworks,HasTopLevelNetworksBundle,HasTopLevelNetworksModule}
import uncore.tilelink2.TLFragmenter

case object PeripheryI2CKey extends Field[Seq[I2CParams]]

trait HasPeripheryI2C extends HasTopLevelNetworks {
  val i2cParams = p(PeripheryI2CKey)
  val i2c = i2cParams map { params =>
    val i2c = LazyModule(new TLI2C(peripheryBusBytes, params))
    i2c.node := TLFragmenter(peripheryBusBytes, cacheBlockBytes)(peripheryBus.node)
    intBus.intnode := i2c.intnode
    i2c
  }
}

trait HasPeripheryI2CBundle extends HasTopLevelNetworksBundle{
  val outer: HasPeripheryI2C
  val i2cs = Vec(outer.i2cParams.size, new I2CPort)
}

trait HasPeripheryI2CModule extends HasTopLevelNetworksModule {
  val outer: HasPeripheryI2C
  val io: HasPeripheryI2CBundle
  (io.i2cs zip outer.i2c).foreach { case (io, device) =>
    io <> device.module.io.port
  }
}
