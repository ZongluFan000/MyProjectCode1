package yaku.uxntal;

import java.util.*;
import static yaku.uxntal.Definitions.*;

public class Encoder {

    public static class EncodeResult {
        public byte[] memory;
        public Map<String, Integer> labelTable;

        public EncodeResult(byte[] memory, Map<String, Integer> labelTable) {
            this.memory = memory;
            this.labelTable = labelTable;
        }
    }

    // 编译入口：tokens -> EncodeResult (内存 + 符号表)
    public EncodeResult encode(List<Token> tokens) {
        // 1. 初始化内存（64K），符号表
        byte[] memory = new byte[MEMORY_SIZE];
        Arrays.fill(memory, (byte) 0); // 默认全 0
        Map<String, Integer> labelTable = new HashMap<>();
        List<UnresolvedRef> unresolvedRefs = new ArrayList<>();

        int pc = 0; // program counter，指示当前写入内存的地址

        // 2. 第一遍遍历：写入指令、常量、数据、记录标签定义和引用
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            switch (t.type) {
                case MAIN:
                    pc = MAIN_ADDRESS;
                    break;
                case ADDR:
                    pc = t.size;
                    break;
                case PAD:
                    int padLen = t.size;
                    for (int j = 0; j < padLen; j++) {
                        if (pc < MEMORY_SIZE) memory[pc++] = 0;
                    }
                    break;
                case LABEL:
                    // LABEL.value: 标签名；t.size: 2=父，1=子
                    String labelName = t.value;
                    if (labelTable.containsKey(labelName)) {
                        throw new RuntimeException("标签重复定义: " + labelName + " (line " + t.line + ")");
                    }
                    labelTable.put(labelName, pc);
                    break;
                case INSTR:
                    // value: 助记符
                    Integer opcode = Definitions.OPCODE_MAP.get(t.value.toUpperCase());
                    if (opcode == null)
                        throw new RuntimeException("未知指令: " + t.value + " (line " + t.line + ")");
                    if (pc < MEMORY_SIZE) memory[pc++] = opcode.byteValue();
                    break;
                // case LIT:
                //     // value: 十六进制字符串, size: 1 or 2 (字节数)
                //     int num = Integer.parseInt(t.value, 16);
                //     if (t.size == 2) {
                //         if (pc + 1 < MEMORY_SIZE) {
                //             memory[pc++] = (byte) ((num >> 8) & 0xFF);
                //             memory[pc++] = (byte) (num & 0xFF);
                //         }
                //     } else {
                //         if (pc < MEMORY_SIZE) memory[pc++] = (byte) (num & 0xFF);
                //     }
                //     break;
                case REF:
                    // 引用标签地址，暂时记下，二次回填
                    unresolvedRefs.add(new UnresolvedRef(t.value, t.size, pc, t.line));
                    // 先占位 2 字节
                    if (pc + 1 < MEMORY_SIZE) {
                        memory[pc++] = 0;
                        memory[pc++] = 0;
                    }
                    break;
                
                case LIT:
                // 先写 LIT 指令码，再写数值
                    if (pc < MEMORY_SIZE) memory[pc++] = (byte) 0x80; // LIT 指令
                    int num = Integer.parseInt(t.value, 16);
                    if (t.size == 2) {
                        if (pc + 1 < MEMORY_SIZE) {
                            memory[pc++] = (byte) ((num >> 8) & 0xFF);
                            memory[pc++] = (byte) (num & 0xFF);
                        }
                    } else {
                        if (pc < MEMORY_SIZE) memory[pc++] = (byte) (num & 0xFF);
                    }
                    break;
                            

                default:
                    // RAW、UNKNOWN、其它暂略
                    break;
   

            }
        }

        // 3. 第二遍：填充所有未解决的引用
        for (UnresolvedRef ref : unresolvedRefs) {
            Integer addr = labelTable.get(ref.name);
            if (addr == null) {
                throw new RuntimeException("标签引用未定义: " + ref.name + " (line " + ref.line + ")");
            }
            // 简单实现：一律写入绝对地址（高字节，低字节）
            memory[ref.pc] = (byte) ((addr >> 8) & 0xFF);
            memory[ref.pc + 1] = (byte) (addr & 0xFF);
        }

        // 返回结果
        return new EncodeResult(memory, labelTable);
    }

    // 内部类：记录未解决的引用
    private static class UnresolvedRef {
        String name;
        int type; // refType，可以用来区分绝对/相对
        int pc;   // memory 写入位置
        int line; // 源码行号

        public UnresolvedRef(String name, int type, int pc, int line) {
            this.name = name;
            this.type = type;
            this.pc = pc;
            this.line = line;
        }
    }
}
