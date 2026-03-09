package projectname

import spinal.core._
import spinal.core.sim._

case class MyCounter(width: Int) extends Component {
  require(width > 0, "Counter width must be positive")

  val io = new Bundle {
    val enable = in Bool()
    val state = out UInt (width bits)
    val overflow = out Bool()
  }

  val counter = Reg(UInt(width bits)) init 0
  val overflow = Reg(Bool()) init False

  overflow := False
  when(io.enable) {
    when(counter === counter.maxValue) {
      counter := 0
      overflow := True
    } otherwise {
      counter := counter + 1
    }
  }

  io.state := counter
  io.overflow := overflow
}

object MyCounterVerilog extends App {
  Config.spinal.generateVerilog(MyCounter(4))
}

object MyCounterTester extends App {
  val width = 4
  val max = (1 << width) - 1

  Config.sim.compile(MyCounter(width)).doSim("ConfigurableCounterTest") { dut =>
    dut.clockDomain.forkStimulus(10)
    dut.io.enable #= false

    var expected = 0
    var expectedOverflow = false

    def checkAndAdvance(enable: Boolean): Unit = {
      dut.io.enable #= enable
      dut.clockDomain.waitSampling()
      assert(dut.io.state.toInt == expected, s"state=${dut.io.state.toInt}, expected=$expected")
      assert(
        dut.io.overflow.toBoolean == expectedOverflow,
        s"overflow=${dut.io.overflow.toBoolean}, expected=$expectedOverflow"
      )

      expectedOverflow = false
      if (enable) {
        if (expected == max) {
          expected = 0
          expectedOverflow = true
        } else {
          expected += 1
        }
      }
    }

    for (_ <- 0 until 8) checkAndAdvance(enable = false)
    for (_ <- 0 until 20) checkAndAdvance(enable = true)
    for (_ <- 0 until 8) checkAndAdvance(enable = false)

    println("MyCounterTester PASS")
  }
}

object MyCounterRandomTester extends App {
  val width = 4
  val max = (1 << width) - 1

  Config.sim.compile(MyCounter(width)).doSim("ConfigurableCounterRandomTest") { dut =>
    dut.clockDomain.forkStimulus(10)
    dut.io.enable #= false

    var expected = 0
    var expectedOverflow = false

    for (_ <- 0 until 1000) {
      dut.clockDomain.waitSampling()
      assert(dut.io.state.toInt == expected, s"state=${dut.io.state.toInt}, expected=$expected")
      assert(
        dut.io.overflow.toBoolean == expectedOverflow,
        s"overflow=${dut.io.overflow.toBoolean}, expected=$expectedOverflow"
      )

      val enable = dut.io.enable.toBoolean
      expectedOverflow = false
      if (enable) {
        if (expected == max) {
          expected = 0
          expectedOverflow = true
        } else {
          expected += 1
        }
      }

      dut.io.enable.randomize()
    }

    println("MyCounterRandomTester PASS")
  }
}
