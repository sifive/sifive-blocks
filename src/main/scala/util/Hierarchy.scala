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

case object HierarchyKey extends Field[DiGraph[HierarchicalLocation]]

case object ESS0 extends HierarchicalLocation("ESS0")
case object ESS1 extends HierarchicalLocation("ESS1")

case class EmptySubsystemParams(
  name: String,
  location: HierarchicalLocation,
  ibus: InterruptBusWrapper,
  logicalTreeNode: LogicalTreeNode,
  asyncClockGroupsNode: ClockGroupEphemeralNode)

class EmptySubsystem(params: EmptySubsystemParams)(implicit p: Parameters) extends LazyModule 
  with Attachable
  with HasConfigurableTLNetworkTopology
  with CanHaveDevices {

  val location = params.location

  val ibus = params.ibus
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

    // TODO: Need to aggregate locateTLBusWrapper functions for each location
    // Add the current hiearchy to the map
    // hierarchyMap += (root -> context)

    // Create and recurse on child hierarchies
    val edges = graph.getEdges(root)
    edges.foreach { edge =>
      val essParams = EmptySubsystemParams(
        name = edge.name,
        ibus = this.ibus,
        location = edge,
        logicalTreeNode = this.logicalTreeNode,
        asyncClockGroupsNode = this.asyncClockGroupsNode)
      val ess = context { LazyModule(new EmptySubsystem(essParams)) }
      createHierarchyMap(edge, graph, ess)
    }
  }


  val hierarchyMap = LocationMap.empty[Attachable]
  createHierarchyMap(location, p(HierarchyKey), this)
  println("\n\n\nPrinting p(HierarchyKey):")
  println(p(HierarchyKey))
  println("\n\n\nPrinting generated hierarchyMap:")
  println(hierarchyMap)

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
