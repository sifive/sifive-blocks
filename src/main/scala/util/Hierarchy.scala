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

case object HierarchyKey extends Field[Option[DiGraph[HierarchicalLocation]]](None)

case object DSS0 extends HierarchicalLocation("DSS0")
case object DSS1 extends HierarchicalLocation("DSS1")

case class DevicesSubsystemParams(
  name: String,
  logicalTreeNode: LogicalTreeNode,
  asyncClockGroupsNode: ClockGroupEphemeralNode)

class DevicesSubsystem(val location: HierarchicalLocation, val ibus: InterruptBusWrapper, params: DevicesSubsystemParams)(implicit p: Parameters)
  extends LazyModule 
    with Attachable
    with HasConfigurableTLNetworkTopology 
    with CanHaveDevices {

  def devicesSubhierarchies = None
  def logicalTreeNode = params.logicalTreeNode
  implicit val asyncClockGroupsNode = params.asyncClockGroupsNode

  lazy val module = new LazyModuleImp(this) {
    override def desiredName: String = params.name
  }
}

trait HasConfigurableHierarchy { this: Attachable =>
  def location: HierarchicalLocation

  def createHierarchyMap(
    root: HierarchicalLocation,
    graph: DiGraph[HierarchicalLocation],
    context: Attachable): Unit = {

    hierarchyMap += (root -> context)

    // Create and recurse on child hierarchies
    val edges = graph.getEdges(root)
    edges.foreach { edge =>
      val dssParams = DevicesSubsystemParams(
        name = edge.name,
        logicalTreeNode = this.logicalTreeNode,
        asyncClockGroupsNode = this.asyncClockGroupsNode)
      val dss = context { LazyModule(new DevicesSubsystem(edge, ibus, dssParams)) }
      createHierarchyMap(edge, graph, dss)
    }
  }

  val busLocationFunctions = LocationMap.empty[LocationMap[TLBusWrapper]]
  val hierarchyMap = LocationMap.empty[Attachable]

  p(HierarchyKey).foreach(createHierarchyMap(location, _, this))

  hierarchyMap.foreach { case(label, context) =>
    tlBusWrapperLocationMap ++= context.tlBusWrapperLocationMap
  }
}

class Hierarchy(val root: HierarchicalLocation) {
  require(root == InSystem || root == InSubsystem, "Invalid root hierarchy")

  val graph = new MutableDiGraph[HierarchicalLocation]
  graph.addVertex(root)

  def addSubhierarchy(parent: HierarchicalLocation, child: HierarchicalLocation): Unit = {
    graph.addVertex(child)
    graph.addEdge(parent,child) 
  }

  def addSubhierarchies(parent: HierarchicalLocation, children: Seq[HierarchicalLocation]): Unit = {
    children.foreach(addSubhierarchy(parent,_))
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
