package yaku.uxntal;

import java.util.*;

import yaku.uxntal.Definitions.TokenType;

public class Encoder {

    // SymbolTable
    public static class SymbolTable {
        // Label info
        public static class LabelInfo {
            public final int address;
            public final Token token;
            public final String type; // "parent" or "child"
            public final String scope; // null or parent label

            public LabelInfo(int address, Token token, String type, String scope) {
                this.address = address;
                this.token = token;
                this.type = type;
                this.scope = scope;
            }
        }

        // Reference info
        public static class RefInfo {
            public final int address;
            public final Token token;
            public final String scope;
            public RefInfo(int address, Token token, String scope) {
                this.address = address;
                this.token = token;
                this.scope = scope;
            }
        }

        public final Map<String, LabelInfo> labels = new HashMap<>();
        public final Map<String, List<RefInfo>> references = new HashMap<>();
        public final Map<String, Set<String>> scopes = new HashMap<>();
        public String currentScope = null;

        public void addLabel(String name, int address, Token token) {
            boolean isParent = token.isParentLabel();
            String scope = isParent ? null : currentScope;

            labels.put(name, new LabelInfo(address, token, isParent ? "parent" : "child", scope));

            if (isParent) {
                currentScope = name;
                scopes.putIfAbsent(name, new HashSet<>());
            } else if (currentScope != null) {
                scopes.get(currentScope).add(name);
            }
        }

        public void addReference(String name, int address, Token token) {
            references.computeIfAbsent(name, k -> new ArrayList<>())
                      .add(new RefInfo(address, token, currentScope));
        }

        // 作用域查找（先查全名再查作用域）
        public LabelInfo resolveReference(String refName, String currentScope) {
            // 1. Exact match
            if (labels.containsKey(refName)) return labels.get(refName);

            // 2. Scoped lookup: 如果当前 scope 存在且 refName 不含 /，尝试 parent/refName
            if (currentScope != null && !refName.contains("/")) {
                String scopedName = currentScope + "/" + refName;
                if (labels.containsKey(scopedName)) return labels.get(scopedName);
            }

            return null;
        }
    }

    public static class EncodeResult {
        public final byte[] memory;
        public final SymbolTable symbolTable;
        public final int pc;
        public EncodeResult(byte[] memory, SymbolTable symtab, int pc) {
            this.memory = memory;
            this.symbolTable = symtab;
            this.pc = pc;
        }
    }

    //核心编码流程
    public static EncodeResult encode(List<Token> tokens) {
        final int MEMORY_SIZE = 0x10000;
        byte[] memory = new byte[MEMORY_SIZE];
        SymbolTable symtab = new SymbolTable();
        int pc = 0x100;

        tokens = filterUsefulTokens(tokens);

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            // System.out.println("Encoder for idx=" + i + ", token=" + t);

            switch (t.type) {
                case MAIN:
                    pc = 0x100;
                    break;
                case ADDR:
                    pc = Integer.parseInt(t.value, 16);
                    break;
                case PAD:
                    for (int j = 0; j < t.size; j++)
                        if (pc < MEMORY_SIZE) memory[pc++] = 0;
                    break;
                case LABEL: {
                    String labelName = t.value;
                    boolean isParent = t.isParentLabel();
                    if (!isParent && symtab.currentScope != null) {
                        labelName = symtab.currentScope + "/" + labelName;
                    }
                    if (symtab.labels.containsKey(labelName))
                        throw new RuntimeException("Duplicate label: " + labelName + " at line " + t.line);

                    symtab.addLabel(labelName, pc, t);
                    break;
                }
                case REF: {
                    String refName = t.value;
                    if (t.isChild == 1 && symtab.currentScope != null && !refName.contains("/")) {
                        refName = symtab.currentScope + "/" + refName;
                    }
                    symtab.addReference(refName, pc, t);
                    int wordSz = (t.refType == 2 || t.refType == 5 || t.refType == 6) ? 2 : 1;
                    for (int j = 0; j < wordSz; j++)
                        if (pc < MEMORY_SIZE) memory[pc++] = 0;
                    break;
                }
                case LIT:
                case RAW: {
                    int wordSz = t.size;
                    int val = Integer.parseInt(t.value, 16);
                    if (wordSz == 2) {
                        storeWord(memory, pc, val);
                        pc += 2;
                    } else {
                        if (pc < MEMORY_SIZE) memory[pc++] = (byte) (val & 0xFF);
                    }
                    break;
                }
                case INSTR: {
                    int opcode = Definitions.OPCODE_MAP.getOrDefault(t.value.toUpperCase(), -1);
                    if (opcode == -1)
                        throw new RuntimeException("Unknown instruction: " + t.value + " at line " + t.line);
                    if (pc < MEMORY_SIZE) memory[pc++] = (byte) (opcode & 0xFF);
                    break;
                }
                // 其他类型可以补充
                default:
                    break;
            }
        }

        //新增的符号引用回填流程
        resolveReferences(memory, symtab);

        return new EncodeResult(memory, symtab, pc);
    }

    //辅助方法
    private static void storeWord(byte[] memory, int pc, int value) {
        memory[pc] = (byte) ((value >> 8) & 0xFF);
        memory[pc + 1] = (byte) (value & 0xFF);
    }

    //符号引用回填
    private static void resolveReferences(byte[] memory, SymbolTable symtab) {
        for (Map.Entry<String, List<SymbolTable.RefInfo>> entry : symtab.references.entrySet()) {
            String label = entry.getKey();
            SymbolTable.LabelInfo def = symtab.labels.get(label);
            if (def == null)
                throw new RuntimeException("Undefined label reference: " + label);
            int address = def.address;
            for (SymbolTable.RefInfo ref : entry.getValue()) {
                Token t = ref.token;
                int pc = ref.address;
                int refType = t.refType;
                // 处理各种寻址类型
                if (refType == 6) { // Immediate，两字节补码
                    int rel = address - (pc + 2);
                    if (rel < -32768 || rel > 32767)
                        throw new RuntimeException("Relative address too large for " + label);
                    memory[pc] = (byte) ((rel >> 8) & 0xFF);
                    memory[pc + 1] = (byte) (rel & 0xFF);
                } else if (refType == 1 || refType == 4) { // 相对，1字节补码
                    int rel = address - (pc + 2);
                    if (rel < -128 || rel > 127)
                        throw new RuntimeException("Relative address too large for " + label);
                    memory[pc] = (byte) (rel & 0xFF);
                } else if (refType == 2 || refType == 5) { // 绝对2字节
                    memory[pc] = (byte) ((address >> 8) & 0xFF);
                    memory[pc + 1] = (byte) (address & 0xFF);
                } else { // 默认1字节绝对
                    memory[pc] = (byte) (address & 0xFF);
                }
            }
        }
    }

    //保持你的辅助方法
    private static List<Token> filterUsefulTokens(List<Token> tokens) {
        List<Token> result = new ArrayList<>();
        for (Token t : tokens) {
            if (t.type != TokenType.EMPTY && t.type != TokenType.UNKNOWN)
                result.add(t);
        }
        return result;
    }
    
}

