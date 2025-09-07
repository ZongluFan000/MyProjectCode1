package yaku.uxntal;

import java.util.*;
import yaku.uxntal.Definitions.TokenType;
import static yaku.uxntal.Definitions.*;

public class Interpreter {

    public static final int MAX_INSTRUCTIONS = 1_000_000;
    private static final int MEM_MASK = 0xFFFF;

    //Stack element
    public static class StackElem {
        public final int value; // 0..0xFFFF
        public final int size;  // 1=byte, 2=short
        public StackElem(int value, int size) {
            this.value = value & (size == 2 ? 0xFFFF : 0xFF);
            this.size = (size == 2 ? 2 : 1);
        }
        @Override public String toString() {
            String hex = (size == 2)
                ? String.format("%04x", value & 0xFFFF)
                : String.format("%02x", value & 0xFF);
            return hex + ((size == 2) ? "s" : "b");
        }
    }

    // VM state
    private final byte[] memory;
    private final List<Definitions.Token> tokens;
    // 反向符号表：值可能是 Definitions.Token 或 Encoder.ReverseEntry
    private final Map<Integer, ?> reverseSymbolTable;

    private final Deque<StackElem> workStack = new ArrayDeque<>();
    private final Deque<StackElem> returnStack = new ArrayDeque<>();
    //////////////////////////////////////////////////////////////////////
    private static final Definitions.Token[] OPCODE_TOKEN_CACHE = new Definitions.Token[256];
    /// /////////////////////////////////////////////////////////////////

    // 按字节解码用
    private static final String[] BASE_NAMES = new String[32];
    static {
        for (var e : BASE_OPCODE_MAP.entrySet()) {
            int v = e.getValue();
            if (v >= 0 && v < 32) BASE_NAMES[v] = e.getKey();
        }
    }

    private static Definitions.Token copyFromOuterToken(yaku.uxntal.Token tk) {
        if (tk == null) return null;
        return new Definitions.Token(tk.type, tk.value, tk.size, tk.stack, tk.keep, tk.lineNum);
    }

    private Definitions.Token tokenFromAny(Object entry, int pc) {
        if (entry == null) return decodeAtPc(pc);
        if (entry instanceof Definitions.Token dt) return dt;
        if (entry instanceof Encoder.ReverseEntry re) return copyFromOuterToken(re.token);
        return decodeAtPc(pc);
    }



// 固定编码四条：BRK/JCI/JMI/JSI
private static boolean isFixedOpcode(int op) {
    return op == 0x00 || op == 0x20 || op == 0x40 || op == 0x60;
}




    // 解释器实例级复用（最大 3：ROT）
    private final StackElem[] argsBuf = new StackElem[3];


 
    private Definitions.Token decodeAtPc(int atPc) {
        int op = readByte(atPc) & 0xFF;
    
        // 先查缓存（每个 op 唯一确定 s/r/k/LIT/固定编码）
        Definitions.Token cached = OPCODE_TOKEN_CACHE[op];
        if (cached != null) return cached;
    
        // 固定编码（忽略 s/r/k 位）
        if (op == 0x00) { // BRK
            Definitions.Token t = new Definitions.Token(TokenType.INSTR, "BRK", 1, 0, 0, 0);
            return OPCODE_TOKEN_CACHE[op] = t;
        }
        if (op == 0x20) { // JCI
            Definitions.Token t = new Definitions.Token(TokenType.INSTR, "JCI", 1, 0, 0, 0);
            return OPCODE_TOKEN_CACHE[op] = t;
        }
        if (op == 0x40) { // JMI
            Definitions.Token t = new Definitions.Token(TokenType.INSTR, "JMI", 1, 0, 0, 0);
            return OPCODE_TOKEN_CACHE[op] = t;
        }
        if (op == 0x60) { // JSI
            Definitions.Token t = new Definitions.Token(TokenType.INSTR, "JSI", 1, 0, 0, 0);
            return OPCODE_TOKEN_CACHE[op] = t;
        }
    
        // LIT：高位 1（带 s/r/k 位）
        if ((op & 0x80) != 0) {
            int size  = ((op & MODE_SHORT)  != 0) ? 2 : 1;
            int rbit  = ((op & MODE_RETURN) != 0) ? 1 : 0;
            int kbit  = ((op & MODE_KEEP)   != 0) ? 1 : 0;
            Definitions.Token t = new Definitions.Token(TokenType.LIT, "LIT", size, rbit, kbit, 0);
            return OPCODE_TOKEN_CACHE[op] = t;
        }
    
        //普通基码：低 5 位为基码，0x10/0x20/0x40 为 k/s/r
        int base = op & 0x1F;
        String name = (base >= 0 && base < BASE_NAMES.length) ? BASE_NAMES[base] : null;
        if (name == null || name.isEmpty()) return null;
    
        int size  = ((op & MODE_SHORT)  != 0) ? 2 : 1;
        int rbit  = ((op & MODE_RETURN) != 0) ? 1 : 0;
        int kbit  = ((op & MODE_KEEP)   != 0) ? 1 : 0;
    
        Definitions.Token t = new Definitions.Token(TokenType.INSTR, name, size, rbit, kbit, 0);
        return OPCODE_TOKEN_CACHE[op] = t;
    }
    



    private int pc = MAIN_ADDRESS;
    private int instructionCount = 0;
    private boolean halted = false;

    // 仅用于警告的“父标签”跟踪
    private final Deque<String> callStack = new ArrayDeque<>();
    private String currentParent = "<main>";

    // 构造 
    public Interpreter(byte[] memory,
                       List<Definitions.Token> tokens,
                       Map<Integer, Definitions.Token> reverseSymbolTable) {
        this.memory = Arrays.copyOf(memory, memory.length);
        this.tokens = tokens;
        this.reverseSymbolTable = reverseSymbolTable;
        this.pc = MAIN_ADDRESS;
        callStack.push(currentParent);
    }

    // 活跃栈（由 r 位决定）
    public Deque<StackElem> active(Definitions.Token t) {
        return (t.stack == 1) ? returnStack : workStack;
    }
    public Deque<StackElem> work()  { return workStack; }
    public Deque<StackElem> ret()   { return returnStack; }

    // Memory helpers 
    int readByte(int addr) { return memory[addr & MEM_MASK] & 0xFF; }

    // 大端读 16 位
    int readShortBE(int addr) {
        int hi = memory[addr & MEM_MASK] & 0xFF;
        int lo = memory[(addr + 1) & MEM_MASK] & 0xFF;
        return (hi << 8) | lo;
    }
    int loadMemory(int addr, int size) {
        return (size == 2) ? readShortBE(addr) : (memory[addr & MEM_MASK] & 0xFF);
    }
    void storeMemory(int addr, int size, int value) {
        if (size == 2) {
            memory[addr & MEM_MASK] = (byte)((value >> 8) & 0xFF); // 高字节在前
            memory[(addr + 1) & MEM_MASK] = (byte)(value & 0xFF);  // 低字节在后
        } else {
            memory[addr & MEM_MASK] = (byte)(value & 0xFF);
        }
    }

    static int maskBySize(int v, int sz) { return (sz == 2) ? (v & 0xFFFF) : (v & 0xFF); }

    //Run 
    public void run() {
        try {
            
            while (!halted && pc >= 0 && pc < memory.length) {
                if (++instructionCount > MAX_INSTRUCTIONS)
                    throw new RuntimeException("Max instruction count exceeded (可能死循环)");
            
                // 先读本条指令字节
                int op = readByte(pc) & 0xFF;
            
                Object entry = reverseSymbolTable.get(pc);
                Definitions.Token t = tokenFromAny(entry, pc);
                if (t == null || (t.type != TokenType.INSTR && t.type != TokenType.LIT)) {
                    t = decodeAtPc(pc);
                    if (t == null) { pc++; continue; }
                }
            
                if (t.type == TokenType.INSTR) {
                    handleCallTracking(t); // 或改成基于 op 的快速版本（可选）
                    halted = executeInstr(t, op); // 这里把 op 传下去
                } else if (t.type == TokenType.LIT) {
                    pushLiteral(t);
                }
                pc++;
            }
            


        } catch (Exception e) {
            System.err.println("Error at pc=" + pc + ": " + e.getMessage()
                    + " (" + e.getClass().getName() + ")");
            showStacks();
            throw new RuntimeException(e);
        }
    }

    public static void runProgram(byte[] program) {
        new Interpreter(program, Collections.emptyList(), Collections.emptyMap()).run();
    }

    // Literal 
    void pushLiteral(Definitions.Token t) {
        int val = (t.size == 2)
            ? readShortBE((pc + 1) & MEM_MASK)  // 大端读取
            : (memory[(pc + 1) & MEM_MASK] & 0xFF);
        active(t).addLast(new StackElem(val, t.size));
        // pc += t.size;
        pc += (t.size == 2 ? 2 : 1);
    }

    // Parent label 跟踪（仅用于日志/警告）
    private void handleCallTracking(Definitions.Token t) {
        if ("JSR".equals(t.value) && t.size == 2 && t.stack == 0) {
            Object _labelTokA = reverseSymbolTable.get(pc - 3);
            Definitions.Token labelTok = tokenFromAny(_labelTokA, pc - 3);
            currentParent = (labelTok != null) ? labelTok.value : "<lambda>";
            callStack.push(currentParent);
        } else if ("JSI".equals(t.value)) {
            Object _labelTokB = reverseSymbolTable.get(pc + 1);
            Definitions.Token labelTok = tokenFromAny(_labelTokB, pc + 1);
            if (labelTok != null) { currentParent = labelTok.value; callStack.push(currentParent); }
        } else if ("JMP".equals(t.value) && t.size == 2 && t.stack == 0) {
            Object _labelTokA = reverseSymbolTable.get(pc - 3);
            Definitions.Token labelTok = tokenFromAny(_labelTokA, pc - 3);
            if (labelTok != null) currentParent = labelTok.value;
        } else if ("JMI".equals(t.value)) {
            Object _labelTokB = reverseSymbolTable.get(pc + 1);
            Definitions.Token labelTok = tokenFromAny(_labelTokB, pc + 1);
            if (labelTok != null) currentParent = labelTok.value;
        }
    }

    // Execute 
    public static class Action {
        public final String name;
        public final int argc;       // 需要的参数个数（按 t.size：1=字节，2=字）
        public final boolean sameSize;
        public final ActionImpl impl;
        public Action(String name, int argc, boolean sameSize, ActionImpl impl) {
            this.name = name; this.argc = argc; this.sameSize = sameSize; this.impl = impl;
        }
    }
    @FunctionalInterface
    public interface ActionImpl {
        void apply(Interpreter vm, Definitions.Token t, StackElem[] args, int sz);
    }

    public static final Map<String, Action> ACTION_TABLE = new HashMap<>();
    static {
        // 控制/跳转
        ACTION_TABLE.put("BRK", new Action("BRK", 0, false, Actions::brk));
        ACTION_TABLE.put("JMP", new Action("JMP", 1, false, Actions::jmp));
        ACTION_TABLE.put("JCN", new Action("JCN", 2, false, Actions::jcn));
        ACTION_TABLE.put("JSR", new Action("JSR", 1, false, Actions::jsr));
        ACTION_TABLE.put("JMI", new Action("JMI", 0, false, Actions::jmi)); // 立即数形式：在 Actions 里读
        ACTION_TABLE.put("JSI", new Action("JSI", 0, false, Actions::jsi)); // 立即数形式：在 Actions 里读
        ACTION_TABLE.put("JCI", new Action("JCI", 0, false, Actions::jci)); // 立即数形式：在 Actions 里读

        // 算术/逻辑/移位
        ACTION_TABLE.put("INC", new Action("INC", 1, true,  Actions::inc));
        ACTION_TABLE.put("ADD", new Action("ADD", 2, true,  Actions::add));
        ACTION_TABLE.put("SUB", new Action("SUB", 2, true,  Actions::sub));
        ACTION_TABLE.put("MUL", new Action("MUL", 2, true,  Actions::mul));
        ACTION_TABLE.put("DIV", new Action("DIV", 2, true,  Actions::div));
        ACTION_TABLE.put("AND", new Action("AND", 2, true,  Actions::and));
        ACTION_TABLE.put("ORA", new Action("ORA", 2, true,  Actions::ora));
        ACTION_TABLE.put("EOR", new Action("EOR", 2, true,  Actions::eor));
        ACTION_TABLE.put("SFT", new Action("SFT", 2, true,  Actions::sft));

        // 比较
        ACTION_TABLE.put("EQU", new Action("EQU", 2, true,  Actions::equ));
        ACTION_TABLE.put("NEQ", new Action("NEQ", 2, true,  Actions::neq));
        ACTION_TABLE.put("GTH", new Action("GTH", 2, true,  Actions::gth));
        ACTION_TABLE.put("LTH", new Action("LTH", 2, true,  Actions::lth));

        // 内存/设备
        ACTION_TABLE.put("LDZ", new Action("LDZ", 1, false, Actions::ldz));
        ACTION_TABLE.put("STZ", new Action("STZ", 2, false, Actions::stz));
        ACTION_TABLE.put("LDR", new Action("LDR", 1, false, Actions::ldr));
        ACTION_TABLE.put("STR", new Action("STR", 2, false, Actions::str));
        ACTION_TABLE.put("LDA", new Action("LDA", 1, false, Actions::lda));
        ACTION_TABLE.put("STA", new Action("STA", 2, false, Actions::sta));
        ACTION_TABLE.put("DEI", new Action("DEI", 1, false, Actions::dei)); // 端口在上
        ACTION_TABLE.put("DEO", new Action("DEO", 2, false, Actions::deo)); // 值在下、端口在上

        // 栈操作
        ACTION_TABLE.put("POP", new Action("POP", 1, true,  Actions::pop));
        ACTION_TABLE.put("NIP", new Action("NIP", 2, true,  Actions::nip));
        ACTION_TABLE.put("SWP", new Action("SWP", 2, true,  Actions::swp));
        ACTION_TABLE.put("ROT", new Action("ROT", 3, true,  Actions::rot));
        ACTION_TABLE.put("DUP", new Action("DUP", 1, true,  Actions::dup));
        ACTION_TABLE.put("OVR", new Action("OVR", 2, true,  Actions::ovr));
        ACTION_TABLE.put("STH", new Action("STH", 1, false, Actions::sth)); // 推到另一栈
    }
///////////////////////////////////////////////////////////////////////////////////
// 
public static final Action[] ACTIONS_BY_BASE = new Action[32];
static {
    for (int i = 0; i < 32; i++) {
        String nm = (i >= 0 && i < BASE_NAMES.length) ? BASE_NAMES[i] : null;
        if (nm != null && !nm.isEmpty()) {
            ACTIONS_BY_BASE[i] = ACTION_TABLE.get(nm);
        }
    }
}

    // 按“字节/字”弹参数

    /** 仅弹出“一个字节”。若栈顶是 16 位：返回低字节，并把高字节留在栈上（变为 8 位元素）。 */
    private StackElem popOneByte(java.util.Deque<StackElem> S, Definitions.Token t) {
        if (S.isEmpty()) throw new RuntimeException("Stack underflow for " + t.value);
        StackElem top = S.removeLast();
        if (top.size == 1) return top; // 就是 8 位

        // 16 位：按大端拆成 hi/lo；栈顶应当是 lo，hi 留在栈上
        int hi = (top.value >> 8) & 0xFF;
        int lo =  top.value       & 0xFF;
        S.addLast(new StackElem(hi, 1));     // 高字节留栈
        return new StackElem(lo, 1);         // 低字节作为“本次弹出的字节”
    }

    /** 弹出一个 16 位参数：按字节弹两次，组合成大端（hi<<8 | lo）。 */
    private StackElem popOneWord(java.util.Deque<StackElem> S, Definitions.Token t) {
        int lo = popOneByte(S, t).value & 0xFF;
        int hi = popOneByte(S, t).value & 0xFF;
        return new StackElem((hi << 8) | lo, 2);
    }

 

private boolean executeInstr(Definitions.Token t, int op) {
    // 选 Action//
    Action a;
    if ((op & 0x80) == 0 && !isFixedOpcode(op)) {
        // 普通基码指令：非 LIT 且 非固定编码
        int base = op & 0x1F;
        a = ACTIONS_BY_BASE[base];
        if (a == null) throw new RuntimeException("Unknown base opcode index: " + base);
    } else {
        // 固定编码（BRK/JCI/JMI/JSI）或其他通过名字分发的情况
        a = ACTION_TABLE.get(t.value); // t.value = "BRK"/"JCI"/"JMI"/"JSI"/...
        if (a == null) throw new RuntimeException("Unknown instruction: " + t.value);
    }

    // 2) 出参准备（按 size 弹；复用 argsBuf //
    Deque<StackElem> S = active(t);
    int sz = t.size, argc = a.argc;
    for (int i = argc - 1; i >= 0; --i) {
        argsBuf[i] = (sz == 2) ? popOneWord(S, t) : popOneByte(S, t);
    }

    if (Flags.isDebug()) {
        System.err.printf("INSTR %s s=%s r=%s k=%s -> %02X%n",
                t.value, (t.size == 2), (t.stack == 1), (t.keep == 1),
                Definitions.getOpcodeByte(t.value, t.size == 2, t.stack == 1, t.keep == 1));
        System.err.flush();
    }

    // 执行//
    a.impl.apply(this, t, argsBuf, sz);

    // BRK 终止 //
    return (op == 0x00); // 比字符串比较更快
}




    // For Actions access 
    public int getPc() { return pc; }
    public void setPc(int value) { pc = value & MEM_MASK; }
    public byte[] mem() { return memory; }

    // Debug
    public void showStacks() {
        System.out.println("WorkStack:   " + workStack);
        System.out.println("ReturnStack: " + returnStack);
    }
}
