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


// package yaku.uxntal;

// import java.nio.file.Files;
// import java.nio.file.Paths;
// import java.util.List;

// public class Main {
//     public static void main(String[] args) throws Exception {
//         // 1. 读取 test.tal 文件
//         String fileName = "test.tal"; // 可放在项目根目录或指定绝对路径
//         String source = new String(Files.readAllBytes(Paths.get(fileName)), "UTF-8");

//         // 2. 词法+语法分析
//         Parser parser = new Parser();
//         List<Token> tokens = parser.parse(source);

//         // 3. 汇编编码
//         Encoder encoder = new Encoder();
//         Encoder.EncodeResult encodeResult = encoder.encode(tokens);

//         // 4. 运行虚拟机解释器
//         Interpreter interpreter = new Interpreter(encodeResult.memory, encodeResult.reverseLabelTable);
//         interpreter.run();
//     }
// }

// public class Main {
//     public static void main(String[] args) throws Exception {
//         // 1. 读取 test.tal 文件
//         String fileName = "test.tal";
//         String source = new String(Files.readAllBytes(Paths.get(fileName)), "UTF-8");

//         // 2. 解析成 Token
//         Parser parser = new Parser();
//         List<Token> tokens = parser.parse(source);

//         // 3. 语义/分配/寻址错误检查
//         ErrorChecker checker = new ErrorChecker();
//         try {
//             checker.check(tokens);
//             System.out.println("通过 ErrorChecker 检查，无严重错误。");
//         } catch (RuntimeException e) {
//             System.err.println("检测到错误，编译中止：" + e.getMessage());
//             return;
//         }

//         // 4. 编码与解释执行（如无错误才继续）
//         Encoder encoder = new Encoder();
//         Encoder.EncodeResult encodeResult = encoder.encode(tokens);

//         Interpreter interpreter = new Interpreter(encodeResult.memory, encodeResult.reverseLabelTable);
//         interpreter.run();
//     }
// }

// public class Main {
//     public static void main(String[] args) throws Exception {
//         // 1. 读取 test.tal 文件
//         String fileName = "test.tal";
//         String source = new String(Files.readAllBytes(Paths.get(fileName)), "UTF-8");

//         // 2. 词法/语法分析为 token 列表
//         Parser parser = new Parser();
//         List<Token> tokens = parser.parse(source);

//         // 3. 用 PrettyPrinter 美化并打印 token 序列
//         System.out.println("====== Pretty Print ======");
//         PrettyPrint.prettyPrint(tokens, false);

//         // 4. 还可以获取字符串，用于保存到文件
//         String prettySource = PrettyPrint.prettyPrintStr(tokens, false);
//         //Files.write(Paths.get("pretty_test.tal"), prettySource.getBytes("UTF-8"));
//     }
// }


// public class Main {
//     public static void main(String[] args) throws Exception {
//         // 1. 读取 test.tal 文件
//         String fileName = "test.tal";
//         String source = new String(Files.readAllBytes(Paths.get(fileName)), "UTF-8");

//         // 2. 解析为 token 列表
//         Parser parser = new Parser();
//         List<Token> tokens = parser.parse(source);

//         // 3. 编码为虚拟机内存
//         Encoder encoder = new Encoder();
//         Encoder.EncodeResult encodeResult = encoder.encode(tokens);

//         // 4. 用 Assembler 精确导出 ROM（只保留有效区间，不带多余0）
//         int startAddr = 0x0100; // uxntal 程序入口一般从0x0100开始
//         String romFile = "out.rom";
//         boolean writeRom = true;
//         boolean verbose = true; // 打印导出ROM的内容

//         Assembler.memToRom(encodeResult.memory, startAddr, writeRom, romFile, verbose);

//         System.out.println("Assembler 测试完成！");
//     }
// }
