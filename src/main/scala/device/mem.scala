package device

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import rv64_3stage._

import ControlConst._

class MemReq(val dataWidth: Int = 64) extends Bundle with phvntomParams { // write
  val addr = Output(UInt(xlen.W))
  val data = Output(UInt(dataWidth.W))
  val wen = Output(Bool())
  val memtype = Output(UInt(xlen.W))

  // override def cloneType: this.type = new MemIO(dataWidth).asInstanceOf[this.type]
  override def toPrintable: Printable = p"addr = 0x${Hexadecimal(addr)}, wen = ${wen}, memtype = ${memtype}\n\tdata = 0x${Hexadecimal(data)}"
}

class MemResp(val dataWidth: Int = 64) extends Bundle with phvntomParams { // read
  val data = Output(UInt(dataWidth.W))

  // override def cloneType: this.type = new MemIO(dataWidth).asInstanceOf[this.type]
  override def toPrintable: Printable = p"data = 0x${Hexadecimal(data)}"
}

class MemIO(val dataWidth: Int = 64) extends Bundle with phvntomParams {
  val req = Flipped(Decoupled(new MemReq(dataWidth)))
  val resp = Decoupled(new MemResp(dataWidth))
  val stall = Input(Bool())

  // override def cloneType: this.type = new MemIO(dataWidth).asInstanceOf[this.type]
  override def toPrintable: Printable = p"stall=${stall}, req: valid=${req.valid}, ready=${req.ready}, ${req.bits}\nresp: valid=${resp.valid}, ready=${resp.ready}, ${resp.bits}\n"
}

class SimMemIO extends Bundle with phvntomParams {
  val clk = Input(Clock())
  val raddr = Input(UInt(xlen.W))
  val rdata = Output(UInt(xlen.W))
  val waddr = Input(UInt(xlen.W))
  val wdata = Input(UInt(xlen.W))
  val wmask = Input(UInt(xlen.W))
  val wen = Input(Bool())
}

class SimMem extends BlackBox {
  val io = IO(new SimMemIO)
}
