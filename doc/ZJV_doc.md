# “一生一芯”二期项目介绍-ZJV

##### 浙江大学 网络空间安全学院
##### 张行健 徐金焱 苑子琦

## 1. 技术指标

ZJV是一个支持RV64 IMAC指令集和MSU特权级的10段顺序流水单发射处理器。主要面向对性能要求较低的嵌入式场景。在110nm的工艺下处理器主频预计可以达到400MHz，裸机环境下运行Coremark的IPC大约可以达到0.5。

## 2. 微结构设计

<!-- 阐述处理器的微架构设计细节. 这一部分至少需要包括: -->

### 2.1. 处理器的整体微架构

处理器整体架构如下图所示，其包含两级Cache的层次结构。在内存访问过程中（灰色箭头），CPU流水线与I-MMU、I-Cache、D-MMU、D-Cache模块相连，进行地址转换与存储访问，而这四个模块又与L2 Cache相连，统一处理数据访问请求，L2 Cache的请求经uncache模块转换后与内存总线相连，最终总线与内存进行数据交互。在MMIO（Memory-Mapped Input/Output）方面（黄色箭头），CPU所发出的MMIO请求在I-Cache和D-Cache模块进行判断后，请求经uncache模块转换后与MMIO总线相连，最终由总线与各个设备进行交互。图中由蓝色虚线框所框住的部分，即为最终流片中由ZJV所提供的部分。

![](https://i.imgur.com/HSnvcqh.png)


### 2.2. 处理器的流水线设计

![](https://i.imgur.com/1hjUFqF.png)

ZJV可以划分为IF1、IF2、IF3、ID、EXE1、EXE2、MEM1、MEM2、MEM3和WB段。

IF需要3段流水，主要负责取指、分支预测、压缩指令预处理、跨页32位指令检测等工作。

ID需要1段流水，主要负责译码。

EXE需要2段流水。EXE1段主要负责算术运算，普通算术运算和乘除法分别由ALU和Multiplier负责，此外Branch Pre-detector进行一部分分支处理。EXE2段主要负责处理分支、接收中断信号和虚拟地址的翻译工作。

MEM需要3段流水。MEM1段主要负责异常的检测。MEM的所有流水段共同负责访存操作，以及LL-SC和AMO类型指令的正确执行。

WB需要1段流水，负责将对应的结果写回寄存器堆。


### 2.3. 处理器的对外接口

#### 数据访问交互接口

在处理器内部涉及数据访问的部分，包括流水线与cache交互、MMU与L2 cache交互、cache间交互、cache与uncache交互等都使用了统一的`MemIO`接口进行，该接口包含`req`和`resp`两个通路，使用`Decoupled`方式握手，以及以及其他控制信号, 信号细节如下：

| 信号名称    | 位宽 | 含义          |
|-------------|------|---------------|
| req         | N/A  | 数据请求通路  |
| resp        | N/A  | 数据响应通路  |
| stall       | 1    | 流水线后续阶段中是否存在stall，1表示存在 |
| flush       | 1    | 是否需要flush，1表示需要 |
| flush_ready | 1    | flush是否完成，1表示完成 |

以上信号中的`req`和`resp`为交互中所必需的信号，而其他三个信号主要用于流水线与I-Cache和D-Cache的交互过程。

`req`通路除了`valid-ready`信号外，还包括以下信号：


| 信号名称 | 位宽         | 含义                               |
|----------|--------------|------------------------------------|
| addr     | `xlen`         | 访存地址（位宽与体系结构实现相关） |
| data     | `dataWidth`    | 接口定义时指定，默认为64           |
| wen      | 1            | 写使能，高位使能                   |
| memtype  | `memBits`（3） | 访问数据类型，主要用于流水线与I-Cache和D-Cache交互  |

`resp`通路除了`valid-ready`信号外，还包括以下信号：

| 信号名称 | 位宽 | 含义 |
| -------- | -------- | -------- |
| data     | `dataWidth`     | 接口定义时指定，默认为64     |

<!-- 将以下内容移至单独一节有关debug的小节 -->

`MemIO`接口中都重写了`toPrintable`方法，使接口中的信号可以更加清晰明确的输出出来，方便在调试过程中进行观察追踪，也节约了在多个模块多个接口中书写大量重复代码的工作。

#### 总线访问交互接口

处理器与外部交互时使用的是AXI4总线接口，当cache需要请求外部数据时，需要将`MemIO`接口的请求转换为AXI4总线接口的一次传输，这个过程由`uncache`模块进行处理。根据内存访问与MMIO访问的不同，`uncache`模块有两个略有差别的实现。相同的部分包括：`lock`、`cache`、`prot`、`qos`保持默认为0；`size`统一为8个字节（与机器字长保持一致）；状态机转移情况基本相同。而区别主要体现在两者针对需要传输的数据宽度不同所做的一些更改。

处理器总线的内部处理逻辑与Nutshell的总线交互逻辑大致一致，即首先通过一个多选一的仲裁器选择一个请求处理，然后通过一个一对多的接口选择对应的接收设备。

### 2.4. 处理器中各个组件的分析

#### 分支预测器

分支预测器采用传统的BHT+BTB，曾经尝试加入P-Share，但由于效果不显著而放弃。分支预测器有256个entry，帮助ZJV在运行RT-Thread和Linux等操作系统时IPC提高25%左右。

#### 乘法器

乘法器使用Wallance Tree的变种算法，可以在3个周期内解决64位乘法，加上前后符号的拓展与结果的拼接，共需要5个周期。此外，ZJV的乘法器支持流水操作，一个乘法器内最多可以有3条指令，但是很可惜我们的流水线目前并不支持流水乘法器。

#### 原子指令支持

![](https://i.imgur.com/fOqXuv4.png)

原子指令主要由AMO Arbiter负责，AMO Arbiter控制了整个D-Cache的流水线。以AMOADD为例，当该原子指令进入MEM1段时，D-Cache会先读取对应地址的数值，并stall住此前所有流水段，此时AMO Arbiter的状态机从idle状态进入read状态。此后D-Cache读取的数值会进入AMO Arbiter，状态机进入alu状态，并通过AMOALU实现原子计算，对于AMOADD就是进行加法操作。在此之后AMO Arbiter会给D-Cache发送写请求信号并进入wb状态，直到D-Cache完成写操作，状态机将重新进入idle状态，并取消对MEM1以前流水段的stall信号。

#### Cache模块设计

Cache模块为内存访问和MMIO访问提供了统一的接口，cache模块包括I-Cache、D-Cache以及L2 Cache等，这些模块有设计上均略有不同，但其接口、参数、存储组织、控制逻辑方面基本上是一致的。

##### 接口

Cache模块与CPU流水线或与下层cache以及存储的交互接口均为`MemIO`接口，这样使内部数据访问的接口比较统一，也无需进行不同接口间的转换。

##### 参数

Cache初始化的方法参照Nutshell，使用了`case class`以及隐式参数。Cache模块在初始化中，主要需要指定的参数如下表所示。通过这些参数可以确定cache的所有参数信息，索引（index）长度、偏移（offset）长度、标签（tag）长度等均可确定。

| 参数名称          | 含义                                  |
|-------------------|---------------------------------------|
| `readOnly`          | 是否只读，控制元数据中是否存在`dirty`位 |
| `hasMMIO`           | 是否存在MMIO接口，控制                |
| `name`              | 写使能，高位使能                      |
| `memtype`           | 访问数据类型                          |
| `blockBits`         | 块大小（与`MemIO`接口`data`宽度一致）     |
| `ways`              | 组相连度                              |
| `lines`             | 一个cacheline中块的数目               |
| `totalSize`         | cache总大小                           |
| `replacementPolicy` | 替换策略，LRU（随机替换等策略未实现）         |

##### 存储组织

Cache的存储模块包括了数据的存储（`dataArray`），也包括对应的元数据的存储（`metaArray`）。在模拟中，cache的存储模块由`SyncReadMem`模块进行搭建；而在流片中，由于设计的需要，L1 cache由双端口SRAM搭建，L2 cache由单端口SRAM搭建，其整体组织层级结构与L1 cache中存储组织逻辑结构如下两幅图所示。

<!-- ![](https://i.imgur.com/c9pFsKo.png) -->

![](https://i.imgur.com/kwRdLIf.png)

![](https://i.imgur.com/V5thOOZ.png)

##### 控制逻辑

Cache的控制逻辑是一个三级流水线的结构，与CPU流水线中IF和MEM的三段流水线相一致。三段流水线的功能分别是：

- 第一段：接受访存请求，将请求地址对应的cache索引地址发送给内部存储器件；
- 第二段：接收对应的元数据与数据，判断是否命中，并选择对应的返回数据或受害者（victim）；
- 第三段：将数据根据`memType`以及`wen`信号进行处理，如果缺失则通过有限状态机与下一级进行交互。

根据实际访问的需要，实现了write-back和write-through两种策略，其在前两段处理中的逻辑相同，只有在处理写操作时的策略不同。

<!-- ，write-back和write-through的有限状态机如下图所示。 -->

#### MMU模块设计


MMU，即Memory Management Unit，负责虚拟地址的转换。处理器中采用了Sv39模式的虚拟地址机制并实现了PIPT（Physically Indexed Physically Tagged）的处理机制，即在地址进入cache前就将虚拟地址转换为物理地址。MMU主要包含TLB（Translation Lookaside Buffer）和PTW（PageTable Worker）两部分，TLB用于存储并查询已经访问过的地址转换项，并进行权限检查；如果在TLB中不存在对应虚拟地址的页表项则使用PTW在内存中查找页表项。若页表项不存在或权限不正常，MMU会产生一个page fault的异常。

<!-- #### Uncache模块设计 -->

<!-- #### 总线交互设计 -->

### 2.5. 处理器亮点与瓶颈

#### 亮点

- 功能较为完备，与RV64GC只差FD指令集。
- 将分支处理分在两个流水段，解决了分支跳转的时序瓶颈。
- 将中断接在CSR所在流水段的前一段，保证了时序的同时保证了中断的实时性。
- 原子指令的处理与Cache解耦，在几乎不牺牲IPC的前提下将A拓展在流水线内就完全解决。
- 乘法器仅需3周期就可以完成乘法运算，对比原来的64周期，使得Coremark成绩提高150%。
- 合理的Forwarding机制兼顾IPC与频率。
- 对于跨页32位指令的特殊处理，不影响在页尾处的16位指令的取指。
- 对于压缩指令的跨Cacheline取指，为了兼顾取指高效和尽量降低处理器改造难度，一级指令缓存采用了Shadow Byte，将压缩指令的特殊处理在IF和ID段内解决。
- Cache内部存在Forwarding，解决了`ld x1 addr1, sd x1, addr2`需要等待较长时间的弊端。

#### 瓶颈

- 指令缓存没有预取。
- 数据缓存采用write-through，开销较大。
- 在跑Coremark时，分支预测效果平平无奇，需要添加RAS等机制。P-Share仅在特定条件下起作用，因此可能需要一个Tournament预测器。
- 在一段流水线被迫stall时，前面的流水线都stall，理论上当前面流水线是BUBBLE的时候完全没必要stall。


## 3. 验证环境

<!-- 描述处理器开发使用的验证环境, 包括以下内容: -->

### 3.1. 验证环境的架构

![](https://i.imgur.com/QjWD6je.png)

ZJV的验证环境基于Verilator和Spike搭建。首先由Chisel从Scala代码生成Verilog代码，此后使用Verilator将Verilog代码转化成C++代码，该步骤需要告诉Verilator我们将使用的测试代码和测试中需要的Spike静态库，最终执行Verilator生成文件目录下的Makefile，就可以得到对比测试ELF。

### 3.2. 描述一条指令在验证环境中执行的细节

首先是ZJV从ZJV的模拟RAM中取指，然后执行直到结束，沿途的debug信息也会携带到最后，在此期间Spike也会跟着增加周期数。此后，对比测试文件检查到有指令执行完成，开始使用Spike执行此指令，执行结束后进行x0至x31的对比、pc和inst的对比以及部分CSR的对比。若对比一致，则继续运行程序，否则报错。

### 3.3. 分析现有验证环境的不足

- 对Spike源代码改动较多。
- 开了优化和多线程，在运行操作系统的时候，还是比较慢。
- 对于异步事件的检测别扭（中断），只能挂在ZJV和Spike其中之一触发。
- 对于对周期敏感的数据读出来会不一致，直接影响程序控制流。

## 4. 验证/性能分析结果

<!-- 描述基于自行搭建的验证框架的处理器功能验证结果. 可以包括以下内容: -->

### 4.1. 如何验证各个指令扩展实现的正确性

对于单条指令的测试，我们使用了riscv-test验证正确性，通过其中的测试可以检测IMA等指令的正确性，也可以检测不同特权模式下的指令执行情况。

### 4.2. 如何实现所需的全部功能

从启动操作系统（如linux）的角度来看，至少需要实现的功能包括RV64IMA指令集和M/S/U模式，以及必要的外设，如CLINT、PLIC、UART等。为了对比测试的需要，这些部分还需要在spike中同样进行实现或模拟。

由于默认的工具链一般为RV64GC，所以还需要重新编译工具链，然后使用只包含我们所实现的指令集的工具链编译操作系统。

### 4.3. 描述处理器设计已经通过了哪些测试

在对比测试与soc仿真测试环境中，处理器均通过了以下测试：
- riscv-tests
- RT-Thread
- xv6
- Linux
- Coremark
- Rinux（浙江大学教学操作系统）

![](https://i.imgur.com/W1cHpc0.png)

![](https://i.imgur.com/mJwR9i5.png)


<!-- 描述在仿真验证中遇到的代表性问题和解决方案. -->

### 4.4. 给出基于仿真的处理器性能分析

执行Coremark，IPC达到0.5；RT-Thread，IPC达到0.25；Linux，IPC接近0.4。

![](https://i.imgur.com/GHcLubm.jpg)


### 4.5. 描述在SOC测试中遇到的问题, 以及解决方案

#### AXI信号的调整

由于我们自己的测试环境的拓扑和AXI协议都与SOC测试的有一些差异，我们需要调整AXI信号。在我们原本的设计中，拓扑结构相对比较混乱，也没有考虑指令从MMIO读取的情况。为了适应SOC测试，我们把ZJV的对外接口修改为一组MMIO的AXI接口、一组Mem的ZXI接口和一个中断信号。此外，SOC需要处理器给出一些多余的AXI信号，比如各种id、qos、cache、prot、region等，我们也按要求给出了这些恒为零的信号。

#### SRAM的替换

在SOC测试中，需要将差分测试时使用的SyncReadMem替换为半导体厂家特殊编译器生成的verilog模块。在此过程中，主要涉及3个方面的困难：对于每块SRAM输入输出信号的替换、cache的重组、仿真环境的限制。由于编译器生成的verilog模块的I/O和SyncReadMem大不一样，而且我们是第一次使用双口SRAM的团队，因此我们仔细阅读了半导体厂商的手册，并请教了唐丹老师和刘彤老师，以进一步确保我们SRAM替换的正确性。此外，由于实际工艺中SRAM对宽度和深度的限制，我们需要把原来的cache进行重组，以适配半导体厂家的SRAM。仿真环境的限制也给我们SRAM替换后的验证工作造成了不少麻烦，厂家的verilog模块只能在VCS上运行，Verilator和Vivado上都会得到错得离谱的结果，这就要求我们对于所有错误只能通过看波形来解决，如果我们想验证更复杂的测试样例，需要再对VCS的SOC进行一次移植。

## 5. 其他工作

- RT-Thread操作系统的移植。
- XV6操作系统的移植与bug修复。
- Linux操作系统的移植。
- Coremark的移植。
- RISC-V test的移植。
- Rinux的移植。
- Spike模拟器外设拓展，添加uart、plic等。
- riscv-pk中加入了`--with-dts`选项，并且被官方merge。
- 将ZJV移植到Nexys A7 FPGA上，并且在FPGA上成功运行Rinux。

## 6.总结感悟

<!-- 总结迄今为止在整个项目中的收获, 为下一期项目提出建议. -->
### 6.1. 心得感悟

- 对RISC-V社区有了更深、更全面的了解，同时也对OS、设备树、工具链、模拟器、差分测试思想有了更深刻的理解，在技术方面得到全方面提高。
- 对ssh、x11、云FPGA服务器的运用更加熟练。
- 对计算机的拓扑结构更加了解，对多核、L2 cache等核外的知识理解更加深入。
- 对SRAM的替换、原子指令与压缩指令的设计、SRAM与register使用的平衡有了更深的理解，可以设计出更优秀的处理器核心。
- 在“一生一芯”项目中，除了Difftest的核心思想是被告知的，其他部分几乎全部是自己探索的，这使得我们在做工程的时候更有信心。通过该项目的锻炼，我们基本上可以做到给定核心思想和需求，就有信心从选择工具开始，完成目标任务。
- 更坚定了自己的研究方向，感受到了水平的提高，找到了研究课题的意义。

### 6.2. 下期建议

- 尽早与后端工程师进行交流，多普及一下有关设计中的具体约束，比如这次处理器设计中由于较晚才涉及cache存储模块设计的问题，处理器在L1 cache中均用的是双口SRAM，而如果了解到一般在这种设计中使用单口SRAM在面积、时序等方面较优的话应该会采取不同的设计。
- 尽早规范SOC所规定的CPU顶层接口。