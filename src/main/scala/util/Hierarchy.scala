// See LICENSE for license details.

package sifive.blocks.util

import chisel3._
import chisel3.util._

import freechips.rocketchip.config._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util.LocationMap

import firrtl.graph._

case object HierarchyKey extends Field[Option[DiGraph[HierarchicalLocation]]](None)

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
      val dss = context { LazyModule(new DevicesSubsystem(
        hierarchyName = edge.name,
        location = edge,
        ibus = context.ibus,
        asyncClockGroupsNode = context.asyncClockGroupsNode,
        logicalTreeNode = context.logicalTreeNode)) }
      dss.suggestName(edge.name)
      createHierarchyMap(edge, graph, dss)
    }
  }

  def getDevicesSubhierarchies: Seq[CanHaveDevices] = {
    hierarchyMap
      .values
      .toSeq
      .asInstanceOf[Seq[CanHaveDevices]]
      .filter(_.location != location)
  }

  val hierarchyMap = LocationMap.empty[Attachable]
  p(HierarchyKey).foreach(createHierarchyMap(location, _, this))

  hierarchyMap.foreach { case(label, context) =>
    tlBusWrapperLocationMap ++= context.tlBusWrapperLocationMap
  }
}

object Hierarchy {
  def apply(edges: Map[HierarchicalLocation, Set[HierarchicalLocation]]): DiGraph[HierarchicalLocation] = {
    DiGraph(edges)
  }
}
