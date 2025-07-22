// package yaku.uxntal;

// import java.util.*;

// import static yaku.uxntal.Definitions.*;

// public class Interpreter {
//     // 内存和两个栈
//     private byte[] memory;
//     private int pc; // 程序计数器
//     private Deque<Integer> wst; // 工作栈
//     private Deque<Integer> rst; // 返回栈
//     private boolean running;

//     // 构造方法，传入内存镜像（通常是 Encoder.encode 的结果）
//     public Interpreter(byte[] memory, int entryPoint) {
//         this.memory = memory;
//         this.pc = entryPoint;
//         this.wst = new ArrayDeque<>();
//         this.rst = new ArrayDeque<>();
//         this.running = true;
//     }

//     // 主解释循环
//     public void run() {
//         while (running && pc >= 0 && pc < memory.length) {
//             int opcode = Byte.toUnsignedInt(memory[pc++]);
//             // 解释一条指令
//             step(opcode);
//         }
//     }

//     // 单步执行
//     private void step(int opcode) {
//         switch (opcode) {
//             case 0x00: // BRK
//                 running = false;
//                 System.out.println("[BRK] 程序终止");
//                 break;
//             case 0x01: // INC
//                 wst.push(wst.pop() + 1);
//                 break;
//             case 0x02: // POP
//                 wst.pop();
//                 break;
//             case 0x03: // NIP
//                 int a3 = wst.pop(); wst.pop(); wst.push(a3);
//                 break;
//             case 0x04: // SWP
//                 int a4 = wst.pop(), b4 = wst.pop(); wst.push(a4); wst.push(b4);
//                 break;
//             case 0x05: // ROT
//                 int a5 = wst.pop(), b5 = wst.pop(), c5 = wst.pop();
//                 wst.push(b5); wst.push(a5); wst.push(c5);
//                 break;
//             case 0x06: // DUP
//                 wst.push(wst.peek());
//                 break;
//             case 0x07: // OVR
//                 int a7 = wst.pop(), b7 = wst.peek(); wst.push(a7); wst.push(b7);
//                 break;
//             case 0x08: // EQU
//                 int a8 = wst.pop(), b8 = wst.pop(); wst.push(a8 == b8 ? 1 : 0);
//                 break;
//             case 0x09: // NEQ
//                 int a9 = wst.pop(), b9 = wst.pop(); wst.push(a9 != b9 ? 1 : 0);
//                 break;
//             case 0x0A: // GTH
//                 int aA = wst.pop(), bA = wst.pop(); wst.push(bA > aA ? 1 : 0);
//                 break;
//             case 0x0B: // LTH
//                 int aB = wst.pop(), bB = wst.pop(); wst.push(bB < aB ? 1 : 0);
//                 break;
//             case 0x0C: // JMP
//                 pc = fetch16();
//                 break;
//             case 0x0D: // JCN
//                 int cond = wst.pop();
//                 int addr = fetch16();
//                 if (cond != 0) pc = addr;
//                 break;
//             case 0x0E: // JSR
//                 rst.push(pc + 2);
//                 pc = fetch16();
//                 break;
//             case 0x0F: // STH
//                 rst.push(wst.pop());
//                 break;
//             case 0x10: // LDZ
//                 wst.push(Byte.toUnsignedInt(memory[wst.pop() & 0xFF]));
//                 break;
//             case 0x11: // STZ
//                 memory[wst.pop() & 0xFF] = (byte) (int) wst.pop();
//                 break;
//             case 0x12: // LDR
//                 wst.push(Byte.toUnsignedInt(memory[wst.pop() & 0xFFFF]));
//                 break;
//             case 0x13: // STR
//                 memory[wst.pop() & 0xFFFF] = (byte) (int) wst.pop();
//                 break;
//             case 0x14: // LDA
//                 int addr14 = wst.pop();
//                 int val14 = (Byte.toUnsignedInt(memory[addr14 & 0xFFFF]) << 8) | Byte.toUnsignedInt(memory[(addr14 + 1) & 0xFFFF]);
//                 wst.push(val14);
//                 break;
//             case 0x15: // STA
//                 int addr15 = wst.pop();
//                 int val15 = wst.pop();
//                 memory[addr15 & 0xFFFF] = (byte) ((val15 >> 8) & 0xFF);
//                 memory[(addr15 + 1) & 0xFFFF] = (byte) (val15 & 0xFF);
//                 break;
//             case 0x16: // DEI (设备输入，简化为0)
//                 wst.push(0);
//                 break;
//             case 0x17: // DEO (设备输出，简化为控制台输出)
//                 System.out.println("[DEO] 输出: " + wst.pop());
//                 break;
//             case 0x18: // ADD
//                 int a18 = wst.pop(), b18 = wst.pop(); wst.push((a18 + b18) & 0xFFFF);
//                 break;
//             case 0x19: // SUB
//                 int a19 = wst.pop(), b19 = wst.pop(); wst.push((b19 - a19) & 0xFFFF);
//                 break;
//             case 0x1A: // MUL
//                 int a1A = wst.pop(), b1A = wst.pop(); wst.push((a1A * b1A) & 0xFFFF);
//                 break;
//             case 0x1B: // DIV
//                 int a1B = wst.pop(), b1B = wst.pop();
//                 wst.push(a1B == 0 ? 0 : (b1B / a1B));
//                 break;
//             case 0x1C: // AND
//                 int a1C = wst.pop(), b1C = wst.pop(); wst.push(a1C & b1C);
//                 break;
//             case 0x1D: // ORA
//                 int a1D = wst.pop(), b1D = wst.pop(); wst.push(a1D | b1D);
//                 break;
//             case 0x1E: // EOR
//                 int a1E = wst.pop(), b1E = wst.pop(); wst.push(a1E ^ b1E);
//                 break;
//             case 0x1F: // SFT
//                 int sft = wst.pop();
//                 int v = wst.pop();
//                 int r = (v >> (sft & 0x0F)) << ((sft >> 4) & 0x0F);
//                 wst.push(r);
//                 break;

//             case 0x80: // LIT
//             int literal = Byte.toUnsignedInt(memory[pc++]);
//             wst.push(literal);
//             break;
            


//             default:
//                 System.err.printf("未知指令: 0x%02X，PC=0x%04X\n", opcode, pc - 1);
//                 running = false;
//         }
//         printStack();
//     }

//     // 辅助方法：取内存中的16位数（大端）
//     private int fetch16() {
//         int hi = Byte.toUnsignedInt(memory[pc++]);
//         int lo = Byte.toUnsignedInt(memory[pc++]);
//         return (hi << 8) | lo;
//     }

//     // 打印当前两个栈的内容
//     private void printStack() {
//         System.out.print("[WST] ");
//         System.out.println(wst);
//         System.out.print("[RST] ");
//         System.out.println(rst);
//     }
// }



package yaku.uxntal;

import java.util.*;
import static yaku.uxntal.Definitions.*;

public class Interpreter {
    private byte[] memory;
    private Map<Integer, String> reverseLabelTable;
    private int pc; // 程序计数器
    private int wordSize = 1;
    private String currentParent = "MAIN";
    private Deque<String> callStack = new ArrayDeque<>();
    private List<Deque<StackValue>> stacks = Arrays.asList(
            new ArrayDeque<>(), // 工作栈 (0)
            new ArrayDeque<>()  // 返回栈 (1)
    );
    private boolean running = true;

    // 栈元素结构（值+字节宽度）
    static class StackValue {
        int value;
        int size; // 1=byte, 2=short
        StackValue(int value, int size) {
            this.value = value; this.size = size;
        }
        public String toString() {
            return String.format("%s(%s)", value, size==1?"byte":"short");
        }
    }

    public Interpreter(byte[] memory, Map<Integer, String> reverseLabelTable) {
        this.memory = memory;
        this.reverseLabelTable = reverseLabelTable != null ? reverseLabelTable : new HashMap<>();
        this.pc = 0x0100;
        callStack.push("MAIN");
    }

    public void run() {
        System.out.println("*** RUNNING ***");
        while (running) {
            if (pc < 0 || pc > 0xFFFF) throw new RuntimeException("Program counter out of bounds.");
            Token token = fetchTokenFromMemory(pc);

            // -------- Parent/CallStack 逻辑 (对应 Perl 解释器主循环) --------
            if (token.type == TokenType.INSTR) {
                String instr = token.value;
                int sz = token.size;
                int rs = token.stack;

                if ("JSR".equals(instr) && sz == 2 && rs == 0) { // JSR2
                    if (reverseLabelTable.containsKey(pc-3)) {
                        currentParent = reverseLabelTable.get(pc-3);
                        callStack.push(currentParent);
                    } else {
                        currentParent = "<lambda>";
                        callStack.push(currentParent);
                    }
                }
                else if ("JSI".equals(instr)) {
                    if (reverseLabelTable.containsKey(pc+1)) {
                        currentParent = reverseLabelTable.get(pc+1);
                        callStack.push(currentParent);
                    }
                }
                else if ("JMP".equals(instr) && sz == 2 && rs == 0) {
                    if (reverseLabelTable.containsKey(pc-3)) {
                        currentParent = reverseLabelTable.get(pc-3);
                    }
                }
                else if ("JMI".equals(instr)) {
                    if (reverseLabelTable.containsKey(pc+1)) {
                        currentParent = reverseLabelTable.get(pc+1);
                    }
                }
                // 弹出子例程
                if ("JMP".equals(instr) && sz == 2 && rs == 1) {
                    if (!callStack.isEmpty()) callStack.pop();
                    currentParent = callStack.isEmpty() ? "MAIN" : callStack.peek();
                }
            }

            // -------- 指令执行 --------
            executeInstr(token);

            pc++;
        }
    }

    private Token fetchTokenFromMemory(int pc) {
        int opcode = Byte.toUnsignedInt(memory[pc]);
        // LIT 单独处理
        if (opcode == 0x80) {
            // 下一个字节即为常量（只支持单字节/简单LIT）
            return new Token(TokenType.INSTR, "LIT", 1, 0, 0, pc);
        }
        // 指令
        for (Map.Entry<String, Integer> entry : Definitions.OPCODE_MAP.entrySet()) {
            if (entry.getValue() == opcode) {
                return new Token(TokenType.INSTR, entry.getKey(), 1, 0, 0, pc);
            }
        }
        return new Token(TokenType.UNKNOWN, Integer.toHexString(opcode), 1, pc);
    }

    private void executeInstr(Token token) {
        String instr = token.value;
        int sz = token.size;
        int rs = token.stack; // 工作栈 or 返回栈
        int keep = token.keep;

        // 0. LIT 指令
        if ("LIT".equals(instr)) {
            int literal = Byte.toUnsignedInt(memory[++pc]);
            stackPush(0, literal, 1);
            printStacks();
            return;
        }

        // 1. 栈操作和简单指令
        switch (instr) {
            case "BRK":
                running = false;
                System.out.println("*** DONE ***");
                showStacks();
                return;
            case "INC":
                checkStack(0, 1); // 至少1元素
                StackValue incv = stackPop(0);
                stackPush(0, incv.value + 1, 1);
                break;
            case "POP":
                checkStack(0, 1);
                stackPop(0);
                break;
            case "NIP":
                checkStack(0, 2);
                StackValue nipA = stackPop(0), nipB = stackPop(0);
                stackPush(0, nipA.value, nipA.size);
                break;
            case "SWP":
                checkStack(0, 2);
                StackValue swpA = stackPop(0), swpB = stackPop(0);
                stackPush(0, swpA.value, swpA.size);
                stackPush(0, swpB.value, swpB.size);
                break;
            case "ROT":
                checkStack(0, 3);
                StackValue rotA = stackPop(0), rotB = stackPop(0), rotC = stackPop(0);
                stackPush(0, rotB.value, rotB.size);
                stackPush(0, rotA.value, rotA.size);
                stackPush(0, rotC.value, rotC.size);
                break;
            case "DUP":
                checkStack(0, 1);
                StackValue dup = stacks.get(0).peek();
                stackPush(0, dup.value, dup.size);
                break;
            case "OVR":
                checkStack(0, 2);
                StackValue ovrA = stackPop(0), ovrB = stacks.get(0).peek();
                stackPush(0, ovrA.value, ovrA.size);
                stackPush(0, ovrB.value, ovrB.size);
                break;
            case "EQU":
                checkStack(0, 2);
                StackValue equA = stackPop(0), equB = stackPop(0);
                stackPush(0, equA.value == equB.value ? 1 : 0, 1);
                break;
            case "NEQ":
                checkStack(0, 2);
                StackValue neqA = stackPop(0), neqB = stackPop(0);
                stackPush(0, neqA.value != neqB.value ? 1 : 0, 1);
                break;
            case "GTH":
                checkStack(0, 2);
                StackValue gthA = stackPop(0), gthB = stackPop(0);
                stackPush(0, gthB.value > gthA.value ? 1 : 0, 1);
                break;
            case "LTH":
                checkStack(0, 2);
                StackValue lthA = stackPop(0), lthB = stackPop(0);
                stackPush(0, lthB.value < lthA.value ? 1 : 0, 1);
                break;
            case "JMP": // 跳转
                int jmpAddr = fetch16(++pc); pc += 1; // fetch16自动向后读两字节
                pc = jmpAddr - 1; // run()最后有pc++，所以-1
                break;
            case "JCN": // 条件跳转
                checkStack(0, 1);
                StackValue cond = stackPop(0);
                int jcnAddr = fetch16(++pc); pc += 1;
                if (cond.value != 0) pc = jcnAddr - 1;
                break;
            case "JSR": // 调用
                int jsrAddr = fetch16(++pc); pc += 1;
                stackPush(1, pc + 1, 2); // 返回地址入返回栈
                pc = jsrAddr - 1;
                break;
            case "STH":
                checkStack(0, 1);
                StackValue sth = stackPop(0);
                stackPush(1, sth.value, sth.size);
                break;
            case "LDZ":
                checkStack(0, 1);
                StackValue ldz = stackPop(0);
                stackPush(0, Byte.toUnsignedInt(memory[ldz.value & 0xFF]), 1);
                break;
            case "STZ":
                checkStack(0, 2);
                StackValue stzVal = stackPop(0), stzAddr = stackPop(0);
                memory[stzAddr.value & 0xFF] = (byte) (stzVal.value & 0xFF);
                break;
            case "LDR":
                checkStack(0, 1);
                StackValue ldr = stackPop(0);
                stackPush(0, Byte.toUnsignedInt(memory[ldr.value & 0xFFFF]), 1);
                break;
            case "STR":
                checkStack(0, 2);
                StackValue strVal = stackPop(0), strAddr = stackPop(0);
                memory[strAddr.value & 0xFFFF] = (byte) (strVal.value & 0xFF);
                break;
            case "LDA":
                checkStack(0, 1);
                StackValue lda = stackPop(0);
                int hi = Byte.toUnsignedInt(memory[lda.value & 0xFFFF]);
                int lo = Byte.toUnsignedInt(memory[(lda.value+1) & 0xFFFF]);
                stackPush(0, (hi << 8) | lo, 2);
                break;
            case "STA":
                checkStack(0, 2);
                StackValue staVal = stackPop(0), staAddr = stackPop(0);
                memory[staAddr.value & 0xFFFF] = (byte) ((staVal.value >> 8) & 0xFF);
                memory[(staAddr.value+1) & 0xFFFF] = (byte) (staVal.value & 0xFF);
                break;
            case "DEI":
                stackPush(0, 0, 1); // 简化为0
                break;
            case "DEO":
                checkStack(0, 1);
                StackValue deo = stackPop(0);
                System.out.println("[DEO] 输出: " + deo.value);
                break;
            case "ADD":
                checkStack(0, 2);
                StackValue addA = stackPop(0), addB = stackPop(0);
                stackPush(0, (addA.value + addB.value) & 0xFFFF, 2);
                break;
            case "SUB":
                checkStack(0, 2);
                StackValue subA = stackPop(0), subB = stackPop(0);
                stackPush(0, (subB.value - subA.value) & 0xFFFF, 2);
                break;
            case "MUL":
                checkStack(0, 2);
                StackValue mulA = stackPop(0), mulB = stackPop(0);
                stackPush(0, (mulA.value * mulB.value) & 0xFFFF, 2);
                break;
            case "DIV":
                checkStack(0, 2);
                StackValue divA = stackPop(0), divB = stackPop(0);
                stackPush(0, divA.value == 0 ? 0 : (divB.value / divA.value), 2);
                break;
            case "AND":
                checkStack(0, 2);
                StackValue andA = stackPop(0), andB = stackPop(0);
                stackPush(0, andA.value & andB.value, 2);
                break;
            case "ORA":
                checkStack(0, 2);
                StackValue oraA = stackPop(0), oraB = stackPop(0);
                stackPush(0, oraA.value | oraB.value, 2);
                break;
            case "EOR":
                checkStack(0, 2);
                StackValue eorA = stackPop(0), eorB = stackPop(0);
                stackPush(0, eorA.value ^ eorB.value, 2);
                break;
            case "SFT":
                checkStack(0, 2);
                StackValue sft = stackPop(0), v = stackPop(0);
                int r = (v.value >> (sft.value & 0x0F)) << ((sft.value >> 4) & 0x0F);
                stackPush(0, r, 2);
                break;
            default:
                throw new RuntimeException("未知指令: " + instr + ", PC=" + pc);
        }
        printStacks();
    }

    // 内存中的2字节高低位（大端序）
    private int fetch16(int addr) {
        int hi = Byte.toUnsignedInt(memory[addr]);
        int lo = Byte.toUnsignedInt(memory[addr+1]);
        return (hi << 8) | lo;
    }

    // 工作栈/返回栈操作
    private void stackPush(int stackIndex, int value, int size) {
        stacks.get(stackIndex).push(new StackValue(value, size));
    }
    private StackValue stackPop(int stackIndex) {
        if (stacks.get(stackIndex).isEmpty()) throw new RuntimeException("Stack underflow");
        return stacks.get(stackIndex).pop();
    }
    private void checkStack(int stackIndex, int n) {
        if (stacks.get(stackIndex).size() < n)
            throw new RuntimeException("Stack underflow: need " + n + ", have " + stacks.get(stackIndex).size());
    }
    private void showStacks() {
        System.out.println("Working Stack: " + stacks.get(0));
        System.out.println("Return Stack : " + stacks.get(1));
    }
    private void printStacks() {
        System.out.print("[WST] "); System.out.println(stacks.get(0));
        System.out.print("[RST] "); System.out.println(stacks.get(1));
    }
}
