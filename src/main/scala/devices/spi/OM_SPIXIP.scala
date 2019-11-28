// See LICENSE for license details.
package sifive.blocks.devices.spi

import freechips.rocketchip.diplomaticobjectmodel.model.{OMDevice, OMInterrupt, OMMemoryRegion}

case class OMSPIXIP(
  instructionAddressBytes: Int,
  instructionPadLengthBits: Int,
  memMapAddressBase: BigInt,
  memMapAddressSizeBytes: BigInt,
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  _types: Seq[String] = Seq("OMSPIXIP", "OMDevice", "OMComponent"),
) extends OMDevice
