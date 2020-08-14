// See LICENSE for license details.
package sifive.blocks.devices.i2c

import Chisel.{defaultCompileOptions => _, _}
import freechips.rocketchip.util.CompileOptions.NotStrictInferReset
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{BaseSubsystem}

case object PeripheryI2CKey extends Field[Seq[I2CParams]](Nil)

trait HasPeripheryI2C { this: BaseSubsystem =>
  val i2cNodes =  p(PeripheryI2CKey).map { ps =>
    I2CAttachParams(ps).attachTo(this).ioNode.makeSink()
  }
}

trait HasPeripheryI2CBundle {
  val i2c: Seq[I2CPort]
}

trait HasPeripheryI2CModuleImp extends LazyModuleImp with HasPeripheryI2CBundle {
  val outer: HasPeripheryI2C
  val i2c  = outer.i2cNodes.zipWithIndex.map  { case(n,i) => n.makeIO()(ValName(s"i2c_$i")) }
}
