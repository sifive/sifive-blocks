// See LICENSE for license details.
package sifive.blocks.devices.wdt

import freechips.rocketchip.diplomaticobjectmodel.model.{OMDevice, OMInterrupt, OMMemoryRegion}

case class OMWDT(
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  _types: Seq[String] = Seq("OMWDT", "OMDevice", "OMComponent"),
) extends OMDevice
