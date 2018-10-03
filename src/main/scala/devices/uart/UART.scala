// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

import sifive.blocks.util.{NonBlockingEnqueue, NonBlockingDequeue}

case class UARTParams(
  address: BigInt,
  dataBits: Int = 8,
  stopBits: Int = 2,
  divisorBits: Int = 16,
  oversample: Int = 4,
  nSamples: Int = 3,
  nTxEntries: Int = 8,
  nRxEntries: Int = 8)
{
  def oversampleFactor = 1 << oversample
  require(divisorBits > oversample)
  require(oversampleFactor > nSamples)
}

class UARTPortIO extends Bundle {
  val txd = Bool(OUTPUT)
  val rxd = Bool(INPUT)
}

class UARTInterrupts extends Bundle {
  val rxwm = Bool()
  val txwm = Bool()
}

abstract class UART(busWidthBytes: Int, val c: UARTParams, divisorInit: Int = 0)
                   (implicit p: Parameters)
    extends IORegisterRouter(
      RegisterRouterParams(
        name = "serial",
        compat = Seq("sifive,uart0"), 
        base = c.address,
        beatBytes = busWidthBytes),
      new UARTPortIO)
    with HasInterruptSources {

  def nInterrupts = 1

  ResourceBinding {
    Resource(ResourceAnchors.aliases, "uart").bind(ResourceAlias(device.label))
  }

  require(divisorInit != 0, "UART divisor wasn't initialized during instantiation")
  require(divisorInit >> c.divisorBits == 0, s"UART divisor reg (width $c.divisorBits) not wide enough to hold $divisorInit")

  lazy val module = new LazyModuleImp(this) {

  val txm = Module(new UARTTx(c))
  val txq = Module(new Queue(txm.io.in.bits, c.nTxEntries))

  val rxm = Module(new UARTRx(c))
  val rxq = Module(new Queue(rxm.io.out.bits, c.nRxEntries))

  val div = Reg(init = UInt(divisorInit, c.divisorBits))

  private val stopCountBits = log2Up(c.stopBits)
  private val txCountBits = log2Floor(c.nTxEntries) + 1
  private val rxCountBits = log2Floor(c.nRxEntries) + 1

  val txen = Reg(init = Bool(false))
  val rxen = Reg(init = Bool(false))
  val txwm = Reg(init = UInt(0, txCountBits))
  val rxwm = Reg(init = UInt(0, rxCountBits))
  val nstop = Reg(init = UInt(0, stopCountBits))

  txm.io.en := txen
  txm.io.in <> txq.io.deq
  txm.io.div := div
  txm.io.nstop := nstop
  port.txd := txm.io.out

  rxm.io.en := rxen
  rxm.io.in := port.rxd
  rxq.io.enq <> rxm.io.out
  rxm.io.div := div

  val ie = Reg(init = new UARTInterrupts().fromBits(Bits(0)))
  val ip = Wire(new UARTInterrupts)

  ip.txwm := (txq.io.count < txwm)
  ip.rxwm := (rxq.io.count > rxwm)
  interrupts(0) := (ip.txwm && ie.txwm) || (ip.rxwm && ie.rxwm)

  regmap(
    UARTCtrlRegs.txfifo -> RegFieldGroup("txdata",Some("Transmit data"),
                           NonBlockingEnqueue(txq.io.enq)),
    UARTCtrlRegs.rxfifo -> RegFieldGroup("rxdata",Some("Receive data"),
                           NonBlockingDequeue(rxq.io.deq)),

    UARTCtrlRegs.txctrl -> RegFieldGroup("txctrl",Some("Serial transmit control"),Seq(
      RegField(1, txen,
               RegFieldDesc("txen","Transmit enable", reset=Some(0))),
      RegField(stopCountBits, nstop,
               RegFieldDesc("nstop","Number of stop bits", reset=Some(0))))),
    UARTCtrlRegs.rxctrl -> Seq(RegField(1, rxen,
               RegFieldDesc("txen","Receive enable", reset=Some(0)))),
    UARTCtrlRegs.txmark -> Seq(RegField(txCountBits, txwm,
               RegFieldDesc("txcnt","Transmit watermark level", reset=Some(0)))),
    UARTCtrlRegs.rxmark -> Seq(RegField(rxCountBits, rxwm,
               RegFieldDesc("rxcnt","Receive watermark level", reset=Some(0)))),

    UARTCtrlRegs.ie -> RegFieldGroup("ie",Some("Serial interrupt enable"),Seq(
      RegField(1, ie.txwm,
               RegFieldDesc("txwm_ie","Transmit watermark interrupt enable", reset=Some(0))),
      RegField(1, ie.rxwm,
               RegFieldDesc("rxwm_ie","Receive watermark interrupt enable", reset=Some(0))))),

    UARTCtrlRegs.ip -> RegFieldGroup("ip",Some("Serial interrupt pending"),Seq(
      RegField.r(1, ip.txwm,
                 RegFieldDesc("txwm_ip","Transmit watermark interrupt pending", volatile=true)),
      RegField.r(1, ip.rxwm,
                 RegFieldDesc("rxwm_ip","Receive watermark interrupt pending", volatile=true)))),

    UARTCtrlRegs.div -> Seq(
      RegField(c.divisorBits, div,
                 RegFieldDesc("div","Baud rate divisor",reset=Some(divisorInit))))
  )
}}

class TLUART(busWidthBytes: Int, params: UARTParams, divinit: Int)(implicit p: Parameters)
  extends UART(busWidthBytes, params, divinit) with HasTLControlRegMap

case class UARTAttachParams(
  uart: UARTParams,
  divinit: Int,
  controlBus: TLBusWrapper,
  intNode: IntInwardNode,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing,
  mclock: Option[ModuleValue[Clock]] = None,
  mreset: Option[ModuleValue[Bool]] = None)
  (implicit val p: Parameters)

object UART {
  val nextId = { var i = -1; () => { i += 1; i} }

  def attach(params: UARTAttachParams): TLUART = {
    implicit val p = params.p
    val name = s"uart_${nextId()}"
    val cbus =  params.controlBus
    val uart = LazyModule(new TLUART(cbus.beatBytes, params.uart, params.divinit))
    uart.suggestName(name)

    cbus.coupleTo(s"slave_named_$name") {
      uart.controlXing(params.controlXType) := TLFragmenter(cbus.beatBytes, cbus.blockBytes) := _
    }
    params.intNode := uart.intXing(params.intXType)
    InModuleBody { uart.module.clock := params.mclock.map(_.getWrappedValue).getOrElse(cbus.module.clock) }
    InModuleBody { uart.module.reset := params.mreset.map(_.getWrappedValue).getOrElse(cbus.module.reset) }

    uart
  }

  def attachAndMakePort(params: UARTAttachParams): ModuleValue[UARTPortIO] = {
    val uart = attach(params)
    val uartNode = uart.ioNode.makeSink()(params.p)
    InModuleBody { uartNode.makeIO()(ValName(uart.name)) }
  }

  def tieoff(port: UARTPortIO) {
    port.rxd := UInt(1)
  }

  def loopback(port: UARTPortIO) {
    port.rxd := port.txd
  }
}
