package rv64_nstage.control

import chisel3._
import chisel3.util._
import rv64_nstage.core.phvntomParams

// TODO only support 'STALL' without 'FORWARDING'

class ALUSchedulerIO extends Bundle with phvntomParams {
  val rs1_used_exe = Input(Bool())
  val rs1_addr_exe = Input(UInt(regWidth.W))
  val rs2_used_exe = Input(Bool())
  val rs2_addr_exe = Input(UInt(regWidth.W))
  val rd_used_mem1 = Input(Bool())
  val rd_addr_mem1 = Input(UInt(regWidth.W))
  val rd_used_wb = Input(Bool())
  val rd_addr_wb= Input(UInt(regWidth.W))
  val rs1_from_reg = Input(UInt(xlen.W))
  val rs2_from_reg = Input(UInt(xlen.W))
  val rd_from_mem1 = Input(UInt(xlen.W))
  val rd_from_wb = Input(UInt(xlen.W))
  val stall_req = Output(Bool())
  val rs1_val = Output(UInt(xlen.W))
  val rs2_val = Output(UInt(xlen.W))
}

class ALUScheduler extends Module with phvntomParams {
  val io = IO(new ALUSchedulerIO)
  val rs1_hazard = io.rs1_used_exe && ((io.rs1_addr_exe === io.rd_addr_mem1 && io.rd_used_mem1) ||
    (io.rs1_addr_exe === io.rd_addr_wb && io.rd_used_wb))
  val rs2_hazard = io.rs2_used_exe && ((io.rs2_addr_exe === io.rd_addr_mem1 && io.rd_used_mem1) ||
    (io.rs2_addr_exe === io.rd_addr_wb && io.rd_used_wb))

  io.rs1_val := io.rs1_from_reg
  io.rs2_val := io.rs2_from_reg

  io.stall_req := rs1_hazard || rs2_hazard
}
