// See LICENSE for license details.

 package sifive.blocks.diplomaticobjectmodel.logicaltree


 import freechips.rocketchip.diplomacy.{ResourceBindings, SimpleDevice}
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.LogicalTreeNode
import freechips.rocketchip.diplomaticobjectmodel.model._
import sifive.blocks.devices.gpio.GPIOAttachParams
import sifive.blocks.devices.spi.{SPIAttachParams, SPIFlashAttachParams}
import sifive.blocks.devices.uart.UARTAttachParams

 class GPIOLogicalTreeNode(device: SimpleDevice, f: () => OMRegisterMap, params: GPIOAttachParams) extends LogicalTreeNode(() => Some(device)) {

   def getOMGPIO(resourceBindings: ResourceBindings): Seq[OMComponent] = {
    val memRegions : Seq[OMMemoryRegion]= DiplomaticObjectModelAddressing.getOMMemoryRegions("GPIO", resourceBindings, Some(f()))
    val ints = DiplomaticObjectModelAddressing.describeInterrupts(device.describe(resourceBindings).name, resourceBindings)

     Seq[OMComponent](
      OMGPIO(
        memoryRegions = memRegions,
        interrupts = ints,
        hasIof = params.gpio.includeIOF,
        controlsDriveStrength = false, // TODO
        hasPinCtrl = false, // TODO
        nGPIO = 0 // TODO
    )
    )
  }

   override def getOMComponents(resourceBindings: ResourceBindings, components: Seq[OMComponent]): Seq[OMComponent] = {
    DiplomaticObjectModelAddressing.getOMComponentHelper(resourceBindings, getOMGPIO)
  }
}

 class UARTLogicalTreeNode(device: SimpleDevice, f: () => OMRegisterMap, params: UARTAttachParams) extends LogicalTreeNode(() => Some(device)) {
  def getOMUART(resourceBindings: ResourceBindings): Seq[OMComponent] = {
    val memRegions : Seq[OMMemoryRegion]= DiplomaticObjectModelAddressing.getOMMemoryRegions("UART", resourceBindings, Some(f()))
    val ints = DiplomaticObjectModelAddressing.describeInterrupts(device.describe(resourceBindings).name, resourceBindings)

     Seq[OMComponent](
      OMUART(
        memoryRegions = memRegions,
        interrupts = ints,
        nPriorities = 0, //TODO
        divisorWidth = params.uart.divisorBits,
        divisorInit = params.divinit,
        nRxExtries = params.uart.nRxEntries,
        nTxEntries = params.uart.nTxEntries
      )
    )
  }

   override def getOMComponents(resourceBindings: ResourceBindings, components: Seq[OMComponent]): Seq[OMComponent] = {
    DiplomaticObjectModelAddressing.getOMComponentHelper(resourceBindings, getOMUART)
  }
}

 class SPIFlashLogicalTreeNode(device: SimpleDevice, f: () => OMRegisterMap, params: SPIFlashAttachParams) extends LogicalTreeNode(() => Some(device)) {
  def getOMSPI(resourceBindings: ResourceBindings): Seq[OMComponent] = {
    val memRegions : Seq[OMMemoryRegion]= DiplomaticObjectModelAddressing.getOMMemoryRegions("SPI", resourceBindings, Some(f()))
    val ints = DiplomaticObjectModelAddressing.describeInterrupts(device.describe(resourceBindings).name, resourceBindings)

     Seq[OMComponent](
      OMSPI(
        memoryRegions = memRegions,
        interrupts = ints,
        divisorWidth = params.spi.divisorBits,
        chipSelectWidth = params.spi.csWidth,
        hasCoarseDelay = params.spi.delayBits  > 0, // TODO Check?
        hasFineDelay = params.spi.delayBits > 0 // TODO Check?
      )
    )
  }

   override def getOMComponents(resourceBindings: ResourceBindings, components: Seq[OMComponent]): Seq[OMComponent] = {
    DiplomaticObjectModelAddressing.getOMComponentHelper(resourceBindings, getOMSPI)
  }
}

 class SPILogicalTreeNode(device: SimpleDevice, f: () => OMRegisterMap, params: SPIAttachParams) extends LogicalTreeNode(() => Some(device)) {
  def getOMSPI(resourceBindings: ResourceBindings): Seq[OMComponent] = {
    val memRegions : Seq[OMMemoryRegion]= DiplomaticObjectModelAddressing.getOMMemoryRegions("SPI", resourceBindings, Some(f()))
    val ints = DiplomaticObjectModelAddressing.describeInterrupts(device.describe(resourceBindings).name, resourceBindings)

     Seq[OMComponent](
      OMSPI(
        memoryRegions = memRegions,
        interrupts = ints,
        divisorWidth = params.spi.divisorBits,
        chipSelectWidth = params.spi.csWidth,
        hasCoarseDelay = params.spi.delayBits  > 0, // TODO Check?
        hasFineDelay = params.spi.delayBits > 0 // TODO Check?
      )
    )
  }

   override def getOMComponents(resourceBindings: ResourceBindings, components: Seq[OMComponent]): Seq[OMComponent] = {
    DiplomaticObjectModelAddressing.getOMComponentHelper(resourceBindings, getOMSPI)
  }
}


