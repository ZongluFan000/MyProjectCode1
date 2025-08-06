

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
        System.out.println("=== MEMORY DUMP [0x100..0x110] ===");
        for (int i = 0x100; i < 0x110; i++) {
            System.out.printf("mem[%04x]=%02x\n", i, memory[i] & 0xFF);
        }
    
        try {
            while (pc >= 0 && pc < memory.length) {
                int opcode = memory[pc] & 0xFF;
                String mnem = Actions.OPCODE_MAP.get(opcode);
    
                if (++instructionCount > MAX_INSTRUCTIONS) {
                    throw new RuntimeException("Max instruction count exceeded (可能死循环)");
                }
    
                // // Debug: show opcode info、、//////////////////////////////////////
                // System.out.println("pc=" + pc + " opcode=" + Integer.toHexString(opcode) + " mnem=" + mnem);
    
                // 直接查表分发，如果指令不存在直接报错
                if (mnem == null) {
                    throw new RuntimeException("Unknown opcode: " + Integer.toHexString(opcode) + " at pc=" + pc);
                }
    
                // LIT, LIT2 处理
                if (mnem.equals("LIT")) {
                    int value = memory[pc + 1] & 0xFF;
                    workStack.addLast(new StackElem(value, 1));
                    pc += 2;
                    continue;
                }
                if (mnem.equals("LIT2")) {
                    int value = ((memory[pc + 1] & 0xFF) << 8) | (memory[pc + 2] & 0xFF);
                    workStack.addLast(new StackElem(value, 2));
                    pc += 3;
                    continue;
                }
    
                // 查 ACTION_TABLE
                Actions.Action action = Actions.ACTION_TABLE.get(mnem);
                if (action == null) {
                    throw new RuntimeException("Not implemented action: " + mnem);
                }
    
                // 栈参数个数
                Deque<StackElem> stack = workStack; // 全部只用主栈 workStack（与JS一致），如需分返回栈可再判断
                StackElem[] args = new StackElem[action.nArgs];
                for (int i = action.nArgs - 1; i >= 0; --i) {
                    args[i] = stack.removeLast();
                }
    
                // 指令执行
                StackElem result = action.exec.apply(args, 0, 0, 0, this); // sz/rs/keep JS风格都可填0
                if (action.hasResult && result != null) {
                    stack.addLast(result);
                }
    
                // DEO/DEO2 终端输出
                if (mnem.equals("DEO") || mnem.equals("DEO2")) {
                    int port = args[1].value & 0xFF;
                    int val = args[0].value;
                    if (port == 0x18) { // 常规终端输出
                        System.out.print((char)(val & 0xFF));
                        System.out.flush();
                    }
                }
    
                // 程序终止
                if (mnem.equals("BRK")) {
                    System.out.println("\n*** PROGRAM HALTED ***");
                    showStacks();
                    break;
                }
    ///////////////////////////////////////////////////////////////////////
    System.out.println("After " + mnem + ": WorkStack = " + stack);
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



