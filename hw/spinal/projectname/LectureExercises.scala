package projectname

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.sim._

import scala.collection.mutable
import scala.util.Random

sealed trait StudentGender

object StudentGender {
  case object Girl extends StudentGender
  case object Boy  extends StudentGender
}

case class Student(name: String, gender: StudentGender)
case class PairingResult(pairs: Seq[(Student, Student)], unmatchedGirls: Seq[Student], unmatchedBoys: Seq[Student])

sealed trait PairingRule {
  def arrange(students: Seq[Student]): Seq[Student]
}

object KeepInputOrder extends PairingRule {
  override def arrange(students: Seq[Student]): Seq[Student] = students
}

object NameOrder extends PairingRule {
  override def arrange(students: Seq[Student]): Seq[Student] = students.sortBy(_.name)
}

object StudentPairingPractice {
  def pairPeople(people: Seq[Student], rule: PairingRule = KeepInputOrder): PairingResult = {
    val girls = rule.arrange(people.filter(_.gender == StudentGender.Girl))
    val boys  = rule.arrange(people.filter(_.gender == StudentGender.Boy))
    val pairs = girls.zip(boys)

    PairingResult(
      pairs = pairs,
      unmatchedGirls = girls.drop(pairs.length),
      unmatchedBoys = boys.drop(pairs.length)
    )
  }
}

object StudentPairingPracticeApp extends App {
  val people = Seq(
    Student("Alice", StudentGender.Girl),
    Student("Bob", StudentGender.Boy),
    Student("Cindy", StudentGender.Girl),
    Student("David", StudentGender.Boy),
    Student("Eve", StudentGender.Girl)
  )

  val result = StudentPairingPractice.pairPeople(people, NameOrder)
  println("Student pairing result:")
  result.pairs.foreach { case (girl, boy) => println(s"  ${girl.name} <-> ${boy.name}") }
  println("Unmatched girls: " + result.unmatchedGirls.map(_.name).mkString(", "))
  println("Unmatched boys: " + result.unmatchedBoys.map(_.name).mkString(", "))
}

case class ConfigurableCounter(width: Int, maxValue: BigInt) extends Component {
  require(width > 0, "width must be positive")
  require(maxValue >= 0, "maxValue must be non-negative")
  require(maxValue < (BigInt(1) << width), "maxValue must fit in width bits")

  val io = new Bundle {
    val enable   = in Bool()
    val value    = out UInt (width bits)
    val overflow = out Bool()
  }

  val counter  = Reg(UInt(width bits)) init 0
  val overflow = Reg(Bool()) init False

  overflow := False
  when(io.enable) {
    when(counter === U(maxValue, width bits)) {
      counter  := 0
      overflow := True
    } otherwise {
      counter := counter + 1
    }
  }

  io.value    := counter
  io.overflow := overflow
}

case class StreamBackPressureCounter(width: Int, emitEvery: Int = 1) extends Component {
  require(width > 0, "width must be positive")
  require(emitEvery > 0, "emitEvery must be positive")

  val io = new Bundle {
    val enable = in Bool()
    val value  = master(Stream(UInt(width bits)))
  }

  val counter = Reg(UInt(width bits)) init 0
  val pending = Reg(Bool()) init False
  val payload = Reg(UInt(width bits)) init 0

  io.value.valid   := pending
  io.value.payload := payload

  when(io.value.fire) {
    pending := False
  }

  val canCount = io.enable && (!pending || io.value.fire)

  if (emitEvery == 1) {
    when(canCount) {
      counter := counter + 1
      payload := counter + 1
      pending := True
    }
  } else {
    val emitIndex = Reg(UInt(log2Up(emitEvery) bits)) init 0

    when(canCount) {
      counter := counter + 1
      when(emitIndex === U(emitEvery - 1, log2Up(emitEvery) bits)) {
        emitIndex := 0
        payload   := counter + 1
        pending   := True
      } otherwise {
        emitIndex := emitIndex + 1
      }
    }
  }
}

case class StreamJoinForkProcessor() extends Component {
  val io = new Bundle {
    val cmdA   = slave(Stream(UInt(32 bits)))
    val cmdB   = slave(Stream(UInt(32 bits)))
    val rspMul = master(Stream(UInt(64 bits)))
    val rspXor = master(Stream(UInt(32 bits)))
  }

  val mulPayload = Reg(UInt(64 bits)) init 0
  val xorPayload = Reg(UInt(32 bits)) init 0
  val mulPending = Reg(Bool()) init False
  val xorPending = Reg(Bool()) init False

  val responseBusy = mulPending || xorPending
  val acceptPair   = !responseBusy && io.cmdA.valid && io.cmdB.valid

  io.cmdA.ready := acceptPair
  io.cmdB.ready := acceptPair

  io.rspMul.valid   := mulPending
  io.rspMul.payload := mulPayload
  io.rspXor.valid   := xorPending
  io.rspXor.payload := xorPayload

  when(acceptPair) {
    mulPayload := (io.cmdA.payload * io.cmdB.payload).resized
    xorPayload := (io.cmdA.payload.asBits ^ io.cmdB.payload.asBits).asUInt
    mulPending := True
    xorPending := True
  }

  when(io.rspMul.fire) {
    mulPending := False
  }

  when(io.rspXor.fire) {
    xorPending := False
  }
}

object LectureExercisesVerilog extends App {
  Config.spinal.generateVerilog(ConfigurableCounter(width = 8, maxValue = 9))
  Config.spinal.generateVerilog(StreamBackPressureCounter(width = 8, emitEvery = 4))
  Config.spinal.generateVerilog(StreamJoinForkProcessor())
}

object ConfigurableCounterVerilog extends App {
  Config.spinal.generateVerilog(ConfigurableCounter(width = 8, maxValue = 9))
}

object StreamBackPressureCounterVerilog extends App {
  Config.spinal.generateVerilog(StreamBackPressureCounter(width = 8, emitEvery = 4))
}

object StreamJoinForkProcessorVerilog extends App {
  Config.spinal.generateVerilog(StreamJoinForkProcessor())
}

object LectureExercisesSoftwareCheck extends App {
  val pairingPeople = Seq(
    Student("Alice", StudentGender.Girl),
    Student("Bob", StudentGender.Boy),
    Student("Cindy", StudentGender.Girl),
    Student("David", StudentGender.Boy),
    Student("Eve", StudentGender.Girl)
  )
  val pairingResult = StudentPairingPractice.pairPeople(pairingPeople, NameOrder)
  assert(pairingResult.pairs.map { case (girl, boy) => girl.name -> boy.name } == Seq("Alice" -> "Bob", "Cindy" -> "David"))
  assert(pairingResult.unmatchedGirls.map(_.name) == Seq("Eve"))
  assert(pairingResult.unmatchedBoys.isEmpty)

  def runCounter(width: Int, maxValue: BigInt, enables: Seq[Boolean]): Seq[(BigInt, Boolean)] = {
    val mask = (BigInt(1) << width) - 1
    var value = BigInt(0)

    enables.map { enable =>
      var overflow = false
      if (enable) {
        if (value == maxValue) {
          value = 0
          overflow = true
        } else {
          value = (value + 1) & mask
        }
      }
      (value, overflow)
    }
  }

  val counterTrace = runCounter(
    width = 4,
    maxValue = 9,
    enables = Seq.fill(12)(true) ++ Seq(false, true, true)
  )
  assert(counterTrace(8) == (BigInt(9), false))
  assert(counterTrace(9) == (BigInt(0), true))
  assert(counterTrace(12) == (BigInt(2), false))

  val pairs = Seq(
    3L          -> 5L,
    0xffffffffL -> 2L,
    0x12345678L -> 0x87654321L,
    42L         -> 99L
  )
  val streamResults = pairs.map { case (a, b) =>
    val mul = BigInt(a) * BigInt(b)
    val xor = (a ^ b) & 0xffffffffL
    (mul, xor)
  }

  assert(streamResults.head == (BigInt(15), 6L))
  assert(streamResults(1)._1 == BigInt("1fffffffe", 16))

  println("Lecture exercise software checks passed.")
  println(s"Student pairs: ${pairingResult.pairs.map { case (girl, boy) => s"${girl.name}-${boy.name}" }.mkString(", ")}")
  println(s"Unmatched girls: ${pairingResult.unmatchedGirls.map(_.name).mkString(", ")}")
  println(s"Counter final state: value=${counterTrace.last._1}, overflow=${counterTrace.last._2}")
  println("Stream join/fork sample results:")
  streamResults.foreach { case (mul, xor) =>
    println(f"  mul=0x$mul%x xor=0x$xor%08x")
  }
}

object ConfigurableCounterSim extends App {
  Config.sim.compile(ConfigurableCounter(width = 4, maxValue = 9)).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)
    dut.io.enable #= false
    dut.clockDomain.waitSampling()

    var modelValue = BigInt(0)
    var modelOverflow = false
    for (cycle <- 0 until 40) {
      val enable = cycle % 5 != 3
      dut.io.enable #= enable
      dut.clockDomain.waitSampling()

      assert(dut.io.value.toBigInt == modelValue)
      assert(dut.io.overflow.toBoolean == modelOverflow)

      modelOverflow = false
      if (enable) {
        if (modelValue == 9) {
          modelValue = 0
          modelOverflow = true
        } else {
          modelValue += 1
        }
      }
    }

    println("ConfigurableCounterSim PASS")
  }
}

object StreamBackPressureCounterSim extends App {
  Config.sim.compile(StreamBackPressureCounter(width = 8, emitEvery = 4)).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)
    dut.io.enable #= false
    StreamReadyRandomizer(dut.io.value, dut.clockDomain).setFactor(0.45f)

    val scoreboard = ScoreboardInOrder[BigInt]()
    var acceptedEnables = 0

    StreamMonitor(dut.io.value, dut.clockDomain) { payload =>
      scoreboard.pushDut(payload.toBigInt)
    }

    dut.clockDomain.onSamplings {
      val stalled = dut.io.value.valid.toBoolean && !dut.io.value.ready.toBoolean
      if (dut.io.enable.toBoolean && !stalled) {
        acceptedEnables += 1
        if (acceptedEnables % 4 == 0) {
          scoreboard.pushRef(BigInt(acceptedEnables & 0xff))
        }
      }
    }

    val random = new Random(0)
    for (_ <- 0 until 300) {
      dut.io.enable #= random.nextBoolean()
      dut.clockDomain.waitSampling()
    }

    dut.io.enable #= false
    dut.clockDomain.waitSampling(20)
    scoreboard.checkEmptyness()

    println("StreamBackPressureCounterSim PASS")
  }
}

object SimStreamJoinForkTester extends App {
  Config.sim.compile(StreamJoinForkProcessor()).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)

    val cmdAValues = mutable.Queue[BigInt]()
    val cmdBValues = mutable.Queue[BigInt]()
    val mulScoreboard = ScoreboardInOrder[BigInt]()
    val xorScoreboard = ScoreboardInOrder[BigInt]()

    val mask32 = (BigInt(1) << 32) - 1
    val random = new Random(1)
    for (_ <- 0 until 64) {
      val a = BigInt(32, random)
      val b = BigInt(32, random)
      cmdAValues.enqueue(a)
      cmdBValues.enqueue(b)
      mulScoreboard.pushRef(a * b)
      xorScoreboard.pushRef((a ^ b) & mask32)
    }

    StreamDriver(dut.io.cmdA, dut.clockDomain) { payload =>
      if (cmdAValues.nonEmpty) {
        payload #= cmdAValues.dequeue()
        true
      } else {
        false
      }
    }.setFactor(0.7f)

    StreamDriver(dut.io.cmdB, dut.clockDomain) { payload =>
      if (cmdBValues.nonEmpty) {
        payload #= cmdBValues.dequeue()
        true
      } else {
        false
      }
    }.setFactor(0.55f)

    StreamReadyRandomizer(dut.io.rspMul, dut.clockDomain).setFactor(0.6f)
    StreamReadyRandomizer(dut.io.rspXor, dut.clockDomain).setFactor(0.5f)

    StreamMonitor(dut.io.rspMul, dut.clockDomain) { payload =>
      mulScoreboard.pushDut(payload.toBigInt)
    }

    StreamMonitor(dut.io.rspXor, dut.clockDomain) { payload =>
      xorScoreboard.pushDut(payload.toBigInt)
    }

    dut.clockDomain.waitSampling(1000)
    mulScoreboard.checkEmptyness()
    xorScoreboard.checkEmptyness()

    println("SimStreamJoinForkTester PASS")
  }
}
