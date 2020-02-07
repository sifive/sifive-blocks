// See LICENSE for license details.
package sifive.blocks.devices.timer

import freechips.rocketchip.diplomaticobjectmodel.model.{OMDevice, OMInterrupt, OMMemoryRegion}

case class OMTimer(
  comparatorWidthBits: Int,
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  _types: Seq[String] = Seq("OMTimer", "OMDevice", "OMComponent"),
) extends OMDevice
