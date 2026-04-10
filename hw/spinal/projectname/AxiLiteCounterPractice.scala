package projectname

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba4.axilite._
import spinal.lib.bus.amba4.axilite.sim._

case class AxiLiteCounterBank(counterCount: Int = 4, counterWidth: Int = 32) extends Component {
  require(counterCount > 0, "At least one counter is required")
  require(counterWidth > 0 && counterWidth <= 32, "Counter width must be in 1..32")

  val busConfig = AxiLite4Config(
    addressWidth = 12,
    dataWidth = 32
  )

  val io = new Bundle {
    val bus = slave(AxiLite4(busConfig))
  }

  val factory = AxiLite4SlaveFactory(io.bus)

  for (index <- 0 until counterCount) {
    val base = BigInt(index) * 0x10
    val enable = Reg(Bool()) init False
    val counter = Reg(UInt(counterWidth bits)) init 0
    val overflow = Reg(Bool()) init False

    overflow := False
    when(enable) {
      when(counter === counter.maxValue) {
        counter := 0
        overflow := True
      } otherwise {
        counter := counter + 1
      }
    }

    factory.driveAndRead(enable, base + 0x00, bitOffset = 0)
    factory.read(counter.resized, base + 0x04)
    factory.read(overflow, base + 0x08, bitOffset = 0)
    factory.read(U(counterWidth, 32 bits), base + 0x0c)
  }
}

object AxiLiteCounterBankVerilog extends App {
  Config.spinal.generateVerilog(AxiLiteCounterBank(counterCount = 4, counterWidth = 8))
}

object AxiLiteCounterBankTester extends App {
  Config.sim.compile(AxiLiteCounterBank(counterCount = 2, counterWidth = 8)).doSim("AxiLiteCounterBankTester") { dut =>
    dut.clockDomain.forkStimulus(10)

    val driver = AxiLite4Driver(dut.io.bus, dut.clockDomain)
    driver.reset()
    dut.clockDomain.waitSampling(4)

    assert(driver.read(BigInt(0x04)) == 0)
    assert(driver.read(BigInt(0x14)) == 0)

    driver.write(BigInt(0x00), BigInt(1))
    dut.clockDomain.waitSampling(12)
    val counter0 = driver.read(BigInt(0x04))
    val counter1StillZero = driver.read(BigInt(0x14))
    assert(counter0 > 0, s"counter0 did not run, state=$counter0")
    assert(counter1StillZero == 0, s"counter1 changed unexpectedly, state=$counter1StillZero")

    driver.write(BigInt(0x10), BigInt(1))
    dut.clockDomain.waitSampling(8)
    val counter0After = driver.read(BigInt(0x04))
    val counter1After = driver.read(BigInt(0x14))
    assert(counter0After > counter0, s"counter0 stopped unexpectedly, before=$counter0 after=$counter0After")
    assert(counter1After > 0, s"counter1 did not run, state=$counter1After")

    driver.write(BigInt(0x00), BigInt(0))
    val frozen = driver.read(BigInt(0x04))
    dut.clockDomain.waitSampling(8)
    assert(driver.read(BigInt(0x04)) == frozen, "counter0 did not stop after clearing enable")

    assert(driver.read(BigInt(0x0c)) == 8)
    assert(driver.read(BigInt(0x1c)) == 8)

    println("AxiLiteCounterBankTester PASS")
  }
}
