package yaku.uxntal;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Assembler {
    public static void main(String[] args) {
        System.out.println("yaku-java assembler (compatible with Perl yaku)");

        if (args.length < 2) {
            System.err.println("用法: java yaku.uxntal.Assembler <输入文件.tal> <输出文件.rom> [--pretty] [--check-only] [--verbose]");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args[1];
        boolean doPretty = false, checkOnly = false, verbose = false;

        for (String arg : args) {
            if (arg.equals("--pretty")) doPretty = true;
            if (arg.equals("--check-only")) checkOnly = true;
            if (arg.equals("--verbose")) verbose = true;
        }

        try {
            // 1. 读取源文件
            String source = new String(Files.readAllBytes(Paths.get(inputFile)), "UTF-8");

            // 2. 词法+语法分析
            Parser parser = new Parser();
            List<Token> tokens = parser.parse(source);

            // 3. pretty print（美化源码）
            if (doPretty) {
                System.out.println("====== Pretty Print ======");
                PrettyPrint.prettyPrint(tokens, false);
            }

            // 4. 语义检查
            ErrorChecker checker = new ErrorChecker();
            checker.check(tokens);

            if (checkOnly) {
                System.out.println("语义检查通过，无严重错误！");
                return;
            }

            // 5. 编码生成虚拟机内存
            Encoder encoder = new Encoder();
            Encoder.EncodeResult encodeResult = encoder.encode(tokens);

            // 6. 导出 ROM 文件（去除多余 0，和 Perl yaku 一致）
            int startAddr = 0x0100; // uxntal 程序入口通常是 0x0100
            Assembler.memToRom(encodeResult.memory, startAddr, true, outputFile, verbose);

            System.out.println("汇编完成，ROM 已写入: " + outputFile);
        } catch (Exception e) {
            System.err.println("汇编失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    /**
     * 与前文 Assembler.memToRom 完全一致，用于导出 ROM 文件
     */
    public static void memToRom(byte[] memory, int startAddr, boolean writeRom, String romFile, boolean verbose) throws Exception {
        int free = memory.length;
        byte[] bytes = new byte[free - startAddr];
        System.arraycopy(memory, startAddr, bytes, 0, free - startAddr);

        // 去掉末尾多余的 0
        int lastNonZero = bytes.length - 1;
        while (lastNonZero >= 0 && bytes[lastNonZero] == 0) lastNonZero--;
        if (lastNonZero < 0) lastNonZero = 0;
        byte[] outBytes = new byte[lastNonZero + 1];
        System.arraycopy(bytes, 0, outBytes, 0, lastNonZero + 1);

        if (verbose) {
            StringBuilder s = new StringBuilder();
            for (byte b : outBytes) s.append(String.format("%02x ", b & 0xFF));
            System.out.println("[ROM内容] " + s);
        }

        if (writeRom) {
            Files.write(Paths.get(romFile), outBytes);
            System.out.println("已导出ROM: " + romFile + " (" + outBytes.length + " bytes)");
        }
    }
}
