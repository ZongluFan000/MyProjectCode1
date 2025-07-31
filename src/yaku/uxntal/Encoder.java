// package yaku.uxntal;

// import java.util.*;
// import static yaku.uxntal.Definitions.*;

// public class Encoder {

//     public static class EncodeResult {
//         public byte[] memory;
//         public Map<String, Integer> labelTable;

//         public EncodeResult(byte[] memory, Map<String, Integer> labelTable) {
//             this.memory = memory;
//             this.labelTable = labelTable;
//         }
//     }

//     // 编译入口：tokens -> EncodeResult (内存 + 符号表)
//     public EncodeResult encode(List<Token> tokens) {
//         // 1. 初始化内存（64K），符号表
//         byte[] memory = new byte[MEMORY_SIZE];
//         Arrays.fill(memory, (byte) 0); // 默认全 0
//         Map<String, Integer> labelTable = new HashMap<>();
//         List<UnresolvedRef> unresolvedRefs = new ArrayList<>();

//         int pc = 0; // program counter，指示当前写入内存的地址

//         // 2. 第一遍遍历：写入指令、常量、数据、记录标签定义和引用
//         for (int i = 0; i < tokens.size(); i++) {
//             Token t = tokens.get(i);
//             switch (t.type) {
//                 case MAIN:
//                     pc = MAIN_ADDRESS;
//                     break;
//                 case ADDR:
//                     pc = t.size;
//                     break;
//                 case PAD:
//                     int padLen = t.size;
//                     for (int j = 0; j < padLen; j++) {
//                         if (pc < MEMORY_SIZE) memory[pc++] = 0;
//                     }
//                     break;
//                 case LABEL:
//                     // LABEL.value: 标签名；t.size: 2=父，1=子
//                     String labelName = t.value;
//                     if (labelTable.containsKey(labelName)) {
//                         throw new RuntimeException("标签重复定义: " + labelName + " (line " + t.line + ")");
//                     }
//                     labelTable.put(labelName, pc);
//                     break;
//                 case INSTR:
//                     // value: 助记符
//                     Integer opcode = Definitions.OPCODE_MAP.get(t.value.toUpperCase());
//                     if (opcode == null)
//                         throw new RuntimeException("未知指令: " + t.value + " (line " + t.line + ")");
//                     if (pc < MEMORY_SIZE) memory[pc++] = opcode.byteValue();
//                     break;
//                 // case LIT:
//                 //     // value: 十六进制字符串, size: 1 or 2 (字节数)
//                 //     int num = Integer.parseInt(t.value, 16);
//                 //     if (t.size == 2) {
//                 //         if (pc + 1 < MEMORY_SIZE) {
//                 //             memory[pc++] = (byte) ((num >> 8) & 0xFF);
//                 //             memory[pc++] = (byte) (num & 0xFF);
//                 //         }
//                 //     } else {
//                 //         if (pc < MEMORY_SIZE) memory[pc++] = (byte) (num & 0xFF);
//                 //     }
//                 //     break;
//                 case REF:
//                     // 引用标签地址，暂时记下，二次回填
//                     unresolvedRefs.add(new UnresolvedRef(t.value, t.size, pc, t.line));
//                     // 先占位 2 字节
//                     if (pc + 1 < MEMORY_SIZE) {
//                         memory[pc++] = 0;
//                         memory[pc++] = 0;
//                     }
//                     break;
                
//                 case LIT:
//                 // 先写 LIT 指令码，再写数值
//                     if (pc < MEMORY_SIZE) memory[pc++] = (byte) 0x80; // LIT 指令
//                     int num = Integer.parseInt(t.value, 16);
//                     if (t.size == 2) {
//                         if (pc + 1 < MEMORY_SIZE) {
//                             memory[pc++] = (byte) ((num >> 8) & 0xFF);
//                             memory[pc++] = (byte) (num & 0xFF);
//                         }
//                     } else {
//                         if (pc < MEMORY_SIZE) memory[pc++] = (byte) (num & 0xFF);
//                     }
//                     break;
                            

//                 default:
//                     // RAW、UNKNOWN、其它暂略
//                     break;
   

//             }
//         }

//         // 3. 第二遍：填充所有未解决的引用
//         for (UnresolvedRef ref : unresolvedRefs) {
//             Integer addr = labelTable.get(ref.name);
//             if (addr == null) {
//                 throw new RuntimeException("标签引用未定义: " + ref.name + " (line " + ref.line + ")");
//             }
//             // 简单实现：一律写入绝对地址（高字节，低字节）
//             memory[ref.pc] = (byte) ((addr >> 8) & 0xFF);
//             memory[ref.pc + 1] = (byte) (addr & 0xFF);
//         }

//         // 返回结果
//         return new EncodeResult(memory, labelTable);
//     }

//     // 内部类：记录未解决的引用
//     private static class UnresolvedRef {
//         String name;
//         int type; // refType，可以用来区分绝对/相对
//         int pc;   // memory 写入位置
//         int line; // 源码行号

//         public UnresolvedRef(String name, int type, int pc, int line) {
//             this.name = name;
//             this.type = type;
//             this.pc = pc;
//             this.line = line;
//         }
//     }
// }

package yaku.uxntal;

import java.util.*;
import yaku.uxntal.Definitions.TokenType;
import static yaku.uxntal.Definitions.*;

public class Encoder {

    public static class EncodeResult {
        public final byte[] memory;
        public final Map<String, Integer> labelTable;
        public final Map<Integer, String> reverseLabelTable;
        public EncodeResult(byte[] memory, Map<String, Integer> labelTable, Map<Integer, String> reverseLabelTable) {
            this.memory = memory;
            this.labelTable = labelTable;
            this.reverseLabelTable = reverseLabelTable;
        }
    }

    private static class EncoderState {
        byte[] memory = new byte[MEMORY_SIZE];
        Map<String, Integer> labelTable = new HashMap<>();
        Map<String, List<Integer>> refTable = new HashMap<>();
        Map<Integer, String> reverseLabelTable = new HashMap<>();
        int pc = 0;
        String currentParentLabel = "";
    }

    public EncodeResult encode(List<Token> tokens) {
        EncoderState state = new EncoderState();

        // 第一遍：写内存、收集标签和引用位置
        for (Token t : tokens) {
            switch (t.type) {
                case MAIN:
                    state.pc = MAIN_ADDRESS;
                    break;
                case ADDR:
                    state.pc = t.size;
                    break;
                case PAD:
                    for (int i = 0; i < t.size; i++) {
                        if (state.pc < MEMORY_SIZE) state.memory[state.pc++] = 0;
                    }
                    break;
                case LABEL:
                    String labelName = t.value;
                    if (t.size == 2) {
                        state.currentParentLabel = labelName;
                    } else if (t.size == 1) {
                        labelName = state.currentParentLabel + "/" + labelName;
                    }
                    if (state.labelTable.containsKey(labelName)) {
                        throw new RuntimeException("重复定义标签: " + labelName + " at line " + t.line);
                    }
                    state.labelTable.put(labelName, state.pc);
                    state.reverseLabelTable.put(state.pc, labelName);
                    break;
                case REF:
                    String refName = t.value;
                    if (t.isChild == 1 && !refName.contains("/")) {
                        refName = state.currentParentLabel + "/" + refName;
                    }
                    state.refTable.computeIfAbsent(refName, k -> new ArrayList<>()).add(state.pc);
                    int wordSz = (t.refType == 2 || t.refType == 5 || t.refType == 6) ? 2 : 1;
                    for (int i = 0; i < wordSz; i++) {
                        if (state.pc < MEMORY_SIZE) state.memory[state.pc++] = 0;
                    }
                    break;
                case INSTR:
                    // 完整编码 opcode 包含高5位 + 低3位 (mode bits)
                    Integer baseCode = OPCODE_MAP.get(t.value.toUpperCase());
                    if (baseCode == null) {
                        throw new RuntimeException("未知指令: " + t.value + " at line " + t.line);
                    }
                    int base = baseCode & 0xF8;  // 高5位
                    // bit2=sz, bit1=rs(refType), bit0=keep(isChild)
                    int mode = (t.size   == 2 ? 0x04 : 0)
                             | (t.refType== 1 ? 0x02 : 0)
                             | (t.isChild== 1 ? 0x01 : 0);
                    byte finalOp = (byte)(base | mode);
                    if (state.pc < MEMORY_SIZE) state.memory[state.pc++] = finalOp;
                    break;
                case LIT:
                    // LIT 同样拼合模式位
                    int litBase = 0x80; // LIT opcode base
                    int litMode = (t.size   == 2 ? 0x04 : 0)
                                | (t.refType== 1 ? 0x02 : 0)
                                | (t.isChild== 1 ? 0x01 : 0);
                    byte litOp = (byte)(litBase | litMode);
                    if (state.pc < MEMORY_SIZE) state.memory[state.pc++] = litOp;
                    int num = Integer.parseInt(t.value, 16);
                    if (t.size == 2) {
                        if (state.pc + 1 < MEMORY_SIZE) {
                            state.memory[state.pc++] = (byte)((num >> 8) & 0xFF);
                            state.memory[state.pc++] = (byte)(num & 0xFF);
                        }
                    } else {
                        if (state.pc < MEMORY_SIZE) state.memory[state.pc++] = (byte)(num & 0xFF);
                    }
                    break;
                case RAW:
                    int rawNum = Integer.parseInt(t.value, 16);
                    if (t.size == 2) {
                        if (state.pc + 1 < MEMORY_SIZE) {
                            state.memory[state.pc++] = (byte)((rawNum >> 8) & 0xFF);
                            state.memory[state.pc++] = (byte)(rawNum & 0xFF);
                        }
                    } else {
                        if (state.pc < MEMORY_SIZE) state.memory[state.pc++] = (byte)(rawNum & 0xFF);
                    }
                    break;
                default:
                    break;
            }
            if (state.pc > MEMORY_SIZE) {
                throw new RuntimeException("超出内存容量!");
            }
        }

        // 第二遍：解析所有 REF，填充真实地址
        for (Map.Entry<String, List<Integer>> entry : state.refTable.entrySet()) {
            String lbl = entry.getKey();
            Integer addr = state.labelTable.get(lbl);
            if (addr == null) {
                throw new RuntimeException("标签引用未定义: " + lbl);
            }
            for (int refPc : entry.getValue()) {
                Token tok = findTokenForRef(tokens, lbl, refPc);
                if (tok == null) {
                    throw new RuntimeException("找不到 REF Token 信息: " + lbl + " @ " + refPc);
                }
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
                    state.memory[refPc]     = (byte)((val >> 8) & 0xFF);
                    state.memory[refPc + 1] = (byte)(val & 0xFF);
                } else {
                    state.memory[refPc] = (byte)(val & 0xFF);
                }
            }
        }

        return new EncodeResult(state.memory, state.labelTable, state.reverseLabelTable);
    }

    private Token findTokenForRef(List<Token> tokens, String label, int pc) {
        for (Token t : tokens) {
            if (t.type == TokenType.REF
                && (t.value.equals(label)
                    || (t.isChild == 1 && label.endsWith("/" + t.value)))) {
                return t;
            }
        }
        return null;
    }

    private int toTwosComplement8(int v)  { return v & 0xFF;   }
    private int toTwosComplement16(int v) { return v & 0xFFFF; }
}
