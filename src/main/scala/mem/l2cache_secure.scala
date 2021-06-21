package mem

import chisel3._
import chisel3.util._
import tile._
import tile.common.control.ControlConst._
import bus._
import device._
import utils._
import chisel3.util.experimental.BoringUtils
import qarma64.QarmaEngine
import chisel3.util.random.FibonacciLFSR
import chisel3.util.random.GaloisLFSR

class L2CacheSecure(val n_sources: Int = 1)(implicit
    val cacheConfig: CacheConfig
) extends Module
    with CacheParameters {
  val io = IO(new L2CacheIO(n_sources))

  // Module Used
  val stall = Wire(Bool())
  val flush_finish = Wire(Bool())

  val arbiter = Module(new L2CacheXbar(n_sources))
  for (i <- 0 until n_sources) {
    io.in(i) <> arbiter.io.in(i)
  }
  val current_request = arbiter.io.out

  val metaTagLength = xlen - offsetLength
  class SecureMetaData(val tagLength: Int) extends Bundle {
    val valid = Bool()
    val dirty = Bool()
    val meta = UInt(log2Ceil(nWays).W)
    val tag = UInt(tagLength.W)    
    override def toPrintable: Printable =
      p"SecureMetaData(valid = ${valid}, dirty = ${dirty}, meta = ${meta}, tag = 0x${Hexadecimal(tag)})"
  }
  def choose_victim(array: Vec[SecureMetaData]): UInt = {
    val ageVec = VecInit(array.map(m => !m.meta.orR)).asUInt
    PriorityEncoder(ageVec)
  }
  def update_meta(
      array: Vec[SecureMetaData],
      access_index: UInt
  ): Vec[SecureMetaData] = {
    val length = log2Ceil(array.length)
    val new_meta = WireDefault(array)
    val old_meta_value =
      Mux(array(access_index).valid, array(access_index).meta, 0.U)
    (new_meta zip array).map {
      case (n, o) =>
        when(o.meta > old_meta_value) { n.meta := o.meta - 1.U }
    }
    new_meta(access_index).meta := Fill(length, 1.U(1.W))
    new_meta
  }

  /* stage1 signals */
  val s1_valid = WireDefault(Bool(), false.B)
  val s1_addr = Wire(UInt(xlen.W))
  // val s1_index = WireDefault(UInt(indexLength.W), 0.U(indexLength.W))
  val s1_data = Wire(UInt(blockBits.W))
  val s1_wen = WireDefault(Bool(), false.B)

  s1_valid := current_request.req.valid
  s1_addr := current_request.req.bits.addr
  // s1_index := s1_addr(indexLength + offsetLength - 1, offsetLength)
  s1_data := current_request.req.bits.data
  s1_wen := current_request.req.bits.wen

  /* stage qarma registers */
  val qarma_valid = RegInit(Bool(), false.B)
  val qarma_addr = RegInit(UInt(xlen.W), 0.U)
  val qarma_data = RegInit(UInt(blockBits.W), 0.U)
  val qarma_wen = RegInit(Bool(), false.B)
  val q_idle :: q_run :: Nil = Enum(2)
  val qarma_state = RegInit(q_idle)
  val last_s1_valid = RegNext(s1_valid)

  val qarma_engine = Module(new QarmaEngine(pec_enable_ppl, static_round, pec_round))
  qarma_engine.kill.valid := false.B
  qarma_engine.input.valid := qarma_valid
  qarma_engine.input.bits.encrypt := true.B
  qarma_engine.input.bits.keyh := RegEnable(FibonacciLFSR.maxPeriod(64), flush_finish)
  qarma_engine.input.bits.keyl := RegEnable(GaloisLFSR.maxPeriod(64), flush_finish)
  qarma_engine.input.bits.tweak := 0.U
  qarma_engine.input.bits.text := qarma_addr(xlen - 1, offsetLength)
  qarma_engine.input.bits.actual_round := 7.U(3.W)
  qarma_engine.output.ready := true.B
  val read_index = qarma_engine.output.bits.result(indexLength - 1, 0)

  when(!stall && ((qarma_state === q_idle && !qarma_valid) || (qarma_state === q_run && qarma_engine.output.valid))) {
    qarma_valid := s1_valid
    qarma_addr := s1_addr
    qarma_data := s1_data
    qarma_wen := s1_wen
  }

  switch(qarma_state) {
    is(q_idle) { when(qarma_engine.input.valid) { qarma_state := q_run } }
    is(q_run) { when(qarma_engine.output.valid) { qarma_state := q_idle } }
  }

  /* stage2 registers */
  val s2_valid = RegInit(Bool(), false.B)
  val s2_addr = RegInit(UInt(xlen.W), 0.U)
  val s2_data = RegInit(UInt(blockBits.W), 0.U)
  val s2_wen = RegInit(Bool(), false.B)
  val s2_index = RegInit(UInt(indexLength.W), 0.U)
  val s2_meta = Wire(Vec(nWays, new SecureMetaData(metaTagLength)))
  val s2_cacheline = Wire(Vec(nWays, new CacheLineData))
  val s2_tag = Wire(UInt(metaTagLength.W))

  when(!stall) {
    s2_valid := qarma_engine.output.valid & qarma_valid
    s2_addr := qarma_addr
    s2_data := qarma_data
    s2_wen := qarma_wen
    s2_index := read_index
  }

  s2_tag := s2_addr(xlen - 1, xlen - metaTagLength)

  val s2_hitVec = VecInit(s2_meta.map(m => m.valid && m.tag === s2_tag)).asUInt
  val s2_hit_index = PriorityEncoder(s2_hitVec)
  val s2_victim_index = choose_victim(s2_meta)
  val s2_victim_vec = UIntToOH(s2_victim_index)
  val s2_hit = s2_hitVec.orR
  val s2_access_index = Mux(s2_hit, s2_hit_index, s2_victim_index)
  val s2_access_vec = UIntToOH(s2_access_index)

  /* stage3 registers */
  val s3_valid = RegInit(Bool(), false.B)
  val s3_addr = RegInit(UInt(xlen.W), 0.U)
  val s3_data = RegInit(UInt(blockBits.W), 0.U)
  val s3_wen = RegInit(Bool(), false.B)
  val s3_meta = Reg(Vec(nWays, new SecureMetaData(metaTagLength)))
  val s3_cacheline = Reg(Vec(nWays, new CacheLineData))
  val s3_index = RegInit(UInt(indexLength.W), 0.U)
  val s3_tag = Wire(UInt(metaTagLength.W))
  val s3_lineoffset = Wire(UInt(lineLength.W))
  val s3_wordoffset = Wire(UInt((offsetLength - lineLength).W))
  val s3_access_index = Reg(chiselTypeOf(s2_access_index))
  val s3_access_vec = Reg(chiselTypeOf(s2_access_vec))
  val s3_hit = Reg(chiselTypeOf(s2_hit))

  when(!stall) {
    s3_valid := s2_valid
    s3_addr := s2_addr
    s3_data := s2_data
    s3_wen := s2_wen
    s3_index := s2_index
    s3_meta := s2_meta
    s3_cacheline := s2_cacheline
    s3_access_index := s2_access_index
    s3_access_vec := s2_access_vec
    s3_hit := s2_hit
  }
  // s3_index := s3_addr(indexLength + offsetLength - 1, offsetLength)
  s3_tag := s3_addr(xlen - 1, xlen - metaTagLength)
  s3_lineoffset := s3_addr(offsetLength - 1, offsetLength - lineLength)
  s3_wordoffset := s3_addr(offsetLength - lineLength - 1, 0)

  val result = Wire(UInt(blockBits.W))
  val cacheline_meta = s3_meta(s3_access_index)
  val cacheline_data = s3_cacheline(s3_access_index)
  val flush_counter = Counter(nSets)
  flush_finish := flush_counter.value === (nSets - 1).U

  val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: s_flush :: Nil =
    Enum(6)
  val state = RegInit(s_flush)
  val read_address = Cat(s3_tag, 0.U(offsetLength.W))
  val write_address = Cat(cacheline_meta.tag, 0.U(offsetLength.W))
  val mem_valid = state === s_memReadResp && io.mem.resp.valid
  val request_satisfied = s3_hit || mem_valid
  stall := (s3_valid && !request_satisfied) || state === s_flush // wait for data

  current_request.resp.valid := s3_valid && request_satisfied
  current_request.resp.bits.data := result
  current_request.req.ready := !stall
  current_request.flush_ready := true.B

  io.mem.stall := false.B
  io.mem.flush := false.B
  io.mem.req.valid := s3_valid && (state === s_memReadReq || state === s_memWriteReq)
  io.mem.req.bits.addr := Mux(
    state === s_memWriteReq || state === s_memWriteResp,
    write_address,
    read_address
  )
  io.mem.req.bits.data := cacheline_data.asUInt
  io.mem.req.bits.wen := state === s_memWriteReq || state === s_memWriteResp
  io.mem.req.bits.memtype := DontCare
  io.mem.resp.ready := s3_valid && (state === s_memReadResp || state === s_memWriteResp)

  switch(state) {
    is(s_idle) {
      when(reset.asBool) {
        state := s_flush
      }.elsewhen(!s3_hit && s3_valid) {
        state := Mux(
          cacheline_meta.valid && cacheline_meta.dirty,
          s_memWriteReq,
          s_memReadReq
        )
      }
    }
    is(s_memReadReq) { when(io.mem.req.fire()) { state := s_memReadResp } }
    is(s_memReadResp) { when(io.mem.resp.fire()) { state := s_idle } }
    is(s_memWriteReq) { when(io.mem.req.fire()) { state := s_memWriteResp } }
    is(s_memWriteResp) { when(io.mem.resp.fire()) { state := s_memReadReq } }
    is(s_flush) { when(flush_finish) { state := s_idle } }
  }

  val fetched_data = io.mem.resp.bits.data
  val fetched_vec = Wire(new CacheLineData)
  for (i <- 0 until nLine) {
    fetched_vec.data(i) := fetched_data((i + 1) * blockBits - 1, i * blockBits)
  }

  val target_data = Mux(s3_hit, cacheline_data, fetched_vec)
  val write_meta = Wire(Vec(nWays, new SecureMetaData(metaTagLength)))
  val new_data = Wire(new CacheLineData)
  val meta_index = Wire(UInt(indexLength.W))
  meta_index := DontCare
  result := DontCare
  write_meta := DontCare
  new_data := DontCare
  when(s3_valid) {
    when(request_satisfied) {
      when(s3_wen) {
        val write_data = Wire(Vec(nWays, new CacheLineData))
        new_data := target_data
        new_data.data(s3_lineoffset) := s3_data
        for (i <- 0 until nWays) {
          when(s3_access_index === i.U) {
            write_data(i) := new_data
          }.otherwise {
            write_data(i) := s3_cacheline(i)
          }
        }
        write_meta := update_meta(s3_meta, s3_access_index)
        write_meta(s3_access_index).valid := true.B
        write_meta(s3_access_index).dirty := true.B
        write_meta(s3_access_index).tag := s3_tag
        meta_index := s3_index
        // printf(
        //   p"l2cache write: s3_index=${s3_index}, s3_access_index=${s3_access_index}\n"
        // )
        // printf(p"\tnew_data=${new_data}\n")
        // printf(p"\twrite_meta=${write_meta}\n")
      }.otherwise {
        val result_data = target_data.data(s3_lineoffset)
        val write_data = Wire(Vec(nWays, new CacheLineData))
        write_data := DontCare
        result := result_data
        new_data := target_data
        when(!s3_hit) {
          for (i <- 0 until nWays) {
            when(s3_access_index === i.U) {
              write_data(i) := target_data
            }.otherwise {
              write_data(i) := s3_cacheline(i)
            }
          }
        }
        write_meta := update_meta(s3_meta, s3_access_index)
        write_meta(s3_access_index).valid := true.B
        when(!s3_hit) {
          write_meta(s3_access_index).dirty := false.B
        }
        write_meta(s3_access_index).tag := s3_tag
        meta_index := s3_index
        // printf(
        //   p"l2cache read update: s3_index=${s3_index}, s3_access_index=${s3_access_index}\n"
        // )
        // printf(p"\ttarget_data=${target_data}\n")
        // printf(p"\twrite_meta=${write_meta}\n")
      }
    }
  }

  when(state === s_flush) {
    for (i <- 0 until nWays) {
      write_meta(i).valid := false.B
      write_meta(i).dirty := false.B
      write_meta(i).meta := 0.U
      write_meta(i).tag := 0.U
    }
    meta_index := flush_counter.value
    flush_counter.inc()
  }

  if (fpga && enable_blockram) {
    val metaArray = List.fill(nWays)(Module(new BRAMSyncReadMem(nSets, (new SecureMetaData(metaTagLength)).getWidth, 1)))
    val dataArray = List.fill(nWays)(Module(new BRAMSyncReadMem(nSets, (new CacheLineData).getWidth, 1)))

    for (i <- 0 until nWays) {
      metaArray(i).io.addra := read_index
      metaArray(i).io.addrb := meta_index
      metaArray(i).io.wea := false.B
      metaArray(i).io.web := state === s_flush || (s3_valid && request_satisfied)
      metaArray(i).io.dina := DontCare
      metaArray(i).io.dinb := write_meta(i).asUInt
      s2_meta(i) := metaArray(i).io.douta.asTypeOf(new SecureMetaData(metaTagLength))
      dataArray(i).io.addra := read_index
      dataArray(i).io.addrb := s3_index
      dataArray(i).io.wea := false.B
      dataArray(i).io.web := s3_valid && request_satisfied && s3_access_index === i.U
      dataArray(i).io.dina := DontCare
      dataArray(i).io.dinb := new_data.asUInt
      s2_cacheline(i) := dataArray(i).io.douta.asTypeOf(new CacheLineData)
    }
  } else {
    val metaArray = List.fill(nWays)(SyncReadMem(nSets, new SecureMetaData(metaTagLength)))
    val dataArray = List.fill(nWays)(SyncReadMem(nSets, new CacheLineData))

    when(state === s_flush || (s3_valid && request_satisfied)) {
      for (i <- 0 until nWays) {
        metaArray(i).write(meta_index, write_meta(i))
      }
    }

    for (i <- 0 until nWays) {
      s2_meta(i) := metaArray(i).read(read_index, true.B)
      s2_cacheline(i) := dataArray(i).read(read_index, true.B)
    }

    when(s3_valid && request_satisfied) {
      for (i <- 0 until nWays) {
        when(s3_access_index === i.U) {
          dataArray(i).write(s3_index, new_data)
        }
      }
    }
  }

  // for stat counters
  if(diffTest) {
    val read_misses = RegInit(UInt(64.W), 0.U)
    val read_count = RegInit(UInt(64.W), 0.U)
    val write_misses = RegInit(UInt(64.W), 0.U)
    val write_count = RegInit(UInt(64.W), 0.U)
    val last_s3_valid = RegNext(s3_valid)
    val last_s3_addr = RegNext(s3_addr)
    val last_s3_data = RegNext(s3_data)
    val last_s3_wen = RegNext(s3_wen)
    val new_req = s3_valid && (!last_s3_valid || last_s3_addr =/= s3_addr || last_s3_data =/= s3_data || last_s3_wen =/= s3_wen)
    when(new_req) {
      when(s3_wen) {
        write_count := write_count + 1.U
        when(!s3_hit) {
          write_misses := write_misses + 1.U
        }
      }.otherwise {
        read_count := read_count + 1.U
        when(!s3_hit) {
          read_misses := read_misses + 1.U
        }
      }
    }
    BoringUtils.addSource(read_misses, "l2cache_read_misses")
    BoringUtils.addSource(read_count, "l2cache_read_count")
    BoringUtils.addSource(write_misses, "l2cache_write_misses")
    BoringUtils.addSource(write_count, "l2cache_write_count")
  }

  // printf(p"[${GTimer()}]: ${cacheName} Debug Info----------\n")
  // printf("state=%d, stall=%d, s3_hit=%d, result=%x\n", state, stall, s3_hit, result)
  // printf("s1_valid=%d, s1_addr=%x, s1_data=%x, s1_wen=%d\n", s1_valid, s1_addr, s1_data, s1_wen)
  // printf("qarma_valid=%d, qarma_addr=%x, read_index=%x\n", qarma_valid, qarma_addr, read_index)
  // printf("qarma_data=%x, qarma_wen=%d\n", qarma_data, qarma_wen)
  // printf("s2_valid=%d, s2_addr=%x, s2_index=%x\n", s2_valid, s2_addr, s2_addr(indexLength + offsetLength - 1, offsetLength))
  // printf("s2_data=%x, s2_wen=%d\n", s2_data, s2_wen)
  // printf("s3_valid=%d, s3_addr=%x, s3_index=%x\n", s3_valid, s3_addr, s3_index)
  // printf("s3_data=%x, s3_wen=%d\n", s3_data, s3_wen)
  // printf(
  //   "s3_tag=%x, s3_lineoffset=%x, s3_wordoffset=%x\n",
  //   s3_tag,
  //   s3_lineoffset,
  //   s3_wordoffset
  // )
  // printf(p"s2_hitVec=${s2_hitVec}, s3_access_index=${s3_access_index}\n")
  // printf(
  //   p"s2_victim_index=${s2_victim_index}, s2_victim_vec=${s2_victim_vec}, s3_access_vec = ${s3_access_vec}\n"
  // )
  // printf(p"s2_cacheline=${s2_cacheline}\n")
  // printf(p"s2_meta=${s2_meta}\n")
  // printf(p"s3_cacheline=${s3_cacheline}\n")
  // printf(p"s3_meta=${s3_meta}\n")
  // printf(p"----------${cacheName} io.mem----------\n")
  // printf(p"${io.mem}\n")
  // printf(p"----------Qarma Engine----------\n")
  // printf(p"input: valid=${qarma_engine.input.valid}, ready=${qarma_engine.input.ready}, keyh=0x${Hexadecimal(qarma_engine.input.bits.keyh)}, keyl=0x${Hexadecimal(qarma_engine.input.bits.keyl)}, tweak=0x${Hexadecimal(qarma_engine.input.bits.tweak)}, text=0x${Hexadecimal(qarma_engine.input.bits.text)}\noutput: valid=${qarma_engine.output.valid}, ready=${qarma_engine.output.ready}, result = 0x${Hexadecimal(qarma_engine.output.bits.result)}\n")
  // printf("-----------------------------------------------\n")
}
