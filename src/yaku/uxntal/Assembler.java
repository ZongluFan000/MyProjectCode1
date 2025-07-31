package yaku.uxntal;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;


public class Assembler {
    public static void main(String[] args) {
        // 兼容 Perl yaku 参数
        boolean wFlag = false;
        boolean runFlag = false;
        boolean assembleFlag = false; 
        boolean doPretty = false, checkOnly = false, verbose = false;
        String inputFile = null;
        String outputFile = null;

        // 参数解析
        for (String arg : args) {
            switch (arg) {
                case "-W":
                    wFlag = true;
                    verbose = true; // -W 视为 verbose
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
                    if (arg.endsWith(".tal")) {
                        inputFile = arg;
                    } else if (arg.endsWith(".rom")) {
                        outputFile = arg;
                    }
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

        try {
            //  读取源文件
            String source = new String(
                Files.readAllBytes(Paths.get(inputFile)),
                "UTF-8"
            );

            //  词法＋语法分析
            Parser parser = new Parser();
            List<Token> tokens = parser.parse(source);

            //  pretty print
            if (doPretty) {
                System.out.println("====== Pretty Print ======");
                PrettyPrint.prettyPrint(tokens, false);
            }

            //  语义检查
            ErrorChecker checker = new ErrorChecker();
            checker.check(tokens);
            if (checkOnly) {
                System.out.println("语义检查通过，无严重错误！");
                return;
            }

            //  编码生成虚拟机内存
            Encoder encoder = new Encoder();
            Encoder.EncodeResult encodeResult = encoder.encode(tokens);

            // 如果是 run 模式，直接用解释器执行
            if (runFlag) {
               
                Interpreter interpreter = new Interpreter(encodeResult.memory);
                interpreter.run();
                return;
            }

            // 默认汇编导出 ROM
            int startAddr = 0x0100;
            memToRom(
                encodeResult.memory,
                startAddr,
                /* writeRom= */ true,
                outputFile,
                verbose
            );
            if (wFlag || verbose) {
                System.out.println("汇编完成，ROM 已写入: " + outputFile);
            }

        } catch (Exception e) {
            System.err.println("汇编失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }


    public static void memToRom(
        byte[] memory,
        int startAddr,
        boolean writeRom,
        String romFile,
        boolean verbose
    ) throws Exception {
        // 剔除前面的空白和末尾的 0
        int len = memory.length - startAddr;
        byte[] slice = new byte[len];
        System.arraycopy(memory, startAddr, slice, 0, len);
        int lastNonZero = len - 1;
        while (lastNonZero >= 0 && slice[lastNonZero] == 0) {
            lastNonZero--;
        }
        if (lastNonZero < 0) lastNonZero = 0;
        byte[] out = new byte[lastNonZero + 1];
        System.arraycopy(slice, 0, out, 0, lastNonZero + 1);

        if (verbose) {
            StringBuilder sb = new StringBuilder();
            for (byte b : out) {
                sb.append(String.format("%02x ", b & 0xFF));
            }
            System.out.println("[ROM 内容] " + sb);
        }
        if (writeRom) {
            Files.write(Paths.get(romFile), out);
            if (verbose) {
                System.out.println("已导出 ROM: " + romFile + " (" + out.length + " bytes)");
            }
        }
    }
}
