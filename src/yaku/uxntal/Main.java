// package yaku.uxntal;

// import java.nio.file.Files;
// import java.nio.file.Paths;
// import java.util.List;

// public class Main {
//     public static void main(String[] args) throws Exception {
//         // 1. 读取 test.tal 文件
//         String fileName = "test.tal"; // 确保和 Main.java 同一目录或传入完整路径
//         String source = new String(Files.readAllBytes(Paths.get(fileName)), "UTF-8");

//         // 2. 解析
//         Parser parser = new Parser();
//         List<Token> tokens = parser.parse(source);

//         // 3. 打印解析结果
//         for (Token t : tokens) {
//             System.out.println(t);
//         }
//     }
// }




// public class Main {
//     public static void main(String[] args) throws Exception {
//         // 1. 读取 test.tal 文件
//         String fileName = "test.tal"; // 请确保和 Main.java 同目录或指定完整路径
//         String source = new String(Files.readAllBytes(Paths.get(fileName)), "UTF-8");

//         // 2. 词法分析 + 语法解析
//         Parser parser = new Parser();
//         List<Token> tokens = parser.parse(source);

//         // 3. 编码到内存
//         Encoder encoder = new Encoder();
//         Encoder.EncodeResult result = encoder.encode(tokens);

//         // 4. 打印符号表
//         System.out.println("符号表：");
//         for (Map.Entry<String, Integer> entry : result.labelTable.entrySet()) {
//             System.out.printf("  %-20s : 0x%04X\n", entry.getKey(), entry.getValue());
//         }

//         // 5. 打印前 64 字节内存内容
//         System.out.println("\n内存前64字节：");
//         for (int i = 0; i < 64; i++) {
//             System.out.printf("%02X ", result.memory[i]);
//             if ((i+1) % 16 == 0) System.out.println();
//         }

//         // 6. 可选：将内存写为 ROM 文件
//         // Files.write(Paths.get("out.rom"), result.memory);
//         int start = 0x0100;
//         System.out.println("\n内存0x0100起64字节：");
//         for (int i = start; i < start + 64; i++) {
//             System.out.printf("%02X ", result.memory[i]);
//             if ((i-start+1) % 16 == 0) System.out.println();
//         }
//     }
// }


package yaku.uxntal;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        // 1. 读取 test.tal 文件
        String fileName = "test.tal"; // 可放在项目根目录或指定绝对路径
        String source = new String(Files.readAllBytes(Paths.get(fileName)), "UTF-8");

        // 2. 词法+语法分析
        Parser parser = new Parser();
        List<Token> tokens = parser.parse(source);

        // 3. 汇编编码
        Encoder encoder = new Encoder();
        Encoder.EncodeResult encodeResult = encoder.encode(tokens);

        // 4. 运行虚拟机解释器
        Interpreter interpreter = new Interpreter(encodeResult.memory, encodeResult.reverseLabelTable);
        interpreter.run();
    }
}

