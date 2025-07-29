package yaku.uxntal;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Assembler {
    public static void main(String[] args) {
        // 兼容 Perl yaku 参数
        boolean wFlag = false;
        boolean runFlag = false;
        boolean assembleFlag = false; // 脚本里其实默认就是 true
        boolean doPretty = false, checkOnly = false, verbose = false;
        String inputFile = null;
        String outputFile = null;

        // 参数解析
        for (String arg : args) {
            switch (arg) {
                case "-W":
                    wFlag = true; verbose = true; // -W 视为 verbose
                    break;
                case "-r":
                    runFlag = true;
                    break;
                case "-a":
                    assembleFlag = true;
                    break;
                case "--pretty":
                    doPretty = true;
                    break;
                case "--check-only":
                    checkOnly = true;
                    break;
                case "--verbose":
                    verbose = true;
                    break;
                default:
                    if (arg.endsWith(".tal")) inputFile = arg;
                    else if (arg.endsWith(".rom")) outputFile = arg;
                    break;
            }
        }

        if (inputFile == null) {
            System.err.println("缺少输入文件（.tal）");
            System.exit(1);
        }

        if (outputFile == null) {
            // 若没指定输出文件，自动替换 .tal 为 .rom
            outputFile = inputFile.replaceAll("\\.tal$", ".rom");
        }

        // ...参数解析部分不变...

        try {
            // 1. 读取源文件
            String source = new String(Files.readAllBytes(Paths.get(inputFile)), "UTF-8");

            // 2. 词法+语法分析
            Parser parser = new Parser();
            List<Token> tokens = parser.parse(source);

            // 3. pretty print
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

            // 如果是 run 模式，直接用解释器执行
            if (runFlag) {
                Interpreter interpreter = new Interpreter(
                    encodeResult.memory, encodeResult.reverseLabelTable
                );
                interpreter.run();
                return;
            }

            // 汇编导出 ROM
            int startAddr = 0x0100;
            Assembler.memToRom(encodeResult.memory, startAddr, true, outputFile, verbose);

            if (wFlag || verbose) System.out.println("汇编完成，ROM 已写入: " + outputFile);

        } catch (Exception e) {
            System.err.println("汇编失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
            }
        }


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
            if (verbose) System.out.println("已导出ROM: " + romFile + " (" + outBytes.length + " bytes)");
        }
    }
}
