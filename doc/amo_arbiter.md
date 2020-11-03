## 这是一个`amo arbiter`草稿，现在只是为了理清作者的思路。



流水段如下：

`reg_exe_dtlb` `reg_dtlb_mem1` `reg_mem1_mem2` `reg_mem2_mem3` `reg_mem3_wb`



每个周期的行为如下：

周期1：理论上，当A指令进入`reg_dtlb_mem1`就应该往`reg_dtlb_mem1`插入气泡，而`reg_exe_dtlb`之前的流水段都被停住。

周期2：这个A指令在进入`reg_mem1_mem2`应该再去插一个气泡。

周期3：在A进入`reg_mem2_mem3`发出`amo arbiter`停滞请求，且在后面的周期一直保持住，知道后面明显说明降下来停滞请求，这个信号才能降下来。注意此时，`reg_dtlb_mem1`和`reg_mem1_mem2`都是气泡，所以说不会对`cache`产生副作用。

//

周期4+x：漫长的等待，直到等到`cache`返回有效信号。注意，这里的x可能为0，比如说`cache`命中。把读出的内容用一个寄存器缓存住，然后进入计算状态。

周期5+x：根据`reg_mem2_mem3`中的内容进行判断，经过`amo alu`的计算，放在缓存内。

周期6+x：把要写入的内容，重定向到`cache`，并且强行升起写信号。

周期7+x+y：等待直到`cache`再次升起有效信号。注意，这里能这么等的前提是，`cache`返回的有效，一定在请求有效的时候才会升起，就不会在请求无效的时候赋值xxx之类的。等到这个信号回来，一切恢复正常，停滞请求以组合逻辑的方式降下来，并且将之前读取的数值强行传出去的信号也以组合逻辑的形式升起。