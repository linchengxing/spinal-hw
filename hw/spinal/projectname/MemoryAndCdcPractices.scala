package projectname

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.sim._

import scala.collection.mutable

case class RamWriteCmd(addressWidth: Int, dataWidth: Int) extends Bundle {
  val address = UInt(addressWidth bits)
  val data = UInt(dataWidth bits)
}

case class StreamRam(depth: Int = 16, dataWidth: Int = 32) extends Component {
  require(depth > 1, "RAM depth must be greater than one")
  require(dataWidth > 0, "RAM data width must be positive")

  val addressWidth = log2Up(depth)

  val io = new Bundle {
    val write = slave Stream (RamWriteCmd(addressWidth, dataWidth))
    val readAddress = slave Stream (UInt(addressWidth bits))
    val readData = master Stream (UInt(dataWidth bits))
  }

  val mem = Mem(UInt(dataWidth bits), depth)

  io.write.ready := True
  mem.write(
    address = io.write.address,
    data = io.write.data,
    enable = io.write.fire
  )

  val readValid = Reg(Bool()) init False
  val readPayload = mem.readSync(
    address = io.readAddress.payload,
    enable = io.readAddress.fire
  )

  io.readAddress.ready := !readValid || io.readData.fire
  io.readData.valid := readValid
  io.readData.payload := readPayload

  when(io.readData.fire && !io.readAddress.fire) {
    readValid := False
  }
  when(io.readAddress.fire) {
    readValid := True
  }
}

object StreamRamVerilog extends App {
  Config.spinal.generateVerilog(StreamRam(depth = 16, dataWidth = 32))
}

object StreamRamTester extends App {
  val content = (0 until 16).map(address => BigInt(address * 17 + 3))

  Config.sim.compile(StreamRam(depth = 16, dataWidth = 32)).doSim("StreamRamTester") { dut =>
    dut.clockDomain.forkStimulus(10)

    dut.io.write.valid #= false
    dut.io.readAddress.valid #= false
    dut.io.readData.ready #= true
    dut.clockDomain.waitSampling(4)

    content.zipWithIndex.foreach { case (data, address) =>
      dut.io.write.valid #= true
      dut.io.write.address #= address
      dut.io.write.data #= data
      dut.clockDomain.waitSampling()
      assert(dut.io.write.ready.toBoolean)
    }
    dut.io.write.valid #= false
    dut.clockDomain.waitSampling(2)

    val scoreboard = ScoreboardInOrder[BigInt]()
    content.foreach(scoreboard.pushRef)
    StreamMonitor(dut.io.readData, dut.clockDomain) { payload =>
      scoreboard.pushDut(payload.toBigInt)
      scoreboard.check()
    }

    content.indices.foreach { address =>
      dut.io.readAddress.valid #= true
      dut.io.readAddress.payload #= address
      dut.clockDomain.waitSampling()
      assert(dut.io.readAddress.ready.toBoolean)
    }
    dut.io.readAddress.valid #= false

    var cycles = 0
    while (scoreboard.matches < content.length) {
      dut.clockDomain.waitSampling()
      cycles += 1
      assert(cycles < 2000, "StreamRamTester timeout")
    }
    scoreboard.checkEmptyness()

    println("StreamRamTester PASS")
  }
}

case class StreamJoinForkCdc(outputClock: ClockDomain, fifoDepth: Int = 16) extends Component {
  val io = new Bundle {
    val cmdA = slave Stream (UInt(32 bits))
    val cmdB = slave Stream (UInt(32 bits))
    val rspMul = master Stream (UInt(64 bits))
    val rspXor = master Stream (UInt(32 bits))
  }

  val core = StreamJoinFork()
  core.io.cmdA << io.cmdA
  core.io.cmdB << io.cmdB

  val mulFifo = StreamFifoCC(
    dataType = UInt(64 bits),
    depth = fifoDepth,
    pushClock = ClockDomain.current,
    popClock = outputClock
  )
  val xorFifo = StreamFifoCC(
    dataType = UInt(32 bits),
    depth = fifoDepth,
    pushClock = ClockDomain.current,
    popClock = outputClock
  )

  mulFifo.io.push << core.io.rspMul
  xorFifo.io.push << core.io.rspXor

  val outputArea = new ClockingArea(outputClock) {
    io.rspMul << mulFifo.io.pop
    io.rspXor << xorFifo.io.pop
  }
}

case class StreamJoinForkCdcTop() extends Component {
  val clockA = in Bool()
  val resetA = in Bool()
  val clockB = in Bool()
  val resetB = in Bool()

  val clockDomainA = ClockDomain(
    clock = clockA,
    reset = resetA,
    config = ClockDomainConfig(resetActiveLevel = HIGH)
  )
  val clockDomainB = ClockDomain(
    clock = clockB,
    reset = resetB,
    config = ClockDomainConfig(resetActiveLevel = HIGH)
  )

  val io = new Bundle {
    val cmdA = slave Stream (UInt(32 bits))
    val cmdB = slave Stream (UInt(32 bits))
    val rspMul = master Stream (UInt(64 bits))
    val rspXor = master Stream (UInt(32 bits))
  }

  val inputArea = new ClockingArea(clockDomainA) {
    val cdc = StreamJoinForkCdc(outputClock = clockDomainB)
    cdc.io.cmdA << io.cmdA
    cdc.io.cmdB << io.cmdB
  }

  val outputArea = new ClockingArea(clockDomainB) {
    io.rspMul << inputArea.cdc.io.rspMul
    io.rspXor << inputArea.cdc.io.rspXor
  }
}

object StreamJoinForkCdcVerilog extends App {
  Config.spinal.generateVerilog(StreamJoinForkCdcTop())
}

object StreamJoinForkCdcTester extends App {
  val samples = Seq(
    BigInt(7) -> BigInt(9),
    BigInt(11) -> BigInt(13),
    BigInt("00010001", 16) -> BigInt("01000001", 16),
    BigInt("ffffffff", 16) -> BigInt(3)
  )

  Config.sim.compile(StreamJoinForkCdcTop()).doSim("StreamJoinForkCdcTester") { dut =>
    val clockDomainA = ClockDomain(
      clock = dut.clockA,
      reset = dut.resetA,
      config = ClockDomainConfig(resetActiveLevel = HIGH)
    )
    val clockDomainB = ClockDomain(
      clock = dut.clockB,
      reset = dut.resetB,
      config = ClockDomainConfig(resetActiveLevel = HIGH)
    )

    clockDomainA.forkStimulus(10)
    clockDomainB.forkStimulus(11)
    clockDomainA.assertReset()
    clockDomainB.assertReset()
    sleep(80)
    clockDomainA.deassertReset()
    clockDomainB.deassertReset()

    val queueA = mutable.Queue(samples.map(_._1): _*)
    val queueB = mutable.Queue(samples.map(_._2): _*)
    val mulScoreboard = ScoreboardInOrder[BigInt]()
    val xorScoreboard = ScoreboardInOrder[BigInt]()

    samples.foreach { case (a, b) =>
      mulScoreboard.pushRef((a * b) & ((BigInt(1) << 64) - 1))
      xorScoreboard.pushRef((a ^ b) & ((BigInt(1) << 32) - 1))
    }

    StreamDriver(dut.io.cmdA, clockDomainA) { payload =>
      if (queueA.nonEmpty) {
        payload #= queueA.dequeue()
        true
      } else {
        false
      }
    }
    StreamDriver(dut.io.cmdB, clockDomainA) { payload =>
      if (queueB.nonEmpty) {
        payload #= queueB.dequeue()
        true
      } else {
        false
      }
    }

    StreamReadyRandomizer(dut.io.rspMul, clockDomainB)
    StreamReadyRandomizer(dut.io.rspXor, clockDomainB)
    StreamMonitor(dut.io.rspMul, clockDomainB) { payload =>
      mulScoreboard.pushDut(payload.toBigInt)
      mulScoreboard.check()
    }
    StreamMonitor(dut.io.rspXor, clockDomainB) { payload =>
      xorScoreboard.pushDut(payload.toBigInt)
      xorScoreboard.check()
    }

    var cycles = 0
    while (mulScoreboard.matches < samples.length || xorScoreboard.matches < samples.length) {
      clockDomainA.waitSampling()
      cycles += 1
      assert(cycles < 4000, "StreamJoinForkCdcTester timeout")
    }
    mulScoreboard.checkEmptyness()
    xorScoreboard.checkEmptyness()

    println("StreamJoinForkCdcTester PASS")
  }
}
