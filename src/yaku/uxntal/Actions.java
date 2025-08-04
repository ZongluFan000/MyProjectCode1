package yaku.uxntal;

import java.util.*;

public class Actions {
    // Action 元信息
    public static class Action {
        public final String name;
        public final int nArgs;
        public final boolean hasResult;
        public final boolean keepable;
        public final ActionExec exec;
        public Action(String name, int nArgs, boolean hasResult, boolean keepable, ActionExec exec) {
            this.name = name; this.nArgs = nArgs; this.hasResult = hasResult; this.keepable = keepable; this.exec = exec;
        }
    }
    @FunctionalInterface
    public interface ActionExec {
        Interpreter.StackElem apply(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm);
    }

    // 助记符映射
    public static final Map<Integer, String> OPCODE_MAP = new HashMap<>();
    static {
        OPCODE_MAP.put(0x00, "BRK");
        OPCODE_MAP.put(0x08, "INC");
        OPCODE_MAP.put(0x10, "POP");
        OPCODE_MAP.put(0x18, "NIP");
        OPCODE_MAP.put(0x20, "SWP");
        OPCODE_MAP.put(0x28, "ROT");
        OPCODE_MAP.put(0x30, "DUP");
        OPCODE_MAP.put(0x38, "OVR");
        OPCODE_MAP.put(0x40, "EQU");
        OPCODE_MAP.put(0x48, "NEQ");
        OPCODE_MAP.put(0x50, "GTH");
        OPCODE_MAP.put(0x58, "LTH");
        OPCODE_MAP.put(0x60, "JMP");
        OPCODE_MAP.put(0x68, "JCN");
        OPCODE_MAP.put(0x70, "JSR");
        OPCODE_MAP.put(0x78, "STH");
        OPCODE_MAP.put(0x80, "LDZ");
        OPCODE_MAP.put(0x88, "STZ");
        OPCODE_MAP.put(0x90, "LDR");
        OPCODE_MAP.put(0x98, "STR");
        OPCODE_MAP.put(0xA0, "LDA");
        OPCODE_MAP.put(0xA8, "STA");
        OPCODE_MAP.put(0xB0, "DEI");
        OPCODE_MAP.put(0xB8, "DEO");
        OPCODE_MAP.put(0xC0, "ADD");
        OPCODE_MAP.put(0xC8, "SUB");
        OPCODE_MAP.put(0xD0, "MUL");
        OPCODE_MAP.put(0xD8, "DIV");
        OPCODE_MAP.put(0xE0, "AND");
        OPCODE_MAP.put(0xE8, "ORA");
        OPCODE_MAP.put(0xF0, "EOR");
        OPCODE_MAP.put(0xF8, "SFT");
        OPCODE_MAP.put(0xFC, "LIT");
        OPCODE_MAP.put(0x80, "LIT"); // 兼容
        // Immediate variants（如有需要可扩展）
        OPCODE_MAP.put(0xC0, "JMI");
        OPCODE_MAP.put(0xC8, "JSI");
        OPCODE_MAP.put(0xD0, "JCI");
    }

    // 指令元信息表
    public static final Map<String, Action> ACTION_TABLE = new HashMap<>();
    static {
        ACTION_TABLE.put("BRK", new Action("BRK", 0, false, false, Actions::brk));
        ACTION_TABLE.put("INC", new Action("INC", 1, true, true, Actions::inc));
        ACTION_TABLE.put("POP", new Action("POP", 1, false, true, Actions::pop));
        ACTION_TABLE.put("NIP", new Action("NIP", 2, false, true, Actions::nip));
        ACTION_TABLE.put("SWP", new Action("SWP", 2, false, true, Actions::swap));
        ACTION_TABLE.put("ROT", new Action("ROT", 3, false, true, Actions::rot));
        ACTION_TABLE.put("DUP", new Action("DUP", 1, false, true, Actions::dup));
        ACTION_TABLE.put("OVR", new Action("OVR", 2, false, true, Actions::over));
        ACTION_TABLE.put("EQU", new Action("EQU", 2, true, true, Actions::equ));
        ACTION_TABLE.put("NEQ", new Action("NEQ", 2, true, true, Actions::neq));
        ACTION_TABLE.put("GTH", new Action("GTH", 2, true, true, Actions::gth));
        ACTION_TABLE.put("LTH", new Action("LTH", 2, true, true, Actions::lth));
        ACTION_TABLE.put("JMP", new Action("JMP", 1, false, false, Actions::jmp));
        ACTION_TABLE.put("JCN", new Action("JCN", 2, false, false, Actions::jcn));
        ACTION_TABLE.put("JSR", new Action("JSR", 1, false, false, Actions::jsr));
        ACTION_TABLE.put("STH", new Action("STH", 1, false, true, Actions::stash));
        ACTION_TABLE.put("LDZ", new Action("LDZ", 1, true, true, Actions::ldz));
        ACTION_TABLE.put("STZ", new Action("STZ", 2, false, true, Actions::stz));
        ACTION_TABLE.put("LDR", new Action("LDR", 1, true, true, Actions::ldr));
        ACTION_TABLE.put("STR", new Action("STR", 2, false, true, Actions::str));
        ACTION_TABLE.put("LDA", new Action("LDA", 1, true, true, Actions::lda));
        ACTION_TABLE.put("STA", new Action("STA", 2, false, true, Actions::sta));
        ACTION_TABLE.put("DEI", new Action("DEI", 1, true, true, Actions::dei));
        ACTION_TABLE.put("DEO", new Action("DEO", 2, false, true, Actions::deo));
        ACTION_TABLE.put("ADD", new Action("ADD", 2, true, true, Actions::add));
        ACTION_TABLE.put("SUB", new Action("SUB", 2, true, true, Actions::sub));
        ACTION_TABLE.put("MUL", new Action("MUL", 2, true, true, Actions::mul));
        ACTION_TABLE.put("DIV", new Action("DIV", 2, true, true, Actions::div));
        ACTION_TABLE.put("AND", new Action("AND", 2, true, true, Actions::and));
        ACTION_TABLE.put("ORA", new Action("ORA", 2, true, true, Actions::ora));
        ACTION_TABLE.put("EOR", new Action("EOR", 2, true, true, Actions::eor));
        ACTION_TABLE.put("SFT", new Action("SFT", 2, true, true, Actions::sft));
        ACTION_TABLE.put("LIT", new Action("LIT", 0, false, false, Actions::lit));
        // Immediate jumps
        ACTION_TABLE.put("JMI", new Action("JMI", 0, false, false, Actions::jmi));
        ACTION_TABLE.put("JSI", new Action("JSI", 0, false, false, Actions::jsi));
        ACTION_TABLE.put("JCI", new Action("JCI", 1, false, false, Actions::jci));
    }

    // ==== 指令实现 ====
    public static Interpreter.StackElem brk(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        System.exit(0); return null;
    }
    public static Interpreter.StackElem inc(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = (args[0].value + 1);
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }
    public static Interpreter.StackElem pop(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return null;
    }
    public static Interpreter.StackElem nip(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[0]);
        return null;
    }
    public static Interpreter.StackElem swap(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[0]);
        st.addLast(args[1]);
        return null;
    }
    public static Interpreter.StackElem rot(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[1]);
        st.addLast(args[2]);
        st.addLast(args[0]);
        return null;
    }
    public static Interpreter.StackElem dup(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[0]);
        st.addLast(args[0]);
        return null;
    }
    public static Interpreter.StackElem over(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[1]);
        st.addLast(args[0]);
        st.addLast(args[1]);
        return null;
    }
    public static Interpreter.StackElem equ(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return new Interpreter.StackElem((short) (args[1].value == args[0].value ? 1 : 0), 1);
    }
    public static Interpreter.StackElem neq(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return new Interpreter.StackElem((short) (args[1].value != args[0].value ? 1 : 0), 1);
    }
    public static Interpreter.StackElem gth(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return new Interpreter.StackElem((short) (args[1].value > args[0].value ? 1 : 0), 1);
    }
    public static Interpreter.StackElem lth(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return new Interpreter.StackElem((short) (args[1].value < args[0].value ? 1 : 0), 1);
    }
    public static Interpreter.StackElem jmp(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        if (sz == 1) {
            int rel = (byte) args[0].value;
            uxn.pc += rel;
        } else {
            uxn.pc = (args[0].value & 0xFFFF) - 1;
        }
        return null;
    }
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
    public static Interpreter.StackElem jsr(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int rel = (byte) args[0].value;
        uxn.returnStack.addLast(new Interpreter.StackElem((short) (uxn.pc + 1), 2));
        uxn.pc += rel;
        return null;
    }
    public static Interpreter.StackElem stash(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> src = (rs == 0 ? uxn.workStack : uxn.returnStack);
        Deque<Interpreter.StackElem> dst = (rs == 0 ? uxn.returnStack : uxn.workStack);
        dst.addLast(args[0]);
        return null;
    }
    public static Interpreter.StackElem ldz(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int addr = args[0].value & 0xFFFF;
        return new Interpreter.StackElem(uxn.loadMemory(addr, sz), sz);
    }
    public static Interpreter.StackElem stz(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int addr = args[1].value & 0xFFFF;
        uxn.storeMemory(addr, args[0].value, sz);
        return null;
    }
    public static Interpreter.StackElem ldr(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int rel = (byte) args[0].value;
        int addr = uxn.pc + rel + 1;
        return new Interpreter.StackElem(uxn.loadMemory(addr, sz), sz);
    }
    public static Interpreter.StackElem str(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int rel = (byte) args[0].value;
        int addr = uxn.pc + rel + 1;
        uxn.storeMemory(addr, args[1].value, sz);
        return null;
    }
    public static Interpreter.StackElem lda(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int addr = args[0].value & 0xFFFF;
        return new Interpreter.StackElem(uxn.loadMemory(addr, sz), sz);
    }
    public static Interpreter.StackElem sta(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int addr = args[1].value & 0xFFFF;
        uxn.storeMemory(addr, args[0].value, sz);
        return null;
    }
    public static Interpreter.StackElem dei(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int port = args[0].value & 0xFF;
        // 常见端口
        if (port == 0x04) return new Interpreter.StackElem((short) uxn.workStack.size(), 1);
        if (port == 0x05) return new Interpreter.StackElem((short) uxn.returnStack.size(), 1);
        System.err.println("[Warn] DEI port not implemented: " + port + " at pc=" + uxn.pc);
        return new Interpreter.StackElem((short) 0, 1);
    }
    public static Interpreter.StackElem deo(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int port = args[0].value & 0xFF, val = args[1].value & 0xFF;
        if (port == 0x18) System.out.print((char) val);
        else if (port == 0x0F && val != 0) System.exit(val & 0x7F);
        return null;
    }
    public static Interpreter.StackElem add(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value + args[0].value;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }
    public static Interpreter.StackElem sub(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value - args[0].value;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }
    public static Interpreter.StackElem mul(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value * args[0].value;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }
    public static Interpreter.StackElem div(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        if (args[0].value == 0) throw new ArithmeticException("Divide by zero");
        int res = args[1].value / args[0].value;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }
    public static Interpreter.StackElem and(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value & args[0].value;
        return new Interpreter.StackElem((short) res, sz);
    }
    public static Interpreter.StackElem ora(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value | args[0].value;
        return new Interpreter.StackElem((short) res, sz);
    }
    public static Interpreter.StackElem eor(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value ^ args[0].value;
        return new Interpreter.StackElem((short) res, sz);
    }
    public static Interpreter.StackElem sft(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int shift = args[0].value & 0xFF;
        int val = args[1].value & (sz == 1 ? 0xFF : 0xFFFF);
        int res;
        if (shift > 15) res = val << (shift >> 4);
        else res = val >> shift;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }
    public static Interpreter.StackElem lit(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        // Interpreter 已实现，Actions中不处理
        return null;
    }
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
