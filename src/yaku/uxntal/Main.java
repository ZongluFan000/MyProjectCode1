package yaku.uxntal;

import java.nio.file.*;
import java.nio.charset.*;
import java.io.IOException;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("当前工作目录: " + System.getProperty("user.dir"));

        try {
            // 1. 读取 Uxntal 源代码文件
            String sourceCode = Files.readString(Paths.get("test.tal"), StandardCharsets.UTF_8);
            System.out.println("文件读取成功！");

            // 2. 解析 Token
            Parser parser = new Parser();
            List<Token> tokens = parser.parse(sourceCode);

            System.out.println("=== 解析结果（Token 列表） ===");
            for (Token t : tokens) {
                System.out.println(t);
            }

            // 3. 静态检查
            ErrorChecker checker = new ErrorChecker();
            List<ErrorChecker.Error> errors = checker.check(tokens);

            if (errors.isEmpty()) {
                System.out.println("静态检查通过，无错误！");
            } else {
                System.out.println("检测到如下错误：");
                for (ErrorChecker.Error err : errors) {
                    System.out.println(err);
                }
                // 有静态错误就不再往下执行
                return;
            }

            // 4. 编码虚拟机内存并打印标签
            Encoder encoder = new Encoder();
            Encoder.EncodeResult result = encoder.encode(tokens);

            System.out.println("标签符号表: " + result.labelTable);
            for (Map.Entry<String, Integer> entry : result.labelTable.entrySet()) {
                System.out.printf("标签 %s: 0x%04X\n", entry.getKey(), entry.getValue());
            }

            System.out.println("内存前 32 字节内容：");
            for (int i = 0x0100; i < 0x0100 + 32; i++) {
                System.out.printf("0x%04X: %02X\n", i, result.memory[i] & 0xFF);
            }

            // 5. 导出 ROM 文件
            try {
                RomWriter.write(result.memory, "out.rom");
                System.out.println("已导出 ROM 文件：out.rom");
            } catch (IOException e) {
                System.err.println("导出 ROM 文件失败: " + e.getMessage());
            }

            // 6. Interpreter 执行（可选）
            System.out.println("\n=== Interpreter 执行过程 ===");
            Interpreter interpreter = new Interpreter(result.memory, Definitions.MAIN_ADDRESS);
            interpreter.run();

        } catch (IOException e) {
            System.err.println("读取文件失败: " + e.getMessage());
        }
    }
}
