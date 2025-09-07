package yaku.uxntal;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.io.IOException;

public class Parser {

    //Public API
    public static class ParseResult {
        public final List<Token> tokens;
        public final List<int[]> lineIdxs; // [lineNum, fileHash]
        public final boolean hasMain;
        public ParseResult(List<Token> tokens, List<int[]> lineIdxs, boolean hasMain) {
            this.tokens = tokens;
            this.lineIdxs = lineIdxs;
            this.hasMain = hasMain;
        }
    }

    //读取文件并解析（不再需要 UxnState） 
    public static ParseResult parseProgram(String programFile) throws IOException {
        String programText = new String(Files.readAllBytes(Paths.get(programFile)));

        // 1) 词法（行号就地记录）
        List<LexerToken> rawTokens = new ArrayList<>();
        List<int[]> lineMapping = new ArrayList<>();
        tokenize(programText, programFile, rawTokens, lineMapping);

        // 2) 递归处理 include
        IncludeResult ir = processIncludes(rawTokens, lineMapping, baseDirOf(programFile), new HashSet<>());

        // 3) 语义展开：STR、? / ! / { / }、普通标识符立即调用、未知助记符降级等
        ParserState st = new ParserState();
        ParseTokensResult pr = processTokens(ir.tokens, ir.lineMapping, st);

        return new ParseResult(pr.tokens, pr.lineIdxs, st.hasMain);
    }

    //解析内存文本（不再需要 UxnState）
    public static ParseResult parseText(String sourceText, String filename) {
        List<LexerToken> rawTokens = new ArrayList<>();
        List<int[]> lineMapping = new ArrayList<>();
        tokenize(sourceText, filename, rawTokens, lineMapping);

        IncludeResult ir = processIncludes(rawTokens, lineMapping, baseDirOf(filename), new HashSet<>());

        ParserState st = new ParserState();
        ParseTokensResult pr = processTokens(ir.tokens, ir.lineMapping, st);

        return new ParseResult(pr.tokens, pr.lineIdxs, st.hasMain);
    }

    //nternal state/types

    private static class ParserState {
        int lambdaCount = 0;
        Deque<Integer> lambdaStack = new ArrayDeque<>();
        boolean hasMain = false;
    }

    private static class IncludeResult {
        final List<LexerToken> tokens;
        final List<int[]> lineMapping;
        IncludeResult(List<LexerToken> tokens, List<int[]> lineMapping) {
            this.tokens = tokens; this.lineMapping = lineMapping;
        }
    }

    private static class ParseTokensResult {
        final List<Token> tokens;
        final List<int[]> lineIdxs;
        ParseTokensResult(List<Token> tokens, List<int[]> lineIdxs) {
            this.tokens = tokens; this.lineIdxs = lineIdxs;
        }
    }

    //仅用于词法阶段的内部 token
    private static class LexerToken {
        public Definitions.TokenType type;   // 正常类型；某些“语法糖”用 special 字段标注
        public String value;                 // 助记符/标识符/字面量/文件名等
        public int size, stack, keep;        // INSTR / LIT / RAW / PAD 等
        public int refType, isChild;         // REF 的类型与子标志（0/1/2）
        public String special;               // "QCALL","MCALL","Q_LAMBDA_START","LAMBDA_START","LAMBDA_END","IDENT"
        public int lineNum;
        public String filename;

        LexerToken(Definitions.TokenType t, String v, int lineNum, String fn) {
            this.type = t; this.value = v; this.lineNum = lineNum; this.filename = fn;
        }
        static LexerToken ofLit(String hex, int sz, int line, String fn) {
            LexerToken lt = new LexerToken(Definitions.TokenType.LIT, hex, line, fn);
            lt.size = sz; return lt;
        }
        static LexerToken ofRaw(String hex, int sz, int line, String fn) {
            LexerToken lt = new LexerToken(Definitions.TokenType.RAW, hex, line, fn);
            lt.size = sz; return lt;
        }
        static LexerToken ofInstr(String mnem, int sz, int r, int k, int line, String fn) {
            LexerToken lt = new LexerToken(Definitions.TokenType.INSTR, mnem, line, fn);
            lt.size = sz; lt.stack = r; lt.keep = k; return lt;
        }
        static LexerToken ofRef(String name, int refType, int isChild, int line, String fn) {
            LexerToken lt = new LexerToken(Definitions.TokenType.REF, name, line, fn);
            lt.refType = refType; lt.isChild = isChild; return lt;
        }
        static LexerToken ofPad(String hex, int size, int line, String fn) {
            LexerToken lt = new LexerToken(Definitions.TokenType.PAD, hex, line, fn);
            lt.size = size; return lt;
        }
        static LexerToken ofAddr(String hex, int line, String fn) {
            return new LexerToken(Definitions.TokenType.ADDR, hex, line, fn);
        }
        static LexerToken ofLabel(String name, int kind, int line, String fn) { // kind: 2=parent,1=child
            LexerToken lt = new LexerToken(Definitions.TokenType.LABEL, name, line, fn);
            lt.size = kind; return lt;
        }
        static LexerToken ofMain(int line, String fn) {
            return new LexerToken(Definitions.TokenType.MAIN, "", line, fn);
        }
        static LexerToken ofInclude(String path, int line, String fn) {
            return new LexerToken(Definitions.TokenType.INCLUDE, path, line, fn);
        }
        static LexerToken ofStr(String s, int line, String fn) {
            return new LexerToken(Definitions.TokenType.STR, s, line, fn);
        }
        static LexerToken ofSpecial(String what, String payload, int line, String fn) {
            LexerToken lt = new LexerToken(Definitions.TokenType.PLACEHOLDER, payload, line, fn);
            lt.special = what; return lt;
        }
    }

    //Tokenization（含注释/字符串 FSM）

    private static void tokenize(String sourceText, String filename,
                                 List<LexerToken> out, List<int[]> lineMapping) {
        String[] lines = normalizeNewlines(sourceText).split("\n", -1);

        int commentDepth = 0; // 跨行
        IntBox depthBox = new IntBox(commentDepth);

        for (int lineNum = 1; lineNum <= lines.length; lineNum++) {
            String line = lines[lineNum - 1].replace('\t', ' ');
            // String noComment = stripCommentsFSMOnLine(line, depthBox);

            // String[] marks = noComment.trim().isEmpty() ? new String[0] : noComment.trim().split("\\s+");
            // for (String mark : marks) {
            //     if (mark.isEmpty()) continue;
            //     LexerToken lt = parseMark(mark, lineNum, filename);
            //     if (lt == null) continue; // [, ] → no-op
            //     out.add(lt);
            //     lineMapping.add(new int[]{lineNum, filename.hashCode()});
            // }

            String noComment = stripCommentsFSMOnLine(line, depthBox);
            String s = noComment.trim();
            int n = s.length(), i = 0;
            while (i < n) {
                while (i < n && s.charAt(i) == ' ') i++;
                int j = i;
                while (j < n && s.charAt(j) != ' ') j++;
                if (j > i) {
                    String mark = s.substring(i, j);
                    LexerToken lt = parseMark(mark, lineNum, filename);
                    if (lt != null) {
                        out.add(lt);
                        lineMapping.add(new int[]{lineNum, filename.hashCode()});
                    }
                }
                i = j + 1;
            }


        }

        if (depthBox.value != 0) {
            throw new RuntimeException("Error: Unbalanced comment parentheses in file: " + filename);
        }
    }

    private static class IntBox { int value; IntBox(int v){ value=v; } }
/////////////////////////////////////////////////////////////////////////
// ASCII 十六进制
private static boolean isHex(String s) {
    if (s == null || s.isEmpty()) return false;
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        boolean ok = (c >= '0' && c <= '9') ||
                     (c >= 'a' && c <= 'f') ||
                     (c >= 'A' && c <= 'F');
        if (!ok) return false;
    }
    return true;
}
private static boolean isAZ3(String s) {
    return s != null && s.length() >= 3 &&
           (s.charAt(0) >= 'A' && s.charAt(0) <= 'Z') &&
           (s.charAt(1) >= 'A' && s.charAt(1) <= 'Z') &&
           (s.charAt(2) >= 'A' && s.charAt(2) <= 'Z');
}
private static boolean onlyFlags_2rk(String flags) {
    for (int i = 0; i < flags.length(); i++) {
        char c = flags.charAt(i);
        if (!(c == '2' || c == 'r' || c == 'k')) return false;
    }
    return true;
}


/// ////////////////////////////////////////////////////////////////////
   
    private static String stripCommentsFSMOnLine(String line, IntBox commentDepth) {
        StringBuilder out = new StringBuilder();
        String s = " " + line + " "; // 便于判断“空格包裹的括号”
        boolean inStr = false;
        char prev = 0;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            char next = (i + 1 < s.length()) ? s.charAt(i + 1) : 0;

            if (!inStr && ch == '"' && next != ' ') {
                inStr = true;
            } else if (inStr && next == ' ') {
                inStr = false;
            }

            boolean parenInStr = inStr && (ch == '(' || ch == ')');

            if (!inStr && ch == '(' && isSpace(prev) && isSpace(next)) {
                commentDepth.value++;
                prev = ch;
                continue;
            }
            if (!inStr && ch == ')' && isSpace(prev) && isSpace(next)) {
                commentDepth.value--;
                if (commentDepth.value < 0) {
                    throw new RuntimeException("Error: Comment invalid near line segment: " + line);
                }
                prev = ch;
                continue;
            }

            if (commentDepth.value == 0 && (ch != '(' || parenInStr)) {
                out.append(ch);
            }

            prev = ch;
        }

        return out.toString();
    }

    private static boolean isSpace(char c){
        return c == ' ' || c == '\t' || c == 0;
    }

    private static String normalizeNewlines(String s){
        return s.replace("\r\n","\n").replace("\r","\n");
    }

    

    private static LexerToken parseMark(String mark, int lineNum, String filename) {
        if (mark == null || mark.isEmpty()) {
            throw new RuntimeException("Error: Invalid token <> at line " + lineNum);
        }
    
        // include: ~path
        if (mark.charAt(0) == '~') {
            return LexerToken.ofInclude(mark.substring(1), lineNum, filename);
        }
    
        // 宏（与 Perl 一致，直接报错）
        if (mark.charAt(0) == '%') {
            throw new RuntimeException("Error: Macros not yet supported at line " + lineNum);
        }
    
        // 匿名块界定符：no-op
        if (mark.length() == 1 && (mark.charAt(0) == '[' || mark.charAt(0) == ']')) {
            return null;
        }
    
        // LIT：#xx / #xxxx
        if (mark.charAt(0) == '#') {
            String hex = mark.substring(1);
            int n = hex.length();
            if ((n == 2 || n == 4) && isHex(hex)) {
                int sz = (n == 4) ? 2 : 1;
                return LexerToken.ofLit(hex.toLowerCase(Locale.ROOT), sz, lineNum, filename);
            }
            throw new RuntimeException("Error: Invalid literal <" + mark + "> at line " + lineNum);
        }
    
        // 字符串：以空白结束；这里只收集，后面展开成 RAW
        if (mark.charAt(0) == '"' && mark.length() > 1) {
            return LexerToken.ofStr(mark.substring(1), lineNum, filename);
        }
    
        // REF：开头一个字符在 { ; , . _ - = } 之中，并且长度>1
        char c0 = mark.charAt(0);
        if ((c0 == ';' || c0 == ',' || c0 == '.' || c0 == '_' || c0 == '-' || c0 == '=') && mark.length() > 1) {
            String rune = mark.substring(0, 1);
            String val  = mark.substring(1);
            int isChild = 0;
            // child/parent 修饰
            if (val.indexOf('&') >= 0) { isChild = 1; val = val.replace("&", ""); }
            else if (val.indexOf('/') >= 0) { isChild = 2; /* 保留 '/' */ }
    
            Integer rt = Definitions.REF_TYPE_MAP.get(rune);
            if (rt == null) {
                throw new RuntimeException("Error: Unknown reference rune '" + rune + "' at line " + lineNum);
            }
            return LexerToken.ofRef(val, rt, isChild, lineNum, filename);
        }
    
        // 标签
        if (c0 == '@' && mark.length() > 1) {
            return LexerToken.ofLabel(mark.substring(1), 2, lineNum, filename); // parent
        }
        if (c0 == '&' && mark.length() > 1) {
            return LexerToken.ofLabel(mark.substring(1), 1, lineNum, filename); // child
        }
    
        // MAIN
        if (mark.equals("|0100") || mark.equals("|100")) {
            return LexerToken.ofMain(lineNum, filename);
        }
    
        // 绝对地址 |xxxx
        if (c0 == '|' && mark.length() > 1) {
            String hex = mark.substring(1);
            if (!isHex(hex)) {
                throw new RuntimeException("Error: Invalid <" + mark + "> at line " + lineNum);
            }
            return LexerToken.ofAddr(hex.toLowerCase(Locale.ROOT), lineNum, filename);
        }
    
        // 相对填充 $xxxx （Perl 语义：会写 size+1 个 0）
        if (c0 == '$' && mark.length() > 1) {
            String hex = mark.substring(1);
            if (!isHex(hex)) {
                throw new RuntimeException("Error: Invalid <" + mark + "> at line " + lineNum);
            }
            int size = Integer.parseInt(hex, 16);
            return LexerToken.ofPad(hex.toLowerCase(Locale.ROOT), size, lineNum, filename);
        }
    
        // 条件/普通 立即调用语法
        if (mark.startsWith("?{")) return LexerToken.ofSpecial("Q_LAMBDA_START", "", lineNum, filename);
        if (c0 == '?' && mark.length() > 1) {
            String nv = mark.substring(1);
            if (nv.equals("}")) return LexerToken.ofSpecial("Q_LAMBDA_END", "", lineNum, filename);
            return LexerToken.ofSpecial("QCALL", nv, lineNum, filename);
        }
        if (mark.startsWith("{"))  return LexerToken.ofSpecial("LAMBDA_START", "", lineNum, filename);
        if (mark.startsWith("}"))  return LexerToken.ofSpecial("LAMBDA_END", "", lineNum, filename);
    
        // 指令（词法识别；语义层再查表，不存在则降级为立即调用）
        if (mark.length() >= 3 && isAZ3(mark)) {
            String instr = mark.substring(0, 3);
            String flags = (mark.length() > 3) ? mark.substring(3) : "";
            if (flags.isEmpty() || onlyFlags_2rk(flags)) {
                int sz = (flags.indexOf('2') >= 0) ? 2 : 1;
                int r  = (flags.indexOf('r') >= 0) ? 1 : 0;
                int k  = (flags.indexOf('k') >= 0) ? 1 : 0;
                return LexerToken.ofInstr(instr, sz, r, k, lineNum, filename);
            }
        }
    
        // RAW：严格两位或四位
        if (isHex(mark)) {
            int n = mark.length();
            if (n == 2 || n == 4) {
                int sz = (n == 2) ? 1 : 2;
                return LexerToken.ofRaw(mark.toLowerCase(Locale.ROOT), sz, lineNum, filename);
            }
            // 其它长度的纯 hex → 报错
            throw new RuntimeException("Error: Invalid number <" + mark + "> at line " + lineNum);
        }
    
        // 普通标识符 → 立即调用（贴近 Perl）
        char h = mark.charAt(0);
        boolean headOk = (h == '<' || h == '*' || h == '+' || h == '^'
                || Character.isLetterOrDigit(h) || h == '_');
        if (headOk) {
            return LexerToken.ofSpecial("IDENT", mark, lineNum, filename);
        }
    
        throw new RuntimeException("Error: Invalid token <" + mark + "> at line " + lineNum);
    }
    
    //include 展开

    private static IncludeResult processIncludes(List<LexerToken> tokens, List<int[]> lineMapping,
                                                 String baseDir, Set<String> includedFiles) {
        List<LexerToken> outTokens = new ArrayList<>();
        List<int[]> outMapping = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            LexerToken t = tokens.get(i);
            int[] lm = (i < lineMapping.size()) ? lineMapping.get(i)
                                                : new int[]{t.lineNum, t.filename.hashCode()};

            if (t.type == Definitions.TokenType.INCLUDE) {
                String incFile = Paths.get(baseDir, t.value).toString();
                if (includedFiles.contains(incFile)) {
                    // 已包含过则跳过（避免循环）
                    continue;
                }
                includedFiles.add(incFile);
                String content;
                try {
                    content = new String(Files.readAllBytes(Paths.get(incFile)));
                } catch (IOException ex) {
                    throw new RuntimeException("Failed to include " + t.value + " at line " + t.lineNum + " (" + t.filename + ")");
                }
                List<LexerToken> incTokens = new ArrayList<>();
                List<int[]> incMapping = new ArrayList<>();
                tokenize(content, incFile, incTokens, incMapping);

                IncludeResult deeper = processIncludes(incTokens, incMapping, baseDirOf(incFile), includedFiles);
                outTokens.addAll(deeper.tokens);
                outMapping.addAll(deeper.lineMapping);
            } else {
                outTokens.add(t);
                outMapping.add(lm);
            }
        }

        return new IncludeResult(outTokens, outMapping);
    }

    private static String baseDirOf(String path) {
        try {
            java.nio.file.Path p = Paths.get(path);
            java.nio.file.Path parent = p.getParent();
            return (parent == null) ? "." : parent.toString();
        } catch (Exception e) {
            return ".";
        }
    }

    //语义展开

    private static ParseTokensResult processTokens(List<LexerToken> lexers, List<int[]> lineMapping, ParserState st) {
        List<Token> out = new ArrayList<>();
        List<int[]> idxs = new ArrayList<>();

        final int REF_IMMED = 6; 

        for (int i = 0; i < lexers.size(); i++) {
            LexerToken t = lexers.get(i);
            int[] lineInfo = (i < lineMapping.size()) ? lineMapping.get(i)
                                                      : new int[]{t.lineNum, t.filename.hashCode()};

            // 特殊语法优先展开
            if (t.special != null) {
                switch (t.special) {
                    case "QCALL": { // ?name → JCI(2) + REF(name, IMMED, isChild)
                        out.add(new Token(Definitions.TokenType.INSTR, "JCI", 2, 0, 0, t.lineNum));
                        idxs.add(lineInfo);
                        out.add(new Token(Definitions.TokenType.REF, t.value, REF_IMMED,  t.isChild, t.lineNum));
                        idxs.add(lineInfo);
                        continue;
                    }
                    case "MCALL": { // !name → JMI(2) + REF(name, IMMED, isChild)
                        out.add(new Token(Definitions.TokenType.INSTR, "JMI", 2, 0, 0, t.lineNum));
                        idxs.add(lineInfo);
                        out.add(new Token(Definitions.TokenType.REF, t.value, REF_IMMED,  t.isChild, t.lineNum));
                        idxs.add(lineInfo);
                        continue;
                    }
                    case "Q_LAMBDA_START": { // ?{
                        int id = st.lambdaCount++;
                        String lbl = id + "_LAMBDA";
                        out.add(new Token(Definitions.TokenType.INSTR, "JCI", 2, 0, 0, t.lineNum));
                        idxs.add(lineInfo);
                        out.add(new Token(Definitions.TokenType.REF, lbl, REF_IMMED, 1, t.lineNum)); // child=1
                        idxs.add(lineInfo);
                        st.lambdaStack.push(id);
                        continue;
                    }
                    case "LAMBDA_START": { // {
                        int id = st.lambdaCount++;
                        String lbl = id + "_LAMBDA";
                        out.add(new Token(Definitions.TokenType.INSTR, "JSI", 2, 0, 0, t.lineNum));
                        idxs.add(lineInfo);
                        out.add(new Token(Definitions.TokenType.REF, lbl, REF_IMMED, 1, t.lineNum)); // child=1
                        idxs.add(lineInfo);
                        st.lambdaStack.push(id);
                        continue;
                    }
                    case "LAMBDA_END": { // }
                        if (st.lambdaStack.isEmpty()) {
                            throw new RuntimeException("Error: Unmatched '}' at line " + t.lineNum + " (" + t.filename + ")");
                        }
                        int id = st.lambdaStack.pop();
                        String lbl = id + "_LAMBDA";
                        out.add(new Token(Definitions.TokenType.LABEL, lbl, 1, t.lineNum)); // child label
                        idxs.add(lineInfo);
                        continue;
                    }
                    case "IDENT": { // 普通标识符 → 立即调用
                        out.add(new Token(Definitions.TokenType.INSTR, "JSI", 2, 0, 0, t.lineNum));
                        idxs.add(lineInfo);
                        out.add(new Token(Definitions.TokenType.REF, t.value, REF_IMMED, 0, t.lineNum));
                        idxs.add(lineInfo);
                        continue;
                    }
                }
            }

            // 常规类型
            switch (t.type) {
                case MAIN: {
                    st.hasMain = true;
                    out.add(new Token(Definitions.TokenType.MAIN, "", t.lineNum));
                    idxs.add(lineInfo);
                    break;
                }
                case STR: { // 展开为 RAW（每个字符 1 字节）
                    for (int k = 0; k < t.value.length(); k++) {
                        int code = t.value.charAt(k);
                        String hex = String.format(Locale.ROOT, "%02x", code & 0xFF);
                        out.add(new Token(Definitions.TokenType.RAW, hex, 1, t.lineNum));
                        idxs.add(lineInfo);
                    }
                    break;
                }
                case PAD: { // 不在 Parser 展开；交给 Encoder
                    out.add(new Token(Definitions.TokenType.PAD, t.value, t.size, t.lineNum));
                    idxs.add(lineInfo);
                    break;
                }
                case LABEL: { // 仅记录 parent/child；不合成 parent/child 全名
                    out.add(new Token(Definitions.TokenType.LABEL, t.value, t.size, t.lineNum)); // size=2(parent)/1(child)
                    idxs.add(lineInfo);
                    break;
                }
                case REF: {
                    out.add(new Token(Definitions.TokenType.REF, t.value, t.refType, t.isChild, t.lineNum));
                    idxs.add(lineInfo);
                    break;
                }
                case INSTR: {
                    if (isKnownOpcode(t.value)) {
                        out.add(new Token(Definitions.TokenType.INSTR, t.value, t.size, t.stack, t.keep, t.lineNum));
                        idxs.add(lineInfo);
                    } else {
                        // 未知助记符 → 立即调用：JSI(2) + REF(name, IMMED)
                        out.add(new Token(Definitions.TokenType.INSTR, "JSI", 2, 0, 0, t.lineNum));
                        idxs.add(lineInfo);
                        out.add(new Token(Definitions.TokenType.REF, t.value, REF_IMMED, 0, t.lineNum));
                        idxs.add(lineInfo);
                    }
                    break;
                }
                case LIT:
                case RAW:
                case ADDR:
                case UNKNOWN:
                case EMPTY:
                case INCLUDE:
                case PLACEHOLDER:
                default: {
                    out.add(toFinalToken(t));
                    idxs.add(lineInfo);
                    break;
                }
            }
        }

        if (!st.lambdaStack.isEmpty()) {
            throw new RuntimeException("Error: Unmatched '{' for lambda block(s).");
        }

        return new ParseTokensResult(out, idxs);
    }

    //尝试所有 (short, r, k) 组合，只要有一个能取到 opcode 就认为是合法助记符。
    private static boolean isKnownOpcode(String mnem) {
        boolean[] flags = new boolean[]{false, true};
        for (boolean s : flags) {
            for (boolean r : flags) {
                for (boolean k : flags) {
                    try {
                        Definitions.getOpcodeByte(mnem, s, r, k);
                        return true;
                    } catch (RuntimeException ignore) {
                        // 该组合不存在，继续尝试
                    }
                }
            }
        }
        return false;
    }

    private static Token toFinalToken(LexerToken t) {
        switch (t.type) {
            case LIT:
            case RAW:
            case LABEL:
                return new Token(t.type, t.value, t.size, t.lineNum);
            case REF:
                return new Token(t.type, t.value, t.refType, t.isChild, t.lineNum);
            case INSTR:
                return new Token(t.type, t.value, t.size, t.stack, t.keep, t.lineNum);
            case PAD:
                return new Token(t.type, t.value, t.size, t.lineNum);
            case ADDR:
                return new Token(t.type, t.value, t.lineNum);
            case MAIN:
                return new Token(t.type, "", t.lineNum);
            default:
                return new Token(t.type, t.value, t.lineNum);
        }
    }
}
