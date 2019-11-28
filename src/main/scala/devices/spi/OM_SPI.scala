// See LICENSE for license details.
package sifive.blocks.devices.spi

import freechips.rocketchip.diplomaticobjectmodel.model.{OMDevice, OMInterrupt, OMMemoryRegion}

case class OMSPI(
  rxDepth: Int,
  txDepth:Int,
  csWidthBits: Int,
  frameBits: Int,
  delayBits: Int,
  divisorBits: Int,
  coarseDelayBits: Int,
  fineDelayBits: Int,
  sampleDelayBits: Int,
  defaultSampleDelay: Int,
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  _types: Seq[String] = Seq("OMSPI", "OMDevice", "OMComponent"),
) extends OMDevice
