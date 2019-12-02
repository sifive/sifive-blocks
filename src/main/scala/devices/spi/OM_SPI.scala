// See LICENSE for license details.
package sifive.blocks.devices.spi

import freechips.rocketchip.diplomaticobjectmodel.model.{OMDevice, OMInterrupt, OMMemoryRegion}

trait baseSPI{
 def  rxDepth: Int
 def  txDepth: Int
 def  csWidthBits: Int
 def  frameBits: Int
 def  delayBits: Int
 def  divisorBits: Int
 def  coarseDelayBits: Int
 def  fineDelayBits: Int
 def  sampleDelayBits: Int
 def  defaultSampleDelay: Int
}

case class OMSPI(
  rxDepth: Int,
  txDepth: Int,
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
) extends baseSPI with OMDevice
