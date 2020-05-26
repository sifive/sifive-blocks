// See LICENSE for license details.
package sifive.blocks.util

import Chisel._

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.RegisterRouter
import freechips.rocketchip.subsystem._

case class DevicesLocated(loc: HierarchicalLocation) extends Field[Seq[DeviceAttachParams]](Nil)

trait CanHaveDevices { this: Attachable =>
  def location: HierarchicalLocation
  val ibus: InterruptBusWrapper
  //val subHierarchies: Option[Seq[CanHaveDevices]]
  val devicesConfigs: Seq[DeviceAttachParams] = p(DevicesLocated(location))// ++ subHierarchies.foreach(_.foreach(_.devicesConfigs))
  val devices: Seq[LazyModule] = devicesConfigs.map(_.attachTo(this))// ++ subHierarchies.foreach(_.foreach(_.devices))
}

trait DeviceParams

trait DeviceAttachParams {
  val device: DeviceParams
  val controlWhere: TLBusWrapperLocation
  val blockerAddr: Option[BigInt]
  val controlXType: ClockCrossingType

  def attachTo(where: Attachable)(implicit p: Parameters): LazyModule
}
