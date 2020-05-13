// See LICENSE for license details.
package sifive.blocks.util

import Chisel._

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.RegisterRouter
import freechips.rocketchip.subsystem.{Attachable, TLBusWrapperLocation, HierarchicalLocation}
import freechips.rocketchip.util._

case object DevicesKey extends Field[Seq[DeviceAttachParams]](Nil)

trait CanHaveDevices extends CanHaveConfigurableHierarchy { this: Attachable =>
  def location: HierarchicalLocation

  val devicesConfigs: Seq[DeviceAttachParams] = p(DevicesKey)
  val isRoot = (p(HierarchyKey).linearize.head == location)
  val devices: Seq[LazyModule] = isRoot.option(devicesConfigs.map { params =>
    params.attachTo(hierarchyMap(params.instWhere))
  }).getOrElse(Nil)
}

trait DeviceParams

trait DeviceAttachParams {
  val device: DeviceParams
  val controlWhere: TLBusWrapperLocation
  val instWhere: HierarchicalLocation
  val blockerAddr: Option[BigInt]
  val controlXType: ClockCrossingType

  def attachTo(where: Attachable)(implicit p: Parameters): LazyModule
}
