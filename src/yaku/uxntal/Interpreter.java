

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

/////////////////////////////////////////////////////
System.out.println("=== MEMORY DUMP [0x100..0x110] ===");
for (int i = 0x100; i < 0x110; i++) {
    System.out.printf("mem[%04x]=%02x\n", i, memory[i] & 0xFF);
}






        try {
            while (pc >= 0 && pc < memory.length) {
                if (++instructionCount > MAX_INSTRUCTIONS)
                    throw new RuntimeException("Max instruction count exceeded (可能死循环)");
            
                int opcode = memory[pc] & 0xFF;
                int instr = opcode & 0xF8;
                int sz = ((opcode & 0x04) != 0) ? 2 : 1;
                int rs = ((opcode & 0x02) != 0) ? 1 : 0;
                int keep = ((opcode & 0x01) != 0) ? 1 : 0;
            
                // 关键分支：LIT/LIT2/带flag的立即数
                if (instr == 0x80) {
                    int value;
                    if (sz == 2) value = ((memory[pc + 1] & 0xFF) << 8) | (memory[pc + 2] & 0xFF);
                    else value = memory[pc + 1] & 0xFF;
                    Deque<StackElem> targetStack = (rs == 0 ? workStack : returnStack);
                    targetStack.addLast(new StackElem(value, sz));
                    if (keep == 1) targetStack.addLast(new StackElem(value, sz));
                    pc += sz + 1;
                    continue;
                }
            


                String mnem = Actions.OPCODE_MAP.get(instr);
                /////////////////////////////////////////////
                System.out.println("opcode=" + Integer.toHexString(instr) + " mnem=" + mnem + " pc=" + pc);
                /// /////////////////////////////////////////
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



