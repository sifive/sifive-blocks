// See LICENSE for license details.
package sifive.blocks.util

import Chisel._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.RegisterRouter
import freechips.rocketchip.subsystem.{Attachable, BaseSubsystemBusAttachment}

trait DeviceParams

trait DeviceAttachParams {
  val device: DeviceParams
  val controlWhere: BaseSubsystemBusAttachment
  val blockerAddr: Option[BigInt]
  val controlXType: ClockCrossingType

  def attachTo(where: Attachable)(implicit p: Parameters): RegisterRouter
}
