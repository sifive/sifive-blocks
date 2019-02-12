package sifive.blocks.diplomaticobjectmodel.models

import freechips.rocketchip.diplomaticobjectmodel.model._

case class OMGPIO(
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  specifications: List[OMSpecification],
  _types: Seq[String] = Seq("OMGPIO", "OMDevice", "OMComponent", "OMCompoundType")
) extends OMDevice
