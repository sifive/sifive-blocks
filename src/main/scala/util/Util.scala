// See LICENSE for license details.
package sifive.blocks.util

import Chisel._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.IORegisterRouter
import freechips.rocketchip.subsystem.{Attachable, BaseSubsystemBusAttachment}

trait DeviceParams

trait DeviceAttachParams[T <: Data] {
  val device: DeviceParams
  val controlWhere: BaseSubsystemBusAttachment
  val blockerAddr: Option[BigInt]
  val controlXType: ClockCrossingType
  val intXType: ClockCrossingType

  def attachTo(where: Attachable)(implicit p: Parameters): IORegisterRouter[T]
}
