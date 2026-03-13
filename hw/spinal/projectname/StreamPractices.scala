package projectname

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.sim._

import scala.collection.mutable

case class StreamCounter(width: Int, validEvery: Int = 4) extends Component {
  require(width > 0, "Counter width must be positive")
  require(validEvery > 0, "validEvery must be positive")
  require((validEvery & (validEvery - 1)) == 0, "validEvery must be a power of two")

  val io = new Bundle {
    val enable = in Bool()
    val state = master Stream (UInt(width bits))
  }

  val counter = Reg(UInt(width bits)) init 0
  val emitNow =
    if (validEvery == 1) True
    else counter(log2Up(validEvery) - 1 downto 0) === 0

  io.state.valid := emitNow
  io.state.payload := counter

  when(io.enable && !io.state.isStall) {
    counter := counter + 1
  }
}

object StreamCounterVerilog extends App {
  Config.spinal.generateVerilog(StreamCounter(width = 8, validEvery = 4))
}

object StreamCounterTester extends App {
  Config.sim.compile(StreamCounter(width = 8, validEvery = 4)).doSim("StreamCounterTest") { dut =>
    dut.clockDomain.forkStimulus(10)
    dut.io.enable #= true

    val scoreboard = ScoreboardInOrder[BigInt]()
    var expected = BigInt(0)
    val targetTransactions = 24

    StreamReadyRandomizer(dut.io.state, dut.clockDomain)
    StreamMonitor(dut.io.state, dut.clockDomain) { payload =>
      val got = payload.toBigInt
      scoreboard.pushRef(expected)
      scoreboard.pushDut(got)
      scoreboard.check()
      expected = (expected + 4) & 0xff
    }

    var cycles = 0
    while (scoreboard.matches < targetTransactions) {
      dut.clockDomain.waitSampling()
      cycles += 1
      assert(cycles < 2000, "StreamCounterTester timeout")
    }
    scoreboard.checkEmptyness()

    println("StreamCounterTester PASS")
  }
}

case class JoinForkResult() extends Bundle {
  val mul = UInt(64 bits)
  val xor = UInt(32 bits)
}

case class StreamJoinFork() extends Component {
  val io = new Bundle {
    val cmdA = slave Stream (UInt(32 bits))
    val cmdB = slave Stream (UInt(32 bits))
    val rspMul = master Stream (UInt(64 bits))
    val rspXor = master Stream (UInt(32 bits))
  }

  val joined = StreamJoin(io.cmdA, io.cmdB)
  val processed = Stream(JoinForkResult())
  val processedValid = Reg(Bool()) init False
  val processedPayload = Reg(JoinForkResult())

  processed.valid := processedValid
  processed.payload := processedPayload
  joined.ready := !processedValid || processed.ready

  when(processed.fire && !joined.fire) {
    processedValid := False
  }

  when(joined.fire) {
    processedPayload.mul := joined.payload._1 * joined.payload._2
    processedPayload.xor := joined.payload._1 ^ joined.payload._2
    processedValid := True
  }

  val forked = StreamFork(processed, portCount = 2, synchronous = true)
  io.rspMul << forked(0).translateWith(forked(0).payload.mul)
  io.rspXor << forked(1).translateWith(forked(1).payload.xor)
}

object StreamJoinForkVerilog extends App {
  Config.spinal.generateVerilog(StreamJoinFork())
}

object SimStreamJoinForkPracticeTester extends App {
  val samples = Seq(
    BigInt("00000000", 16) -> BigInt("ffffffff", 16),
    BigInt("00000003", 16) -> BigInt("00000005", 16),
    BigInt("12345678", 16) -> BigInt("01020304", 16),
    BigInt("ffffffff", 16) -> BigInt("00000002", 16),
    BigInt("abcdef01", 16) -> BigInt("11111111", 16),
    BigInt("80000000", 16) -> BigInt("00000004", 16)
  )

  Config.sim.compile(StreamJoinFork()).doSim("SimStreamJoinForkPracticeTester") { dut =>
    dut.clockDomain.forkStimulus(10)

    val queueA = mutable.Queue(samples.map(_._1): _*)
    val queueB = mutable.Queue(samples.map(_._2): _*)
    val mulScoreboard = ScoreboardInOrder[BigInt]()
    val xorScoreboard = ScoreboardInOrder[BigInt]()

    samples.foreach { case (a, b) =>
      mulScoreboard.pushRef((a * b) & ((BigInt(1) << 64) - 1))
      xorScoreboard.pushRef((a ^ b) & ((BigInt(1) << 32) - 1))
    }

    StreamDriver(dut.io.cmdA, dut.clockDomain) { payload =>
      if (queueA.nonEmpty) {
        payload #= queueA.dequeue()
        true
      } else {
        false
      }
    }
    StreamDriver(dut.io.cmdB, dut.clockDomain) { payload =>
      if (queueB.nonEmpty) {
        payload #= queueB.dequeue()
        true
      } else {
        false
      }
    }

    StreamReadyRandomizer(dut.io.rspMul, dut.clockDomain)
    StreamReadyRandomizer(dut.io.rspXor, dut.clockDomain)
    StreamMonitor(dut.io.rspMul, dut.clockDomain) { payload =>
      mulScoreboard.pushDut(payload.toBigInt)
      mulScoreboard.check()
    }
    StreamMonitor(dut.io.rspXor, dut.clockDomain) { payload =>
      xorScoreboard.pushDut(payload.toBigInt)
      xorScoreboard.check()
    }

    var cycles = 0
    while (mulScoreboard.matches < samples.length || xorScoreboard.matches < samples.length) {
      dut.clockDomain.waitSampling()
      cycles += 1
      assert(cycles < 2000, "SimStreamJoinForkPracticeTester timeout")
    }
    mulScoreboard.checkEmptyness()
    xorScoreboard.checkEmptyness()

    println("SimStreamJoinForkPracticeTester PASS")
  }
}
