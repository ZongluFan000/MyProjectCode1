package yaku.uxntal;

import java.util.*;
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
            this.size = size;
        }
        @Override public String toString() {
            return size == 2 ? String.format("%04x", value & 0xFFFF)
                             : String.format("%02x", value & 0xFF);
        }
    }

    //VM state
    private final byte[] memory;
    private final List<Definitions.Token> tokens;
    private final Map<Integer, Definitions.Token> reverseSymbolTable;

    private final Deque<StackElem> workStack = new ArrayDeque<>();
    private final Deque<StackElem> returnStack = new ArrayDeque<>();
    @SuppressWarnings("unchecked")
    private final Deque<StackElem>[] stacks = new Deque[]{ workStack, returnStack };

    private int pc = MAIN_ADDRESS;
    private int instructionCount = 0;
    private boolean halted = false;

    private String currentParent = "MAIN";
    private final Deque<String> callStack = new ArrayDeque<>();
    private final Set<String> warned = new HashSet<>();

    public Interpreter(byte[] memory,
                       List<Definitions.Token> tokens,
                       Map<Integer, Definitions.Token> reverseSymbolTable) {
        this.memory = Arrays.copyOf(memory, memory.length);
        this.tokens = tokens;
        this.reverseSymbolTable = reverseSymbolTable;
        this.pc = MAIN_ADDRESS;
        callStack.push(currentParent);
    }

    //Public mini-API for Actions
    public int  getPc() { return pc; }
    public void setPcRaw(int newPc) { this.pc = newPc; }
    public void jumpAbs(int addr)   { this.pc = (addr & 0xFFFF) - 1; }
    public void jumpRel8(int rel8)  { this.pc += (byte) rel8; }

    public void jumpRelShort(int rel16) { this.pc = this.pc + 2 + (short) rel16; }
    public void pushReturnAddr(int addr) { this.returnStack.addLast(new StackElem(addr & 0xFFFF, 2)); }
    public int  getWorkStackSize()   { return this.workStack.size(); }
    public int  getReturnStackSize() { return this.returnStack.size(); }
    public int  readByte(int addr) { return memory[addr & MEM_MASK] & 0xFF; }
    public int  readShortLE(int addr) {
        int hi = memory[addr & MEM_MASK] & 0xFF;
        int lo = memory[(addr + 1) & MEM_MASK] & 0xFF;
        return ((hi << 8) | lo) & 0xFFFF;
    }
    public int loadMemory(int addr, int sz) {
        return (sz == 2)
                ? ((readByte(addr) << 8) | readByte((addr + 1) & MEM_MASK)) & 0xFFFF
                : readByte(addr) & 0xFF;
    }
    public void storeMemory(int addr, int val, int sz) {
        if (sz == 2) {
            memory[addr & MEM_MASK] = (byte)((val >> 8) & 0xFF);
            memory[(addr + 1) & MEM_MASK] = (byte)(val & 0xFF);
        } else {
            memory[addr & MEM_MASK] = (byte)(val & 0xFF);
        }
    }
    public void requestHalt() { this.halted = true; }

    //Run loop
    public void run() {
        try {
            while (!halted && pc >= 0 && pc < memory.length) {
                if (++instructionCount > MAX_INSTRUCTIONS)
                    throw new RuntimeException("Max instruction count exceeded (可能死循环)");

                Definitions.Token t = reverseSymbolTable.get(pc);
                if (t == null) { // data byte
                    pc++;
                    continue;
                }

                if (t.type == TokenType.INSTR) {
                    handleCallTracking(t);
                    halted = executeInstr(t);
                } else if (t.type == TokenType.LIT) {
                    pushLiteral(t);
                }
                pc++;
            }
        } catch (Exception e) {
            System.err.println("Error at pc=" + pc + ": " + e.getMessage());
            showStacks();
            throw e;
        }
    }

    //Execute one instruction
    private boolean executeInstr(Definitions.Token t) {
        final String instr = t.value;

        if ("BRK".equals(instr)) {
            System.out.println("*** DONE ***");
            showStacks();
            return true;
        }

        // Stack-manipulation ops handled here 
        if (stack_operations.containsKey(instr)) {
            conditionStack(t);
            performStackOp(t);
            return false;
        }

        // First: try Actions table (ALU / mem / branch / IO)
        Actions.Action act = Actions.ACTION_TABLE.get(instr);
        if (act != null) {
            List<Integer> argsVals = (act.nArgs == 0) ? Collections.emptyList() : getArgsFromStack(t, act.nArgs);
            // Convert to StackElem[] for Actions API
            Interpreter.StackElem[] arr = new Interpreter.StackElem[argsVals.size()];
            for (int i = 0; i < argsVals.size(); i++) {
                
                arr[i] = new Interpreter.StackElem(argsVals.get(i), t.size);
            }
            Interpreter.StackElem res = act.exec.apply(arr, t.size, t.stack, t.keep, this);
            if (act.hasResult && res != null) {
                
                stacks[t.stack].addLast(new StackElem(res.value, res.size));
            }
            return false;
        }

        // table-driven ALU from Definitions 
        if (alu_ops.containsKey(instr)) {
            List<Integer> args = getArgsFromStack(t, alu_ops.get(instr).nArgs);
            int result = alu_ops.get(instr).apply(args, t.size);
            if (instr.matches("EQU|NEQ|LTH|GTH")) {
                stacks[t.stack].addLast(new StackElem(result, 1));
            } else {
                stacks[t.stack].addLast(new StackElem(result, t.size));
            }
            return false;
        }

        throw new UnsupportedOperationException("Unknown or unimplemented instruction: " + instr);
    }

    //Literal push
    private void pushLiteral(Definitions.Token t) {
        int val = (t.size == 2)
                ? readShortLE((pc + 1) & MEM_MASK)
                : (memory[(pc + 1) & MEM_MASK] & 0xFF);
        stacks[t.stack].addLast(new StackElem(val, t.size));
        pc += t.size; // skip literal payload
    }

    //Parent label tracking (for warnings)
    private void handleCallTracking(Definitions.Token t) {
        if ("JSR".equals(t.value) && t.size == 2 && t.stack == 0) {
            Definitions.Token labelTok = reverseSymbolTable.get(pc - 3);
            currentParent = (labelTok != null) ? labelTok.value : "<lambda>";
            callStack.push(currentParent);
        } else if ("JSI".equals(t.value)) {
            Definitions.Token labelTok = reverseSymbolTable.get(pc + 1);
            if (labelTok != null) { currentParent = labelTok.value; callStack.push(currentParent); }
        } else if ("JMP".equals(t.value) && t.size == 2 && t.stack == 0) {
            Definitions.Token labelTok = reverseSymbolTable.get(pc - 3);
            if (labelTok != null) currentParent = labelTok.value;
        } else if ("JMI".equals(t.value)) {
            Definitions.Token labelTok = reverseSymbolTable.get(pc + 1);
            if (labelTok != null) currentParent = labelTok.value;
        }

        if ("JMP".equals(t.value) && t.size == 2 && t.stack == 1) { // JMP2r
            if (!callStack.isEmpty()) callStack.pop();
            currentParent = callStack.peek();
        } else if ("JMP".equals(t.value) && t.size == 2 && t.stack == 0) {
            Definitions.Token prev = reverseSymbolTable.get(pc - 1);
            if (prev != null && "STH".equals(prev.value) && prev.size == 2 && prev.stack == 1) {
                if (!callStack.isEmpty()) callStack.pop();
                currentParent = callStack.peek();
            }
        }
    }

    //Arg handling
    private List<Integer> getArgsFromStack(Definitions.Token t, int nArgs) {
        List<Integer> args = new ArrayList<>();
        List<StackElem> keepList = new ArrayList<>();
        final String instr = t.value;

        for (int i = 0; i < nArgs; i++) {
            StackElem arg;

            // Special rules like Perl:
            if (("LDA".equals(instr) || "STA".equals(instr)) && i == 0) {
                arg = getShortArg(t, t.stack);
            } else if ("JCN".equals(instr) && i == 1) {
                arg = getByteArg(t, t.stack);
            } else if ("JCI".equals(instr)) {
                arg = getByteArg(t, t.stack);
            } else if (needsByteFirst(instr, i)) {
                arg = getByteArg(t, t.stack);
            } else {
                arg = (t.size == 2) ? getShortArg(t, t.stack) : getByteArg(t, t.stack);
            }

            if (t.keep == 1) keepList.add(arg);
            args.add(arg.value);
        }
        if (t.keep == 1) {
            Collections.reverse(keepList);
            stacks[t.stack].addAll(keepList);
        }
        return args;
    }

    private boolean needsByteFirst(String instr, int argIndex) {
        if (instr.startsWith("LD") || instr.startsWith("ST")
                || "DEI".equals(instr) || "SFT".equals(instr) || "DEO".equals(instr)) {
            return argIndex == 0;
        }
        return false;
    }

    //Width conditioning for stack-ops
    private void conditionStack(Definitions.Token t) {
        int bytesNeeded = stack_operations.get(t.value)[0] * t.size;
        int bytesGot = 0;
        List<StackElem> temp = new ArrayList<>();

        while (bytesGot < bytesNeeded) {
            if (stacks[t.stack].isEmpty())
                throw new RuntimeException("Stack underflow, need " + bytesNeeded + " bytes for " + t.value);

            StackElem e = stacks[t.stack].removeLast();
            bytesGot += e.size;

            if (bytesGot > bytesNeeded) {
                int hi = (e.value >> 8) & 0xFF, lo = e.value & 0xFF;
                stacks[t.stack].addLast(new StackElem(hi, 1));
                bytesGot -= 1;
                e = new StackElem(lo, 1);
            }

            if (t.size == 2 && e.size == 1) {
                warnSizeMismatch(t, 0);
                e = getShortArg(t, t.stack);
                bytesGot += (e.size - 1);
            } else if (t.size == 1 && e.size == 2) {
                warnSizeMismatch(t, 1);
                int lo = e.value & 0xFF, hi = (e.value >> 8) & 0xFF;
                stacks[t.stack].addLast(new StackElem(hi, 1));
                bytesGot -= 1;
                e = new StackElem(lo, 1);
            }
            temp.add(0, e);
        }
        stacks[t.stack].addAll(temp);
    }

    private StackElem getByteArg(Definitions.Token t, int rs) {
        if (stacks[rs].isEmpty()) throw new RuntimeException("Stack underflow on " + t.value);
        StackElem arg = stacks[rs].removeLast();
        if (arg.size == 2 && !bin_ops.containsKey(t.value)) {
            warnSizeMismatch(t, 1); // short, expects byte
            int hi = (arg.value >> 8) & 0xFF, lo = arg.value & 0xFF;
            stacks[rs].addLast(new StackElem(hi, 1));
            arg = new StackElem(lo, 1);
        }
        return arg;
    }

    private StackElem getShortArg(Definitions.Token t, int rs) {
        if (stacks[rs].isEmpty()) throw new RuntimeException("Stack underflow on " + t.value);
        StackElem arg = stacks[rs].removeLast();
        if (arg.size == 1) return extendWithExtraByte(arg, t, rs);
        return arg;
    }

    private StackElem extendWithExtraByte(StackElem lowByteElem, Definitions.Token t, int rs) {
        if (stacks[rs].isEmpty()) throw new RuntimeException("Stack underflow forming short on " + t.value);
        StackElem highPart = stacks[rs].removeLast();
        boolean splitShort = false;
        int hiByte;
        if (highPart.size == 1) hiByte = highPart.value & 0xFF;
        else {
            splitShort = true;
            int hi = (highPart.value >> 8) & 0xFF, lo = highPart.value & 0xFF;
            stacks[rs].addLast(new StackElem(hi, 1)); hiByte = lo;
        }
        if (splitShort) warnSizeMismatch(t, 0);
        int shortVal = ((hiByte & 0xFF) << 8) | (lowByteElem.value & 0xFF);
        return new StackElem(shortVal, 2);
    }

    private void warnSizeMismatch(Definitions.Token t, int sb) {
        String key = t.value + "_" + currentParent + "_line" + t.lineNum;
        if (warned.contains(key)) return;
        warned.add(key);
        System.err.println("Warning: value on stack is " +
                (sb == 1 ? "short, instruction expects byte" : "byte, instruction expects short")
                + ": " + t.value + " in " + currentParent + " (line " + t.lineNum + ")");
    }

    //Stack ops
    private void performStackOp(Definitions.Token t) {
        Deque<StackElem> S = stacks[t.stack];
        switch (t.value) {
            case "DUP" -> { ensureSize(S, 1, t); StackElem a = S.peekLast(); S.addLast(new StackElem(a.value, t.size)); }
            case "POP" -> { ensureSize(S, 1, t); S.removeLast(); }
            case "NIP" -> { ensureSize(S, 2, t); StackElem a = S.removeLast(); S.removeLast(); S.addLast(a); }
            case "SWP" -> { ensureSize(S, 2, t); StackElem a = S.removeLast(), b = S.removeLast(); S.addLast(a); S.addLast(b); }
            case "ROT" -> { ensureSize(S, 3, t); StackElem a = S.removeLast(), b = S.removeLast(), c = S.removeLast(); S.addLast(b); S.addLast(a); S.addLast(c); }
            case "OVR" -> { ensureSize(S, 2, t); StackElem a = S.removeLast(), b = S.peekLast(); S.addLast(a); S.addLast(new StackElem(b.value, t.size)); }
            case "STH" -> { ensureSize(S, 1, t); StackElem a = S.removeLast(); stacks[t.stack ^ 1].addLast(new StackElem(a.value, t.size)); }
            default -> throw new UnsupportedOperationException("Stack op not implemented: " + t.value);
        }
    }
    private void ensureSize(Deque<StackElem> S, int n, Definitions.Token t) {
        if (S.size() < n) throw new RuntimeException("Stack underflow for " + t.value);
    }

    //Debug
    public void showStacks() {
        System.out.println("WorkStack:   " + workStack);
        System.out.println("ReturnStack: " + returnStack);
    }
}
