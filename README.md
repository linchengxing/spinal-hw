# SpinalHDL 课件实践作业

本工程已经根据 `lecture_pdf/` 中的课件补齐实践作业代码。源码位于 `hw/spinal/projectname/`，生成的 Verilog 位于 `hw/gen/`。

## 进入 MSYS2 环境

在任意 Windows 终端中先进入 `C:\msys64`，再启动 MINGW64 环境：

```bat
cd /d C:\msys64
msys2_shell.cmd -defterm -no-start -mingw64
```

进入 MSYS2 后进入项目目录：

```sh
cd ~/workspace/SpinalTemplateSbt
```

如果当前 shell 中找不到 `sbt` 或 `java`，补一下 PATH：

```sh
export PATH=/e/Java/bin:/sdkman/candidates/sbt/current/bin:$PATH
```

检查工具链：

```sh
which sbt
which java
which iverilog
```

## 编译

```sh
sbt compile
```

如果编译时出现 `Error downloading ch.epfl.scala:sbt-bloop:2.0.19`，说明本地 Metals/Bloop 生成的 SBT 辅助文件被普通 `sbt compile` 读到了。它们不是工程源码，可以删除后重试：

```sh
rm -rf project/metals.sbt project/project project/.bloop
sbt compile
```

## 一次运行所有作业测试

```sh
sbt \
  'runMain projectname.ScalaPairingPractice' \
  'runMain projectname.MyCounterTester' \
  'runMain projectname.MyCounterRandomTester' \
  'runMain projectname.StreamCounterTester' \
  'runMain projectname.SimStreamJoinForkPracticeTester' \
  'runMain projectname.StreamRamTester' \
  'runMain projectname.StreamJoinForkCdcTester' \
  'runMain projectname.AxiLiteCounterBankTester'
```

每个测试成功时会打印对应的 `PASS`。仿真使用 Icarus Verilog，波形文件生成在 `simWorkspace/` 下。

## 按作业单独运行

第 2 讲 Scala 配对练习：

```sh
sbt 'runMain projectname.ScalaPairingPractice'
```

第 2 讲可配置计数器：

```sh
sbt 'runMain projectname.MyCounterTester'
sbt 'runMain projectname.MyCounterRandomTester'
```

第 3 讲 Stream 反压计数器：

```sh
sbt 'runMain projectname.StreamCounterTester'
```

第 3 讲 StreamJoin/Fork 流处理组件：

```sh
sbt 'runMain projectname.SimStreamJoinForkPracticeTester'
```

第 4 讲 RAM 流控读写：

```sh
sbt 'runMain projectname.StreamRamTester'
```

第 4 讲跨时钟域 StreamJoin/Fork：

```sh
sbt 'runMain projectname.StreamJoinForkCdcTester'
```

第 5/6 讲 AXI4-Lite 多计数器访问：

```sh
sbt 'runMain projectname.AxiLiteCounterBankTester'
```

## 生成 Verilog

```sh
sbt \
  'runMain projectname.MyCounterVerilog' \
  'runMain projectname.StreamCounterVerilog' \
  'runMain projectname.StreamJoinForkVerilog' \
  'runMain projectname.StreamRamVerilog' \
  'runMain projectname.StreamJoinForkCdcVerilog' \
  'runMain projectname.AxiLiteCounterBankVerilog'
```

生成文件在 `hw/gen/`：

- `MyCounter.v`
- `StreamCounter.v`
- `StreamJoinFork.v`
- `StreamRam.v`
- `StreamJoinForkCdcTop.v`
- `AxiLiteCounterBank.v`

## LectureExercises.scala 兼容入口

`hw/spinal/projectname/LectureExercises.scala` 中也保留了一份浓缩版入口，可以这样运行：

```sh
sbt \
  'runMain projectname.LectureExercisesSoftwareCheck' \
  'runMain projectname.ConfigurableCounterSim' \
  'runMain projectname.StreamBackPressureCounterSim' \
  'runMain projectname.SimStreamJoinForkTester'
```

生成这份浓缩版 Verilog：

```sh
sbt 'runMain projectname.LectureExercisesVerilog'
```
