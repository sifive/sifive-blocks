// See LICENSE for license details.
package sifive.blocks.util

import chisel3._
import chisel3.util._

import freechips.rocketchip.config._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.LogicalTreeNode
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.prci._
import freechips.rocketchip.util.LocationMap

import firrtl.graph._

case object HierarchyKey extends Field[DiGraph[HierarchicalLocation]]

case object ESS0 extends HierarchicalLocation("ESS0")
case object ESS1 extends HierarchicalLocation("ESS1")

case class EmptySubsystemParams(
  ibus: InterruptBusWrapper,
  tlBusWrapperLocationMap: LocationMap[TLBusWrapper],
  logicalTreeNode: LogicalTreeNode,
  asyncClockGroupsNode: ClockGroupEphemeralNode)
class EmptySubsystem(name: String, params: EmptySubsystemParams)(implicit p: Parameters) extends LazyModule with Attachable {

  val ibus = params.ibus
  override val tlBusWrapperLocationMap = params.tlBusWrapperLocationMap
  def logicalTreeNode = params.logicalTreeNode
  implicit val asyncClockGroupsNode = params.asyncClockGroupsNode

  lazy val module = new LazyModuleImp(this) {
    override def desiredName: String = name
  }
}

trait CanHaveConfigurableHierarchy { this: Attachable =>
  def location: HierarchicalLocation

  def createHierarchyMap(
    root: HierarchicalLocation,
    graph: DiGraph[HierarchicalLocation],
    context: Attachable,
    params: EmptySubsystemParams): Unit = {

    if(root == InSystem || root == InSubsystem) {
      hierarchyMap += (root -> context)
    }

    val edges = graph.getEdges(root)
    edges.foreach { edge =>
      val ess = context { LazyModule(new EmptySubsystem(edge.name, params)) }
      hierarchyMap += (edge -> ess)
      createHierarchyMap(edge, graph, ess, params)
    }
  }

  val essParams = EmptySubsystemParams(
    ibus = this.ibus,
    tlBusWrapperLocationMap = this.tlBusWrapperLocationMap,
    logicalTreeNode = this.logicalTreeNode,
    asyncClockGroupsNode = this.asyncClockGroupsNode)

  var hierarchyMap = Map[HierarchicalLocation, Attachable]()
  createHierarchyMap(location, p(HierarchyKey), this, essParams)
  println("\n\n\nPrinting p(HierarchyKey):")
  println(p(HierarchyKey))
  println("\n\n\nPrinting generated hierarchyMap:")
  println(hierarchyMap)

}
