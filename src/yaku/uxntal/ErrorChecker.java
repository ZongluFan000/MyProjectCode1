package yaku.uxntal;
import yaku.uxntal.units.UxnState;

import java.util.*;
import java.util.regex.Pattern;


public final class ErrorChecker {

    private ErrorChecker() {}

    //Public API

    // 主入口：执行静态检查；发现错误则抛出异常
    public static void checkErrors(List<Token> tokens, UxnState uxn) {
        if (tokens == null) throw new IllegalArgumentException("tokens == null");
        if (uxn == null)    throw new IllegalArgumentException("uxn == null");

        // 构建（或刷新）分配表
        buildAllocationTable(tokens, uxn);

        String currentParent = "";
        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        boolean inZeroPage = true; // 遇到 MAIN 或 |0100 后退出零页

        for (int idx = 0; idx < tokens.size(); idx++) {
            Token tok  = tokens.get(idx);
            Token next = (idx + 1 < tokens.size()) ? tokens.get(idx + 1) : makeEmpty();
            Token prev = (idx - 1 >= 0) ? tokens.get(idx - 1) : null;

            // 离开零页的两个条件：MAIN 或 |0100
            if (tok.type == Definitions.TokenType.MAIN) {
                inZeroPage = false;
            }
            if (tok.type == Definitions.TokenType.ADDR &&
                "0100".equalsIgnoreCase(safe(tok.value))) {
                inZeroPage = false;
            }

            // 0) 零页 RAW 禁止
            if (inZeroPage && tok.type == Definitions.TokenType.RAW) {
                errors.add("Writing raw values in the zero page is not allowed: "
                        + pp(tok) + line(tok));
                continue;
            }

            // 1) 记录当前 @parent
            if (tok.type == Definitions.TokenType.LABEL && tok.size == 2) {
                currentParent = safe(tok.value);
            }

            // 2) 常量+访存相邻（与 Perl 规则一致）
            if (tok.type == Definitions.TokenType.LIT
                    && next.type == Definitions.TokenType.INSTR
                    && isLdSt(base(next.value))) {
                errors.add("Stack constant followed by load or store: "
                        + ppPair(tok, next) + line(tok));
                continue;
            }
            if (tok.type == Definitions.TokenType.RAW
                    && next.type == Definitions.TokenType.INSTR
                    && isLdSt(base(next.value))) {
                errors.add("Raw constant followed by load or store: "
                        + ppPair(tok, next) + line(tok));
                continue;
            }

            // 3) REF 相关检查
            if (tok.type == Definitions.TokenType.REF) {

                // 子引用但没有父标签：警告
                if (isChildRef(tok) && currentParent.isEmpty()) {
                    warnings.add("Child reference without a parent label: "
                            + pp(tok) + line(tok));
                }

                if (next.type == Definitions.TokenType.INSTR) {
                    final String nb = base(next.value);

                    // 3a) LD*/ST* 寻址模式兼容性 + 分配表尺寸检查
                    if (isLdSt(nb)) {
                        char aMode    = accessLetter(nb); // Z/R/A
                        char expected = expectedFromRefType(tok.refType);
                        if (expected != 0 && aMode != 0 && aMode != expected) {
                            errors.add(pp(next) + " has address with incompatible reference mode "
                                    + pp(tok) + line(tok));
                        }

                        // 只对 . , ; 做 allocation 检查
                        if (tok.refType <= 2) {
                            String name = (isChildRef(tok) && !currentParent.isEmpty())
                                    ? currentParent + "/" + safe(tok.value)
                                    : safe(tok.value);

                            int allocSz = uxn.allocationTable.getOrDefault(name, 0);
                            int wordSz  = Math.max(1, next.size); // 1=byte, 2=short

                            if (allocSz == 0) {
                                errors.add("No allocation for reference: "
                                        + ppPair(tok, next) + line(next) + " <" + name + ">");
                            } else if (allocSz < wordSz) {
                                errors.add("Allocation is only a byte, access is a short: "
                                        + ppPair(tok, next) + line(next));
                            } else if (allocSz > wordSz && allocSz == 2) {
                                warnings.add("Allocation is larger than access size: "
                                        + ppPair(tok, next) + line(next));
                            }

                            // 存储时，和前一个 LIT 的大小对齐提示
                            if (isStore(nb) && prev != null && prev.type == Definitions.TokenType.LIT) {
                                if (prev.size > allocSz) {
                                    warnings.add("Allocation size smaller than size of constant to be stored: "
                                            + ppTriple(prev, tok, next) + line(next));
                                }
                                if (prev.size != wordSz) {
                                    warnings.add("Store size different from size of constant to be stored: "
                                            + ppTriple(prev, tok, next) + line(next));
                                }
                            }
                        }
                    }

                    // 3b) 跳转宽度兼容（JMP/JCN/JSR）
                    if (isJump(nb)) {
                        if (tok.size == 1 && next.size != 1) {
                            errors.add(pp(next) + " has address with incompatible reference mode "
                                    + pp(tok) + line(tok));
                        } else if (tok.size == 2 && next.size != 2) {
                            errors.add(pp(next) + " has address with incompatible reference mode "
                                    + pp(tok) + line(tok));
                        }
                    }
                }
            }

            // 4) SFT 检查：第二个参数必须是 byte；short-mode 与第一个参数兼容
            if (tok.type == Definitions.TokenType.LIT
                    && next.type == Definitions.TokenType.INSTR
                    && "SFT".equals(base(next.value))) {

                if (tok.size == 2) {
                    errors.add("Second argument of SFT must be a byte: "
                            + ppTriple(prev, tok, next) + line(tok));
                }

                if (prev != null
                        && (prev.type == Definitions.TokenType.LIT
                        || (prev.type == Definitions.TokenType.INSTR && base(prev.value).startsWith("LD")))
                        && prev.size != next.size) {
                    // 仅告警，非致命
                    warnings.add("SFT short mode not compatible with size of first argument: "
                            + ppTriple(prev, tok, next) + line(tok));
                }
            }
        }

        //输出告警（可能升级为错误）
        if (!warnings.isEmpty()) {
            for (String w : warnings) {
                if (!Flags.NSW || !w.contains("stack")) { // 可以按需细化 -S 的屏蔽范围
                    System.err.println("Warning: " + w);
                    if (Flags.EE && Flags.FF) {
                        throw new RuntimeException("Warnings treated as errors (EE) and stop on first (FF).");
                    }
                }
            }
            if (Flags.EE) {
                throw new RuntimeException("Warnings treated as errors (EE).");
            }
        }

        // 输出错误
        if (!errors.isEmpty()) {
            for (String e : errors) {
                System.err.println("Error: " + e);
                if (Flags.FF) {
                    throw new RuntimeException("Stop on first error (FF).");
                }
            }
            throw new RuntimeException("There were errors.");
        }
    }

    /**
     * 构建 allocation 表：@parent / &child + PAD/RAW
     * - PAD: 按 Encoder 对齐，分配为 (value + 1)
     * - RAW: 分配为 max(1, size)
     * - 连续标签（@a &b &c $2）用队列一次性回填同一 allocation
     */
    public static UxnState buildAllocationTable(List<Token> tokens, UxnState uxn) {
        if (uxn.allocationTable == null) {
            uxn.allocationTable = new HashMap<>();
        } else {
            uxn.allocationTable.clear();
        }

        String currentParent = "";
        String currentCFQN   = "";
        Deque<String> pending = new ArrayDeque<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            Token next = (i + 1 < tokens.size()) ? tokens.get(i + 1) : makeEmpty();

            if (t.type == Definitions.TokenType.LABEL) {
                // 计算当前 label 的规范名
                if (t.size == 2) { // @parent
                    currentParent = safe(t.value);
                    currentCFQN = "";
                } else {           // &child
                    String child = safe(t.value);
                    currentCFQN = currentParent.isEmpty() ? child : currentParent + "/" + child;
                }
                String curName = !currentCFQN.isEmpty() ? currentCFQN : currentParent;

                // 连续标签，挂起等待下一条分配语句
                if (next.type == Definitions.TokenType.LABEL) {
                    pending.addLast(curName);
                    continue;
                }

                int alloc = 0;
                if (next.type == Definitions.TokenType.PAD) {
                    alloc = parseInt(next.value, 0) + 1; // 与 Encoder 对齐
                } else if (next.type == Definitions.TokenType.RAW) {
                    alloc = Math.max(1, next.size);
                } else {
                    alloc = 0; // 声明但未分配
                }

                putAlloc(curName, alloc, uxn.allocationTable);
                while (!pending.isEmpty()) putAlloc(pending.removeFirst(), alloc, uxn.allocationTable);
            }
        }
        return uxn;
    }

    //Line helpers

    
    public static String getLineForToken(Token token, UxnState uxn) {
        return line(token);
    }

   
    public static Map<String, List<Integer>> getLinesForTokens(String programText) {
        Map<String, List<Integer>> map = new HashMap<>();
        if (programText == null) return map;

        String[] lines = programText.split("\\R");
        int lineIdx = 1;
        for (String raw : lines) {
            String line = raw.replaceAll("\\s*\\(.+?\\)\\s*$", ""); // 去行尾 ( ... ) 注释
            if (line.isEmpty()) { lineIdx++; continue; }

            String[] toks = line.trim().split("\\s+");
            for (String t : toks) {
                if (t.startsWith("\"")) {
                    String s = t.substring(1);
                    for (int j = 0; j < s.length(); j++) {
                        String hx = String.format("%02x", (int) s.charAt(j));
                        map.computeIfAbsent(hx, k -> new ArrayList<>()).add(lineIdx);
                    }
                } else {
                    map.computeIfAbsent(t, k -> new ArrayList<>()).add(lineIdx);
                }
            }
            lineIdx++;
        }
        return map;
    }

    //Internal helpers

    private static String safe(String s) { return s == null ? "" : s; }

    private static boolean isChildRef(Token t) {
        // 你的 Token 中：REF 的 stack==1 通常表示子引用；若有 isChild 字段也可一起判
        return t != null && t.type == Definitions.TokenType.REF && (t.stack == 1 || getIsChildSafely(t) == 1);
    }
    private static int getIsChildSafely(Token t) {
        try {
            java.lang.reflect.Field f = t.getClass().getDeclaredField("isChild");
            f.setAccessible(true);
            Object v = f.get(t);
            return (v instanceof Integer) ? (Integer) v : 0;
        } catch (Exception ignore) { return 0; }
    }

    private static String base(String instr) {
        if (instr == null) return "";
        return instr.replaceAll("\\d+$", ""); // 去掉末尾 '2'
    }

    private static boolean isLdSt(String b) {
        return b.startsWith("LD") || (b.startsWith("ST") && !b.startsWith("STH"));
    }

    private static boolean isStore(String b) {
        return b.startsWith("ST") && !b.startsWith("STH");
    }

    private static boolean isJump(String b) {
        return b.equals("JMP") || b.equals("JCN") || b.equals("JSR");
    }

    private static char accessLetter(String b) {
        // 形如 "LDA", "LDZ", "LDR", "STA", "STR", "STZ"
        return (b.length() >= 3) ? b.charAt(2) : 0;
    }

    private static char expectedFromRefType(int refType) {
        // 0..5: . , ; - _ = ;  6: immediate/none
        final char[] accessMode = new char[]{'Z','R','A','Z','R','A', 0};
        if (refType >= 0 && refType < accessMode.length) return accessMode[refType];
        return 0;
    }

    private static Token makeEmpty() {
        return new Token(Definitions.TokenType.EMPTY, "0", 0);
    }

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try {
            if (HEX.matcher(s).matches()) {
                String v = s.startsWith("0x") || s.startsWith("0X") ? s.substring(2) : s;
                return Integer.parseInt(v, 16);
            }
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static String pp(Token t) {
        if (t == null) return "<null>";
        switch (t.type) {
            case LABEL: return (t.size == 2 ? "@" : "&") + safe(t.value);
            case REF:   return refPunct(t.refType) + safe(t.value);
            case LIT:   return safe(t.value);
            case INSTR: return safe(t.value);
            case RAW:   return safe(t.value);
            case PAD:   return "$" + safe(t.value);
            case ADDR:  return "|" + safe(t.value);
            default:    return t.type.name() + ":" + safe(t.value);
        }
    }

    private static String ppPair(Token a, Token b) {
        return "[" + pp(a) + " " + pp(b) + "]";
    }

    private static String ppTriple(Token a, Token b, Token c) {
        String sa = (a == null) ? "" : pp(a) + " ";
        return "[" + sa + pp(b) + " " + pp(c) + "]";
    }

    private static String refPunct(int refType) {
        switch (refType) {
            case 0: return ".";
            case 1: return ",";
            case 2: return ";";
            case 3: return "-";
            case 4: return "_";
            case 5: return "=";
            case 6: return ""; // immediate/none
            default: return "";
        }
    }

    private static String line(Token t) {
        if (t == null || t.lineNum <= 0) return "";
        return " on line " + t.lineNum;
    }

    private static void putAlloc(String name, int size, Map<String,Integer> tbl) {
        if (name == null || name.isEmpty()) return;
        // 如果想要“重复标签”报错/警告，可在此处加判断
        // if (tbl.containsKey(name)) { ... }
        tbl.put(name, size);
    }

    private static final Pattern HEX = Pattern.compile("^(?:0x)?[0-9a-fA-F]+$");
}
