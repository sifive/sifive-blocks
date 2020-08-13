// See LICENSE for license details.
package sifive.blocks.util

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.diplomacy._

trait HasDeviceDFTPorts[T <: Bundle] {
  
  def dftNode: BundleBridgeSource[T]

  def makeDFTPort(implicit p: Parameters): ModuleValue[T] = {
    val dftSink = dftNode.makeSink()
    InModuleBody { dontTouch(dftSink.bundle) }
  }
}

trait CanHaveDFT { 
  
  implicit val p: Parameters

  def devices: Seq[LazyModule]

  val dftNodes = devices.collect { case source: HasDeviceDFTPorts[Bundle] => source }
  dftNodes.foreach(_.makeDFTPort)
}