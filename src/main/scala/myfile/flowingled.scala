package myfile
import chisel3._
import _root_.circt.stage.ChiselStage

class flowingled extends Module {
  val io = IO(new Bundle{
    val led = Output(UInt(16.W))
  })

  val counter = ((5000000 / 2) - 1).U
  val counterreg = RegInit(0.U(32.W))
  val ledreg = RegInit(1.U(16.W))

  counterreg := counterreg + 1.U

  when(counterreg === counter){
    counterreg := 0.U
    ledreg := ledreg(14, 0) ## ledreg(15)
  }

  io.led := ledreg
}

object flowingled extends App {
  ChiselStage.emitSystemVerilogFile(
    new flowingled,
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info", "-default-layer-specialization=enable")
  )
}