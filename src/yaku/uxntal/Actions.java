package yaku.uxntal;



public final class Actions {
    private Actions() {}

    private static int maskBySize(int v, int sz) { return (sz == 2) ? (v & 0xFFFF) : (v & 0xFF); }

    private static void push(Interpreter vm, Definitions.Token t, int value, int sz) {
        vm.active(t).addLast(new Interpreter.StackElem(maskBySize(value, sz), sz));
    }

    // ===== 算术 / 逻辑 / 位移 =====
    public static void inc(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        push(vm, t, args[0].value + 1, sz);
    }
    public static void add(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int a = args[0].value, b = args[1].value; push(vm, t, b + a, sz);
    }
    public static void sub(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int a = args[0].value, b = args[1].value; push(vm, t, b - a, sz);
    }
    public static void mul(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int a = args[0].value, b = args[1].value; push(vm, t, b * a, sz);
    }
    public static void div(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int a = args[0].value, b = args[1].value; push(vm, t, (a == 0 ? 0 : (b / a)), sz);
    }
    public static void and(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int a = args[0].value, b = args[1].value; push(vm, t, b & a, sz);
    }
    public static void ora(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int a = args[0].value, b = args[1].value; push(vm, t, b | a, sz);
    }
    public static void eor(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int a = args[0].value, b = args[1].value; push(vm, t, b ^ a, sz);
    }
    public static void sft(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int right = args[0].value & 0x0F;
        int left  = (args[0].value >> 4) & 0x0F;
        int v = args[1].value;
        v = (v >> right) & ((sz == 2) ? 0xFFFF : 0xFF);
        v = (v << left)  & ((sz == 2) ? 0xFFFF : 0xFF);
        push(vm, t, v, sz);
    }

    // ===== 比较 =====
    public static void equ(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        push(vm, t, (args[1].value == args[0].value) ? 1 : 0, 1);
    }
    public static void neq(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        push(vm, t, (args[1].value != args[0].value) ? 1 : 0, 1);
    }
    public static void gth(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        push(vm, t, (args[1].value > args[0].value) ? 1 : 0, 1);
    }
    public static void lth(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        push(vm, t, (args[1].value < args[0].value) ? 1 : 0, 1);
    }

    // ===== 控制/跳转 =====
    public static void brk(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        // 由 Interpreter.executeInstr 返回 true 结束
    }
    // public static void jmp(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
    //     int rel = (byte) (args[0].value & 0xFF);
    //     vm.setPc((vm.getPc() + rel) & 0xFFFF);
    // }
    // public static void jcn(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
    //     int cond = args[0].value & 0xFF;
    //     int rel  = (byte) (args[1].value & 0xFF);
    //     if (cond != 0) vm.setPc((vm.getPc() + rel) & 0xFFFF);
    // }
    // public static void jsr(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
    //     int rel = (byte) (args[0].value & 0xFF);
    //     int ret = (vm.getPc() + 1) & 0xFFFF;
    //     vm.ret().addLast(new Interpreter.StackElem(ret, 2));
    //     vm.setPc((vm.getPc() + rel) & 0xFFFF);
    // }
// ===== 控制/跳转 =====
public static void jmp(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
    if (sz == 2) {
        // 16 位绝对跳（含 r 位：JMP2 / JMP2r）
        int addr = args[0].value & 0xFFFF;
        vm.setPc(addr);
    } else {
        // 8 位相对跳（JMP）
        int rel = (byte) (args[0].value & 0xFF);
        vm.setPc((vm.getPc() + rel) & 0xFFFF);
    }
}

public static void jcn(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
    int cond = args[0].value & 0xFF;
    if (cond == 0) return;

    if (sz == 2) {
        // 16 位绝对跳（JCN2 / JCN2r）
        int addr = args[1].value & 0xFFFF;
        vm.setPc(addr);
    } else {
        // 8 位相对跳（JCN）
        int rel  = (byte) (args[1].value & 0xFF);
        vm.setPc((vm.getPc() + rel) & 0xFFFF);
    }
}

public static void jsr(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
    // 返回地址 = 当前 pc 后的下一条指令
    int ret = (vm.getPc() + 1) & 0xFFFF;
    vm.ret().addLast(new Interpreter.StackElem(ret, 2));

    if (sz == 2) {
        // 16 位绝对调用（JSR2 / JSR2r）
        int addr = args[0].value & 0xFFFF;
        vm.setPc(addr);
    } else {
        // 8 位相对调用（JSR）
        int rel = (byte) (args[0].value & 0xFF);
        vm.setPc((vm.getPc() + rel) & 0xFFFF);
    }
}




    // JMI/JSI/JCI 这里按照“从活跃栈取额外参数”的语义实现
    public static void jmi(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        var S = vm.active(t);
        int addr = S.removeLast().value & 0xFFFF;
        int ret  = (vm.getPc() + 1) & 0xFFFF;
        vm.ret().addLast(new Interpreter.StackElem(ret, 2));
        vm.setPc(addr);
    }
    public static void jsi(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        var S = vm.active(t);
        int addr = S.removeLast().value & 0xFFFF;
        int ret  = (vm.getPc() + 1) & 0xFFFF;
        vm.ret().addLast(new Interpreter.StackElem(ret, 2));
        vm.setPc(addr);
    }
    public static void jci(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        var S = vm.active(t);
        int cond = S.removeLast().value & 0xFF;
        int addr = S.removeLast().value & 0xFFFF;
        if (cond != 0) vm.setPc(addr);
    }

    // ===== 内存/设备 =====
    // LDZ: 栈顶为 zero-page 地址（8-bit），读取 sz 宽度的值
    public static void ldz(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int z = args[0].value & 0xFF;
        int v = (sz == 2)
                ? ((vm.mem()[z] & 0xFF) | ((vm.mem()[(z + 1) & 0xFF] & 0xFF) << 8))
                : (vm.mem()[z] & 0xFF);
        push(vm, t, v, sz);
    }
    // STZ: 值在下、地址在上 => args[0]=value, args[1]=zpage
    public static void stz(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int val = args[0].value;
        int z   = args[1].value & 0xFF;
        if (sz == 2) {
            vm.mem()[z] = (byte) (val & 0xFF);
            vm.mem()[(z + 1) & 0xFF] = (byte) ((val >> 8) & 0xFF);
        } else {
            vm.mem()[z] = (byte) (val & 0xFF);
        }
    }
    // LDR/STR: 相对 PC 偏移（8-bit 有符号），按 sz 宽度
    public static void ldr(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int rel  = (byte) (args[0].value & 0xFF);
        int addr = (vm.getPc() + rel) & 0xFFFF;
        int v = (sz == 2)
                ? ((vm.mem()[addr] & 0xFF) | ((vm.mem()[(addr + 1) & 0xFFFF] & 0xFF) << 8))
                : (vm.mem()[addr] & 0xFF);
        push(vm, t, v, sz);
    }
    public static void str(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int val  = args[0].value;
        int rel  = (byte) (args[1].value & 0xFF);
        int addr = (vm.getPc() + rel) & 0xFFFF;
        if (sz == 2) {
            vm.mem()[addr] = (byte) (val & 0xFF);
            vm.mem()[(addr + 1) & 0xFFFF] = (byte) ((val >> 8) & 0xFF);
        } else {
            vm.mem()[addr] = (byte) (val & 0xFF);
        }
    }
    public static void lda(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int addr = args[0].value & 0xFFFF;
        int v = (sz == 2)
                ? ((vm.mem()[addr] & 0xFF) | ((vm.mem()[(addr + 1) & 0xFFFF] & 0xFF) << 8))
                : (vm.mem()[addr] & 0xFF);
        push(vm, t, v, sz);
    }
    public static void sta(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        int val  = args[0].value;
        int addr = args[1].value & 0xFFFF;
        if (sz == 2) {
            vm.mem()[addr] = (byte) (val & 0xFF);
            vm.mem()[(addr + 1) & 0xFFFF] = (byte) ((val >> 8) & 0xFF);
        } else {
            vm.mem()[addr] = (byte) (val & 0xFF);
        }
    }
    // 设备：DEI/DEO 端口在上；DEO 为“值在下、端口在上”
    public static void dei(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        // 简化：未实现其他设备输入，读取 0
        push(vm, t, 0, sz);
    }
    // public static void deo(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
    //     int val  = maskBySize(args[0].value, sz); // 值在下
    //     int port = args[1].value & 0xFF;         // 端口在上
    //     if (port == 0x18) { // Console/write
    //         System.out.print((char) (val & 0xFF));
    //         System.out.flush();
    //     } else if (port == 0x0F) { // System/state
    //         if ((val & 0xFF) != 0) System.exit(val & 0x7F);
    //     }
    // }

        // public static void deo(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        //         int a = maskBySize(args[0].value, sz); // 栈顶
        //         int b = maskBySize(args[1].value, sz); // 次顶
        //         int port, val;
        //         // 优先把“像端口”的数当端口（0x18=stdout, 0x19=stderr, 0x0F=system/exit）
        //         if ((b & 0xFF) == 0x18 || (b & 0xFF) == 0x19 || (b & 0xFF) == 0x0F) { val = a; port = b & 0xFF; }
        //         else if ((a & 0xFF) == 0x18 || (a & 0xFF) == 0x19 || (a & 0xFF) == 0x0F) { val = b; port = a & 0xFF; }
        //         else { val = a; port = b & 0xFF; } // 维持原来的“值在上、端口在下”的假设
        
        //         switch (port) {
        //             case 0x18 -> { System.out.print((char)(val & 0xFF)); System.out.flush(); }
        //             case 0x19 -> { System.err.print((char)(val & 0xFF)); System.err.flush(); }
        //             case 0x0F -> { if ((val & 0xFF) != 0) System.exit(val & 0x7F); }
        //             default -> { /* 其它端口：忽略或在需要时扩展 */ }
        //         }
        //     }

        private static boolean isKnownPort(int v) {
            int p = v & 0xFF;
            return p == 0x18 || p == 0x19 || p == 0x0F;
        }
        
        /**
         * DEO：向设备端口写值。
         * 兼容两种压栈顺序：
         *   1) 传统写法（更常见）：  值 在下，端口 在上   —— 示例：#41 #18 DEO
         *   2) 另一种写法：          端口 在下，值 在上   —— 示例：#18 #41 DEO
         * 我们用“看起来像端口”的那个当端口，另一边当值；都不像时退回到原先的假设（值在下、端口在上）。
         */
        public static void deo(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
            int a = maskBySize(args[0].value, sz); // 次顶
            int b = maskBySize(args[1].value, sz); // 顶
            int port, val;
        
            if (isKnownPort(b)) { port = b & 0xFF; val = a; }
            else if (isKnownPort(a)) { port = a & 0xFF; val = b; }
            else { port = b & 0xFF; val = a; } // 默认维持“值在下、端口在上”的老假设
        
            switch (port) {
                case 0x18 -> { // Console/write
                    System.out.print((char)(val & 0xFF));
                    System.out.flush();
                }
                case 0x19 -> { // Console/error
                    System.err.print((char)(val & 0xFF));
                    System.err.flush();
                }
                case 0x0F -> { // System/state（非零退出）
                    if ((val & 0xFF) != 0) System.exit(val & 0x7F);
                }
                default -> {
                    // 其它设备端口：当前忽略，如果之后需要可以在这里扩展
                }
            }
        }

    // ===== 栈操作（仅用 args，写回活跃栈）=====
    public static void pop(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        // 丢弃 args[0]
    }
    public static void nip(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        // x y -> y
        push(vm, t, args[1].value, args[1].size);
    }
    public static void swp(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        // x y -> y x
        push(vm, t, args[1].value, args[1].size);
        push(vm, t, args[0].value, args[0].size);
    }
    public static void rot(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        // x y z -> y z x
        push(vm, t, args[1].value, args[1].size);
        push(vm, t, args[2].value, args[2].size);
        push(vm, t, args[0].value, args[0].size);
    }
    public static void dup(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        // x -> x x
        push(vm, t, args[0].value, sz);
        push(vm, t, args[0].value, sz);
    }
    public static void ovr(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        // x y -> x y x
        push(vm, t, args[0].value, args[0].size);
        push(vm, t, args[1].value, args[1].size);
        push(vm, t, args[0].value, sz);
    }
    public static void sth(Interpreter vm, Definitions.Token t, Interpreter.StackElem[] args, int sz) {
        // 将活跃栈顶部元素推到另一栈
        var other = (t.stack == 1) ? vm.work() : vm.ret();
        other.addLast(new Interpreter.StackElem(args[0].value, sz));
    }
}

