package diplomaticobjectmodel.Utils

import freechips.rocketchip.diplomacy.{Binding, Device, DiplomacyUtils, ResourceBindings, ResourceValue}
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing.omMemoryRegion
import freechips.rocketchip.diplomaticobjectmodel.model.{OMMemoryRegion, OMRegisterMap}

object Utils {
  def getOMMemoryRegions(name: String, resourceBindings: ResourceBindings, omRegMap: Option[OMRegisterMap] = None): Seq[OMMemoryRegion]= {
    resourceBindings.map.collect {
      case (x: String, seq: Seq[Binding]) if (DiplomacyUtils.regFilter(x) || DiplomacyUtils.rangeFilter(x)) =>
        seq.map {
          case Binding(device: Option[Device], value: ResourceValue) =>
            DiplomaticObjectModelAddressing.omMemoryRegion(name, DiplomacyUtils.regName(x).getOrElse(""), value, omRegMap)
        }
    }.flatten.toSeq
  }

}
