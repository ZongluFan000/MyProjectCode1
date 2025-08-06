package yaku.uxntal;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import yaku.uxntal.units.UxnState;


public class Parser {
    //结果数据结构
    public static class ParseResult {
        public List<Token> tokens;
        public List<int[]> lineIdxs; // 每个 token 的 [lineNum, fileIdx]
        public UxnState uxn;
        public ParseResult(List<Token> tokens, List<int[]> lineIdxs, UxnState uxn) {
            this.tokens = tokens;
            this.lineIdxs = lineIdxs;
            this.uxn = uxn;
        }
    }




    //主入口
    //解析 .tal 源文件（全静态，无成员变量）
    public static ParseResult parseProgram(String programFile, UxnState initialUxn) throws Exception {
        String programText = new String(Files.readAllBytes(Paths.get(programFile)));
        UxnState uxn = (initialUxn != null) ? initialUxn : new UxnState();
        // Tokenize
        List<LexerToken> rawTokens = new ArrayList<>();
        List<int[]> lineMapping = new ArrayList<>();
        tokenize(programText, programFile, rawTokens, lineMapping);
        // include 处理（传递一个新的 Set 给递归）
        List<LexerToken> tokensWithIncludes = processIncludes(rawTokens, Paths.get(programFile).getParent().toString(), new HashSet<>());
        // 语法处理
        ParseTokensResult pr = processTokens(tokensWithIncludes, lineMapping, uxn);
        return new ParseResult(pr.tokens, pr.lineIdxs, uxn);
    }

    /** 解析直接传入的源代码文本（全静态） */
    public static ParseResult parseText(String sourceText, String filename, UxnState initialUxn) {
        UxnState uxn = (initialUxn != null) ? initialUxn : new UxnState();
        List<LexerToken> rawTokens = new ArrayList<>();
        List<int[]> lineMapping = new ArrayList<>();
        tokenize(sourceText, filename, rawTokens, lineMapping);
        ParseTokensResult pr = processTokens(rawTokens, lineMapping, uxn);
        return new ParseResult(pr.tokens, pr.lineIdxs, uxn);
    }

  


        private static void tokenize(String sourceText, String filename, List<LexerToken> tokens, List<int[]> lineMapping) {
            String cleanText = sourceText.replace("\r\n", "\n").replace("\r", "\n");
            String[] lines = cleanText.split("\n");
            int lineNum = 1;
            for (String line : lines) {
                StringBuilder noComment = new StringBuilder();
                int depth = 0;
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '(') {
                        depth++;
                    } else if (c == ')') {
                        if (depth > 0) depth--;
                    } else {
                        if (depth == 0) noComment.append(c);
                    }
                }
                for (String mark : noComment.toString().trim().split("\\s+")) {
                    if (mark.isEmpty()) continue;
                    LexerToken lt = parseMark(mark, lineNum, filename);
                    if (lt != null) {
            //////////////////////////////////////////////////////
            System.out.println("Parsed token: " + lt);
                        tokens.add(lt);
                        lineMapping.add(new int[]{lineNum, filename.hashCode()});
                    }
                }
                lineNum++;
            }
        }
        


    // 单个 token 解析，兼容 JS 版 lexer
    private static LexerToken parseMark(String mark, int lineNum, String filename) {



        
        if (mark.startsWith("#")) {
            String hexStr = mark.substring(1);
            if (!hexStr.matches("[0-9a-fA-F]{2}|[0-9a-fA-F]{4}"))
                throw new RuntimeException("Constant #" + hexStr + " must be two or four hex digits at line " + lineNum);
            int sz = hexStr.length() == 4 ? 2 : 1;   // 关键
            return new LexerToken(Definitions.TokenType.LIT, hexStr, sz, lineNum, filename);
        }
        if (mark.startsWith("~")) return new LexerToken(Definitions.TokenType.INCLUDE, mark.substring(1), lineNum, filename);
        if (mark.startsWith("%")) throw new RuntimeException("Macros not yet supported at line " + lineNum);
        if (mark.startsWith("\"") && mark.length() > 1) {
            return new LexerToken(Definitions.TokenType.STR, mark.substring(1), lineNum, filename);
        }
        if (mark.matches("^[;,.\\._\\-=].+")) {
            String prefix = mark.substring(0,1);
            String refName = mark.substring(1);
            int refType = Definitions.REF_TYPE_MAP.getOrDefault(prefix, Definitions.TokenType.REF.ordinal());
            return new LexerToken(Definitions.TokenType.REF, refName, refType, 0, lineNum, filename);
        }
        if (mark.startsWith("@")) return new LexerToken(Definitions.TokenType.LABEL, mark.substring(1), 2, lineNum, filename);
        if (mark.startsWith("&")) return new LexerToken(Definitions.TokenType.LABEL, mark.substring(1), 1, lineNum, filename);
        if (mark.startsWith("|")) {
            String val = mark.substring(1);
            return new LexerToken(Definitions.TokenType.ADDR, val, Integer.parseInt(val, 16), lineNum, filename);
        }
        if (mark.startsWith("$")) {
            String val = mark.substring(1);
            return new LexerToken(Definitions.TokenType.PAD, val, Integer.parseInt(val, 16), lineNum, filename);
        }
        if (mark.matches("^[A-Z]{3}[2kr]*$")) {
            String instr = mark.substring(0,3);
            int sz=1, r=0, k=0;
            if (mark.length() > 3) {
                String flags = mark.substring(3);
                sz = flags.contains("2") ? 2 : 1;
                r  = flags.contains("r") ? 1 : 0;
                k  = flags.contains("k") ? 1 : 0;
            }
            return new LexerToken(Definitions.TokenType.INSTR, instr, sz, r, k, lineNum, filename);
        }
        if (mark.matches("^[a-fA-F0-9]{2,4}$"))
            return new LexerToken(Definitions.TokenType.RAW, mark, (mark.length()==2)?1:2, lineNum, filename);
        return new LexerToken(Definitions.TokenType.UNKNOWN, mark, lineNum, filename);

    }
    

    // Include处理（递归，静态）
    private static List<LexerToken> processIncludes(List<LexerToken> tokens, String baseDir, Set<String> includedFiles) throws Exception {
        List<LexerToken> out = new ArrayList<>();
        for (LexerToken t : tokens) {
            if (t.type == Definitions.TokenType.INCLUDE) {
                String incFile = Paths.get(baseDir, t.value).toString();
                if (includedFiles.contains(incFile)) continue;
                includedFiles.add(incFile);
                String content = new String(Files.readAllBytes(Paths.get(incFile)));
                List<LexerToken> incTokens = new ArrayList<>();
                tokenize(content, incFile, incTokens, new ArrayList<>());
                out.addAll(processIncludes(incTokens, Paths.get(incFile).getParent().toString(), includedFiles));
            } else {
                out.add(t);
            }
        }
        return out;
    }

    // 语法分析/Token生成 
    private static class ParseTokensResult {
        List<Token> tokens;
        List<int[]> lineIdxs;
        ParseTokensResult(List<Token> tokens, List<int[]> lineIdxs) {
            this.tokens = tokens; this.lineIdxs = lineIdxs;
        }
    }
    private static ParseTokensResult processTokens(List<LexerToken> lexerTokens, List<int[]> lineMapping, UxnState uxn) {
        List<Token> out = new ArrayList<>();
        List<int[]> idxs = new ArrayList<>();
        String currentParent = "";
        int currentAddress = 0;
        for (int i=0; i<lexerTokens.size(); ++i) {
            LexerToken t = lexerTokens.get(i);
            int[] lineInfo = (i < lineMapping.size()) ? lineMapping.get(i) : new int[]{t.lineNum, t.filename.hashCode()};
            switch (t.type) {
                case ADDR:
                    currentAddress = t.size;
                    if (t.size == 0x100) uxn.hasMain = Math.max(uxn.hasMain, 1);
                    out.add(t.toToken());
                    idxs.add(lineInfo);
                    break;
                case PAD:
                    int padSize = t.size;
                    for (int j = 0; j < padSize; ++j) {
                        out.add(new Token(Definitions.TokenType.RAW, "0", 1, t.lineNum));
                        idxs.add(lineInfo);
                    }
                    break;
                case LABEL:
                    if (t.size == 2) {
                        currentParent = t.value;
                        if (i+1<lexerTokens.size() && lexerTokens.get(i+1).type == Definitions.TokenType.PAD)
                            uxn.allocationTable.put(currentParent, lexerTokens.get(i+1).size);
                    } else {
                        String fullName = currentParent.isEmpty() ? t.value : (currentParent + "/" + t.value);
                        out.add(new Token(Definitions.TokenType.LABEL, fullName, 1, t.lineNum));
                        idxs.add(lineInfo);
                        continue;
                    }
                    out.add(t.toToken());
                    idxs.add(lineInfo);
                    break;
                case REF:
                    String refName = t.value;
                    if (refName.contains("/")) t.isChild = 1;
                    else if (!currentParent.isEmpty()) {
                        refName = currentParent + "/" + refName;
                        t.isChild = 1;
                    }
                    out.add(new Token(Definitions.TokenType.REF, refName, t.refType, t.isChild, t.lineNum));
                    idxs.add(lineInfo);
                    break;
                default:
                    out.add(t.toToken());
                    idxs.add(lineInfo);
            }
        }
        return new ParseTokensResult(out, idxs);
    }

    //词法分析用的临时结构 
    private static class LexerToken {
        public Definitions.TokenType type;
        public String value;
        public int size, stack, keep, refType, isChild, lineNum;
        public String filename;
        public LexerToken(Definitions.TokenType type, String value, int lineNum, String filename) {
            this.type=type; this.value=value; this.lineNum=lineNum; this.filename=filename;
        }
        public LexerToken(Definitions.TokenType type, String value, int size, int lineNum, String filename) {
            this.type=type; this.value=value; this.size=size; this.lineNum=lineNum; this.filename=filename;
        }
        public LexerToken(Definitions.TokenType type, String value, int refType, int isChild, int lineNum, String filename) {
            this.type=type; this.value=value; this.refType=refType; this.isChild=isChild; this.lineNum=lineNum; this.filename=filename;
        }
        public LexerToken(Definitions.TokenType type, String value, int sz, int r, int k, int lineNum, String filename) {
            this.type=type; this.value=value; this.size=sz; this.stack=r; this.keep=k; this.lineNum=lineNum; this.filename=filename;
        }
        public Token toToken() {
            if (type == Definitions.TokenType.LIT || type == Definitions.TokenType.RAW)
                return new Token(type, value, size, lineNum);
            if (type == Definitions.TokenType.LABEL)
                return new Token(type, value, size, lineNum);
            if (type == Definitions.TokenType.REF)
                return new Token(type, value, refType, isChild, lineNum);
            if (type == Definitions.TokenType.INSTR)
                return new Token(type, value, size, stack, keep, lineNum);
            return new Token(type, value, lineNum);
        }
    }
}



