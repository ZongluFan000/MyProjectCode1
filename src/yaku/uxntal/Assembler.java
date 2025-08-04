package yaku.uxntal;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
// import yaku.uxntal.Token;
// import yaku.uxntal.Definitions;

public class Assembler {

    // =========== 主接口：导出内存为 ROM =============
    public static byte[] memToRom(Token[] memory, int free, boolean writeRom, String romFile, boolean verbose) throws Exception {
        // 按 JS 版功能：只导出 0x100 ~ free
        int startAddr = 0x100;
        int len = free - startAddr;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            Token token = memory[startAddr + i];
            bytes[i] = tokenToByte(token);
        }
        // 去除尾部 0
        byte[] trimmed = trimTrailingZeros(bytes);

        if (verbose) {
            System.out.println("[ROM 内容] " + createHexDump(trimmed, 16));
        }

        if (writeRom) {
            Files.write(Paths.get(romFile), trimmed);
            if (verbose) {
                System.out.println("已导出 ROM: " + romFile + " (" + trimmed.length + " bytes)");
            }
        }
        return trimmed;
    }

    // =========== Token -> 字节流 =============

    private static byte tokenToByte(Token token) {
        if (token == null) return 0;
        if (token.type == Definitions.TokenType.INSTR) {
            return instructionToByte(token);
        }
        try {
            return (byte) (Integer.parseInt(token.value) & 0xFF);
        } catch (Exception e) {
            return 0;
        }
    }

    // 解释型指令转编码
    private static byte instructionToByte(Token token) {
        // 支持 LIT、JCI、JMI、JSI、BRK 特殊处理
        String name = token.value.toUpperCase();
        int shortMode = token.size;      // 通常 size==2 表示 short
        int returnMode = token.stack;    // 按你的 token 结构
        int keepMode = token.keep;

        if (name.equals("LIT")) {
            int instrByte = 0x80;
            if (shortMode == 2) instrByte |= 0x20;
            if (returnMode == 1) instrByte |= 0x40;
            if (keepMode == 1) instrByte |= 0x80;
            return (byte) instrByte;
        }
        if (name.equals("JCI")) return 0x20;
        if (name.equals("JMI")) return 0x40;
        if (name.equals("JSI")) return 0x60;
        if (name.equals("BRK")) return 0x00;

        // 普通指令（查 Definitions.OPCODE_MAP）
        Integer baseOpcode = Definitions.OPCODE_MAP.get(name);
        if (baseOpcode == null) {
            throw new RuntimeException("未知指令: " + name + " (token: " + token + ")");
        }
        int instrByte = baseOpcode & 0x1F;
        if (shortMode == 2) instrByte |= 0x20;
        if (returnMode == 1) instrByte |= 0x40;
        if (keepMode == 1) instrByte |= 0x80;
        return (byte) instrByte;
    }

    // =========== 工具方法 =============

    // 去除尾部零字节
    private static byte[] trimTrailingZeros(byte[] bytes) {
        int endIndex = bytes.length - 1;
        while (endIndex >= 0 && bytes[endIndex] == 0) endIndex--;
        return Arrays.copyOf(bytes, endIndex + 1);
    }

    // 十六进制转储
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

    // ROM 校验
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

    // ROM 统计
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

    // ROM 比较
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

    // =========== 字节码流构造器 =============
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

        public BytecodeBuilder instruction(String instr, boolean shortMode, boolean returnMode, boolean keepMode) {
            bytes.add(InstructionEncoder.encode(instr, shortMode, returnMode, keepMode));
            return this;
        }

        public BytecodeBuilder literal(int value, boolean isShort) {
            instruction("LIT", isShort, false, false);
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

    // =========== 指令编码器 =============
    public static class InstructionEncoder {
        public static byte encode(String instruction, boolean shortMode, boolean returnMode, boolean keepMode) {
            switch (instruction.toUpperCase()) {
                case "BRK": return 0x00;
                case "LIT": return encodeLit(shortMode, returnMode, keepMode);
                case "JCI": return 0x20;
                case "JMI": return 0x40;
                case "JSI": return 0x60;
            }
            Integer baseOpcode = Definitions.OPCODE_MAP.get(instruction.toUpperCase());
            if (baseOpcode == null) throw new RuntimeException("Unknown instruction: " + instruction);
            int code = baseOpcode & 0x1F;
            if (shortMode) code |= 0x20;
            if (returnMode) code |= 0x40;
            if (keepMode) code |= 0x80;
            return (byte) code;
        }
        public static byte encodeLit(boolean shortMode, boolean returnMode, boolean keepMode) {
            int code = 0x80;
            if (shortMode) code |= 0x20;
            if (returnMode) code |= 0x40;
            if (keepMode) code |= 0x80;
            return (byte) code;
        }
        
    }
}
