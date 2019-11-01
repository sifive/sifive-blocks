// See LICENSE for license details.
package sifive.blocks.devices.pwm

import freechips.rocketchip.diplomaticobjectmodel.model.{OMDevice, OMInterrupt, OMMemoryRegion}

case class OMPWM(
  numComparators: Int,
  compareWidth: Int,
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  _types: Seq[String] = Seq("OMPWM", "OMDevice", "OMComponent"),
) extends OMDevice
