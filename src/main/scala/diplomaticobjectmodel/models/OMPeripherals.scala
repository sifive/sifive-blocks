// See LICENSE for license details.

package sifive.blocks.diplomaticobjectmodel.logicaltree

import freechips.rocketchip.diplomaticobjectmodel.model._

case class OMGPIO(
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  hasIof: Boolean,
  controlsDriveStrength: Boolean,
  hasPinCtrl: Boolean,
  nGPIO: Int,
  _types: Seq[String] = Seq("OMGPIO", "OMDevice", "OMComponent", "OMCompoundType")
) extends OMDevice

case class OMUART(
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  divisorWidth: Int,
  divisorInit: Int,
  nRxEntries: Int,
  nTxEntries: Int,
  _types: Seq[String] = Seq("OMUART", "OMDevice", "OMComponent", "OMCompoundType")
) extends OMDevice

case class OMSPI(
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  divisorWidth: Int,
  chipSelectWidth: Int,
  hasCoarseDelay: Boolean,
  hasFineDelay: Boolean,
  _types: Seq[String] = Seq("OMSPI", "OMDevice", "OMComponent", "OMCompoundType")
) extends OMDevice
