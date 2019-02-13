// See LICENSE for license details.
package sifive.blocks.util

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

object BasicBusBlocker {
  def apply(addr: BigInt, cbus: TLBusWrapper, beatBytes: Int, name: String)(implicit p: Parameters): TLNode = {
    val bus_blocker = LazyModule(new BasicBusBlocker(BasicBusBlockerParams(
      controlAddress = addr,
      controlBeatBytes = cbus.beatBytes,
      deviceBeatBytes = beatBytes)))

    cbus.coupleTo(s"bus_blocker_$name") { bus_blocker.controlNode := TLFragmenter(cbus) := _ }
    
    bus_blocker.node
  }
}
