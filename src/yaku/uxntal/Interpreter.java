package yaku.uxntal;

import java.util.*;

import static yaku.uxntal.Definitions.*;

public class Interpreter {
    // 内存和两个栈
    private byte[] memory;
    private int pc; // 程序计数器
    private Deque<Integer> wst; // 工作栈
    private Deque<Integer> rst; // 返回栈
    private boolean running;

    // 构造方法，传入内存镜像（通常是 Encoder.encode 的结果）
    public Interpreter(byte[] memory, int entryPoint) {
        this.memory = memory;
        this.pc = entryPoint;
        this.wst = new ArrayDeque<>();
        this.rst = new ArrayDeque<>();
        this.running = true;
    }

    // 主解释循环
    public void run() {
        while (running && pc >= 0 && pc < memory.length) {
            int opcode = Byte.toUnsignedInt(memory[pc++]);
            // 解释一条指令
            step(opcode);
        }
    }

    // 单步执行
    private void step(int opcode) {
        switch (opcode) {
            case 0x00: // BRK
                running = false;
                System.out.println("[BRK] 程序终止");
                break;
            case 0x01: // INC
                wst.push(wst.pop() + 1);
                break;
            case 0x02: // POP
                wst.pop();
                break;
            case 0x03: // NIP
                int a3 = wst.pop(); wst.pop(); wst.push(a3);
                break;
            case 0x04: // SWP
                int a4 = wst.pop(), b4 = wst.pop(); wst.push(a4); wst.push(b4);
                break;
            case 0x05: // ROT
                int a5 = wst.pop(), b5 = wst.pop(), c5 = wst.pop();
                wst.push(b5); wst.push(a5); wst.push(c5);
                break;
            case 0x06: // DUP
                wst.push(wst.peek());
                break;
            case 0x07: // OVR
                int a7 = wst.pop(), b7 = wst.peek(); wst.push(a7); wst.push(b7);
                break;
            case 0x08: // EQU
                int a8 = wst.pop(), b8 = wst.pop(); wst.push(a8 == b8 ? 1 : 0);
                break;
            case 0x09: // NEQ
                int a9 = wst.pop(), b9 = wst.pop(); wst.push(a9 != b9 ? 1 : 0);
                break;
            case 0x0A: // GTH
                int aA = wst.pop(), bA = wst.pop(); wst.push(bA > aA ? 1 : 0);
                break;
            case 0x0B: // LTH
                int aB = wst.pop(), bB = wst.pop(); wst.push(bB < aB ? 1 : 0);
                break;
            case 0x0C: // JMP
                pc = fetch16();
                break;
            case 0x0D: // JCN
                int cond = wst.pop();
                int addr = fetch16();
                if (cond != 0) pc = addr;
                break;
            case 0x0E: // JSR
                rst.push(pc + 2);
                pc = fetch16();
                break;
            case 0x0F: // STH
                rst.push(wst.pop());
                break;
            case 0x10: // LDZ
                wst.push(Byte.toUnsignedInt(memory[wst.pop() & 0xFF]));
                break;
            case 0x11: // STZ
                memory[wst.pop() & 0xFF] = (byte) (int) wst.pop();
                break;
            case 0x12: // LDR
                wst.push(Byte.toUnsignedInt(memory[wst.pop() & 0xFFFF]));
                break;
            case 0x13: // STR
                memory[wst.pop() & 0xFFFF] = (byte) (int) wst.pop();
                break;
            case 0x14: // LDA
                int addr14 = wst.pop();
                int val14 = (Byte.toUnsignedInt(memory[addr14 & 0xFFFF]) << 8) | Byte.toUnsignedInt(memory[(addr14 + 1) & 0xFFFF]);
                wst.push(val14);
                break;
            case 0x15: // STA
                int addr15 = wst.pop();
                int val15 = wst.pop();
                memory[addr15 & 0xFFFF] = (byte) ((val15 >> 8) & 0xFF);
                memory[(addr15 + 1) & 0xFFFF] = (byte) (val15 & 0xFF);
                break;
            case 0x16: // DEI (设备输入，简化为0)
                wst.push(0);
                break;
            case 0x17: // DEO (设备输出，简化为控制台输出)
                System.out.println("[DEO] 输出: " + wst.pop());
                break;
            case 0x18: // ADD
                int a18 = wst.pop(), b18 = wst.pop(); wst.push((a18 + b18) & 0xFFFF);
                break;
            case 0x19: // SUB
                int a19 = wst.pop(), b19 = wst.pop(); wst.push((b19 - a19) & 0xFFFF);
                break;
            case 0x1A: // MUL
                int a1A = wst.pop(), b1A = wst.pop(); wst.push((a1A * b1A) & 0xFFFF);
                break;
            case 0x1B: // DIV
                int a1B = wst.pop(), b1B = wst.pop();
                wst.push(a1B == 0 ? 0 : (b1B / a1B));
                break;
            case 0x1C: // AND
                int a1C = wst.pop(), b1C = wst.pop(); wst.push(a1C & b1C);
                break;
            case 0x1D: // ORA
                int a1D = wst.pop(), b1D = wst.pop(); wst.push(a1D | b1D);
                break;
            case 0x1E: // EOR
                int a1E = wst.pop(), b1E = wst.pop(); wst.push(a1E ^ b1E);
                break;
            case 0x1F: // SFT
                int sft = wst.pop();
                int v = wst.pop();
                int r = (v >> (sft & 0x0F)) << ((sft >> 4) & 0x0F);
                wst.push(r);
                break;

            case 0x80: // LIT
            int literal = Byte.toUnsignedInt(memory[pc++]);
            wst.push(literal);
            break;
            


            default:
                System.err.printf("未知指令: 0x%02X，PC=0x%04X\n", opcode, pc - 1);
                running = false;
        }
        printStack();
    }

    // 辅助方法：取内存中的16位数（大端）
    private int fetch16() {
        int hi = Byte.toUnsignedInt(memory[pc++]);
        int lo = Byte.toUnsignedInt(memory[pc++]);
        return (hi << 8) | lo;
    }

    // 打印当前两个栈的内容
    private void printStack() {
        System.out.print("[WST] ");
        System.out.println(wst);
        System.out.print("[RST] ");
        System.out.println(rst);
    }
}
