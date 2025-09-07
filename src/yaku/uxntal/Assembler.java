package yaku.uxntal;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Assembler {

   
    //将内存导出成 ROM，去掉多余 0 填充
    public static byte[] memToRom(byte[] memory, boolean writeRom, String romFile) throws Exception {
        int romStart = 0x100; // 程序入口
        int end = memory.length;

        // 去掉尾部的零
        while (end > romStart && memory[end - 1] == 0) {
            end--;
        }
        byte[] trimmed = Arrays.copyOfRange(memory, romStart, end);

        if (writeRom) {
            Files.write(Paths.get(romFile), trimmed);
            System.out.printf("[Assembler] ROM 写入完成：%s（大小 %d 字节）%n", romFile, trimmed.length);
        } else {
            System.out.printf("[Assembler] 测试模式：未写入 ROM（大小 %d 字节）%n", trimmed.length);
        }
        return trimmed;
    }

    //将内存导出成 ROM，带 free 边界，且从尾部反向剔除连续 0
    public static byte[] memToRom(byte[] memory, int free, boolean writeRom, String romFile) throws Exception {
        final int romStart = 0x100;
        int end = Math.max(Math.min(free, memory.length), romStart);

        // 仅裁剪 romStart..end，再从尾部反向剔除 0
        int tail = end;
        while (tail > romStart && memory[tail - 1] == 0) tail--;

        byte[] trimmed = Arrays.copyOfRange(memory, romStart, tail);

        if (writeRom) {
            Files.write(Paths.get(romFile), trimmed);
            System.out.printf("[Assembler] ROM 写入完成：%s（大小 %d 字节）%n", romFile, trimmed.length);
        } else {
            System.out.printf("[Assembler] 测试模式：未写入 ROM（大小 %d 字节）%n", trimmed.length);
        }
        return trimmed;
    }

  
    // 从 Token 内存导出 ROM
    public static byte[] memToRomFromTokens(List<Token> memoryTokens, int free, boolean writeRom, String romFile) throws Exception {
        final int romStart = 0x100;
        final int upper = Math.max(free, romStart);

        List<Byte> bytes = new ArrayList<>();
        for (int i = romStart; i < upper; i++) {
            Token tk = (i >= 0 && i < memoryTokens.size()) ? memoryTokens.get(i) : null;
            bytes.add(tokenToBytePerlExact(tk));
        }

        // 反向剔除尾部连续 0（Perl 同款策略）
        int end = bytes.size();
        while (end > 0 && bytes.get(end - 1) == 0) end--;
        byte[] trimmed = new byte[end];
        for (int i = 0; i < end; i++) trimmed[i] = bytes.get(i);

        if (writeRom) {
            Files.write(Paths.get(romFile), trimmed);
            System.out.printf("[Assembler] ROM 写入完成：%s（大小 %d 字节）%n", romFile, trimmed.length);
        } else {
            System.out.printf("[Assembler] 测试模式：未写入 ROM（大小 %d 字节）%n", trimmed.length);
        }
        return trimmed;
    }

    
    //token→byte 规则
    private static byte tokenToBytePerlExact(Token token) {
        if (token == null) return 0;
        if (token.type == Definitions.TokenType.INSTR) {
            return instructionToBytePerlExact(token);
        }
       
        Integer v = tryParseIntFlexible(token.value);
        return (byte)((v != null ? v : 0) & 0xFF);
    }

    //指令编码 
    private static byte instructionToBytePerlExact(Token tk) {
        final String instr = tk.value.toUpperCase();   // 用完整助记符
        final int shortBit = ((tk.size == 2 ? 1 : 0) << 5);
        final int rBit     = ((tk.stack != 0 ? 1 : 0) << 6);
        final int kBit     = ((tk.keep  != 0 ? 1 : 0) << 7);

        // LIT 特判：0x80 + 模式位，不拼低5位
        if ("LIT".equals(instr)) {
            int b = 0x80 | shortBit | rBit | kBit;
            return (byte)(b & 0xFF);
        }

        // J?I 特判：JCI/JMI/JSI 直接取 opcode 表值，忽略模式位
        if ("JCI".equals(instr) || "JMI".equals(instr) || "JSI".equals(instr)) {
            int op = Definitions.getOpcode(instr); // 需要 Definitions 提供该访问器
            return (byte)(op & 0xFF);
        }

        // 普通指令：模式位 + (opcode & 0x1F)
        int base = Definitions.getOpcode(instr); // 从 opcode 映射取值
        int b = shortBit | rBit | kBit | (base & 0x1F);
        return (byte)(b & 0xFF);
    }

    //宽容解析：0x.. / 两位十六进制 / 十进制
    private static Integer tryParseIntFlexible(String s) {
        if (s == null) return null;
        String t = s.trim();
        try {
            if (t.startsWith("0x") || t.startsWith("0X")) {
                return Integer.parseInt(t.substring(2), 16);
            }
            // 若是 2 位十六进制，也按十六进制试一次
            if (t.matches("^[0-9a-fA-F]{2}$")) {
                return Integer.parseInt(t, 16);
            }
            // 其他情况走十进制
            return Integer.parseInt(t);
        } catch (Exception e) {
            return null;
        }
    }

    
    //将 Token 转成一个字节
    // private static byte tokenToByte(Token token) {
    //     if (token == null) return 0;
    //     if (token.type == Definitions.TokenType.INSTR) {
    //         return instructionToByte(token);
    //     }
    //     try {
    //         return (byte) (Integer.parseInt(token.value, 16) & 0xFF);
    //     } catch (Exception e) {
    //         return 0;
    //     }
    // }

    //指令编码
    // private static byte instructionToByte(Token token) {
    //     String baseName = token.value.substring(0, Math.min(3, token.value.length())).toUpperCase(); // 防御性截断
    //     boolean shortMode = token.size == 2;
    //     boolean returnMode = token.stack != 0;
    //     boolean keepMode = token.keep != 0;
    //     return (byte) Definitions.getOpcodeByte(baseName, shortMode, returnMode, keepMode);
    // }

  
    //除尾部零字节
    // private static byte[] trimTrailingZeros(byte[] bytes) {
    //     int endIndex = bytes.length - 1;
    //     while (endIndex >= 0 && bytes[endIndex] == 0) endIndex--;
    //     return Arrays.copyOf(bytes, endIndex + 1);
    // }

    //十六进制转储
    public static String createHexDump(byte[] bytes, int bytesPerLine) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i += bytesPerLine) {
            int addr = 0x100 + i;
            sb.append(String.format("%04X: ", addr));
            StringBuilder ascii = new StringBuilder();
            for (int j = 0; j < bytesPerLine; j++) {
                int k = i + j;
                if (k < bytes.length) {
                    int b = bytes[k] & 0xFF;
                    sb.append(String.format("%02X ", b));
                    ascii.append((b >= 32 && b <= 126) ? (char) b : '.');
                } else {
                    sb.append("   ");
                    ascii.append(" ");
                }
            }
            sb.append("|").append(ascii).append("|\n");
        }
        return sb.toString();
    }

    //ROM 校验 
    public static RomValidationResult validateRom(byte[] bytes) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (bytes.length == 0) errors.add("ROM is empty");
        if (bytes.length > 0xFF00) warnings.add("ROM is large (" + bytes.length + " bytes)");
        boolean hasValidInstructions = false;
        for (byte b : bytes) {
            int baseOpcode = b & 0x1F;
            if (baseOpcode < 32) {
                hasValidInstructions = true;
                break;
            }
        }
        if (!hasValidInstructions) warnings.add("ROM does not appear to contain valid instructions");
        return new RomValidationResult(errors.isEmpty(), errors, warnings, bytes.length);
    }

    public static class RomValidationResult {
        public boolean valid;
        public List<String> errors;
        public List<String> warnings;
        public int size;
        public RomValidationResult(boolean valid, List<String> errors, List<String> warnings, int size) {
            this.valid = valid; this.errors = errors; this.warnings = warnings; this.size = size;
        }
    }

    //ROM 统计
    public static RomStats getRomStats(byte[] bytes) {
        RomStats stats = new RomStats();
        stats.totalBytes = bytes.length;
        for (byte b : bytes) {
            if (b == 0) stats.zeroBytes++;
            else {
                int baseOpcode = b & 0x1F;
                if (baseOpcode < 32) { stats.instructionBytes++; stats.instructionCount++; }
                else stats.dataBytes++;
            }
        }
        return stats;
    }
    public static class RomStats {
        public int totalBytes, instructionBytes, dataBytes, zeroBytes, instructionCount;
    }

    //ROM 比较 
    public static RomDiffResult compareRoms(byte[] rom1, byte[] rom2) {
        List<RomDiff> diffs = new ArrayList<>();
        int maxLen = Math.max(rom1.length, rom2.length);
        for (int i = 0; i < maxLen; i++) {
            int b1 = i < rom1.length ? rom1[i] & 0xFF : -1;
            int b2 = i < rom2.length ? rom2[i] & 0xFF : -1;
            if (b1 != b2) {
                diffs.add(new RomDiff(0x100 + i, b1, b2));
            }
        }
        return new RomDiffResult(diffs.isEmpty(), diffs, rom1.length, rom2.length);
    }
    public static class RomDiffResult {
        public boolean identical;
        public List<RomDiff> differences;
        public int size1, size2;
        public RomDiffResult(boolean identical, List<RomDiff> differences, int s1, int s2) {
            this.identical = identical; this.differences = differences; this.size1 = s1; this.size2 = s2;
        }
    }
    public static class RomDiff {
        public int address, byte1, byte2;
        public RomDiff(int addr, int b1, int b2) { address = addr; byte1 = b1; byte2 = b2; }
    }

   
    //字节码构造器
    public static class BytecodeBuilder {
        private final List<Byte> bytes = new ArrayList<>();
        private final Map<String, Integer> labels = new HashMap<>();
        private final List<Patch> patches = new ArrayList<>();

        public BytecodeBuilder byteVal(int b) {
            if (b < 0 || b > 255) throw new RuntimeException("Invalid byte value: " + b);
            bytes.add((byte) b);
            return this;
        }

        public BytecodeBuilder shortVal(int value) {
            if (value < 0 || value > 65535) throw new RuntimeException("Invalid short value: " + value);
            bytes.add((byte) ((value >> 8) & 0xFF));
            bytes.add((byte) (value & 0xFF));
            return this;
        }

        public BytecodeBuilder instruction(String baseName, int size, int r, int k) {
            bytes.add((byte) Definitions.getOpcodeByte(baseName, size, r, k));
            return this;
        }

        public BytecodeBuilder literal(int value, boolean isShort) {
            instruction("LIT", isShort ? 2 : 1, 0, 0);
            if (isShort) shortVal(value);
            else byteVal(value);
            return this;
        }

        public BytecodeBuilder label(String name) {
            labels.put(name, bytes.size());
            return this;
        }

        public BytecodeBuilder reference(String labelName, boolean isShort) {
            int pos = bytes.size();
            patches.add(new Patch(labelName, pos, isShort));
            if (isShort) shortVal(0);
            else byteVal(0);
            return this;
        }

        public byte[] build() {
            for (Patch patch : patches) {
                Integer labelPos = labels.get(patch.labelName);
                if (labelPos == null) throw new RuntimeException("Undefined label: " + patch.labelName);
                if (patch.isShort) {
                    bytes.set(patch.position, (byte) ((labelPos >> 8) & 0xFF));
                    bytes.set(patch.position + 1, (byte) (labelPos & 0xFF));
                } else {
                    bytes.set(patch.position, (byte) (labelPos & 0xFF));
                }
            }
            byte[] arr = new byte[bytes.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = bytes.get(i);
            return arr;
        }

        public int position() { return bytes.size(); }
        public BytecodeBuilder align(int boundary) {
            while (bytes.size() % boundary != 0) byteVal(0);
            return this;
        }
        public BytecodeBuilder pad(int count, int value) {
            for (int i = 0; i < count; i++) byteVal(value);
            return this;
        }
        public BytecodeBuilder clear() {
            bytes.clear(); labels.clear(); patches.clear();
            return this;
        }

        private static class Patch {
            public final String labelName; public final int position; public final boolean isShort;
            Patch(String labelName, int position, boolean isShort) { this.labelName = labelName; this.position = position; this.isShort = isShort; }
        }
    }
}
