package myfile

import chisel3._
import _root_.circt.stage.ChiselStage

// ==================== 8 位加法器 ====================
class Adder8bit extends Module {
  val io = IO(new Bundle { val a = Input(UInt(8.W)); val b = Input(UInt(8.W)); val out = Output(UInt(8.W)) })
  io.out := io.a + io.b
}

// ==================== 程序计数器 PC ====================
class PC extends Module {
  val io = IO(new Bundle { val clk = Input(Clock()); val rst = Input(Bool()); val en = Input(Bool()); val jalmux = Input(Bool()); val jal2mux = Input(UInt(4.W)); val pc2romaddr = Output(UInt(4.W)) })
  val pcReg = RegInit(0.U(4.W))
  val nextPc = Mux(io.jalmux, io.jal2mux, pcReg + 1.U(4.W))
  when(io.rst) {
    pcReg := 0.U
  }.elsewhen(io.en) {
    pcReg := nextPc
  }
  io.pc2romaddr := pcReg
}

// ==================== 通用寄存器堆 GRF ====================
class GRF extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock()); val rst = Input(Bool()); val rden = Input(Bool()); val out1en = Input(Bool()); val out2en = Input(Bool());
    val immen = Input(Bool()); val imm = Input(UInt(4.W)); val out1addr = Input(UInt(2.W)); val out2addr = Input(UInt(2.W));
    val inaddr = Input(UInt(2.W)); val din = Input(UInt(8.W)); val out1 = Output(UInt(8.W)); val out2 = Output(UInt(8.W));
    val r0 = Output(UInt(8.W)); val r2 = Output(UInt(8.W))
  })

  val writeData = Mux(io.immen, io.imm, io.din)

  val writeEnable = Wire(Vec(4, Bool()))
  for (i <- 0 until 4) {
    writeEnable(i) := io.rden && (io.inaddr === i.U)
  }

  val regs = RegInit(VecInit(Seq.fill(4)(0.U(8.W))))
  when(io.rst) {
    regs.foreach(_ := 0.U)
  }.otherwise {
    for (i <- 0 until 4) {
      when(writeEnable(i)) {
        regs(i) := writeData
      }
    }
  }

  io.out1 := Mux(io.out1en, regs(io.out1addr), 0.U(8.W))
  io.out2 := Mux(io.out2en, regs(io.out2addr), 0.U(8.W))
  io.r0 := regs(0)
  io.r2 := regs(2)
}

// ==================== 比较器 ====================
class Comparator extends Module {
  val io = IO(new Bundle { val en = Input(Bool()); val r0 = Input(UInt(8.W)); val rs2 = Input(UInt(8.W)); val out2jalmux = Output(Bool()) })
  io.out2jalmux := io.en && (io.r0 =/= io.rs2)
}

// ==================== 指令译码器 IDU ====================
class IDU extends Module {
  val io = IO(new Bundle {
    val instruction = Input(UInt(8.W)); val rden = Output(Bool()); val rs1en = Output(Bool()); val rs2en = Output(Bool());
    val addren = Output(Bool()); val immen = Output(Bool()); val rd = Output(UInt(2.W)); val rs1 = Output(UInt(2.W));
    val rs2 = Output(UInt(2.W)); val addr = Output(UInt(4.W)); val imm = Output(UInt(4.W))
  })
  val op = io.instruction(7,6)
  val isAdd   = op === 0.U(2.W)
  val isLi    = op === 2.U(2.W)
  val isBner0 = op === 3.U(2.W)

  io.rden   := isAdd || isLi
  io.rs1en  := isAdd
  io.rs2en  := isAdd || isBner0
  io.addren := isBner0
  io.immen  := isLi
  io.rd     := io.instruction(5,4)
  io.rs1    := io.instruction(3,2)
  io.rs2    := io.instruction(1,0)
  io.addr   := io.instruction(5,2)
  io.imm    := io.instruction(3,0)
}

// ==================== 指令存储器 ROM ====================
class ROM extends Module {
  val io = IO(new Bundle { val romaddr = Input(UInt(4.W)); val instruction = Output(UInt(8.W)) })
  val romData = VecInit(Seq(
    0x8A.U(8.W), 0x90.U(8.W), 0xA0.U(8.W), 0xB1.U(8.W),
    0x17.U(8.W), 0x29.U(8.W), 0xD1.U(8.W), 0xDF.U(8.W),
    0x00.U(8.W), 0x00.U(8.W), 0x00.U(8.W), 0x00.U(8.W),
    0x00.U(8.W), 0x00.U(8.W), 0x00.U(8.W), 0x00.U(8.W)
  ))
  io.instruction := romData(io.romaddr)
}

// ==================== 顶层 top ====================
class top extends Module {
  val io = IO(new Bundle { val clk = Input(Clock()); val rst = Input(Bool()); val r2_out = Output(UInt(8.W)) })

  val pc2romaddr = Wire(UInt(4.W))
  val instruction = Wire(UInt(8.W))

  val rden   = Wire(Bool())
  val rs1en  = Wire(Bool())
  val rs2en  = Wire(Bool())
  val addren = Wire(Bool())
  val immen  = Wire(Bool())
  val rd     = Wire(UInt(2.W))
  val rs1    = Wire(UInt(2.W))
  val rs2    = Wire(UInt(2.W))
  val addr   = Wire(UInt(4.W))
  val imm    = Wire(UInt(4.W))

  val alu_result = Wire(UInt(8.W))
  val out1       = Wire(UInt(8.W))
  val out2       = Wire(UInt(8.W))
  val r0         = Wire(UInt(8.W))
  val r2         = Wire(UInt(8.W))
  val jalmux     = Wire(Bool())

  // PC
  val pc_mod = Module(new PC)
  pc_mod.io.clk      := io.clk
  pc_mod.io.rst      := io.rst
  pc_mod.io.en       := true.B
  pc_mod.io.jalmux   := jalmux
  pc_mod.io.jal2mux  := addr
  pc2romaddr := pc_mod.io.pc2romaddr

  // ROM
  val rom_mod = Module(new ROM)
  rom_mod.io.romaddr := pc2romaddr
  instruction := rom_mod.io.instruction

  // IDU
  val idu_mod = Module(new IDU)
  idu_mod.io.instruction := instruction
  rden   := idu_mod.io.rden
  rs1en  := idu_mod.io.rs1en
  rs2en  := idu_mod.io.rs2en
  addren := idu_mod.io.addren
  immen  := idu_mod.io.immen
  rd     := idu_mod.io.rd
  rs1    := idu_mod.io.rs1
  rs2    := idu_mod.io.rs2
  addr   := idu_mod.io.addr
  imm    := idu_mod.io.imm

  // GRF
  val grf_mod = Module(new GRF)
  grf_mod.io.clk      := io.clk
  grf_mod.io.rst      := io.rst
  grf_mod.io.rden     := rden
  grf_mod.io.out1en   := rs1en
  grf_mod.io.out2en   := rs2en
  grf_mod.io.immen    := immen
  grf_mod.io.imm      := imm
  grf_mod.io.out1addr := rs1
  grf_mod.io.out2addr := rs2
  grf_mod.io.inaddr   := rd
  grf_mod.io.din      := alu_result
  out1 := grf_mod.io.out1
  out2 := grf_mod.io.out2
  r0   := grf_mod.io.r0
  r2   := grf_mod.io.r2
  io.r2_out := r2

  // ALU
  val alu_mod = Module(new Adder8bit)
  alu_mod.io.a   := out1
  alu_mod.io.b   := out2
  alu_result := alu_mod.io.out

  // Comparator
  val comp_mod = Module(new Comparator)
  comp_mod.io.en  := addren
  comp_mod.io.r0  := r0
  comp_mod.io.rs2 := out2
  jalmux := comp_mod.io.out2jalmux
}

// ==================== 生成 Verilog ====================
object SCPU extends App {
  ChiselStage.emitSystemVerilogFile(
    new top,
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info", "-default-layer-specialization=enable")
  )
}