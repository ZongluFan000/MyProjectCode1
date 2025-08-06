
package yaku.uxntal;

import java.util.*;

public class Encoder {
   


    
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
            System.out.println("Encoder for idx=" + i + ", token=" + t);
    
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







 
                case INSTR: {
                    String instrName = t.value.toUpperCase();
                
                    // 需要判断是否2字节
                    Set<String> instr2Set = new HashSet<>(Arrays.asList(
                        "ADD","SUB","MUL","DIV","AND","ORA","EOR","SFT",
                        "DEO","DEI","STA","LDA","STZ","LDZ","JCN","JMP","JSR",
                        "POP","DUP","SWP","OVR","ROT","EQU","NEQ","GTH","LTH",
                        "STR","LDR","STH"
                    ));
                    if (t.size == 2 && instr2Set.contains(instrName)) {
                        instrName += "2";
                    }
                
                    Integer opcode = Definitions.OPCODE_MAP.get(instrName);
                    if (opcode == null)
                        throw new RuntimeException("Unknown instruction: " + instrName + " at line " + t.line);
                    if (pc < MEMORY_SIZE) {
                        memory[pc++] = (byte) (opcode & 0xFF);
                    }
                    break;
                }
                





                case LIT: {
                    Integer opcode = (t.size == 2)
                        ? Definitions.OPCODE_MAP.get("LIT2")
                        : Definitions.OPCODE_MAP.get("LIT");
                    memory[pc++] = (byte) (opcode & 0xFF);
                
                    int litNum = Integer.parseInt(t.value, 16);
                    if (t.size == 2) {
                        memory[pc++] = (byte) ((litNum >> 8) & 0xFF); // 高字节
                        memory[pc++] = (byte) (litNum & 0xFF);        // 低字节
                    } else {
                        memory[pc++] = (byte) (litNum & 0xFF);
                    }
                    break;
                }
                
                






                
                
                case RAW: {
                    int rawNum = Integer.parseInt(t.value, 16);
                    if (t.size == 2) {
                        if (pc + 1 < MEMORY_SIZE) {
                            memory[pc++] = (byte) ((rawNum >> 8) & 0xFF);
                            memory[pc++] = (byte) (rawNum & 0xFF);
                        }
                    } else {
                        if (pc < MEMORY_SIZE) {
                            memory[pc++] = (byte) (rawNum & 0xFF);
                        }
                    }
                    break;
                }
                default:
                    break;
            }
            if (pc > MEMORY_SIZE)
                throw new RuntimeException("Memory capacity exceeded at line " + t.line);
        }
    
        // 第二遍：回填符号引用（作用域解析）
        for (Map.Entry<String, List<SymbolTable.RefInfo>> entry : symtab.references.entrySet()) {
            String refName = entry.getKey();
            List<SymbolTable.RefInfo> refs = entry.getValue();
            for (SymbolTable.RefInfo ref : refs) {
                SymbolTable.LabelInfo lbl = symtab.resolveReference(refName, ref.scope);
                if (lbl == null)
                    throw new RuntimeException("Undefined label reference: " + refName);
    
                Token tok = ref.token;
                int wSz = (tok.refType == 2 || tok.refType == 5 || tok.refType == 6) ? 2 : 1;
                int val = lbl.address;
                if (tok.refType == 6) { // Immediate
                    val = lbl.address - (ref.address + 2);
                    if (val < 0) val = toTwosComplement16(val);
                } else if (tok.refType == 1 || tok.refType == 4) { // relative
                    val = lbl.address - (ref.address + 2);
                    if (val < 0) val = toTwosComplement8(val);
                }
                if (wSz == 2) {
                    memory[ref.address] = (byte) ((val >> 8) & 0xFF);
                    memory[ref.address + 1] = (byte) (val & 0xFF);
                } else {
                    memory[ref.address] = (byte) (val & 0xFF);
                }
            }
        }
        return new EncodeResult(memory, symtab, pc);
    }
    

    // 只保留有用token，丢弃无用token
    private static List<Token> filterUsefulTokens(List<Token> tokens) {
        List<Token> filtered = new ArrayList<>();
        for (Token t : tokens) {
            switch (t.type) {
                case INSTR:
                case LIT:
                case RAW:
                case REF:
                case LABEL:
                case ADDR:
                case PAD:
                    filtered.add(t);
                    break;
                default:
                    break;
            }
        }
        return filtered;
    }

    public static Token findTokenForRef(List<Token> tokens, String label, int pc) {
        for (Token t : tokens) {
            if (t.type == Definitions.TokenType.REF &&
                (t.value.equals(label) || (t.isChild == 1 && label.endsWith("/" + t.value)))) {
                return t;
            }
        }
        return null;
    }


   
    


    public static int toTwosComplement8(int v) { return v & 0xFF; }
    public static int toTwosComplement16(int v) { return v & 0xFFFF; }
}

