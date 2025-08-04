package yaku.uxntal;

import java.util.*;

public class Encoder {

    // ==================== 符号表/布局相关 ====================

    public static class SymbolTable {
        public Map<String, Integer> labels = new HashMap<>(); // label -> address
        public Map<String, List<Integer>> references = new HashMap<>(); // ref -> [pc...]
        public Map<Integer, String> reverseLabels = new HashMap<>(); // addr -> label
        public Map<String, Integer> allocations = new HashMap<>(); // label -> size
        public Map<String, String> scopes = new HashMap<>(); // child -> parent
        public String currentParent = null;

        public void addLabel(String name, int addr, boolean isParent) {
            labels.put(name, addr);
            if (isParent) currentParent = name;
            reverseLabels.put(addr, name);
        }

        public void addReference(String name, int addr) {
            references.computeIfAbsent(name, k -> new ArrayList<>()).add(addr);
        }

        public void addAllocation(String label, int size) {
            allocations.put(label, size);
        }
    }

    public static void tokensToMemory(List<Token> tokens, yaku.uxntal.units.UxnState uxn) {
        for (int i = 0; i < uxn.memory.length; i++) {
            uxn.memory[i] = new Token(Definitions.TokenType.EMPTY, "", 0, 0);
        }
        int pc = 0x100;
        for (Token t : tokens) {
            switch (t.type) {
                case MAIN:
                    pc = 0x100;
                    break;
                case ADDR:
                    pc = t.size;
                    break;
                case PAD:
                    for (int j = 0; j < t.size; j++) {
                        uxn.memory[pc++] = new Token(Definitions.TokenType.RAW, "0", 1, t.line);
                    }
                    break;
                case LABEL:
                    break;
                case REF:
                    int wordSz = (t.refType == 2 || t.refType == 5 || t.refType == 6) ? 2 : 1;
                    for (int j = 0; j < wordSz; j++) {
                        uxn.memory[pc++] = new Token(Definitions.TokenType.REF, t.value, t.refType, t.isChild, t.line);
                    }
                    break;
                case INSTR:
                    uxn.memory[pc++] = t;
                    break;
                case LIT:
                    uxn.memory[pc++] = t;
                    break;
                case RAW:
                    uxn.memory[pc++] = t;
                    break;
                default:
                    break;
            }
        }
    }

    // 内存布局，支持简单分配和冲突检测
    public static class MemoryLayout {
        public Map<String, Allocation> allocations = new HashMap<>();
        public int currentAddress = 0x100;

        public static class Allocation {
            public int start, size;
            public String type;
            public Allocation(int s, int sz, String t) { start = s; size = sz; type = t; }
        }

        public int allocate(String name, int size, String type) {
            int addr = currentAddress;
            allocations.put(name, new Allocation(addr, size, type));
            currentAddress += size;
            return addr;
        }

        public Allocation get(String name) { return allocations.get(name); }
    }

    // ==================== 编码主流程 ====================

    public static class EncodeResult {
        public final byte[] memory;
        public final SymbolTable symbolTable;
        public final MemoryLayout memoryLayout;
        public EncodeResult(byte[] memory, SymbolTable symtab, MemoryLayout layout) {
            this.memory = memory; this.symbolTable = symtab; this.memoryLayout = layout;
        }
    }

    public static EncodeResult encode(List<Token> tokens) {

        final int MEMORY_SIZE = 0x10000;
        byte[] memory = new byte[MEMORY_SIZE];
        SymbolTable symtab = new SymbolTable();
        MemoryLayout layout = new MemoryLayout();

        int pc = 0x100;

        // ===== 第一遍：写入内存/收集标签/引用/分配 =====
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            switch (t.type) {
                case MAIN:
                    pc = 0x100; // 程序入口
                    break;
                case ADDR:
                    pc = t.size;
                    break;
                case PAD:
                    for (int j = 0; j < t.size; j++) {
                        if (pc < MEMORY_SIZE) memory[pc++] = 0;
                    }
                    break;
                case LABEL:
                    String labelName = t.value;
                    boolean isParent = t.size == 2;
                    if (!isParent && symtab.currentParent != null) {
                        labelName = symtab.currentParent + "/" + labelName;
                        symtab.scopes.put(labelName, symtab.currentParent);
                    }
                    if (symtab.labels.containsKey(labelName)) {
                        throw new RuntimeException("重复定义标签: " + labelName + " at line " + t.line);
                    }
                    symtab.addLabel(labelName, pc, isParent);
                    if (isParent && i + 1 < tokens.size() && tokens.get(i + 1).type == Definitions.TokenType.PAD) {
                        layout.allocate(labelName, tokens.get(i + 1).size, "variable");
                        symtab.addAllocation(labelName, tokens.get(i + 1).size);
                    }
                    break;
                case REF:
                    String refName = t.value;
                    if (t.isChild == 1 && symtab.currentParent != null && !refName.contains("/")) {
                        refName = symtab.currentParent + "/" + refName;
                    }
                    symtab.addReference(refName, pc);
                    int wordSz = (t.refType == 2 || t.refType == 5 || t.refType == 6) ? 2 : 1;
                    for (int j = 0; j < wordSz; j++) {
                        if (pc < MEMORY_SIZE) memory[pc++] = 0;
                    }
                    break;
                case INSTR:
                    Integer baseCode = Definitions.OPCODE_MAP.get(t.value.toUpperCase());
                    if (baseCode == null) throw new RuntimeException("未知指令: " + t.value + " at line " + t.line);
                    int base = baseCode & 0xF8;
                    int mode = (t.size == 2 ? 0x04 : 0) | (t.refType == 1 ? 0x02 : 0) | (t.isChild == 1 ? 0x01 : 0);
                    byte finalOp = (byte)(base | mode);
                    if (pc < MEMORY_SIZE) memory[pc++] = finalOp;
                    break;
                case LIT:
                    int litBase = 0x80, litNum = Integer.parseInt(t.value, 16);
                    int litMode = (t.size == 2 ? 0x04 : 0) | (t.refType == 1 ? 0x02 : 0) | (t.isChild == 1 ? 0x01 : 0);
                    byte litOp = (byte)(litBase | litMode);
                    if (pc < MEMORY_SIZE) memory[pc++] = litOp;
                    if (t.size == 2) {
                        if (pc + 1 < MEMORY_SIZE) {
                            memory[pc++] = (byte)((litNum >> 8) & 0xFF);
                            memory[pc++] = (byte)(litNum & 0xFF);
                        }
                    } else {
                        if (pc < MEMORY_SIZE) memory[pc++] = (byte)(litNum & 0xFF);
                    }
                    break;
                case RAW:
                    int rawNum = Integer.parseInt(t.value, 16);
                    if (t.size == 2) {
                        if (pc + 1 < MEMORY_SIZE) {
                            memory[pc++] = (byte)((rawNum >> 8) & 0xFF);
                            memory[pc++] = (byte)(rawNum & 0xFF);
                        }
                    } else {
                        if (pc < MEMORY_SIZE) memory[pc++] = (byte)(rawNum & 0xFF);
                    }
                    break;
                default:
                    break;
            }
            if (pc > MEMORY_SIZE) throw new RuntimeException("超出内存容量!");
        }

        // ===== 第二遍：回填引用地址 =====
        for (Map.Entry<String, List<Integer>> entry : symtab.references.entrySet()) {
            String lbl = entry.getKey();
            Integer addr = symtab.labels.get(lbl);
            if (addr == null) throw new RuntimeException("标签引用未定义: " + lbl);

            for (int refPc : entry.getValue()) {
                Token tok = findTokenForRef(tokens, lbl, refPc);
                if (tok == null) throw new RuntimeException("找不到 REF Token 信息: " + lbl + " @ " + refPc);

                int wSz = (tok.refType == 2 || tok.refType == 5 || tok.refType == 6) ? 2 : 1;
                int val = addr;
                if (tok.refType == 6) {
                    val = addr - (refPc + 2);
                    if (val < 0) val = toTwosComplement16(val);
                } else if (tok.refType == 1 || tok.refType == 4) {
                    val = addr - (refPc + 2);
                    if (val < 0) val = toTwosComplement8(val);
                }
                if (wSz == 2) {
                    memory[refPc]     = (byte)((val >> 8) & 0xFF);
                    memory[refPc + 1] = (byte)(val & 0xFF);
                } else {
                    memory[refPc] = (byte)(val & 0xFF);
                }
            }
        }

        return new EncodeResult(memory, symtab, layout);
    }

    // ---- 所有工具方法均设为 static ----
    public static Token findTokenForRef(List<Token> tokens, String label, int pc) {
        for (Token t : tokens) {
            if (t.type == Definitions.TokenType.REF &&
                (t.value.equals(label) || (t.isChild == 1 && label.endsWith("/" + t.value)))) {
                return t;
            }
        }
        return null;
    }

    public static int toTwosComplement8(int v)  { return v & 0xFF;   }
    public static int toTwosComplement16(int v) { return v & 0xFFFF; }

}
