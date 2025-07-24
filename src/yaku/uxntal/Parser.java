// package yaku.uxntal;

// import java.util.*;
// import static yaku.uxntal.Definitions.*;

// public class Parser {
//     // 主入口，传入源码字符串，返回Token列表
//     public List<Token> parse(String source) {
//         List<Token> tokens = new ArrayList<>();
//         String[] lines = source.split("\\r?\\n");
//         int lineNum = 1;
//         for (String line : lines) {
//             String code = stripComment(line);
//             if (code.trim().isEmpty()) {
//                 lineNum++;
//                 continue;
//             }
//             // 拆分为标记
//             String[] marks = code.trim().split("\\s+");
//             for (String mark : marks) {
//                 if (mark.isEmpty()) continue;
//                 Token token = parseToken(mark, lineNum);
//                 if (token != null) {
//                     tokens.add(token);
//                 }
//             }
//             lineNum++;
//         }
//         return tokens;
//     }

//     // 去除一行中的注释 ( ... )
//     private String stripComment(String line) {
//         int start = line.indexOf('(');
//         int end = line.indexOf(')');
//         if (start != -1 && end > start) {
//             return line.substring(0, start) + line.substring(end + 1);
//         }
//         return line;
//     }

//     // 根据标记内容和行号生成Token
//     private Token parseToken(String mark, int lineNum) {
//         // 主程序入口
//         if (mark.equals("|0100") || mark.equals("|100")) {
//             return new Token(TokenType.MAIN, "", 0, lineNum);
//         }
//         // 地址跳转
//         if (mark.startsWith("|") && mark.length() > 1) {
//             int addr = parseHex(mark.substring(1));
//             return new Token(TokenType.ADDR, mark, addr, lineNum);
//         }
//         // 填充
//         if (mark.startsWith("$")) {
//             int count = parseHex(mark.substring(1));
//             return new Token(TokenType.PAD, mark, count, lineNum);
//         }
//         // 父标签
//         if (mark.startsWith("@")) {
//             return new Token(TokenType.LABEL, mark.substring(1), 2, lineNum);
//         }
//         // 子标签
//         if (mark.startsWith("&")) {
//             return new Token(TokenType.LABEL, mark.substring(1), 1, lineNum);
//         }
//         // 常量（#10，#1000）
//         if (mark.startsWith("#")) {
//             String hexStr = mark.substring(1);
//             int value = parseHex(hexStr);
//             int sz = (hexStr.length() > 2) ? 2 : 1;
//             return new Token(TokenType.LIT, hexStr, sz, lineNum);
//         }
//         // 标签引用：;,.,等
//         if (mark.length() > 1 && Definitions.isRefPrefix(mark.substring(0, 1))) {
//             String prefix = mark.substring(0, 1);
//             String refName = mark.substring(1);
//             int refType = Definitions.REF_TYPE_MAP.get(prefix).ordinal();
//             return new Token(TokenType.REF, refName, refType, lineNum);
//         }
//         // 指令
//         if (Definitions.isOpcode(mark.toUpperCase())) {
//             return new Token(TokenType.INSTR, mark.toUpperCase(), 1, lineNum);
//         }
//         // 其它（未知/暂不支持）
//         return new Token(TokenType.UNKNOWN, mark, 0, lineNum);
//     }

//     // 简易十六进制解析（兼容十进制写法）
//     private int parseHex(String s) {
//         if (s == null || s.isEmpty()) return 0;
//         try {
//             if (s.matches("[0-9A-Fa-f]+")) {
//                 return Integer.parseInt(s, 16);
//             }
//             return Integer.parseInt(s); // fallback to decimal
//         } catch (Exception e) {
//             return 0;
//         }
//     }

//     // 你可以在这里加 parseStringLiteral、parseInclude、parseBlock 等高级功能
// }





package yaku.uxntal;

import java.util.*;

import yaku.uxntal.Definitions.TokenType;

import java.io.*;
import static yaku.uxntal.Definitions.*;

public class Parser {
    //  include File recursive processing
    private Set<String> includedFiles = new HashSet<>();

    /**
     * Main entry: pass in source code and filename
     */
    public List<Token> parse(String source) throws Exception {
        return parse(source, null, 1, null);
    }
    public List<Token> parse(String source, String fileName, int baseLineNum, Map<String, String> fileMap) throws Exception {
        List<Token> tokens = new ArrayList<>();
        String programText = source.replace("\r\n", "\n").replace("\r", "\n");

        // stripCommentsFSM
        String stripped = stripCommentsFSM(programText);

        if (stripped.trim().isEmpty()) {
            throw new RuntimeException("Error: All code is commented out or missing! Maybe unbalanced parens in comments.");
        }

        String[] lines = stripped.split("\n");
        int lineNum = baseLineNum;
        for (String line : lines) {
            String[] marks = line.trim().split("\\s+");
            for (String mark : marks) {
                if (mark.isEmpty()) continue;

                List<Token> tokenList = parseToken(mark, lineNum, fileName, fileMap);
                for (Token t : tokenList) {
                    tokens.add(t);
                }
            }
            lineNum++;
        }
        return flattenTokens(tokens);
    }

    /**
     * FSM approach to handling nested and in-string comments
     */
    private String stripCommentsFSM(String src) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        int parenDepth = 0;
        for (int i = 0; i < src.length(); ++i) {
            char c = src.charAt(i);
            // deal（"）
            if (c == '"') inString = !inString;
            // begin
            if (!inString && c == '(') {
                parenDepth++;
                // jump
                while (parenDepth > 0 && ++i < src.length()) {
                    char cc = src.charAt(i);
                    if (cc == '"' ) inString = !inString;
                    if (!inString && cc == '(') parenDepth++;
                    if (!inString && cc == ')') parenDepth--;
                }
                if (parenDepth < 0) throw new RuntimeException("Unmatched paren in comment!");
                continue;
            }
            if (parenDepth == 0) result.append(c);
        }
        if (parenDepth != 0) throw new RuntimeException("Unmatched paren in comment!");
        return result.toString();
    }

    /**
     * Parses a single token and returns one or more tokens.
     */
    private List<Token> parseToken(String mark, int lineNum, String fileName, Map<String, String> fileMap) throws Exception {
        List<Token> tokens = new ArrayList<>();

        // include  ~filename
        if (mark.startsWith("~")) {
            String includeFile = mark.substring(1);
            if (includedFiles.contains(includeFile)) {
                return tokens;
            }
            includedFiles.add(includeFile);

            String includeContent;
            if (fileMap != null && fileMap.containsKey(includeFile)) {
                includeContent = fileMap.get(includeFile);
            } else {
                // read file
                includeContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(includeFile)));
            }
            // recursive  parse
            List<Token> incTokens = parse(includeContent, includeFile, 1, fileMap);
            tokens.addAll(incTokens);
            return tokens;
        }

        // macro 
        if (mark.startsWith("%")) {
            throw new RuntimeException("Error: Macros not yet supported. at line: " + lineNum);
        }

        // block
        if (mark.equals("[") || mark.equals("]")) {
        
            return tokens;
        }

        // string constant
        if (mark.startsWith("\"")) {
            String raw = mark.substring(1);
            for (int i = 0; i < raw.length(); ++i) {
                tokens.add(new Token(TokenType.RAW, String.valueOf((int)raw.charAt(i)), 1, lineNum));
            }
            return tokens;
        }

        // LIT (#10 #1234)
        if (mark.startsWith("#")) {
            String hexStr = mark.substring(1);
            if (!hexStr.matches("[0-9a-fA-F]{2}|[0-9a-fA-F]{4}")) {
                throw new RuntimeException("Error: Constant #" + hexStr + " must be two or four hex digits at line " + lineNum);
            }
            int value = Integer.parseInt(hexStr, 16);
            int size = (hexStr.length() == 2) ? 1 : 2;
            tokens.add(new Token(TokenType.LIT, hexStr, size, lineNum));
            return tokens;
        }

        // ref;,._-=xxxx
        if (mark.matches("^[;,.\\._\\-=].+")) {
            String prefix = mark.substring(0,1);
            String refName = mark.substring(1);
            int refType = Definitions.REF_TYPE_MAP.getOrDefault(prefix, TokenType.REF.ordinal());
            int isChild = 0;
            if (refName.contains("&")) {
                isChild = 1;
                refName = refName.replace("&", "");
            } else if (refName.contains("/")) {
                isChild = 2;
            }
            tokens.add(new Token(TokenType.REF, refName, refType, isChild, lineNum));
            return tokens;
        }

        // parent label
        if (mark.startsWith("@")) {
            tokens.add(new Token(TokenType.LABEL, mark.substring(1), 2, lineNum));
            return tokens;
        }

        // sub label
        if (mark.startsWith("&")) {
            tokens.add(new Token(TokenType.LABEL, mark.substring(1), 1, lineNum));
            return tokens;
        }

        // Main Program Entry
        if (mark.equals("|0100") || mark.equals("|100")) {
            tokens.add(new Token(TokenType.MAIN, "", 0, lineNum));
            return tokens;
        }

        // address jumping
        if (mark.startsWith("|")) {
            String val = mark.substring(1);
            if (val.isEmpty()) throw new RuntimeException("Error: Invalid address token: " + mark + " at line: " + lineNum);
            tokens.add(new Token(TokenType.ADDR, val, Integer.parseInt(val, 16), lineNum));
            return tokens;
        }

        // padding
        if (mark.startsWith("$")) {
            String val = mark.substring(1);
            if (val.isEmpty()) throw new RuntimeException("Error: Invalid pad token: " + mark + " at line: " + lineNum);
            tokens.add(new Token(TokenType.PAD, val, Integer.parseInt(val, 16), lineNum));
            return tokens;
        }

        // Lambda/JCI
        if (mark.startsWith("?{")) {
            String lambdaName = "LAMBDA_" + lineNum;
            tokens.add(new Token(TokenType.INSTR, "JCI", 2, 0, 0, lineNum));
            tokens.add(new Token(TokenType.REF, lambdaName, 6, 1, lineNum));
            return tokens;
        }
        if (mark.startsWith("?")) {
            String val = mark.substring(1);
            int isChild = 0;
            if (val.contains("&") || val.contains("/")) {
                isChild = 1;
                val = val.replace("&", "");
            }
            tokens.add(new Token(TokenType.INSTR, "JCI", 2, 0, 0, lineNum));
            tokens.add(new Token(TokenType.REF, val, 6, isChild, lineNum));
            return tokens;
        }
        if (mark.startsWith("!")) {
            String val = mark.substring(1);
            int isChild = 0;
            if (val.contains("&") || val.contains("/")) {
                isChild = 1;
                val = val.replace("&", "");
            }
            tokens.add(new Token(TokenType.INSTR, "JMI", 2, 0, 0, lineNum));
            tokens.add(new Token(TokenType.REF, val, 6, isChild, lineNum));
            return tokens;
        }
        if (mark.startsWith("{")) {
            String lambdaName = "LAMBDA_" + lineNum;
            tokens.add(new Token(TokenType.INSTR, "JSI", 2, 0, 0, lineNum));
            tokens.add(new Token(TokenType.REF, lambdaName, 6, 1, lineNum));
            return tokens;
        }
        if (mark.startsWith("}")) {
            String lambdaName = "LAMBDA_" + lineNum;
            tokens.add(new Token(TokenType.LABEL, lambdaName, 1, lineNum));
            return tokens;
        }

        // Instruction/subroutine call/immediate subroutine
        if (mark.matches("^[A-Z]{3}[2kr]*$")) {
            // Parsing by length and suffix
            String instr = mark.substring(0, 3);
            int sz = 1, r = 0, k = 0;
            if (mark.length() == 4) {
                char mode = mark.charAt(3);
                if (mode == '2') sz = 2;
                else if (mode == 'r') r = 1;
                else if (mode == 'k') k = 1;
            } else if (mark.length() == 5) {
                String mode = mark.substring(3, 5);
                if (mode.equals("2r") || mode.equals("r2")) { sz = 2; r = 1; }
                else if (mode.equals("2k") || mode.equals("k2")) { sz = 2; k = 1; }
                else if (mode.equals("rk") || mode.equals("kr")) { r = 1; k = 1; }
            } else if (mark.length() == 6) {
                sz = 2; r = 1; k = 1;
            }
            if (Definitions.OPCODE_SET.contains(instr)) {
                tokens.add(new Token(TokenType.INSTR, instr, sz, r, k, lineNum));
                return tokens;
            } else {
                // Non-instruction three-letter, direct subroutine call JSI
                tokens.add(new Token(TokenType.INSTR, "JSI", 2, 0, 0, lineNum));
                tokens.add(new Token(TokenType.REF, mark, 6, 0, lineNum));
                return tokens;
            }
        }

        // regoin hex
        if (mark.matches("^[a-f0-9]{2,4}$")) {
            int wordSz = (mark.length() == 2) ? 1 : 2;
            tokens.add(new Token(TokenType.RAW, mark, wordSz, lineNum));
            return tokens;
        }

        // Immediate subroutine calls or normal symbols
        if (mark.matches("^[<*+^\\w].+")) {
            tokens.add(new Token(TokenType.INSTR, "JSI", 2, 0, 0, lineNum));
            tokens.add(new Token(TokenType.REF, mark, 6, 0, lineNum));
            return tokens;
        }

        // other
        tokens.add(new Token(TokenType.UNKNOWN, mark, 0, lineNum));
        return tokens;
    }

    /**
     *  token flatten
     */
    private List<Token> flattenTokens(List<Token> input) {
        List<Token> out = new ArrayList<>();
        for (Token t : input) {
            if (t == null) continue;
            if (t.isGroup()) out.addAll(Arrays.asList(t.getGroup()));
            else out.add(t);
        }
        return out;
    }
}

