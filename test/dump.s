
rv32mi-p-csr：     文件格式 elf32-littleriscv


Disassembly of section .text.init:

80000000 <_start>:
80000000:	04c0006f          	j	8000004c <reset_vector>

80000004 <trap_vector>:
80000004:	34202f73          	csrr	t5,mcause
80000008:	00800f93          	li	t6,8
8000000c:	03ff0a63          	beq	t5,t6,80000040 <write_tohost>
80000010:	00900f93          	li	t6,9
80000014:	03ff0663          	beq	t5,t6,80000040 <write_tohost>
80000018:	00b00f93          	li	t6,11
8000001c:	03ff0263          	beq	t5,t6,80000040 <write_tohost>
80000020:	00000f17          	auipc	t5,0x0
80000024:	26cf0f13          	addi	t5,t5,620 # 8000028c <mtvec_handler>
80000028:	000f0463          	beqz	t5,80000030 <trap_vector+0x2c>
8000002c:	000f0067          	jr	t5
80000030:	34202f73          	csrr	t5,mcause
80000034:	000f5463          	bgez	t5,8000003c <handle_exception>
80000038:	0040006f          	j	8000003c <handle_exception>

8000003c <handle_exception>:
8000003c:	5391e193          	ori	gp,gp,1337

80000040 <write_tohost>:
80000040:	00001f17          	auipc	t5,0x1
80000044:	fc3f2023          	sw	gp,-64(t5) # 80001000 <tohost>
80000048:	ff9ff06f          	j	80000040 <write_tohost>

8000004c <reset_vector>:
8000004c:	f1402573          	csrr	a0,mhartid
80000050:	00051063          	bnez	a0,80000050 <reset_vector+0x4>
80000054:	00000297          	auipc	t0,0x0
80000058:	01028293          	addi	t0,t0,16 # 80000064 <reset_vector+0x18>
8000005c:	30529073          	csrw	mtvec,t0
80000060:	18005073          	csrwi	satp,0
80000064:	00000297          	auipc	t0,0x0
80000068:	02028293          	addi	t0,t0,32 # 80000084 <reset_vector+0x38>
8000006c:	30529073          	csrw	mtvec,t0
80000070:	800002b7          	lui	t0,0x80000
80000074:	fff28293          	addi	t0,t0,-1 # 7fffffff <_end+0xffffdfef>
80000078:	3b029073          	csrw	pmpaddr0,t0
8000007c:	01f00293          	li	t0,31
80000080:	3a029073          	csrw	pmpcfg0,t0
80000084:	00000297          	auipc	t0,0x0
80000088:	01828293          	addi	t0,t0,24 # 8000009c <reset_vector+0x50>
8000008c:	30529073          	csrw	mtvec,t0
80000090:	30205073          	csrwi	medeleg,0
80000094:	30305073          	csrwi	mideleg,0
80000098:	30405073          	csrwi	mie,0
8000009c:	00000193          	li	gp,0
800000a0:	00000297          	auipc	t0,0x0
800000a4:	f6428293          	addi	t0,t0,-156 # 80000004 <trap_vector>
800000a8:	30529073          	csrw	mtvec,t0
800000ac:	00100513          	li	a0,1
800000b0:	01f51513          	slli	a0,a0,0x1f
800000b4:	00054c63          	bltz	a0,800000cc <reset_vector+0x80>
800000b8:	0ff0000f          	fence
800000bc:	00100193          	li	gp,1
800000c0:	05d00893          	li	a7,93
800000c4:	00000513          	li	a0,0
800000c8:	00000073          	ecall
800000cc:	80000297          	auipc	t0,0x80000
800000d0:	f3428293          	addi	t0,t0,-204 # 0 <_start-0x80000000>
800000d4:	00028e63          	beqz	t0,800000f0 <reset_vector+0xa4>
800000d8:	10529073          	csrw	stvec,t0
800000dc:	0000b2b7          	lui	t0,0xb
800000e0:	10928293          	addi	t0,t0,265 # b109 <_start-0x7fff4ef7>
800000e4:	30229073          	csrw	medeleg,t0
800000e8:	30202373          	csrr	t1,medeleg
800000ec:	f46298e3          	bne	t0,t1,8000003c <handle_exception>
800000f0:	30005073          	csrwi	mstatus,0
800000f4:	00002537          	lui	a0,0x2
800000f8:	80050513          	addi	a0,a0,-2048 # 1800 <_start-0x7fffe800>
800000fc:	30052073          	csrs	mstatus,a0
80000100:	00000297          	auipc	t0,0x0
80000104:	01428293          	addi	t0,t0,20 # 80000114 <reset_vector+0xc8>
80000108:	34129073          	csrw	mepc,t0
8000010c:	f1402573          	csrr	a0,mhartid
80000110:	30200073          	mret
80000114:	3401d073          	csrwi	mscratch,3

80000118 <test_2>:
80000118:	34002573          	csrr	a0,mscratch
8000011c:	00300e93          	li	t4,3
80000120:	00200193          	li	gp,2
80000124:	13d51c63          	bne	a0,t4,8000025c <fail>

80000128 <test_3>:
80000128:	3400f5f3          	csrrci	a1,mscratch,1
8000012c:	00300e93          	li	t4,3
80000130:	00300193          	li	gp,3
80000134:	13d59463          	bne	a1,t4,8000025c <fail>

80000138 <test_4>:
80000138:	34026673          	csrrsi	a2,mscratch,4
8000013c:	00200e93          	li	t4,2
80000140:	00400193          	li	gp,4
80000144:	11d61c63          	bne	a2,t4,8000025c <fail>

80000148 <test_5>:
80000148:	340156f3          	csrrwi	a3,mscratch,2
8000014c:	00600e93          	li	t4,6
80000150:	00500193          	li	gp,5
80000154:	11d69463          	bne	a3,t4,8000025c <fail>

80000158 <test_6>:
80000158:	0bad2537          	lui	a0,0xbad2
8000015c:	dea50513          	addi	a0,a0,-534 # bad1dea <_start-0x7452e216>
80000160:	340515f3          	csrrw	a1,mscratch,a0
80000164:	00200e93          	li	t4,2
80000168:	00600193          	li	gp,6
8000016c:	0fd59863          	bne	a1,t4,8000025c <fail>

80000170 <test_7>:
80000170:	00002537          	lui	a0,0x2
80000174:	dea50513          	addi	a0,a0,-534 # 1dea <_start-0x7fffe216>
80000178:	34053573          	csrrc	a0,mscratch,a0
8000017c:	0bad2eb7          	lui	t4,0xbad2
80000180:	deae8e93          	addi	t4,t4,-534 # bad1dea <_start-0x7452e216>
80000184:	00700193          	li	gp,7
80000188:	0dd51a63          	bne	a0,t4,8000025c <fail>

8000018c <test_8>:
8000018c:	0000c537          	lui	a0,0xc
80000190:	eef50513          	addi	a0,a0,-273 # beef <_start-0x7fff4111>
80000194:	34052573          	csrrs	a0,mscratch,a0
80000198:	0bad0eb7          	lui	t4,0xbad0
8000019c:	00800193          	li	gp,8
800001a0:	0bd51e63          	bne	a0,t4,8000025c <fail>

800001a4 <test_9>:
800001a4:	34002573          	csrr	a0,mscratch
800001a8:	0badceb7          	lui	t4,0xbadc
800001ac:	eefe8e93          	addi	t4,t4,-273 # badbeef <_start-0x74524111>
800001b0:	00900193          	li	gp,9
800001b4:	0bd51463          	bne	a0,t4,8000025c <fail>
800001b8:	30102573          	csrr	a0,misa
800001bc:	02057513          	andi	a0,a0,32
800001c0:	02050863          	beqz	a0,800001f0 <test_10+0x14>
800001c4:	000065b7          	lui	a1,0x6
800001c8:	3005a073          	csrs	mstatus,a1
800001cc:	f0000053          	fmv.w.x	ft0,zero
800001d0:	3005b073          	csrc	mstatus,a1
800001d4:	00002597          	auipc	a1,0x2
800001d8:	e2c58593          	addi	a1,a1,-468 # 80002000 <begin_signature>

800001dc <test_10>:
800001dc:	0005a027          	fsw	ft0,0(a1)
800001e0:	0005a503          	lw	a0,0(a1)
800001e4:	00100e93          	li	t4,1
800001e8:	00a00193          	li	gp,10
800001ec:	07d51863          	bne	a0,t4,8000025c <fail>
800001f0:	30102573          	csrr	a0,misa
800001f4:	01455513          	srli	a0,a0,0x14
800001f8:	00157513          	andi	a0,a0,1
800001fc:	04050463          	beqz	a0,80000244 <finish>
80000200:	000022b7          	lui	t0,0x2
80000204:	80028293          	addi	t0,t0,-2048 # 1800 <_start-0x7fffe800>
80000208:	3002b073          	csrc	mstatus,t0
8000020c:	00000297          	auipc	t0,0x0
80000210:	01028293          	addi	t0,t0,16 # 8000021c <test_11>
80000214:	34129073          	csrw	mepc,t0
80000218:	30200073          	mret

8000021c <test_11>:
8000021c:	0ff00513          	li	a0,255
80000220:	c0001573          	csrrw	a0,cycle,zero
80000224:	0ff00e93          	li	t4,255
80000228:	00b00193          	li	gp,11
8000022c:	03d51863          	bne	a0,t4,8000025c <fail>

80000230 <test_12>:
80000230:	0ff00513          	li	a0,255
80000234:	30002573          	csrr	a0,mstatus
80000238:	0ff00e93          	li	t4,255
8000023c:	00c00193          	li	gp,12
80000240:	01d51e63          	bne	a0,t4,8000025c <fail>

80000244 <finish>:
80000244:	0ff0000f          	fence
80000248:	00100193          	li	gp,1
8000024c:	05d00893          	li	a7,93
80000250:	00000513          	li	a0,0
80000254:	00000073          	ecall
80000258:	02301063          	bne	zero,gp,80000278 <pass>

8000025c <fail>:
8000025c:	0ff0000f          	fence
80000260:	00018063          	beqz	gp,80000260 <fail+0x4>
80000264:	00119193          	slli	gp,gp,0x1
80000268:	0011e193          	ori	gp,gp,1
8000026c:	05d00893          	li	a7,93
80000270:	00018513          	mv	a0,gp
80000274:	00000073          	ecall

80000278 <pass>:
80000278:	0ff0000f          	fence
8000027c:	00100193          	li	gp,1
80000280:	05d00893          	li	a7,93
80000284:	00000513          	li	a0,0
80000288:	00000073          	ecall

8000028c <mtvec_handler>:
8000028c:	00900293          	li	t0,9
80000290:	0051e663          	bltu	gp,t0,8000029c <mtvec_handler+0x10>
80000294:	00b00293          	li	t0,11
80000298:	0232f263          	bgeu	t0,gp,800002bc <privileged>
8000029c:	342022f3          	csrr	t0,mcause
800002a0:	00800313          	li	t1,8
800002a4:	fa629ce3          	bne	t0,t1,8000025c <fail>
800002a8:	0ff0000f          	fence
800002ac:	00100193          	li	gp,1
800002b0:	05d00893          	li	a7,93
800002b4:	00000513          	li	a0,0
800002b8:	00000073          	ecall

800002bc <privileged>:
800002bc:	342022f3          	csrr	t0,mcause
800002c0:	00200313          	li	t1,2
800002c4:	f8629ce3          	bne	t0,t1,8000025c <fail>
800002c8:	341022f3          	csrr	t0,mepc
800002cc:	00428293          	addi	t0,t0,4
800002d0:	34129073          	csrw	mepc,t0
800002d4:	30200073          	mret
800002d8:	c0001073          	unimp
	...

Disassembly of section .tohost:

80001000 <tohost>:
	...

80001040 <fromhost>:
	...

Disassembly of section .data:

80002000 <begin_signature>:
80002000:	0001                	nop
	...

Disassembly of section .riscv.attributes:

00000000 <.riscv.attributes>:
   0:	2d41                	jal	690 <_start-0x7ffff970>
   2:	0000                	unimp
   4:	7200                	flw	fs0,32(a2)
   6:	7369                	lui	t1,0xffffa
   8:	01007663          	bgeu	zero,a6,14 <_start-0x7fffffec>
   c:	00000023          	sb	zero,0(zero) # 0 <_start-0x80000000>
  10:	7205                	lui	tp,0xfffe1
  12:	3376                	fld	ft6,376(sp)
  14:	6932                	flw	fs2,12(sp)
  16:	7032                	flw	ft0,44(sp)
  18:	5f30                	lw	a2,120(a4)
  1a:	326d                	jal	fffff9c4 <_end+0x7fffd9b4>
  1c:	3070                	fld	fa2,224(s0)
  1e:	615f 7032 5f30      	0x5f307032615f
  24:	3266                	fld	ft4,120(sp)
  26:	3070                	fld	fa2,224(s0)
  28:	645f 7032 0030      	0x307032645f
