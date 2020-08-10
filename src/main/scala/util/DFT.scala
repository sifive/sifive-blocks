// See LICENSE for license details.
package sifive.blocks.util

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.diplomacy._

trait HasDADft[T <: Data] {
  
  def dftDANode: BundleBridgeSource[T]

  def makeDADftPort(implicit p: Parameters, valName: ValName): ModuleValue[T] = {
    val dftSink = dftDANode.makeSink()
    InModuleBody { dftSink.makeIO() } // will be change
  }
}

trait HasJTAGDft[T <: Data] {
  
  def dftJTAGNode: BundleBridgeSource[T]

  def makeJTAGDftPort(implicit p: Parameters): ModuleValue[T] = {
    val dftSink = dftJTAGNode.makeSink()
    InModuleBody { dontTouch(dftSink.bundle) }
  }
}

trait CanHaveDFT { 
  
  def devices: Seq[LazyModule]

  val dftDA = devices.collect { case dft: HasDADft[Data] => dft }
  val dftJTAG = devices.collect { case dft: HasJTAGDft[Data] => dft }
  
  def makeDftPort(implicit p: Parameters, valName: ValName) = {
    dftDA.foreach(_.makeDADftPort)
    dftJTAG.foreach(_.makeJTAGDftPort)
  }
}