package yaku.uxntal;

import java.nio.file.*;
import java.nio.charset.*;
import java.io.IOException;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("当前工作目录: " + System.getProperty("user.dir"));
        try {
            String sourceCode = Files.readString(Paths.get("test.tal"), StandardCharsets.UTF_8);
            System.out.println("文件读取成功！");

            // 解析 Token
            Parser parser = new Parser();
            List<Token> tokens = parser.parse(sourceCode);

            System.out.println("=== 解析结果（Token 列表） ===");
            for (Token t : tokens) {
                System.out.println(t);
            }

            // 编码虚拟机内存并打印标签
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

            // Interpreter 部分
            System.out.println("\n=== Interpreter 执行过程 ===");
            Interpreter interpreter = new Interpreter(result.memory, Definitions.MAIN_ADDRESS);
            interpreter.run();

        } catch (IOException e) {
            System.err.println("读取文件失败: " + e.getMessage());
        }
    }
}
