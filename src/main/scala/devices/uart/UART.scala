// See LICENSE for license details.
package sifive.blocks.devices.uart

import Chisel._
import config._
import regmapper._
import uncore.tilelink2._
import junctions._
import util._
import rocketchip.PeripheryBusConfig
import sifive.blocks.util.{NonBlockingEnqueue, NonBlockingDequeue}

case class UARTConfig(
  address: BigInt,
  dataBits: Int = 8,
  stopBits: Int = 2,
  divisorBits: Int = 16,
  oversample: Int = 4,
  nSamples: Int = 3,
  nTxEntries: Int = 8,
  nRxEntries: Int = 8)

trait HasUARTParameters {
  val c: UARTConfig
  val uartDataBits = c.dataBits
  val uartStopBits = c.stopBits
  val uartDivisorBits = c.divisorBits

  val uartOversample = c.oversample
  val uartOversampleFactor = 1 << uartOversample
  val uartNSamples = c.nSamples

  val uartNTxEntries = c.nTxEntries
  val uartNRxEntries = c.nRxEntries

  require(uartDivisorBits > uartOversample)
  require(uartOversampleFactor > uartNSamples)
}

abstract class UARTModule(val c: UARTConfig)(implicit val p: Parameters)
    extends Module with HasUARTParameters

class UARTPortIO extends Bundle {
  val txd = Bool(OUTPUT)
  val rxd = Bool(INPUT)
}

trait MixUARTParameters {
  val params: (UARTConfig, Parameters)
  val c = params._1
  implicit val p = params._2
}

trait UARTTopBundle extends Bundle with MixUARTParameters with HasUARTParameters {
  val port = new UARTPortIO
}

class UARTTx(c: UARTConfig)(implicit p: Parameters) extends UARTModule(c)(p) {
  val io = new Bundle {
    val en = Bool(INPUT)
    val in = Decoupled(Bits(width = uartDataBits)).flip
    val out = Bits(OUTPUT, 1)
    val div = UInt(INPUT, uartDivisorBits)
    val nstop = UInt(INPUT, log2Up(uartStopBits))
  }

  val prescaler = Reg(init = UInt(0, uartDivisorBits))
  val pulse = (prescaler === UInt(0))

  private val n = uartDataBits + 1
  val counter = Reg(init = UInt(0, log2Floor(n + uartStopBits) + 1))
  val shifter = Reg(Bits(width = n))
  val out = Reg(init = Bits(1, 1))
  io.out := out

  val busy = (counter =/= UInt(0))
  io.in.ready := io.en && !busy
  when (io.in.fire()) {
    printf("%c", io.in.bits)
    shifter := Cat(io.in.bits, Bits(0, 1))
    counter := Mux1H((0 until uartStopBits).map(i =>
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

class UARTRx(c: UARTConfig)(implicit p: Parameters) extends UARTModule(c)(p) {
  val io = new Bundle {
    val en = Bool(INPUT)
    val in = Bits(INPUT, 1)
    val out = Valid(Bits(width = uartDataBits))
    val div = UInt(INPUT, uartDivisorBits)
  }

  val debounce = Reg(init = UInt(0, 2))
  val debounce_max = (debounce === UInt(3))
  val debounce_min = (debounce === UInt(0))

  val prescaler = Reg(init = UInt(0, uartDivisorBits - uartOversample))
  val start = Wire(init = Bool(false))
  val busy = Wire(init = Bool(false))
  val pulse = (prescaler === UInt(0)) && busy

  when (busy) {
    prescaler := prescaler - UInt(1)
  }
  when (start || pulse) {
    prescaler := io.div >> uartOversample
  }

  val sample = Reg(Bits(width = uartNSamples))
  val voter = new Majority(sample.toBools.toSet)
  when (pulse) {
    sample := Cat(sample, io.in)
  }

  private val delay0 = (uartOversampleFactor + uartNSamples) >> 1
  private val delay1 = uartOversampleFactor

  val timer = Reg(UInt(width = uartOversample + 1))
  val counter = Reg(UInt(width = log2Floor(uartDataBits) + 1))
  val shifter = Reg(Bits(width = uartDataBits))
  val expire = (timer === UInt(0)) && pulse

  val sched = Wire(init = Bool(false))
  when (pulse) {
    timer := timer - UInt(1)
  }
  when (sched) {
    timer := UInt(delay1-1)
  }

  val valid = Reg(init = Bool(false))
  valid := Bool(false)
  io.out.valid := valid
  io.out.bits := shifter

  val (s_idle :: s_start :: s_data :: Nil) = Enum(UInt(), 3)
  val state = Reg(init = s_idle)

  switch (state) {
    is (s_idle) {
      when (!(!io.in) && !debounce_min) {
        debounce := debounce - UInt(1)
      }
      when (!io.in) {
        debounce := debounce + UInt(1)
        when (debounce_max) {
          state := s_start
          start := Bool(true)
          timer := UInt(delay0-1)
        }
      }
    }

    is (s_start) {
      busy := Bool(true)
      when (expire) {
        sched := Bool(true)
        when (voter.out) {
          state := s_idle
        } .otherwise {
          state := s_data
          counter := UInt(uartDataBits)
        }
      }
    }

    is (s_data) {
      busy := Bool(true)
      when (expire) {
        counter := counter - UInt(1)
        when (counter === UInt(0)) {
          state := s_idle
          valid := Bool(true)
        } .otherwise {
          shifter := Cat(voter.out, shifter >> 1)
          sched := Bool(true)
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

trait UARTTopModule extends Module with MixUARTParameters with HasUARTParameters with HasRegMap {
  val io: UARTTopBundle

  val txm = Module(new UARTTx(c))
  val txq = Module(new Queue(txm.io.in.bits, uartNTxEntries))

  val rxm = Module(new UARTRx(c))
  val rxq = Module(new Queue(rxm.io.out.bits, uartNRxEntries))

  val divinit = 542 // (62.5MHz / 115200)
  val div = Reg(init = UInt(divinit, uartDivisorBits))

  private val stopCountBits = log2Up(uartStopBits)
  private val txCountBits = log2Floor(uartNTxEntries) + 1
  private val rxCountBits = log2Floor(uartNRxEntries) + 1

  val txen = Reg(init = Bool(false))
  val rxen = Reg(init = Bool(false))
  val txwm = Reg(init = UInt(0, txCountBits))
  val rxwm = Reg(init = UInt(0, rxCountBits))
  val nstop = Reg(init = UInt(0, stopCountBits))

  txm.io.en := txen
  txm.io.in <> txq.io.deq
  txm.io.div := div
  txm.io.nstop := nstop
  io.port.txd := txm.io.out

  rxm.io.en := rxen
  rxm.io.in := io.port.rxd
  rxq.io.enq <> rxm.io.out
  rxm.io.div := div

  val ie = Reg(init = new UARTInterrupts().fromBits(Bits(0)))
  val ip = Wire(new UARTInterrupts)

  ip.txwm := (txq.io.count < txwm)
  ip.rxwm := (rxq.io.count > rxwm)
  interrupts(0) := (ip.txwm && ie.txwm) || (ip.rxwm && ie.rxwm)

  regmap(
    UARTCtrlRegs.txfifo -> NonBlockingEnqueue(txq.io.enq),
    UARTCtrlRegs.rxfifo -> NonBlockingDequeue(rxq.io.deq),

    UARTCtrlRegs.txctrl -> Seq(
      RegField(1, txen),
      RegField(stopCountBits, nstop)),
    UARTCtrlRegs.rxctrl -> Seq(RegField(1, rxen)),
    UARTCtrlRegs.txmark -> Seq(RegField(txCountBits, txwm)),
    UARTCtrlRegs.rxmark -> Seq(RegField(rxCountBits, rxwm)),

    UARTCtrlRegs.ie -> Seq(
      RegField(1, ie.txwm),
      RegField(1, ie.rxwm)),

    UARTCtrlRegs.ip -> Seq(
      RegField.r(1, ip.txwm),
      RegField.r(1, ip.rxwm)),

    UARTCtrlRegs.div -> Seq(
      RegField(uartDivisorBits, div))
  )
}

class Majority(in: Set[Bool]) {
  private val n = (in.size >> 1) + 1
  private val clauses = in.subsets(n).map(_.reduce(_ && _))
  val out = clauses.reduce(_ || _)
}

// Magic TL2 Incantation to create a TL2 Slave
class UART(c: UARTConfig)(implicit val p: Parameters)
  extends TLRegisterRouter(c.address, interrupts = 1, beatBytes = p(PeripheryBusConfig).beatBytes)(
  new TLRegBundle((c, p), _)    with UARTTopBundle)(
  new TLRegModule((c, p), _, _) with UARTTopModule)
