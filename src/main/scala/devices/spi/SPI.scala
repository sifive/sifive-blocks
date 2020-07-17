// See LICENSE for license details.
package sifive.blocks.devices.spi

import Chisel._

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.util._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import freechips.rocketchip.diplomaticobjectmodel.model.{OMComponent, OMRegister}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalModuleTree, LogicalTreeNode}

import sifive.blocks.util._

case class SPILocated(loc: HierarchicalLocation) extends Field[Seq[SPIAttachParams]](Nil)

case class SPIAttachParams(
  device: SPIParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  controlXType: ClockCrossingType = NoCrossing,
  blockerAddr: Option[BigInt] = None,
  clockSinkWhere: Option[ClockSinkLocation] = None,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLSPI = where {
    val id = SPI.nextId()
    val name = s"spi_${id}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val spiClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val spi = spiClockDomainWrapper { LazyModule(new TLSPI(tlbus.beatBytes, device)) }
    spi.suggestName(name)

    val sinkLocation = clockSinkWhere.getOrElse(new ClockSinkLocation(s"spi${id}_sink"))
    where.anyLocationMap += (sinkLocation -> spiClockDomainWrapper.clockNode)

    tlbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus) := _ }
        blocker
      }

      (spi.controlXing(controlXType)
        := TLFragmenter(tlbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := spi.intXing(intXType)

    LogicalModuleTree.add(where.logicalTreeNode, spi.logicalTreeNode)

    spi
  }
}

case class SPIFlashLocated(loc: HierarchicalLocation) extends Field[Seq[SPIFlashAttachParams]](Nil)

case class SPIFlashAttachParams(
  device: SPIFlashParams,
  controlXType: ClockCrossingType = NoCrossing,
  controlWhere: TLBusWrapperLocation = PBUS,
  dataWhere: TLBusWrapperLocation = PBUS,
  memXType: ClockCrossingType = NoCrossing,
  fBufferDepth: Int = 0,
  blockerAddr: Option[BigInt] = None,
  clockSinkWhere: Option[ClockSinkLocation] = None,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLSPIFlash = where {
    val id = SPI.nextFlashId()
    val name = s"qspi_${id}" // TODO should these be shared with regular SPIs?
    val cbus = where.locateTLBusWrapper(controlWhere)
    val mbus = where.locateTLBusWrapper(dataWhere)
    val qspiClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val qspi = qspiClockDomainWrapper { LazyModule(new TLSPIFlash(cbus.beatBytes, device)) }
    qspi.suggestName(name)

    val sinkLocation = clockSinkWhere.getOrElse(new ClockSinkLocation(s"qspi${id}_sink"))
    where.anyLocationMap += (sinkLocation -> qspiClockDomainWrapper.clockNode)

    cbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, cbus.beatBytes, cbus.beatBytes)))
        cbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(cbus) := _ }
        blocker
      }

      (qspi.controlXing(controlXType)
        := TLFragmenter(cbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    mbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a+0x1000, mbus.beatBytes, mbus.beatBytes)))
        mbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(mbus) := _ }
        blocker
      }

      (qspi.memXing(memXType)
        := TLFragmenter(1, mbus.blockBytes)
        := TLBuffer(BufferParams(fBufferDepth), BufferParams.none)
        := TLWidthWidget(mbus.beatBytes)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := qspi.intXing(intXType)

    LogicalModuleTree.add(where.logicalTreeNode, qspi.logicalTreeNode)

    qspi
  }
}

object SPI {
  val nextId = { var i = -1; () => { i += 1; i} }

  def makePort(node: BundleBridgeSource[SPIPortIO], name: String)(implicit p: Parameters): ModuleValue[SPIPortIO] = {
    val spiNode = node.makeSink()
    InModuleBody { spiNode.makeIO()(ValName(name)) }
  }

  val nextFlashId = { var i = -1; () => { i += 1; i} }

  def makeFlashPort(node: BundleBridgeSource[SPIPortIO], name: String)(implicit p: Parameters): ModuleValue[SPIPortIO] = {
    val qspiNode = node.makeSink()
    InModuleBody { qspiNode.makeIO()(ValName(name)) }
  }

  def connectPort(q: SPIPortIO): SPIPortIO = {
    val x = Wire(new SPIPortIO(q.c))
    x <> q
    x
  }
}
