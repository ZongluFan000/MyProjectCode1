package yaku.uxntal;

import java.util.Deque;
// import java.util.List;
// import java.util.ArrayList;

public class Actions {


    // BRK: 停机
    public static Interpreter.StackElem brk(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        System.exit(0);
        return null;
    }

    // INC: a+1
    public static Interpreter.StackElem inc(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = (args[0].value + 1);
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }

    // POP: 弹栈
    public static Interpreter.StackElem pop(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        // Interpreter 已经自动pop，无需做任何事
        return null;
    }

    // NIP: 弹次顶元素
    public static Interpreter.StackElem nip(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        // Interpreter 已自动pop出两个，重构目标栈
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[0]);
        return null;
    }

    // SWP: 交换栈顶两个
    public static Interpreter.StackElem swap(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[0]);
        st.addLast(args[1]);
        return null;
    }

    // ROT: 旋转栈顶三元素
    public static Interpreter.StackElem rot(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[1]);
        st.addLast(args[2]);
        st.addLast(args[0]);
        return null;
    }

    // DUP: 复制栈顶
    public static Interpreter.StackElem dup(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[0]);
        st.addLast(args[0]);
        return null;
    }

    // OVR: 复制次顶
    public static Interpreter.StackElem over(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[1]);
        st.addLast(args[0]);
        st.addLast(args[1]);
        return null;
    }

    // EQU: a==b
    public static Interpreter.StackElem equ(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return new Interpreter.StackElem((short) (args[1].value == args[0].value ? 1 : 0), 1);
    }

    // NEQ: a!=b
    public static Interpreter.StackElem neq(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return new Interpreter.StackElem((short) (args[1].value != args[0].value ? 1 : 0), 1);
    }

    // GTH: a > b
    public static Interpreter.StackElem gth(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return new Interpreter.StackElem((short) (args[1].value > args[0].value ? 1 : 0), 1);
    }

    // LTH: a < b
    public static Interpreter.StackElem lth(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return new Interpreter.StackElem((short) (args[1].value < args[0].value ? 1 : 0), 1);
    }

    // JMP: 跳转
    public static Interpreter.StackElem jmp(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        if (sz == 1) {
            int rel = (byte) args[0].value;
            uxn.pc += rel;
        } else {
            uxn.pc = (args[0].value & 0xFFFF) - 1;
        }
        return null;
    }

    // JCN: 条件跳转
    public static Interpreter.StackElem jcn(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int flag = args[1].value;
        if (flag != 0) {
            if (sz == 1) {
                int rel = (byte) args[0].value;
                uxn.pc += rel;
            } else {
                uxn.pc = (args[0].value & 0xFFFF) - 1;
            }
        }
        return null;
    }

    // JSR: 子程序跳转
    public static Interpreter.StackElem jsr(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int rel = (byte) args[0].value;
        uxn.returnStack.addLast(new Interpreter.StackElem((short) (uxn.pc + 1), 2));
        uxn.pc += rel;
        return null;
    }

    // STH: 栈间转移（work↔return）
    public static Interpreter.StackElem stash(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> src = (rs == 0 ? uxn.workStack : uxn.returnStack);
        Deque<Interpreter.StackElem> dst = (rs == 0 ? uxn.returnStack : uxn.workStack);
        dst.addLast(args[0]);
        return null;
    }

    // LDZ: 读取绝对地址（零页）
    public static Interpreter.StackElem ldz(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int addr = args[0].value & 0xFFFF;
        return new Interpreter.StackElem(uxn.loadMemory(addr, sz), sz);
    }

    // STZ: 写绝对地址（零页）
    public static Interpreter.StackElem stz(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int addr = args[1].value & 0xFFFF;
        uxn.storeMemory(addr, args[0].value, sz);
        return null;
    }

    // LDR: 读取相对地址
    public static Interpreter.StackElem ldr(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int rel = (byte) args[0].value;
        int addr = uxn.pc + rel + 1;
        return new Interpreter.StackElem(uxn.loadMemory(addr, sz), sz);
    }

    // STR: 写相对地址
    public static Interpreter.StackElem str(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int rel = (byte) args[0].value;
        int addr = uxn.pc + rel + 1;
        uxn.storeMemory(addr, args[1].value, sz);
        return null;
    }

    // LDA: 读取绝对地址
    public static Interpreter.StackElem lda(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int addr = args[0].value & 0xFFFF;
        return new Interpreter.StackElem(uxn.loadMemory(addr, sz), sz);
    }

    // STA: 写绝对地址
    public static Interpreter.StackElem sta(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int addr = args[1].value & 0xFFFF;
        uxn.storeMemory(addr, args[0].value, sz);
        return null;
    }

    // DEI: 端口读
    public static Interpreter.StackElem dei(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int port = args[0].value & 0xFF;
        // 这里只模拟最常见端口
        if (port == 0x04) return new Interpreter.StackElem((short) uxn.workStack.size(), 1);
        if (port == 0x05) return new Interpreter.StackElem((short) uxn.returnStack.size(), 1);
        System.err.println("[Warn] DEI port not implemented: " + port + " at pc=" + uxn.pc);
        return new Interpreter.StackElem((short) 0, 1);
    }

    // DEO: 端口写
    public static Interpreter.StackElem deo(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int port = args[0].value & 0xFF, val = args[1].value & 0xFF;
        if (port == 0x18) System.out.print((char) val);
        else if (port == 0x0F && val != 0) System.exit(val & 0x7F);
        // 其它端口忽略
        return null;
    }

    // ADD
    public static Interpreter.StackElem add(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value + args[0].value;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }

    // SUB
    public static Interpreter.StackElem sub(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value - args[0].value;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }

    // MUL
    public static Interpreter.StackElem mul(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value * args[0].value;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }

    // DIV
    public static Interpreter.StackElem div(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        if (args[0].value == 0) throw new ArithmeticException("Divide by zero");
        int res = args[1].value / args[0].value;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }

    // AND
    public static Interpreter.StackElem and(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value & args[0].value;
        return new Interpreter.StackElem((short) res, sz);
    }

    // ORA
    public static Interpreter.StackElem ora(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value | args[0].value;
        return new Interpreter.StackElem((short) res, sz);
    }

    // EOR
    public static Interpreter.StackElem eor(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value ^ args[0].value;
        return new Interpreter.StackElem((short) res, sz);
    }

    // SFT
    public static Interpreter.StackElem sft(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int shift = args[0].value & 0xFF;
        int val = args[1].value & (sz == 1 ? 0xFF : 0xFFFF);
        int res;
        if (shift > 15) res = val << (shift >> 4);
        else res = val >> shift;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }

    // LIT: 读取立即数到栈，Interpreter主循环已实现
    public static Interpreter.StackElem lit(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        
        return null;
    }

    // Immediate jump/call
    public static Interpreter.StackElem jmi(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int lo = uxn.memory[uxn.pc + 1] & 0xFF, hi = uxn.memory[uxn.pc + 2] & 0xFF;
        int rel = (short) ((hi << 8) | lo);
        uxn.pc = uxn.pc + 2 + rel;
        return null;
    }

    public static Interpreter.StackElem jsi(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        uxn.returnStack.addLast(new Interpreter.StackElem((short) (uxn.pc + 3), 2));
        int lo = uxn.memory[uxn.pc + 1] & 0xFF, hi = uxn.memory[uxn.pc + 2] & 0xFF;
        int rel = (short) ((hi << 8) | lo);
        uxn.pc = uxn.pc + 2 + rel;
        return null;
    }

    public static Interpreter.StackElem jci(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        if (args[0].value != 0) {
            int lo = uxn.memory[uxn.pc + 1] & 0xFF, hi = uxn.memory[uxn.pc + 2] & 0xFF;
            int rel = (short) ((hi << 8) | lo);
            uxn.pc = uxn.pc + 2 + rel;
        } else {
            uxn.pc += 2;
        }
        return null;
    }
}
