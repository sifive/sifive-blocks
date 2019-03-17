// See LICENSE for license details.

package sifive.blocks.diplomaticobjectmodel.logicaltree


import freechips.rocketchip.diplomacy.{ResourceBindings, ResourceBindingsMap, SimpleDevice}
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.LogicalTreeNode
import freechips.rocketchip.diplomaticobjectmodel.model._
import sifive.blocks.devices.uart.UARTAttachParams

class UARTLogicalTreeNode(device: SimpleDevice, f: () => OMRegisterMap, params: UARTAttachParams) extends LogicalTreeNode {

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

  override def getOMComponents(resourceBindingsMap: ResourceBindingsMap, components: Seq[OMComponent]): Seq[OMComponent] = {
    DiplomaticObjectModelAddressing.getOMComponentHelper(device, resourceBindingsMap, getOMUART)
  }
}

