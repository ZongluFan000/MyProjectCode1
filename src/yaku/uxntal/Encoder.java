package yaku.uxntal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import yaku.uxntal.Definitions.TokenType;

public class Encoder {

    //符号表
    public static class SymbolTable {
        public static class LabelInfo {
            public final int address;
            public final Token token;
            public final String type;  // "parent" or "child"
            public final String scope; // null or parent label
            public LabelInfo(int address, Token token, String type, String scope) {
                this.address = address;
                this.token = token;
                this.type = type;
                this.scope = scope;
            }
        }

        public static class RefInfo {
            // 地址字节的起始位置（若此前插入了 LIT，则为 LIT 之后的第一个地址字节）
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

        public LabelInfo resolveReference(String refName, String currentScope) {
            if (labels.containsKey(refName)) return labels.get(refName);
            if (currentScope != null && !refName.contains("/")) {
                String scopedName = currentScope + "/" + refName;
                if (labels.containsKey(scopedName)) return labels.get(scopedName);
            }
            return null;
        }
    }

    //反向符号信息
    public static class ReverseEntry {
        public final Token token;
        /**
         * 0 = 普通引用
         * 1 = 父标签定义位置
         * 2 = 调用（JSI/JSR）
         * 3 = 跳转（JMI/JMP）
         */
        public final int kind;
        public ReverseEntry(Token token, int kind) {
            this.token = token;
            this.kind = kind;
        }
    }

    //编码结果
    public static class EncodeResult {
        public final byte[] memory;
        public final SymbolTable symbolTable;
        public final int pc;
        public final int maxAddr; // 本次装配期间“写到的最后一个地址”（含该字节）
        public final Map<Integer, ReverseEntry> reverseSymbolTable;

        
        public EncodeResult(byte[] memory, SymbolTable symtab, int pc) {
            this(memory, symtab, pc, Collections.emptyMap(), Math.max(Definitions.MAIN_ADDRESS, pc - 1));
        }

        public EncodeResult(byte[] memory, SymbolTable symtab, int pc,
                            Map<Integer, ReverseEntry> reverseSymbolTable) {
            this(memory, symtab, pc, reverseSymbolTable,
                 // 回退一字节，至少不小于 ORIGIN
                 Math.max(Definitions.MAIN_ADDRESS, pc - 1));
        }

        public EncodeResult(byte[] memory, SymbolTable symtab, int pc,
                            Map<Integer, ReverseEntry> reverseSymbolTable, int maxAddr) {
            this.memory = memory;
            this.symbolTable = symtab;
            this.pc = pc;
            this.reverseSymbolTable = reverseSymbolTable != null ? reverseSymbolTable : Collections.emptyMap();
            this.maxAddr = maxAddr;
        }

       
        public byte[] romSlice() {
            final int ORIGIN = Definitions.MAIN_ADDRESS;
            int end = Math.max(ORIGIN, maxAddr + 1);
            // 去掉尾部多余 0
            while (end > ORIGIN && memory[end - 1] == 0) end--;
            if (end <= ORIGIN) return new byte[0];
            return Arrays.copyOfRange(memory, ORIGIN, end);
        }

        //直接写出 .rom 文件（裁剪后）
        public void writeRom(Path out) throws IOException {
            byte[] slice = romSlice();
            Files.write(out, slice);
        }
    }

    //对外主入口
    public static EncodeResult encode(List<Token> tokens) {
        final int MEMORY_SIZE = Definitions.MEMORY_SIZE;
        byte[] memory = new byte[MEMORY_SIZE];
        SymbolTable symtab = new SymbolTable();
        int pc = Definitions.MAIN_ADDRESS;
        int maxAddr = pc - 1; // 跟踪“写到的最后一个地址”

        tokens = filterUsefulTokens(tokens);

        for (Token t : tokens) {
            switch (t.type) {
                case MAIN: {
                    pc = Definitions.MAIN_ADDRESS;
                    break;
                }

                case ADDR: {
                    pc = parseHexSafe(t.value);
                    break;
                }

                case PAD: { // Perl 语义：写入 size+1 个 0
                    int count = t.size + 1;
                    pc = emitZeroes(memory, pc, count);
                    maxAddr = Math.max(maxAddr, pc - 1);
                    break;
                }

                case LABEL: {
                    String labelName = t.value;
                    if (!t.isParentLabel() && symtab.currentScope != null) {
                        labelName = symtab.currentScope + "/" + labelName;
                    }
                    if (symtab.labels.containsKey(labelName)) {
                        throw new RuntimeException("Duplicate label: " + labelName + " at line " + t.lineNum);
                    }
                    symtab.addLabel(labelName, pc, t);
                    break;
                }

                case REF: {
                    String refName = t.value;
                    if (t.isChildRef() && symtab.currentScope != null && !refName.contains("/")) {
                        refName = symtab.currentScope + "/" + refName;
                    }

                    // 约定：2/5/6 为 2 字节，其它按 1 字节
                    int wordSz = (t.refType == 2 || t.refType == 5 || t.refType == 6) ? 2 : 1;

                    // . , ; 需要先把地址压栈
                    boolean needLit = (t.refType < 3);

                    if (needLit) {
                        int litOpcode = Definitions.getOpcodeByte("LIT", wordSz == 2, false, false);
                        pc = emitByte(memory, pc, litOpcode);
                    }

                    int addrPos = pc;                  // 地址字节起始位置
                    symtab.addReference(refName, addrPos, t);

                    // 为地址留位（1 或 2 字节）
                    pc = emitZeroes(memory, pc, wordSz);
                    maxAddr = Math.max(maxAddr, pc - 1);
                    break;
                }

                case LIT: {
                    boolean shortMode  = (t.size == 2);
                    boolean returnMode = t.hasReturnMode();
                    boolean keepMode   = t.hasKeepMode();

                    int litOpcode = Definitions.getOpcodeByte("LIT", shortMode, returnMode, keepMode);
                    pc = emitByte(memory, pc, litOpcode);

                    int literalValue = parseHexSafe(t.value);
                    if (shortMode) {
                        pc = emitWord(memory, pc, literalValue); // 大端
                    } else {
                        pc = emitByte(memory, pc, literalValue);
                    }
                    maxAddr = Math.max(maxAddr, pc - 1);
                    break;
                }

                case RAW: {
                    int wordSz = t.size;
                    int val = parseHexSafe(t.value);
                    if (wordSz == 2) {
                        pc = emitWord(memory, pc, val); // 大端
                    } else {
                        pc = emitByte(memory, pc, val);
                    }
                    maxAddr = Math.max(maxAddr, pc - 1);
                    break;
                }

                case INSTR: {
                    boolean shortMode  = (t.size == 2);
                    boolean returnMode = t.hasReturnMode();
                    boolean keepMode   = t.hasKeepMode();

                    int opcode = Definitions.getOpcodeByte(t.value, shortMode, returnMode, keepMode);
                    //////////////////////////////////////////////////////////////
                    if (Flags.isDebug() || Flags.shouldPrintAndQuit()) {
                            System.err.printf("INSTR %s s=%s r=%s k=%s -> %02X%n",
                                 t.value, shortMode, returnMode, keepMode, opcode);
                    }
                    ////////////////////////////////////////////////////////////
                    pc = emitByte(memory, pc, opcode);
                    maxAddr = Math.max(maxAddr, pc - 1);
                    break;
                }

                default:
                    // EMPTY / UNKNOWN 已被 filterUsefulTokens 去除
                    break;
            }
        }

        resolveReferences(memory, symtab);

        Map<Integer, ReverseEntry> reverseSymbolTable = buildReverseSymbolTable(memory, symtab);

        return new EncodeResult(memory, symtab, pc, reverseSymbolTable, maxAddr);
    }

    //写入工具

    private static int emitByte(byte[] memory, int pc, int value) {
        ensureCapacity(Definitions.MEMORY_SIZE, pc, 1, "emitByte");
        memory[pc] = (byte) (value & 0xFF);
        return pc + 1;
    }

    private static int emitWord(byte[] memory, int pc, int value) {
        ensureCapacity(Definitions.MEMORY_SIZE, pc, 2, "emitWord");
        // UXN 16 位为大端
        memory[pc]     = (byte) ((value >> 8) & 0xFF);
        memory[pc + 1] = (byte) (value & 0xFF);
        return pc + 2;
    }

    private static int emitZeroes(byte[] memory, int pc, int count) {
        ensureCapacity(Definitions.MEMORY_SIZE, pc, count, "emitZeroes");
        Arrays.fill(memory, pc, pc + count, (byte) 0);
        return pc + count;
    }

    //引用回填

    private static void resolveReferences(byte[] memory, SymbolTable symtab) {
        for (Map.Entry<String, List<SymbolTable.RefInfo>> entry : symtab.references.entrySet()) {
            final String refKey = entry.getKey();
            SymbolTable.LabelInfo def = symtab.labels.get(refKey);

            if (def == null) {
                
                String probe = refKey.length() >= 3 ? refKey.substring(0, 3).toUpperCase()
                                                     : refKey.toUpperCase();

                boolean looksLikeOpcode = false;
                try {
                    Definitions.getOpcodeByte(probe, false, false, false);
                    looksLikeOpcode = true;
                } catch (RuntimeException ignored) { /* not an opcode */ }

                if (looksLikeOpcode) {
                    int line = entry.getValue().isEmpty() ? -1 : entry.getValue().get(0).token.lineNum;
                    throw new RuntimeException(
                        "Invalid opcode " + refKey +
                        (line >= 0 ? (" at line " + line) : "") +
                        " (did you intend an instruction rather than a label reference?)"
                    );
                }
                throw new RuntimeException("Undefined label reference: " + refKey);
            }

            int address = def.address;

            for (SymbolTable.RefInfo ref : entry.getValue()) {
                Token t = ref.token;
                int pc = ref.address;
                int refType = t.refType;

                // 约定保持你现有语义：
                // 6 -> 2字节相对；1/4 -> 1字节相对；2/5 -> 2字节绝对；其它 -> 1字节绝对
                if (refType == 6) { // 2 字节相对
                    int rel = address - (pc + 2); // 基准：相对字段之后
                    if (rel < -32768 || rel > 32767)
                        throw new RuntimeException("Relative address too large for " + refKey);
                    memory[pc]     = (byte) ((rel >> 8) & 0xFF);
                    memory[pc + 1] = (byte) (rel & 0xFF);

                } else if (refType == 1 || refType == 4) { // 1 字节相对
                    int rel = address - (pc + 2); // 注意：如果你确认应为 (pc+1)，改这里
                    if (rel < -128 || rel > 127)
                        throw new RuntimeException("Relative address too large for " + refKey);
                    memory[pc] = (byte) (rel & 0xFF);

                } else if (refType == 2 || refType == 5) { // 2 字节绝对
                    memory[pc]     = (byte) ((address >> 8) & 0xFF);
                    memory[pc + 1] = (byte) (address & 0xFF);

                } else { // 1 字节绝对（零页）
                    memory[pc] = (byte) (address & 0xFF);
                }
            }
        }
    }

    //其它

    private static List<Token> filterUsefulTokens(List<Token> tokens) {
        List<Token> result = new ArrayList<>();
        for (Token t : tokens) {
            if (t.type != TokenType.EMPTY && t.type != TokenType.UNKNOWN) {
                result.add(t);
            }
        }
        return result;
    }

    private static int parseHexSafe(String hex) {
        // 容错：允许大小写，不带 0x 前缀
        return Integer.parseInt(hex.trim(), 16);
    }

    private static void ensureCapacity(int memorySize, int pc, int bytesToWrite, String ctx) {
        if (pc < 0 || pc + bytesToWrite > memorySize) {
            throw new RuntimeException("Memory overflow at pc=" + pc + " while writing " + ctx + " (" + bytesToWrite + " bytes)");
        }
    }

    //反向符号表构建

    /**
     * 构建与 Perl 版一致的反向索引：
     * - 父标签地址：kind=1
     * - 引用地址：根据附近字节判别是调用(JSI/JSR)还是跳转(JMI/JMP)，否则 kind=0
     */
    private static Map<Integer, ReverseEntry> buildReverseSymbolTable(byte[] memory, SymbolTable symtab) {
        Map<Integer, ReverseEntry> rtab = new HashMap<>();

        // 父标签定义处
        for (var e : symtab.labels.entrySet()) {
            var li = e.getValue();
            if ("parent".equals(li.type)) {
                rtab.put(li.address, new ReverseEntry(li.token, 1));
            }
        }

        // 引用用途分类：准备各助记符的全部 opcode 取值集合
        Set<Integer> JSRs = allOpcodesOf("JSR");
        Set<Integer> JSIs = allOpcodesOf("JSI");
        Set<Integer> JMPs = allOpcodesOf("JMP");
        Set<Integer> JMIs = allOpcodesOf("JMI");

        for (var e : symtab.references.entrySet()) {
            for (var ref : e.getValue()) {
                int addr = ref.address; // 地址坑位开始
                int wordSz = (ref.token.refType == 2 || ref.token.refType == 5 || ref.token.refType == 6) ? 2 : 1;

                int kind = 0;

                // 立即数形式：地址前一个字节应是 JSI/JMI
                int before = addr - 1;
                if (before >= 0 && before < memory.length) {
                    int b = memory[before] & 0xFF;
                    if (JSIs.contains(b)) kind = 2;
                    if (JMIs.contains(b)) kind = 3;
                }

                // 非立即数形式：地址后面紧跟的那个字节通常是 JSR/JMP
                
                int after = addr + wordSz + 1;
                if (after >= 0 && after < memory.length) {
                    int b = memory[after] & 0xFF;
                    if (JSRs.contains(b)) kind = 2;
                    if (JMPs.contains(b)) kind = 3;
                }

                rtab.put(addr, new ReverseEntry(ref.token, kind));
            }
        }

        return rtab;
    }

    /**
     * 生成某助记符在（short, r, k）三位所有组合下可用的 opcode 字节集合。
     * 若某组合不被支持，getOpcodeByte 会抛异常，忽略即可。
     */
    private static Set<Integer> allOpcodesOf(String mnemonic) {
        Set<Integer> set = new HashSet<>();
        boolean[] flags = new boolean[]{false, true};
        for (boolean s : flags) {
            for (boolean r : flags) {
                for (boolean k : flags) {
                    try {
                        int byteVal = Definitions.getOpcodeByte(mnemonic, s, r, k);
                        set.add(byteVal & 0xFF);
                    } catch (RuntimeException ignore) {
                        // 该组合不存在
                    }
                }
            }
        }
        return set;
    }
}
