// See LICENSE for license details.
package sifive.blocks.util

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.diplomacy._

trait HasDFT[T <: Data] {
  
  def dftNode: BundleBridgeSource[T]

  def makePort(implicit p: Parameters): ModuleValue[T] = {
    val dftSink = dftNode.makeSink()
    InModuleBody { dontTouch(dftSink.bundle) }
  }
}

trait CanHaveDFT { 
  
  implicit val p: Parameters

  def devices: Seq[LazyModule]

  val dftNode = devices.collect { case source: HasDFT[Data] => source }
  dftNode.foreach(_.makePort)
}