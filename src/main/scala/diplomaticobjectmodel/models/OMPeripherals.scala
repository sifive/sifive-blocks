// See LICENSE for license details.

package sifive.blocks.diplomaticobjectmodel.logicaltree

import freechips.rocketchip.diplomaticobjectmodel.model.{OMDevice, OMInterrupt, OMMemoryRegion, OMSpecification}

case class OMUART(
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  nPriorities: Int,
  divisorWidth: Int,
  divisorInit: Int,
  nRxExtries: Int,
  nTxEntries: Int,
  _types: Seq[String] = Seq("OMUART", "OMDevice", "OMComponent", "OMCompoundType")
) extends OMDevice
