// See LICENSE for license details.

package sifive.blocks.util

import chisel3._
import chisel3.util._

import freechips.rocketchip.config._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.LogicalTreeNode
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.util.LocationMap

import firrtl.graph._
import scala.collection.mutable.ListBuffer

case object HierarchyKey extends Field[Option[DiGraph[HierarchicalLocation]]](None)

case object ESS0 extends HierarchicalLocation("ESS0")
case object ESS1 extends HierarchicalLocation("ESS1")

case class EmptySubsystemParams(
  name: String,
  location: HierarchicalLocation,
  ibus: InterruptBusWrapper,
  logicalTreeNode: LogicalTreeNode,
  asyncClockGroupsNode: ClockGroupEphemeralNode)

class EmptySubsystem(val location: HierarchicalLocation = ESS0, val ibus: InterruptBusWrapper, params: EmptySubsystemParams)(implicit p: Parameters)
  extends LazyModule 
    with Attachable
    with HasConfigurableTLNetworkTopology 
    with CanHaveDevices {

  def devicesSubhierarchies = None

  def logicalTreeNode = params.logicalTreeNode
  implicit val asyncClockGroupsNode = params.asyncClockGroupsNode

  lazy val module = new LazyModuleImp(this) {
    //override def desiredName: String = name
  }
}

trait HasConfigurableHierarchy { this: Attachable =>
  def location: HierarchicalLocation

  def createHierarchyMap(
    root: HierarchicalLocation,
    graph: DiGraph[HierarchicalLocation],
    context: Attachable): Unit = {

    busLocationFunctions += (root -> context.tlBusWrapperLocationMap)
    hierarchyMap += (root -> context)

    // Create and recurse on child hierarchies
    val edges = graph.getEdges(root)
    edges.foreach { edge =>
      val essParams = EmptySubsystemParams(
        name = edge.name,
        ibus = this.ibus,
        location = edge,
        logicalTreeNode = this.logicalTreeNode,
        asyncClockGroupsNode = this.asyncClockGroupsNode)
      val ess = context { LazyModule(new EmptySubsystem(edge, ibus, essParams)) }
      createHierarchyMap(edge, graph, ess)
      busLocationFunctions.foreach { case(hier, func) => tlBusWrapperLocationMap ++= func }
    }
  }


  val busLocationFunctions = LocationMap.empty[LocationMap[TLBusWrapper]]
  val hierarchyMap = LocationMap.empty[Attachable]
  p(HierarchyKey).foreach(createHierarchyMap(location, _, this))

}

class Hierarchy(val root: HierarchicalLocation) {
  require(root == InSystem || root == InSubsystem, "Invalid root hierarchy")

  val graph = new MutableDiGraph[HierarchicalLocation]
  graph.addVertex(root)

  def addSubhierarchy(parent: HierarchicalLocation, child: HierarchicalLocation): Unit = {
    graph.addVertex(child)
    graph.addEdge(parent,child) 
  }

  def closeHierarchy(): DiGraph[HierarchicalLocation] = {
    DiGraph(graph)
  }

}

object Hierarchy {
  def init(root: HierarchicalLocation): Hierarchy = {
    val hierarchy = new Hierarchy(root)
    hierarchy
  }

  def default(root: HierarchicalLocation): DiGraph[HierarchicalLocation] = {
    val h = init(root)
    h.closeHierarchy()
  }

}
