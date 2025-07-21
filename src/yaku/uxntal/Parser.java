package yaku.uxntal;

import java.util.*;
import static yaku.uxntal.Definitions.*;

public class Parser {
    // 主入口，传入源码字符串，返回Token列表
    public List<Token> parse(String source) {
        List<Token> tokens = new ArrayList<>();
        String[] lines = source.split("\\r?\\n");
        int lineNum = 1;
        for (String line : lines) {
            String code = stripComment(line);
            if (code.trim().isEmpty()) {
                lineNum++;
                continue;
            }
            // 拆分为标记
            String[] marks = code.trim().split("\\s+");
            for (String mark : marks) {
                if (mark.isEmpty()) continue;
                Token token = parseToken(mark, lineNum);
                if (token != null) {
                    tokens.add(token);
                }
            }
            lineNum++;
        }
        return tokens;
    }

    // 去除一行中的注释 ( ... )
    private String stripComment(String line) {
        int start = line.indexOf('(');
        int end = line.indexOf(')');
        if (start != -1 && end > start) {
            return line.substring(0, start) + line.substring(end + 1);
        }
        return line;
    }

    // 根据标记内容和行号生成Token
    private Token parseToken(String mark, int lineNum) {
        // 主程序入口
        if (mark.equals("|0100") || mark.equals("|100")) {
            return new Token(TokenType.MAIN, "", 0, lineNum);
        }
        // 地址跳转
        if (mark.startsWith("|") && mark.length() > 1) {
            int addr = parseHex(mark.substring(1));
            return new Token(TokenType.ADDR, mark, addr, lineNum);
        }
        // 填充
        if (mark.startsWith("$")) {
            int count = parseHex(mark.substring(1));
            return new Token(TokenType.PAD, mark, count, lineNum);
        }
        // 父标签
        if (mark.startsWith("@")) {
            return new Token(TokenType.LABEL, mark.substring(1), 2, lineNum);
        }
        // 子标签
        if (mark.startsWith("&")) {
            return new Token(TokenType.LABEL, mark.substring(1), 1, lineNum);
        }
        // 常量（#10，#1000）
        if (mark.startsWith("#")) {
            String hexStr = mark.substring(1);
            int value = parseHex(hexStr);
            int sz = (hexStr.length() > 2) ? 2 : 1;
            return new Token(TokenType.LIT, hexStr, sz, lineNum);
        }
        // 标签引用：;,.,等
        if (mark.length() > 1 && Definitions.isRefPrefix(mark.substring(0, 1))) {
            String prefix = mark.substring(0, 1);
            String refName = mark.substring(1);
            int refType = Definitions.REF_TYPE_MAP.get(prefix).ordinal();
            return new Token(TokenType.REF, refName, refType, lineNum);
        }
        // 指令
        if (Definitions.isOpcode(mark.toUpperCase())) {
            return new Token(TokenType.INSTR, mark.toUpperCase(), 1, lineNum);
        }
        // 其它（未知/暂不支持）
        return new Token(TokenType.UNKNOWN, mark, 0, lineNum);
    }

    // 简易十六进制解析（兼容十进制写法）
    private int parseHex(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            if (s.matches("[0-9A-Fa-f]+")) {
                return Integer.parseInt(s, 16);
            }
            return Integer.parseInt(s); // fallback to decimal
        } catch (Exception e) {
            return 0;
        }
    }

    // 你可以在这里加 parseStringLiteral、parseInclude、parseBlock 等高级功能
}
