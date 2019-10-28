// See LICENSE for license details.
package sifive.blocks.devices.gpio

import freechips.rocketchip.diplomaticobjectmodel.model.{OMDevice, OMInterrupt, OMMemoryRegion}

case class OMGPIO(
  hasIOF: Boolean,
  nPins: Int,
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  _types: Seq[String] = Seq("OMGPIO", "OMDevice", "OMComponent"),
) extends OMDevice
