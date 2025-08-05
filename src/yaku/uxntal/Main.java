package yaku.uxntal;
import yaku.uxntal.units.UxnState; 
import java.nio.file.*;
import java.io.*;
import java.util.*;

public class Main {





    public static void main(String[] args) {
        try {

////////////////////////////////////////////
System.out.println(Definitions.OPCODE_MAP) ;
System.out.println("当前OPCODE_MAP：" + yaku.uxntal.Definitions.OPCODE_MAP);



            // 1. 解析命令行参数
            Map<String, Object> options = parseArgs(args);
            if (Boolean.TRUE.equals(options.get("help"))) {
                showHelp();
                return;
            }

            boolean useStdin = Boolean.TRUE.equals(options.get("stdin"));
            String inputFile = useStdin ? null : (String) options.getOrDefault("inputFile", null);

            if (!useStdin && inputFile == null) {
                System.err.println("Error: Please provide the path to the .tal file");
                System.exit(1);
            }

            String romFile = "from_stdin.rom";
            if (inputFile != null) {
                romFile = inputFile.replaceAll("\\.tal$", ".rom");
                if (!Files.exists(Paths.get(inputFile))) {
                    System.err.println("Error: File not found: " + inputFile);
                    System.exit(1);
                }
            }

            // 2. 设置 flags
            Flags.setFlagsFromOptions(options);

            // 3. 初始化 UxnState
            int hasMain = Boolean.TRUE.equals(options.get("assume-main")) ? 2 : 0;
            UxnState uxn = new UxnState(hasMain);

            // 4. 解析 .tal 源码为 tokens
            List<Token> tokens;
            List<int[]> lineIdxs;
            if (useStdin) {
                String input = new BufferedReader(new InputStreamReader(System.in)).lines()
                        .reduce("", (a, b) -> a + "\n" + b);
                Parser.ParseResult parseRes = Parser.parseText(input, "from_stdin.tal", uxn);
                tokens = parseRes.tokens;
                lineIdxs = parseRes.lineIdxs;   
                uxn = parseRes.uxn;
            } else {
                Parser.ParseResult parseRes = Parser.parseProgram(inputFile, uxn);
                tokens = parseRes.tokens;
                lineIdxs = parseRes.lineIdxs;
                uxn = parseRes.uxn;
            }

            // 5. 补全 token 下标
            for (int i = 0; i < tokens.size(); i++) {
                tokens.get(i).line = i;
            }

            // 6. 检查 MAIN token
            if (uxn.hasMain == 2) {
                Token mainTok = new Token(Definitions.TokenType.MAIN, "main", 0);
                tokens.add(0, mainTok);
            }


         

        //    List<Token> tokens;
        //    List<int[]> lineIdxs;
           if (useStdin) {
               String input = new BufferedReader(new InputStreamReader(System.in)).lines()
                       .reduce("", (a, b) -> a + "\n" + b);
               Parser.ParseResult parseRes = Parser.parseText(input, "from_stdin.tal", uxn);
               tokens = parseRes.tokens;
               lineIdxs = parseRes.lineIdxs;   
               uxn = parseRes.uxn;
           } else {
               Parser.ParseResult parseRes = Parser.parseProgram(inputFile, uxn);
               tokens = parseRes.tokens;
               lineIdxs = parseRes.lineIdxs;
               uxn = parseRes.uxn;
           }

/////////////////////////////////////////////////////////////////////////////////
           System.out.println("==== TOKENS DUMP ====");
           for (Token t : tokens) {
               System.out.println(t);
           }
           System.out.println("=====================");

           // ...原流程继续...


            // 7. 可选：错误检查
            // if (!Flags.shouldShowFewerWarnings()) {
            //     ErrorChecker.checkErrors(tokens, uxn);
            // }

            // 8. -p 只打印 token 并退出
            if (Boolean.TRUE.equals(options.get("print-and-quit"))) {
                PrettyPrint.prettyPrintTokens(tokens, true, 1);
                return;
            }
            
            // 9. 编码为内存（得到 byte[]）
            Encoder.EncodeResult encodeResult = Encoder.encode(tokens);
            byte[] byteMemory = encodeResult.memory;

            // 10. -r 运行解释器
            if (Boolean.TRUE.equals(options.get("run"))) {
                Interpreter vm = new Interpreter(byteMemory);
                vm.run();
                if (Boolean.TRUE.equals(options.get("show-stacks"))) {
                    vm.showStacks();
                }
            }

            // 11. -a 汇编 .rom
            if (Boolean.TRUE.equals(options.get("assemble"))) {
                RomWriter.memToRom(byteMemory, !Boolean.TRUE.equals(options.get("no-rom")), romFile);
                System.out.println("ROM written: " + romFile);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 命令行参数解析，仿 JS 版 parseArgs 逻辑
     */
    public static Map<String, Object> parseArgs(String[] args) {
        Map<String, Object> opts = new HashMap<>();
        List<String> positionals = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h":
                case "--help":
                    opts.put("help", true); break;
                case "-r":
                case "--run":
                    opts.put("run", true); break;
                case "-a":
                case "--assemble":
                    opts.put("assemble", true); break;
                case "-D":
                case "--no-rom":
                    opts.put("no-rom", true); break;
                case "-s":
                case "--show-stacks":
                    opts.put("show-stacks", true); break;
                case "-p":
                case "--print-and-quit":
                    opts.put("print-and-quit", true); break;
                case "-W":
                case "--fewer-warnings":
                    opts.put("fewer-warnings", true); break;
                case "-S":
                case "--no-stack-warnings":
                    opts.put("no-stack-warnings", true); break;
                case "-i":
                case "--stdin":
                    opts.put("stdin", true); break;
                case "-m":
                case "--assume-main":
                    opts.put("assume-main", true); break;
                case "-e":
                case "--errors-from-warnings":
                    opts.put("errors-from-warnings", true); break;
                case "-f":
                case "--fatal":
                    opts.put("fatal", true); break;
                case "-v":
                case "--verbose":
                    if (i + 1 < args.length) opts.put("verbose", args[++i]); break;
                case "-d":
                case "--debug":
                    opts.put("debug", true); break;
                default:
                    if (arg.endsWith(".tal")) {
                        opts.put("inputFile", arg);
                    } else {
                        positionals.add(arg);
                    }
            }
        }
        opts.put("positionals", positionals);
        return opts;
    }

    /**
     * 帮助信息
     */
    public static void showHelp() {
        System.out.println(
                "Yaku-Java - Uxntal assembler and interpreter\n" +
                        "\nUsage: java -jar yaku.jar [options] <.tal file>\n" +
                        "\nOptions:\n" +
                        "  -h, --help                    Show this help message\n" +
                        "  -r, --run                     Run the program\n" +
                        "  -a, --assemble                Assemble the program into a .rom file\n" +
                        "  -D, --no-rom                  Don't write .rom file (for test/debug)\n" +
                        "  -s, --show-stacks             Show the stacks at the end of the run\n" +
                        "  -p, --print-and-quit          Print generated code and exit\n" +
                        "  -W, --fewer-warnings          Fewer warning and error messages\n" +
                        "  -S, --no-stack-warnings       No warnings for byte/short mismatch on stack manipulation instructions\n" +
                        "  -i, --stdin                   Take input from stdin instead of a file\n" +
                        "  -m, --assume-main             Assume a 'main' |0100 at the start of the program\n" +
                        "  -e, --errors-from-warnings    Turn all warnings into errors\n" +
                        "  -f, --fatal                   Fatal mode - die on the first error\n" +
                        "  -v, --verbose <level>         Verbosity level (0-3)\n" +
                        "  -d, --debug                   Enable debug mode\n" +
                        "\nExamples:\n" +
                        "  java -jar yaku.jar -r hello.tal             Run hello.tal\n" +
                        "  java -jar yaku.jar -a hello.tal             Assemble hello.tal to hello.rom\n" +
                        "  java -jar yaku.jar -p hello.tal             Print generated code for hello.tal\n" +
                        "  echo \"#42 #18 DEO BRK\" | java -jar yaku.jar -i -r    Run code from stdin\n"
        );
    }
}