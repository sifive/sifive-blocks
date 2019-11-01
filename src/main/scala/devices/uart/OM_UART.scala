// See LICENSE for license details.
package sifive.blocks.devices.uart

import freechips.rocketchip.diplomaticobjectmodel.model.{OMDevice, OMInterrupt, OMMemoryRegion}

case class OMUART(
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  _types: Seq[String] = Seq("OMUART", "OMDevice", "OMComponent"),
) extends OMDevice
