// package yaku.uxntal;

// import java.util.*;

// public class Interpreter {
//     public final byte[] memory;
//     public final Deque<StackElem> workStack = new ArrayDeque<>();
//     public final Deque<StackElem> returnStack = new ArrayDeque<>();
//     public int pc = 0x100; // 默认入口
//     public int instructionCount = 0;
//     public static final int MAX_INSTRUCTIONS = 1000000;

//     // 栈元素结构
//     public static class StackElem {
//         public final int value;
//         public final int size; // 1 = byte, 2 = short
//         public StackElem(int value, int size) { this.value = value; this.size = size; }
//         @Override public String toString() {
//             return (size == 2) ? String.format("%04x", value & 0xFFFF) : String.format("%02x", value & 0xFF);
//         }
//     }


   

//     // Action 元信息
//     public static class Action {
//         public final String name;
//         public final int nArgs;
//         public final boolean hasResult;
//         public final boolean keepable;
//         public final ActionExec exec;
//         public Action(String name, int nArgs, boolean hasResult, boolean keepable, ActionExec exec) {
//             this.name = name; this.nArgs = nArgs; this.hasResult = hasResult; this.keepable = keepable; this.exec = exec;
//         }
//     }
//     @FunctionalInterface public interface ActionExec {
//         StackElem apply(StackElem[] args, int sz, int rs, int keep, Interpreter vm);
//     }

//     // 指令元信息表（补全全部指令）
//     public static final Map<String, Action> ACTION_TABLE = new HashMap<>();
//     static {
//         ACTION_TABLE.put("BRK", new Action("BRK", 0, false, false, Actions::brk));
//         ACTION_TABLE.put("INC", new Action("INC", 1, true, true, Actions::inc));
//         ACTION_TABLE.put("POP", new Action("POP", 1, false, true, Actions::pop));
//         ACTION_TABLE.put("NIP", new Action("NIP", 2, false, true, Actions::nip));
//         ACTION_TABLE.put("SWP", new Action("SWP", 2, false, true, Actions::swap));
//         ACTION_TABLE.put("ROT", new Action("ROT", 3, false, true, Actions::rot));
//         ACTION_TABLE.put("DUP", new Action("DUP", 1, false, true, Actions::dup));
//         ACTION_TABLE.put("OVR", new Action("OVR", 2, false, true, Actions::over));
//         ACTION_TABLE.put("EQU", new Action("EQU", 2, true, true, Actions::equ));
//         ACTION_TABLE.put("NEQ", new Action("NEQ", 2, true, true, Actions::neq));
//         ACTION_TABLE.put("GTH", new Action("GTH", 2, true, true, Actions::gth));
//         ACTION_TABLE.put("LTH", new Action("LTH", 2, true, true, Actions::lth));
//         ACTION_TABLE.put("JMP", new Action("JMP", 1, false, false, Actions::jmp));
//         ACTION_TABLE.put("JCN", new Action("JCN", 2, false, false, Actions::jcn));
//         ACTION_TABLE.put("JSR", new Action("JSR", 1, false, false, Actions::jsr));
//         ACTION_TABLE.put("STH", new Action("STH", 1, false, true, Actions::stash));
//         ACTION_TABLE.put("LDZ", new Action("LDZ", 1, true, true, Actions::ldz));
//         ACTION_TABLE.put("STZ", new Action("STZ", 2, false, true, Actions::stz));
//         ACTION_TABLE.put("LDR", new Action("LDR", 1, true, true, Actions::ldr));
//         ACTION_TABLE.put("STR", new Action("STR", 2, false, true, Actions::str));
//         ACTION_TABLE.put("LDA", new Action("LDA", 1, true, true, Actions::lda));
//         ACTION_TABLE.put("STA", new Action("STA", 2, false, true, Actions::sta));
//         ACTION_TABLE.put("DEI", new Action("DEI", 1, true, true, Actions::dei));
//         ACTION_TABLE.put("DEO", new Action("DEO", 2, false, true, Actions::deo));
//         ACTION_TABLE.put("ADD", new Action("ADD", 2, true, true, Actions::add));
//         ACTION_TABLE.put("SUB", new Action("SUB", 2, true, true, Actions::sub));
//         ACTION_TABLE.put("MUL", new Action("MUL", 2, true, true, Actions::mul));
//         ACTION_TABLE.put("DIV", new Action("DIV", 2, true, true, Actions::div));
//         ACTION_TABLE.put("AND", new Action("AND", 2, true, true, Actions::and));
//         ACTION_TABLE.put("ORA", new Action("ORA", 2, true, true, Actions::ora));
//         ACTION_TABLE.put("EOR", new Action("EOR", 2, true, true, Actions::eor));
//         ACTION_TABLE.put("SFT", new Action("SFT", 2, true, true, Actions::sft));
//         ACTION_TABLE.put("LIT", new Action("LIT", 0, false, false, Actions::lit));
//         // Immediate jumps if implemented
//         ACTION_TABLE.put("JMI", new Action("JMI", 0, false, false, Actions::jmi));
//         ACTION_TABLE.put("JSI", new Action("JSI", 0, false, false, Actions::jsi));
//         ACTION_TABLE.put("JCI", new Action("JCI", 1, false, false, Actions::jci));
//     }
    

//     // 指令码→助记符表
//     public static final Map<Integer, String> OPCODE_MAP = new HashMap<>();
//     static {
//         OPCODE_MAP.put(0x00, "BRK");
//         OPCODE_MAP.put(0x08, "INC");
//         OPCODE_MAP.put(0x10, "POP");
//         OPCODE_MAP.put(0x18, "NIP");
//         OPCODE_MAP.put(0x20, "SWP");
//         OPCODE_MAP.put(0x28, "ROT");
//         OPCODE_MAP.put(0x30, "DUP");
//         OPCODE_MAP.put(0x38, "OVR");
//         OPCODE_MAP.put(0x40, "EQU");
//         OPCODE_MAP.put(0x48, "NEQ");
//         OPCODE_MAP.put(0x50, "GTH");
//         OPCODE_MAP.put(0x58, "LTH");
//         OPCODE_MAP.put(0x60, "JMP");
//         OPCODE_MAP.put(0x68, "JCN");
//         OPCODE_MAP.put(0x70, "JSR");
//         OPCODE_MAP.put(0x78, "STH");
//         OPCODE_MAP.put(0x80, "LDZ");
//         OPCODE_MAP.put(0x88, "STZ");
//         OPCODE_MAP.put(0x90, "LDR");
//         OPCODE_MAP.put(0x98, "STR");
//         OPCODE_MAP.put(0xA0, "LDA");
//         OPCODE_MAP.put(0xA8, "STA");
//         OPCODE_MAP.put(0xB0, "DEI");
//         OPCODE_MAP.put(0xB8, "DEO");
//         OPCODE_MAP.put(0xC0, "ADD");
//         OPCODE_MAP.put(0xC8, "SUB");
//         OPCODE_MAP.put(0xD0, "MUL");
//         OPCODE_MAP.put(0xD8, "DIV");
//         OPCODE_MAP.put(0xE0, "AND");
//         OPCODE_MAP.put(0xE8, "ORA");
//         OPCODE_MAP.put(0xF0, "EOR");
//         OPCODE_MAP.put(0xF8, "SFT");
//         OPCODE_MAP.put(0xFC, "LIT"); // LIT 高位也是 0xF8，但通常用 0x80，兼容写法
//         OPCODE_MAP.put(0x80, "LIT");
//         // Immediate family (非 Uxn 标准，但有的实现加了)
//         OPCODE_MAP.put(0xC0, "JMI");
//         OPCODE_MAP.put(0xC8, "JSI");
//         OPCODE_MAP.put(0xD0, "JCI");
//     }
    

//     public Interpreter(byte[] memory) {
//         this.memory = Arrays.copyOf(memory, memory.length);
//         this.pc = 0x0100; // 默认入口
//     }

//     public void run() {
//         try {
//             while (pc >= 0 && pc < memory.length) {
//                 if (++instructionCount > MAX_INSTRUCTIONS)
//                     throw new RuntimeException("Max instruction count exceeded (可能死循环)");

//                 int opcode = memory[pc] & 0xFF;
//                 int instr = opcode & 0xF8;
//                 int sz = ((opcode & 0x04) != 0) ? 2 : 1;
//                 int rs = ((opcode & 0x02) != 0) ? 1 : 0;
//                 int keep = ((opcode & 0x01) != 0) ? 1 : 0;

//                 String mnem = OPCODE_MAP.get(instr);
//                 if (mnem == null) throw new RuntimeException("Unknown opcode: " + Integer.toHexString(instr) + " at pc=" + pc);

//                 if (mnem.equals("LIT")) {
//                     executeLit(sz, rs, keep);
//                     pc += sz;
//                 } else if (isJump(mnem)) {
//                     if (mnem.equals("JMP") || mnem.equals("JSR")) {
//                         executeJump(mnem, sz, rs);
//                     } else if (mnem.equals("JCN")) {
//                         executeCondJump(sz, rs);
//                     }
//                     // JMI/JSI/JCI 可以仿照实现
//                 } else if (isStackOperation(mnem)) {
//                     executeStackOp(mnem, sz, rs, keep);
//                     pc++;
//                 } else if (isMemoryOperation(mnem)) {
//                     executeMemOp(mnem, sz, rs);
//                     pc++;
//                 } else if (isDeviceOperation(mnem)) {
//                     executeDeviceOp(mnem, sz, rs);
//                     pc++;
//                 } else if (isArithmeticOperation(mnem)) {
//                     executeArithOp(mnem, sz, rs, keep);
//                     pc++;
//                 } else if (isComparisonOperation(mnem)) {
//                     executeCompareOp(mnem, sz, rs, keep);
//                     pc++;
//                 } else if (mnem.equals("BRK")) {
//                     System.out.println("*** PROGRAM HALTED ***");
//                     showStacks();
//                     break;
//                 } else {
//                     throw new RuntimeException("未实现指令: " + mnem);
//                 }
//             }
//         } catch (Exception e) {
//             System.err.println("Error at pc=" + pc + ": " + e.getMessage());
//             showStacks();
//             throw e;
//         }
//     }


//      // ---- LIT 指令 ----
//      private void executeLit(int sz, int rs, int keep) {
//         int value;
//         if (sz == 2) {
//             value = ((memory[pc + 1] & 0xFF) << 8) | (memory[pc + 2] & 0xFF);
//         } else {
//             value = memory[pc + 1] & 0xFF;
//         }
//         push(rs, value, sz);
//         if (keep == 1) push(rs, value, sz); // 保持原有值
//     }

//     // ---- 跳转 ----
//     private void executeJump(String mnem, int sz, int rs) {
//         Deque<StackElem> stack = rs == 0 ? workStack : returnStack;
//         int target;
//         if (sz == 2) {
//             target = pop(stack, 2).value;
//             if (mnem.equals("JSR")) returnStack.addLast(new StackElem(pc + 1, 2));
//             pc = target - 1; // -1 因为主循环会 pc++
//         } else {
//             int offset = (byte) pop(stack, 1).value;
//             if (mnem.equals("JSR")) returnStack.addLast(new StackElem(pc + 1, 2));
//             pc += offset;
//         }
//     }

//     private void executeCondJump(int sz, int rs) {
//         Deque<StackElem> stack = rs == 0 ? workStack : returnStack;
//         int cond = pop(stack, 1).value;
//         if (cond != 0) {
//             executeJump("JMP", sz, rs);
//         } else {
//             pc++;
//         }
//     }

//     // ---- Stack 操作 ----
//     private void executeStackOp(String mnem, int sz, int rs, int keep) {
//         Deque<StackElem> stack = rs == 0 ? workStack : returnStack;
//         switch (mnem) {
//             case "POP": pop(stack, sz); break;
//             case "DUP":
//                 StackElem top = stack.peekLast();
//                 stack.addLast(new StackElem(top.value, sz));
//                 break;
//             case "SWP":
//                 StackElem a = pop(stack, sz), b = pop(stack, sz);
//                 stack.addLast(a); stack.addLast(b);
//                 break;
//             case "ROT":
//                 StackElem c = pop(stack, sz), b2 = pop(stack, sz), a2 = pop(stack, sz);
//                 stack.addLast(b2); stack.addLast(c); stack.addLast(a2);
//                 break;
//             case "NIP":
//                 pop(stack, sz); break;
//             case "OVR":
//                 StackElem t2 = pop(stack, sz), t1 = stack.peekLast();
//                 stack.addLast(t2); stack.addLast(new StackElem(t1.value, t1.size));
//                 break;
//             case "STH": // 栈切换
//                 Deque<StackElem> target = rs == 0 ? returnStack : workStack;
//                 StackElem val = pop(stack, sz);
//                 target.addLast(val);
//                 break;
//         }
//         if (keep == 1 && stack.peekLast() != null) {
//             StackElem last = stack.peekLast();
//             stack.addLast(new StackElem(last.value, last.size));
//         }
//     }

//     // ---- 内存操作 ----
//     private void executeMemOp(String mnem, int sz, int rs) {
//         Deque<StackElem> stack = rs == 0 ? workStack : returnStack;
//         switch (mnem) {
//             case "LDZ": // 零页读
//                 int addr = pop(stack, 1).value;
//                 int v = loadMemory(addr, sz);
//                 stack.addLast(new StackElem(v, sz));
//                 break;
//             case "STZ":
//                 int v2 = pop(stack, sz).value;
//                 int addr2 = pop(stack, 1).value;
//                 storeMemory(addr2, (short) v2, sz);
//                 break;
//             case "LDA":
//                 int addr3 = pop(stack, sz).value;
//                 int v3 = loadMemory(addr3, sz);
//                 stack.addLast(new StackElem(v3, sz));
//                 break;
//             case "STA":
//                 int v4 = pop(stack, sz).value;
//                 int addr4 = pop(stack, sz).value;
//                 storeMemory(addr4, (short) v4, sz);
//                 break;
//             // 其它类似 LDR/STR 可以按需扩展
//         }
//     }

//     // ---- 设备/端口 ----
//     private void executeDeviceOp(String mnem, int sz, int rs) {
//         Deque<StackElem> stack = rs == 0 ? workStack : returnStack;
//         switch (mnem) {
//             case "DEO":
//                 int port = pop(stack, 1).value;
//                 int val = pop(stack, 1).value;
//                 if (port == 0x18) { // 控制台输出
//                     System.out.print((char) val);
//                 }
//                 break;
//             case "DEI":
//                 int port2 = pop(stack, 1).value;
//                 stack.addLast(new StackElem(0, sz)); // 输入恒为0
//                 break;
//         }
//     }

//     // ---- 算术操作 ----
//     private void executeArithOp(String mnem, int sz, int rs, int keep) {
//         Deque<StackElem> stack = rs == 0 ? workStack : returnStack;
//         int b = pop(stack, sz).value;
//         int a = pop(stack, sz).value;
//         int result = 0;
//         switch (mnem) {
//             case "INC": result = (a + 1) & mask(sz); break;
//             case "ADD": result = (a + b) & mask(sz); break;
//             case "SUB": result = (a - b) & mask(sz); break;
//             case "MUL": result = (a * b) & mask(sz); break;
//             case "DIV": result = b == 0 ? 0 : (a / b); break;
//             case "AND": result = (a & b); break;
//             case "ORA": result = (a | b); break;
//             case "EOR": result = (a ^ b); break;
//             case "SFT": // shift
//                 int left = (b & 0xF0) >> 4, right = b & 0x0F;
//                 result = a << left;
//                 result = result >> right;
//                 result = result & mask(sz);
//                 break;
//         }
//         stack.addLast(new StackElem(result, sz));
//         if (keep == 1) {
//             stack.addLast(new StackElem(a, sz));
//             stack.addLast(new StackElem(b, sz));
//         }
//     }

//     // ---- 比较 ----
//     private void executeCompareOp(String mnem, int sz, int rs, int keep) {
//         Deque<StackElem> stack = rs == 0 ? workStack : returnStack;
//         int b = pop(stack, sz).value;
//         int a = pop(stack, sz).value;
//         int res = 0;
//         switch (mnem) {
//             case "EQU": res = (a == b) ? 1 : 0; break;
//             case "NEQ": res = (a != b) ? 1 : 0; break;
//             case "GTH": res = (a > b) ? 1 : 0; break;
//             case "LTH": res = (a < b) ? 1 : 0; break;
//         }
//         stack.addLast(new StackElem(res, 1));
//         if (keep == 1) {
//             stack.addLast(new StackElem(a, sz));
//             stack.addLast(new StackElem(b, sz));
//         }
//     }

//     // ==== 工具 ====
//     private int mask(int sz) { return (sz == 2) ? 0xFFFF : 0xFF; }

//     private StackElem pop(Deque<StackElem> stack, int sz) {
//         if (stack.isEmpty()) throw new RuntimeException("Stack underflow");
//         StackElem e = stack.removeLast();
//         if (e.size != sz) throw new RuntimeException("Type mismatch on stack: expect sz=" + sz + " but got " + e.size);
//         return e;
//     }

//     private boolean isJump(String mnem) {
//         return mnem.equals("JMP") || mnem.equals("JSR") || mnem.equals("JCN") || mnem.equals("JMI") || mnem.equals("JSI");
//     }
//     private boolean isStackOperation(String mnem) {
//         return Set.of("POP", "NIP", "SWP", "ROT", "DUP", "OVR", "STH").contains(mnem);
//     }
//     private boolean isMemoryOperation(String mnem) {
//         return Set.of("LDZ", "STZ", "LDR", "STR", "LDA", "STA").contains(mnem);
//     }
//     private boolean isDeviceOperation(String mnem) {
//         return Set.of("DEI", "DEO").contains(mnem);
//     }
//     private boolean isArithmeticOperation(String mnem) {
//         return Set.of("INC", "ADD", "SUB", "MUL", "DIV", "AND", "ORA", "EOR", "SFT").contains(mnem);
//     }
//     private boolean isComparisonOperation(String mnem) {
//         return Set.of("EQU", "NEQ", "GTH", "LTH").contains(mnem);
//     }

//     public int loadMemory(int addr, int sz) {
//         if (addr < 0 || addr + sz > memory.length)
//             throw new RuntimeException("Memory access out of bounds: " + addr + " sz=" + sz);
//         if (sz == 1) return memory[addr] & 0xFF;
//         else return ((memory[addr] & 0xFF) << 8) | (memory[addr + 1] & 0xFF);
//     }

//     public void storeMemory(int addr, short value, int sz) {
//         if (addr < 0 || addr + sz > memory.length)
//             throw new RuntimeException("Memory write out of bounds: " + addr + " sz=" + sz);
//         if (sz == 1) memory[addr] = (byte) (value & 0xFF);
//         else {
//             memory[addr] = (byte) ((value >> 8) & 0xFF);
//             memory[addr + 1] = (byte) (value & 0xFF);
//         }
//     }

//     public void push(int rs, int value, int size) {
//         Deque<StackElem> st = (rs == 0 ? workStack : returnStack);
//         st.addLast(new StackElem(value, size));
//     }

//     public void showStacks() {
//         System.out.println("WorkStack: " + stackToString(workStack));
//         System.out.println("ReturnStack: " + stackToString(returnStack));
//     }
//     private String stackToString(Deque<StackElem> stack) {
//         if (stack.isEmpty()) return "[]";
//         StringBuilder sb = new StringBuilder("[");
//         for (StackElem e : stack) sb.append(e).append(" ");
//         sb.append("]");
//         return sb.toString();
//     }
// }



package yaku.uxntal;

import java.util.*;

public class Interpreter {
    public final byte[] memory;
    public final Deque<StackElem> workStack = new ArrayDeque<>();
    public final Deque<StackElem> returnStack = new ArrayDeque<>();
    public int pc = 0x100;
    public int instructionCount = 0;
    public static final int MAX_INSTRUCTIONS = 1000000;

    public static class StackElem {
        public final int value;
        public final int size;
        public StackElem(int value, int size) { this.value = value; this.size = size; }
        @Override public String toString() {
            return (size == 2) ? String.format("%04x", value & 0xFFFF) : String.format("%02x", value & 0xFF);
        }
    }

    public Interpreter(byte[] memory) {
        this.memory = Arrays.copyOf(memory, memory.length);
        this.pc = 0x100;
    }

    public void run() {
        try {
            while (pc >= 0 && pc < memory.length) {
                if (++instructionCount > MAX_INSTRUCTIONS)
                    throw new RuntimeException("Max instruction count exceeded (可能死循环)");

                int opcode = memory[pc] & 0xFF;
                int instr = opcode & 0xF8;
                int sz = ((opcode & 0x04) != 0) ? 2 : 1;
                int rs = ((opcode & 0x02) != 0) ? 1 : 0;
                int keep = ((opcode & 0x01) != 0) ? 1 : 0;

                String mnem = Actions.OPCODE_MAP.get(instr);
                if (mnem == null) throw new RuntimeException("Unknown opcode: " + Integer.toHexString(instr) + " at pc=" + pc);

                // --- 指令执行统一分发 ---
                Actions.Action action = Actions.ACTION_TABLE.get(mnem);
                if (action == null) throw new RuntimeException("Not implemented action: " + mnem);

                // LIT指令特殊处理，带立即数
                if (mnem.equals("LIT")) {
                    int value;
                    if (sz == 2) value = ((memory[pc + 1] & 0xFF) << 8) | (memory[pc + 2] & 0xFF);
                    else value = memory[pc + 1] & 0xFF;
                    Deque<StackElem> targetStack = (rs == 0 ? workStack : returnStack);
                    targetStack.addLast(new StackElem(value, sz));
                    if (keep == 1) targetStack.addLast(new StackElem(value, sz));
                    pc += sz + 1;
                    continue;
                }

                // 从对应栈弹出操作数
                StackElem[] args = new StackElem[action.nArgs];
                Deque<StackElem> stack = (rs == 0 ? workStack : returnStack);
                for (int i = action.nArgs - 1; i >= 0; --i) {
                    args[i] = stack.removeLast();
                }

                // 执行具体指令
                StackElem result = action.exec.apply(args, sz, rs, keep, this);
                if (action.hasResult && result != null) {
                    stack.addLast(result);
                }
                if (mnem.equals("BRK")) {
                    System.out.println("*** PROGRAM HALTED ***");
                    showStacks();
                    break;
                }
                pc++;
            }
        } catch (Exception e) {
            System.err.println("Error at pc=" + pc + ": " + e.getMessage());
            showStacks();
            throw e;
        }
    }

    // 其它工具函数保持不变
    public int loadMemory(int addr, int sz) {
        if (addr < 0 || addr + sz > memory.length)
            throw new RuntimeException("Memory access out of bounds: " + addr + " sz=" + sz);
        if (sz == 1) return memory[addr] & 0xFF;
        else return ((memory[addr] & 0xFF) << 8) | (memory[addr + 1] & 0xFF);
    }
    public void storeMemory(int addr, int value, int sz) {
        if (addr < 0 || addr + sz > memory.length)
            throw new RuntimeException("Memory write out of bounds: " + addr + " sz=" + sz);
        if (sz == 1) memory[addr] = (byte) (value & 0xFF);
        else {
            memory[addr] = (byte) ((value >> 8) & 0xFF);
            memory[addr + 1] = (byte) (value & 0xFF);
        }
    }
    public void showStacks() {
        System.out.println("WorkStack: " + stackToString(workStack));
        System.out.println("ReturnStack: " + stackToString(returnStack));
    }
    private String stackToString(Deque<StackElem> stack) {
        if (stack.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (StackElem e : stack) sb.append(e).append(" ");
        sb.append("]");
        return sb.toString();
    }
}
