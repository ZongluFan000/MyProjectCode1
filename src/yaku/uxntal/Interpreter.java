package yaku.uxntal;

import java.util.*;

public class Interpreter {
    public final byte[] memory;
    public final Deque<StackElem> workStack = new ArrayDeque<>();
    public final Deque<StackElem> returnStack = new ArrayDeque<>();
    public int pc = 0;

    // 栈元素结构
    public static class StackElem {
        public final short value;
        public final int size; // 1 = byte, 2 = short
        public StackElem(short value, int size) { this.value = value; this.size = size; }
        @Override public String toString() {
            return (size == 2) ? String.format("%04x", value & 0xFFFF) : String.format("%02x", value & 0xFF);
        }
    }

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
    @FunctionalInterface public interface ActionExec {
        StackElem apply(StackElem[] args, int sz, int rs, int keep, Interpreter vm);
    }

    // 指令元信息表（补全全部指令）
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
        // Immediate jumps if implemented
        ACTION_TABLE.put("JMI", new Action("JMI", 0, false, false, Actions::jmi));
        ACTION_TABLE.put("JSI", new Action("JSI", 0, false, false, Actions::jsi));
        ACTION_TABLE.put("JCI", new Action("JCI", 1, false, false, Actions::jci));
    }
    

    // 指令码→助记符表
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
        OPCODE_MAP.put(0xFC, "LIT"); // LIT 高位也是 0xF8，但通常用 0x80，兼容写法
        OPCODE_MAP.put(0x80, "LIT");
        // Immediate family (非 Uxn 标准，但有的实现加了)
        OPCODE_MAP.put(0xC0, "JMI");
        OPCODE_MAP.put(0xC8, "JSI");
        OPCODE_MAP.put(0xD0, "JCI");
    }
    

    public Interpreter(byte[] memory) {
        this.memory = Arrays.copyOf(memory, memory.length);
        this.pc = 0x0100; // 默认入口
    }

    public void run() {
        try {
            while (pc >= 0 && pc < memory.length) {
                int opcode = memory[pc] & 0xFF;
                int instr = opcode & 0xF8;
                int sz = ((opcode & 0x04) != 0) ? 2 : 1;
                int rs = ((opcode & 0x02) != 0) ? 1 : 0;
                int keep = ((opcode & 0x01) != 0) ? 1 : 0;

                String mnem = OPCODE_MAP.get(instr);
                if (mnem == null) throw new RuntimeException("Unknown opcode: " + Integer.toHexString(instr) + " at pc=" + pc);
                Action act = ACTION_TABLE.get(mnem);
                if (act == null) throw new RuntimeException("Action not found: " + mnem + " at pc=" + pc);

                Deque<StackElem> stack = (rs == 0 ? workStack : returnStack);
                StackElem[] args = popArgs(stack, act.nArgs, sz, mnem, keep);

                StackElem result = act.exec.apply(args, sz, rs, keep, this);

                if (act.hasResult && result != null) {
                    stack.addLast(new StackElem(result.value, sz));
                }

                if (!isJump(mnem)) pc += 1;
            }
        } catch (Exception e) {
            System.err.println("Error at pc=" + pc + ": " + e.getMessage());
            showStacks();
            throw e;
        }
    }

    // ==== 带类型参数弹出（完全兼容 Perl/JS pop_arg 行为） ====
    private StackElem[] popArgs(Deque<StackElem> stack, int nArgs, int sz, String instr, int keep) {
        if (nArgs == 0) return new StackElem[0];
        if (stack.size() < nArgs)
            throw new RuntimeException("Stack underflow at pc=" + pc + " for " + instr + " (need " + nArgs + ", have " + stack.size() + ")");
        List<StackElem> argList = new ArrayList<>();
        // 按原版顺序逆序收集
        for (int i = 0; i < nArgs; i++) argList.add(0, stack.removeLast());
        StackElem[] args = new StackElem[nArgs];
        for (int i = 0; i < nArgs; i++) {
            StackElem e = argList.get(i);
            if (e.size == sz) {
                args[i] = e;
            } else if (e.size == 2 && sz == 1) {
                System.err.println("[Warn] Type mismatch, short→byte for " + instr + " @pc=" + pc);
                args[i] = new StackElem((short)(e.value & 0xFF), 1);
            } else if (e.size == 1 && sz == 2) {
                System.err.println("[Warn] Type mismatch, byte→short for " + instr + " @pc=" + pc);
                if (i+1 < nArgs && argList.get(i+1).size == 1) {
                    args[i] = new StackElem(
                        (short)(((argList.get(i+1).value & 0xFF) << 8) | (e.value & 0xFF)), 2);
                    i++;
                } else {
                    throw new RuntimeException("Stack type error: Need two byte for short at pc=" + pc);
                }
            } else {
                throw new RuntimeException("Stack type error at pc=" + pc + ": want " + sz + " byte, got " + e.size);
            }
        }
        return args;
    }

    private boolean isJump(String mnem) {
        return mnem.equals("JMP") || mnem.equals("JSR") || mnem.equals("JCI") || mnem.equals("JMI") || mnem.equals("JSI");
    }

    private void showStacks() {
        System.err.println("WorkStack: " + stackToString(workStack));
        System.err.println("ReturnStack: " + stackToString(returnStack));
    }
    private String stackToString(Deque<StackElem> stack) {
        if (stack.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (StackElem e : stack) sb.append(e).append(" ");
        sb.append("]");
        return sb.toString();
    }

    public Short loadMemory(int addr, int sz) {
        if (addr < 0 || addr+sz > memory.length)
            throw new RuntimeException("Memory access out of bounds: " + addr + " sz=" + sz);
        if (sz == 1) return (short)(memory[addr] & 0xFF);
        else return (short)(((memory[addr] & 0xFF) << 8) | (memory[addr+1] & 0xFF));
    }
    public void storeMemory(int addr, short value, int sz) {
        if (addr < 0 || addr+sz > memory.length)
            throw new RuntimeException("Memory write out of bounds: " + addr + " sz=" + sz);
        if (sz == 1) memory[addr] = (byte)(value & 0xFF);
        else {
            memory[addr] = (byte)((value >> 8) & 0xFF);
            memory[addr+1] = (byte)(value & 0xFF);
        }
    }

    public void push(int rs, short value, int size) {
        Deque<StackElem> st = (rs == 0 ? workStack : returnStack);
        st.addLast(new StackElem(value, size));
    }
}
