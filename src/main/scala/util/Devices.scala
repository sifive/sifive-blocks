// See LICENSE for license details.
package sifive.blocks.util

import Chisel.{defaultCompileOptions => _, _}
import freechips.rocketchip.util.CompileOptions.NotStrictInferReset

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.LogicalTreeNode
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper.RegisterRouter
import freechips.rocketchip.subsystem._

case class DevicesLocated(loc: HierarchicalLocation) extends Field[Seq[DeviceAttachParams]](Nil)

trait CanHaveDevices { this: Attachable =>
  def location: HierarchicalLocation
  def devicesSubhierarchies: Option[Seq[CanHaveDevices]]

  val devicesConfigs: Seq[DeviceAttachParams] = p(DevicesLocated(location)) ++
    devicesSubhierarchies.map(_.map(_.devicesConfigs)).getOrElse(Nil).flatten

  val devices: Seq[LazyModule] = p(DevicesLocated(location)).map(_.attachTo(this)) ++
    devicesSubhierarchies.map(_.map(_.devices)).getOrElse(Nil).flatten
}

trait DeviceParams

trait DeviceAttachParams {
  val device: DeviceParams
  val controlWhere: TLBusWrapperLocation
  val blockerAddr: Option[BigInt]
  val controlXType: ClockCrossingType

  def attachTo(where: Attachable)(implicit p: Parameters): LazyModule
}

case class DevicesSubsystemParams()

// TODO: Use DevicesSubsystemParams as the constructor arugment once Attachable's ibus and
// location are made into defs instead of vals
class DevicesSubsystem(
  val hierarchyName: String,
  val location: HierarchicalLocation,
  val ibus: InterruptBusWrapper,
  val asyncClockGroupsNode: ClockGroupEphemeralNode,
  val logicalTreeNode: LogicalTreeNode)(implicit p: Parameters) extends LazyModule
    with Attachable
    with HasConfigurableTLNetworkTopology
    with CanHaveDevices {

  def devicesSubhierarchies = None

  lazy val module = new LazyModuleImp(this) {
    override def desiredName: String = hierarchyName
  }
}
