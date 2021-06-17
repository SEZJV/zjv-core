#include "engine.h"


extern unsigned int sim_uart_irq, sim_prio, sim_ie, sim_ip, sim_thrs, sim_claim;
extern reg_t physic_addr;

int main(int argc, char** argv)
{
   unsigned random_seed = (unsigned)time(NULL) ^ (unsigned)getpid();
   uint64_t max_cycles = 0;

   bool log = false;
   const char* loadmem = NULL;
   FILE *vcdfile = NULL, *logfile = stderr;
   const char* failure = NULL;
   int optind;

   for (int i = 1; i < argc; i++)
   {
      std::string arg = argv[i];
      if (arg.substr(0, 2) == "-v")
         vcdfile = fopen(argv[i]+2,(const char*)"w+");
      else if (arg.substr(0, 2) == "-s")
         random_seed = atoi(argv[i]+2);
      else if (arg == "+verbose")
         log = true;
      else if (arg.substr(0, 12) == "+max-cycles=")
         max_cycles = atoll(argv[i]+12);
      else { // End of EMULATOR options
         optind = i;
         break;
      }
   }

   // Shift HTIF options to the front of argv
   int htif_argc = 1 + argc - optind;
   for (int i = 1; optind < argc;)
      argv[i++] = argv[optind++];

   if (htif_argc != 2) {
      #ifdef ZJV_DEBUG
         printf("ARGUMENTS WRONG with %d\n", htif_argc);
      #endif
      exit(1);
   }

   dtengine_t engine(64, argv[1]);
   engine.emu_reset(10);
   #ifdef ZJV_DEBUG
      fprintf(stderr, "[Emu] Reset after 10 cycles \n");
   #endif

   bool startTest = false;
   int faultExitLatency = 0;
   bool faultFlag = false;
   
   int cont_count = 0;
   int bubble_cnt = 0;
   int int_total_cnt = 0;
   long sim_cnt = 0;

   // fprintf(stderr, "name,IPC,icache misses,icache count,icache miss rate,icache MPKI,");
   // fprintf(stderr, "dcache read misses,dcache read count,dcache read miss rate,");
   // fprintf(stderr, "dcache write misses,dcache write count,dcache write miss rate,");
   // fprintf(stderr, "dcache total misses,dcache total count,dcache total miss rate,dcache MPKI,");
   // fprintf(stderr, "l2cache read misses,l2cache read count,l2cache read miss rate,");
   // fprintf(stderr, "l2cache write misses,l2cache write count,l2cache write miss rate,");
   // fprintf(stderr, "l2cache total misses,l2cache total count,l2cache total miss rate,l2cache MPKI\n");

   while (!engine.is_finish()) {
      engine.emu_step(1);
      // fprintf(stderr,"zjv   pc: 0x%016lx (0x%08lx): %s\n",  engine.emu_get_pc(), engine.emu_get_inst(), engine.disasm(engine.emu_get_inst()).c_str());
      if (!startTest && engine.emu_get_pc() == 0x80000000) {
         startTest = true;
      }
      if (engine.is_finish()) {
         if (engine.emu_difftest_poweroff() == (long)PROGRAM_PASS) {
            fprintf(stderr, "\n\t\t \x1b[32m========== [ %s PASS with IPC %f ] ==========\x1b[0m\n", argv[1], 1.0 * sim_cnt / engine.trace_count);
            // fprintf(stderr, "\n\t\t \x1b[32m %s inst count %ld, access count %ld\x1b[0m\n", argv[1], sim_cnt, (engine.get_emu_state()->dcache_read_count + engine.get_emu_state()->dcache_write_count));
            // fprintf(stderr, "%s,%f,%ld,%ld,%lf%%,%lf,", argv[1], 1.0 * sim_cnt / engine.trace_count, engine.get_emu_state()->icache_read_misses,engine.get_emu_state()->icache_read_count,100.0 * engine.get_emu_state()->icache_read_misses / engine.get_emu_state()->icache_read_count,1000.0 * engine.get_emu_state()->icache_read_misses / sim_cnt);
            // fprintf(stderr, "%ld,%ld,%lf%%,", engine.get_emu_state()->dcache_read_misses,
            //       engine.get_emu_state()->dcache_read_count,
            //       100.0 * engine.get_emu_state()->dcache_read_misses / engine.get_emu_state()->dcache_read_count);
            // fprintf(stderr, "%ld,%ld,%lf%%,", engine.get_emu_state()->dcache_write_misses,
            //       engine.get_emu_state()->dcache_write_count,
            //       100.0 * engine.get_emu_state()->dcache_write_misses / engine.get_emu_state()->dcache_write_count);
            // fprintf(stderr, "%ld,%ld,%lf%%,%lf,", (engine.get_emu_state()->dcache_read_misses + engine.get_emu_state()->dcache_write_misses),
            //       (engine.get_emu_state()->dcache_read_count + engine.get_emu_state()->dcache_write_count),
            //       100.0 * (engine.get_emu_state()->dcache_read_misses + engine.get_emu_state()->dcache_write_misses) / (engine.get_emu_state()->dcache_read_count + engine.get_emu_state()->dcache_write_count),
            //       1000.0 * (engine.get_emu_state()->dcache_read_misses + engine.get_emu_state()->dcache_write_misses) / sim_cnt);
            // fprintf(stderr, "%ld,%ld,%lf%%,", engine.get_emu_state()->l2cache_read_misses,
            //       engine.get_emu_state()->l2cache_read_count,
            //       100.0 * engine.get_emu_state()->l2cache_read_misses / engine.get_emu_state()->l2cache_read_count);
            // fprintf(stderr, "%ld,%ld,%lf%%,", engine.get_emu_state()->l2cache_write_misses,
            //       engine.get_emu_state()->l2cache_write_count,
            //       100.0 * engine.get_emu_state()->l2cache_write_misses / engine.get_emu_state()->l2cache_write_count);
            // fprintf(stderr, "%ld,%ld,%lf%%,%lf\n", (engine.get_emu_state()->l2cache_read_misses + engine.get_emu_state()->l2cache_write_misses),
            //       (engine.get_emu_state()->l2cache_read_count + engine.get_emu_state()->l2cache_write_count),
            //       100.0 * (engine.get_emu_state()->l2cache_read_misses + engine.get_emu_state()->l2cache_write_misses) / (engine.get_emu_state()->l2cache_read_count + engine.get_emu_state()->l2cache_write_count),
            //       1000.0 * (engine.get_emu_state()->l2cache_read_misses + engine.get_emu_state()->l2cache_write_misses) / sim_cnt);
            

            fprintf(stderr, "\t\t \x1b[32micache total: misses %ld, count %ld, miss rate %lf%%, MPKI %lf\x1b[0m\n",
                  engine.get_emu_state()->icache_read_misses,
                  engine.get_emu_state()->icache_read_count,
                  100.0 * engine.get_emu_state()->icache_read_misses / engine.get_emu_state()->icache_read_count,
                  1000.0 * engine.get_emu_state()->icache_read_misses / sim_cnt);
            fprintf(stderr, "\t\t \x1b[32mdcache read: misses %ld, count %ld, miss rate %lf%%,\x1b[0m\n",
                  engine.get_emu_state()->dcache_read_misses,
                  engine.get_emu_state()->dcache_read_count,
                  100.0 * engine.get_emu_state()->dcache_read_misses / engine.get_emu_state()->dcache_read_count);
            fprintf(stderr, "\t\t \x1b[32mdcache write: misses %ld, count %ld, miss rate %lf%%\x1b[0m\n",
                  engine.get_emu_state()->dcache_write_misses,
                  engine.get_emu_state()->dcache_write_count,
                  100.0 * engine.get_emu_state()->dcache_write_misses / engine.get_emu_state()->dcache_write_count);
            fprintf(stderr, "\t\t \x1b[32mdcache total: misses %ld, count %ld, miss rate %lf%%, MPKI %lf\x1b[0m\n",
                  (engine.get_emu_state()->dcache_read_misses + engine.get_emu_state()->dcache_write_misses),
                  (engine.get_emu_state()->dcache_read_count + engine.get_emu_state()->dcache_write_count),
                  100.0 * (engine.get_emu_state()->dcache_read_misses + engine.get_emu_state()->dcache_write_misses) / (engine.get_emu_state()->dcache_read_count + engine.get_emu_state()->dcache_write_count),
                  1000.0 * (engine.get_emu_state()->dcache_read_misses + engine.get_emu_state()->dcache_write_misses) / sim_cnt);
            fprintf(stderr, "\t\t \x1b[32ml2cache read: misses %ld, count %ld, miss rate %lf%%,\x1b[0m\n",
                  engine.get_emu_state()->l2cache_read_misses,
                  engine.get_emu_state()->l2cache_read_count,
                  100.0 * engine.get_emu_state()->l2cache_read_misses / engine.get_emu_state()->l2cache_read_count);
            fprintf(stderr, "\t\t \x1b[32ml2cache write: misses %ld, count %ld, miss rate %lf%%\x1b[0m\n",
                  engine.get_emu_state()->l2cache_write_misses,
                  engine.get_emu_state()->l2cache_write_count,
                  100.0 * engine.get_emu_state()->l2cache_write_misses / engine.get_emu_state()->l2cache_write_count);
            fprintf(stderr, "\t\t \x1b[32ml2cache total: misses %ld, count %ld, miss rate %lf%%, MPKI %lf\x1b[0m\n",
                  (engine.get_emu_state()->l2cache_read_misses + engine.get_emu_state()->l2cache_write_misses),
                  (engine.get_emu_state()->l2cache_read_count + engine.get_emu_state()->l2cache_write_count),
                  100.0 * (engine.get_emu_state()->l2cache_read_misses + engine.get_emu_state()->l2cache_write_misses) / (engine.get_emu_state()->l2cache_read_count + engine.get_emu_state()->l2cache_write_count),
                  1000.0 * (engine.get_emu_state()->l2cache_read_misses + engine.get_emu_state()->l2cache_write_misses) / sim_cnt);
         }
         else
            fprintf(stderr, "\n\t\t \x1b[31m========== [ %s FAIL ] ==========\x1b[0m\n", argv[1]);
         break;
      }
      if (startTest && engine.emu_difftest_valid()) {
         sim_cnt++;
      }
   }

   //  while (!engine.is_finish()) {
   //        engine.sim_solo();
   //  }

   while (!engine.is_finish()) {
      engine.emu_step(1);
      engine.sim_sync_cycle();

      if (!startTest && engine.emu_get_pc() == 0x80000000) {
         startTest = true;
         #ifdef ZJV_DEBUG
            fprintf(stderr, "[Emu] DiffTest Start \n");
         #endif
      }

      #ifdef ZJV_DEBUG
      //  fprintf(stderr, "\t\t\t\t [ ROUND %lx %lx ]\n", engine.trace_count, engine.emu_get_mcycle());
//        fprintf(stderr,"zjv   pc: 0x%016lx (0x%08lx): %s\n",  engine.emu_get_pc(), engine.emu_get_inst(), engine.disasm(engine.emu_get_inst()).c_str());
      #endif

      if (engine.is_finish()) {
         if (engine.emu_difftest_poweroff() == (long)PROGRAM_PASS) {
            fprintf(stderr, "\n\t\t \x1b[32m========== [ %s PASS with IPC %f ] ==========\x1b[0m\n", argv[1], 1.0 * sim_cnt / engine.trace_count);
            fprintf(stderr, "\t\t \x1b[32msr_itlb %ld, sr_i$ %ld, sr_exe %ld, sr_dtlb %ld, sr_d$ %ld, bj_flush %f\x1b[0m\n",
                engine.get_emu_state()->streqs[0],
                engine.get_emu_state()->streqs[2],
                engine.get_emu_state()->streqs[4],
                engine.get_emu_state()->streqs[5],
                engine.get_emu_state()->streqs[8],
                1.0 * engine.get_emu_state()->streqs[9] / engine.get_emu_state()->streqs[7]);
            //sleep(5);
         }
         else
            fprintf(stderr, "\n\t\t \x1b[31m========== [ %s FAIL ] ==========\x1b[0m\n", argv[1]);
         break;
      }


      if(engine.emu_get_interrupt()) {
         engine.sim_check_interrupt();
         int_total_cnt++;

         if (int_total_cnt > 250) {
            fprintf(stderr, "\n\t\t \x1b[32m========== [ %s PASS with IPC %f ] ==========\x1b[0m\n", argv[1], 1.0 * sim_cnt / engine.trace_count);
            fprintf(stderr, "\t\t \x1b[32msr_itlb %ld, sr_i$ %ld, sr_exe %ld, sr_dtlb %ld, sr_d$ %ld, bj_flush %lf\x1b[0m\n",
                engine.get_emu_state()->streqs[0],
                engine.get_emu_state()->streqs[2],
                engine.get_emu_state()->streqs[4],
                engine.get_emu_state()->streqs[5],
                engine.get_emu_state()->streqs[8],
                1.0 * engine.get_emu_state()->streqs[9] / engine.get_emu_state()->streqs[7]);
            printf("Total Int Cnt is %d!\n", int_total_cnt);
            //sleep(5);
            exit(0);
         }

      }

      if (startTest && engine.emu_difftest_valid()) {
         bubble_cnt = 0;
      #ifdef ZJV_DEBUG
            // fprintf(stderr,"zjv   pc: 0x%016lx (0x%08lx): %s\n",  engine.emu_get_pc(), engine.emu_get_inst(), engine.disasm(engine.emu_get_inst()).c_str());
      #endif
         engine.sim_step(1);
         sim_cnt++;

            // fprintf(stderr,"zjv   pc: 0x%016lX (0x%08X): %s\n",  engine.emu_get_pc(), (unsigned int)engine.emu_get_inst(), engine.disasm((signed int)engine.emu_get_inst()).c_str());
            // fprintf(stderr,"spike pc: 0x%016lx (0x%08x): %s\n",  engine.sim_get_pc(), (unsigned int)engine.sim_get_inst(), engine.disasm(engine.sim_get_inst()).c_str());

            // difftest_check_point(pc);        difftest_check_point(priv, "\n");
            // difftest_check_point(mstatus);   difftest_check_point(mepc, "\n");
            // difftest_check_point(mtval);     difftest_check_point(mcause);          difftest_check_point(mtvec, "\n");
            // difftest_check_point(mideleg);   difftest_check_point(medeleg, "\n");
            // difftest_check_point(sstatus);   difftest_check_point(sepc, "\n");
            // difftest_check_point(stval);     difftest_check_point(scause);          difftest_check_point(stvec, "\n");
            // difftest_check_point(mip);       difftest_check_point(sip, "\n");
            // fprintf(stderr, "emu: uart %d plic0 %d plic1 %d prio %x ie %x ip %x thrs %x claim %x\n",
            //                  engine.get_emu_state()->uartirq, engine.get_emu_state()->plicmeip, engine.get_emu_state()->plicseip,
            //                  engine.get_emu_state()->plicprio, engine.get_emu_state()->plicie, engine.get_emu_state()->plicip, engine.get_emu_state()->plicthrs, engine.get_emu_state()->plicclaim);
            // fprintf(stderr, "sim: uart %d plic0 %d plic1 %d prio %x ie %x ip %x thrs %x claim %x\n",
            //                  sim_uart_irq, (engine.sim_get_mip() & MIP_MEIP) != 0, (engine.sim_get_mip() & MIP_SEIP) != 0,
            //                  sim_prio, sim_ie, sim_ip, sim_thrs, sim_claim);
            // difftest_check_general_register();


      if((faultExitLatency || (engine.emu_get_pc() != engine.sim_get_pc()) || 
         (engine.emu_get_mem() && (engine.emu_get_pa() != physic_addr)) ||
         (engine.get_emu_state()->plicmeip != ((engine.sim_get_mip() & MIP_MEIP) != 0)) ||
	      (memcmp(engine.get_sim_state()->regs, engine.get_emu_state()->regs, 32*sizeof(reg_t)) != 0 ))) {
            faultExitLatency++;
            fprintf(stderr, "\n\t\t \x1b[31m========== [ %s FAIL ] ==========\x1b[0m\n", argv[1]);

            fprintf(stderr,"zjv   pc: 0x%016lX (0x%08X): %s\n",  engine.emu_get_pc(), (unsigned int)engine.emu_get_inst(), engine.disasm((signed int)engine.emu_get_inst()).c_str());
            fprintf(stderr,"spike pc: 0x%016lx (0x%08x): %s\n",  engine.sim_get_pc(), (unsigned int)engine.sim_get_inst(), engine.disasm(engine.sim_get_inst()).c_str());

            difftest_check_point(pc);        difftest_check_point(priv, "\n");
            difftest_check_point(mstatus);   difftest_check_point(mepc, "\n");
            difftest_check_point(mtval);     difftest_check_point(mcause);          difftest_check_point(mtvec, "\n");
            difftest_check_point(mideleg);   difftest_check_point(medeleg, "\n");
            difftest_check_point(sstatus);   difftest_check_point(sepc, "\n");
            difftest_check_point(stval);     difftest_check_point(scause);          difftest_check_point(stvec, "\n");
            difftest_check_point(mip);       difftest_check_point(sip, "\n");
            difftest_check_point(mcycle, "\n");
            fprintf(stderr, "emu: pa 0x%016lX, sim: pa 0x%016lX\n", engine.emu_get_pa(), physic_addr);
            fprintf(stderr, "emu: uart %d plic0 %d plic1 %d prio %x ie %x ip %x thrs %x claim %x\n", 
                             engine.get_emu_state()->uartirq, engine.get_emu_state()->plicmeip, engine.get_emu_state()->plicseip,
                             engine.get_emu_state()->plicprio, engine.get_emu_state()->plicie, engine.get_emu_state()->plicip, engine.get_emu_state()->plicthrs, engine.get_emu_state()->plicclaim);
            fprintf(stderr, "sim: uart %d plic0 %d plic1 %d prio %x ie %x ip %x thrs %x claim %x\n", 
                             sim_uart_irq, (engine.sim_get_mip() & MIP_MEIP) != 0, (engine.sim_get_mip() & MIP_SEIP) != 0, 
                             sim_prio, sim_ie, sim_ip, sim_thrs, sim_claim);
            difftest_check_general_register();


            fprintf(stderr, "\n");
            if (faultExitLatency == 1)
                exit(-1);
         }
         else {
            faultExitLatency = 0;
         }
      }
      else {
        bubble_cnt++;
      }

      if(bubble_cnt > 4096) {
        printf("Too many bubbles, end at %lx\n", engine.emu_get_pc());
        exit(-1);
      }

   }

   engine.trace_close();
   return 0;
}


