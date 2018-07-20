// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.util._

import sifive.blocks.util.{NonBlockingEnqueue, NonBlockingDequeue}

case class UARTParams(
  address: BigInt,
  dataBits: Int = 8,
  stopBits: Int = 2,
  divisorInit: Int = 0,
  divisorBits: Int = 16,
  oversample: Int = 4,
  nSamples: Int = 3,
  nTxEntries: Int = 8,
  nRxEntries: Int = 8) {
  def oversampleFactor = 1 << oversample
  require(divisorBits > oversample)
  require(oversampleFactor > nSamples)
}

class UARTPortIO extends Bundle {
  val txd = Bool(OUTPUT)
  val rxd = Bool(INPUT)
}

class UARTTx(c: UARTParams)(implicit p: Parameters) extends Module {
  val io = new Bundle {
    val en = Bool(INPUT)
    val in = Decoupled(Bits(width = c.dataBits)).flip
    val out = Bits(OUTPUT, 1)
    val div = UInt(INPUT, c.divisorBits)
    val nstop = UInt(INPUT, log2Up(c.stopBits))
  }

  val prescaler = Reg(init = UInt(0, c.divisorBits))
  val pulse = (prescaler === UInt(0))

  private val n = c.dataBits + 1
  val counter = Reg(init = UInt(0, log2Floor(n + c.stopBits) + 1))
  val shifter = Reg(Bits(width = n))
  val out = Reg(init = Bits(1, 1))
  io.out := out

  val plusarg_tx = PlusArg("uart_tx", 1, "Enable/disable the TX to speed up simulation").orR

  val busy = (counter =/= UInt(0))
  io.in.ready := io.en && !busy
  when (io.in.fire()) {
    printf("UART TX (%x): %c\n", io.in.bits, io.in.bits)
  }
  when (io.in.fire() && plusarg_tx) {
    shifter := Cat(io.in.bits, Bits(0, 1))
    counter := Mux1H((0 until c.stopBits).map(i =>
      (io.nstop === UInt(i)) -> UInt(n + i + 1)))
  }
  when (busy) {
    prescaler := Mux(pulse, io.div, prescaler - UInt(1))
  }
  when (pulse && busy) {
    counter := counter - UInt(1)
    shifter := Cat(Bits(1, 1), shifter >> 1)
    out := shifter(0)
  }
}

class UARTRx(c: UARTParams)(implicit p: Parameters) extends Module {
  val io = new Bundle {
    val en = Bool(INPUT)
    val in = Bits(INPUT, 1)
    val out = Valid(Bits(width = c.dataBits))
    val div = UInt(INPUT, c.divisorBits)
  }

  val debounce = Reg(init = UInt(0, 2))
  val debounce_max = (debounce === UInt(3))
  val debounce_min = (debounce === UInt(0))

  val prescaler = Reg(UInt(width = c.divisorBits - c.oversample + 1))
  val start = Wire(init = Bool(false))
  val pulse = (prescaler === UInt(0))

  private val dataCountBits = log2Floor(c.dataBits) + 1

  val data_count = Reg(UInt(width = dataCountBits))
  val data_last = (data_count === UInt(0))
  val sample_count = Reg(UInt(width = c.oversample))
  val sample_mid = (sample_count === UInt((c.oversampleFactor - c.nSamples + 1) >> 1))
  val sample_last = (sample_count === UInt(0))
  val countdown = Cat(data_count, sample_count) - UInt(1)

  // Compensate for the divisor not being a multiple of the oversampling period.
  // Let remainder k = (io.div % c.oversampleFactor).
  // For the last k samples, extend the sampling delay by 1 cycle.
  val remainder = io.div(c.oversample-1, 0)
  val extend = (sample_count < remainder) // Pad head: (sample_count > ~remainder)
  val restore = start || pulse
  val prescaler_in = Mux(restore, io.div >> c.oversample, prescaler)
  val prescaler_next = prescaler_in - Mux(restore && extend, UInt(0), UInt(1))

  val sample = Reg(Bits(width = c.nSamples))
  val voter = Majority(sample.toBools.toSet)
  val shifter = Reg(Bits(width = c.dataBits))

  val valid = Reg(init = Bool(false))
  valid := Bool(false)
  io.out.valid := valid
  io.out.bits := shifter

  val (s_idle :: s_data :: Nil) = Enum(UInt(), 2)
  val state = Reg(init = s_idle)

  switch (state) {
    is (s_idle) {
      when (!(!io.in) && !debounce_min) {
        debounce := debounce - UInt(1)
      }
      when (!io.in) {
        debounce := debounce + UInt(1)
        when (debounce_max) {
          state := s_data
          start := Bool(true)
          prescaler := prescaler_next
          data_count := UInt(c.dataBits+1)
          sample_count := UInt(c.oversampleFactor - 1)
        }
      }
    }

    is (s_data) {
      prescaler := prescaler_next
      when (pulse) {
        sample := Cat(sample, io.in)
        data_count := countdown >> c.oversample
        sample_count := countdown(c.oversample-1, 0)

        when (sample_mid) {
          when (data_last) {
            state := s_idle
            valid := Bool(true)
          } .otherwise {
            shifter := Cat(voter, shifter >> 1)
          }
        }
      }
    }
  }

  when (!io.en) {
    debounce := UInt(0)
  }
}

class UARTInterrupts extends Bundle {
  val rxwm = Bool()
  val txwm = Bool()
}

abstract class UART(busWidthBytes: Int, baseAddr: BigInt, val c: UARTParams, divisorInit: Int)(implicit p: Parameters)
    extends PeripheralPuncher(
      devParams = PeripheralPuncherParams(
        name = "serial",
        compat = Seq("sifive,uart0"), 
        base = baseAddr,
        beatBytes = busWidthBytes))(
      portBundle = new UARTPortIO)
    with HasCrossableInterrupts {

  override def nInterrupts = 1

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

case class AttachedUARTParams(uart: UARTParams, baseAddress: BigInt, control: ClockCrossingType, int: ClockCrossingType, divinit: Int)

object UART {
  def attach(name: String, params: AttachedUARTParams, controlBus: TLBusWrapper, intNode: IntInwardNode, mclock: ModuleValue[Clock])
            (implicit p: Parameters): TLUART = {
    val uart = LazyModule(new TLUART(controlBus.beatBytes, params.baseAddress, params.uart, params.divinit)).suggestName(name)

    controlBus.coupleTo(s"slave_named_$name") {
      uart.crossControl(params.control) := TLFragmenter(controlBus.beatBytes, controlBus.blockBytes)
    }
    intNode := uart.crossInt(params.int)
    InModuleBody { uart.module.clock := mclock }

    uart
  }

  def tieoff(uart: UARTPortIO) {
    uart.rxd := UInt(1)
  }
}

class TLUART(busWidthBytes: Int, baseAddress: BigInt, params: UARTParams, divinit: Int)(implicit p: Parameters)
  extends UART(busWidthBytes, baseAddress, params, divinit)
  with HasCrossableTLControlRegMap
