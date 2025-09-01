package yaku.uxntal;

import java.util.*;
import java.util.regex.Pattern;

/**
 * ErrorChecker (no UxnState dependency)
 *
 * Static analyzer for assembled token streams. The original implementation
 * stored intermediate results (like allocationTable) on an external UxnState.
 * This version keeps everything local and returns/throws results directly.
 */
public final class ErrorChecker {

    private ErrorChecker() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Run static checks; throw on any error. */
    public static void checkErrors(List<Token> tokens) {
        if (tokens == null) throw new IllegalArgumentException("tokens == null");

        List<String> errors = new ArrayList<>();
        Map<String,Integer> allocation = buildAllocationTable(tokens);

        String currentParent = "";

        for (int i = 0; i < tokens.size(); i++) {
            Token tok = tokens.get(i);
            Token next = (i + 1 < tokens.size()) ? tokens.get(i + 1) : makeEmpty();

            // Track current @parent
            if (tok.type == Definitions.TokenType.LABEL && tok.size == 2) {
                currentParent = safe(tok.value);
            }

            // Constant followed by LD/ST (matches Perl rule of "stack const + ld/st" warning)
            if (tok.type == Definitions.TokenType.LIT
                    && next.type == Definitions.TokenType.INSTR
                    && isLdSt(base(next.value))) {
                errors.add("Stack constant followed by load or store: " + pp(tok) + line(tok));
            }

            // Reference allocation checks for ., , , ;
            if (tok.type == Definitions.TokenType.REF) {
                if (tok.refType <= 2) { // . , ;
                    String name = (isChildRef(tok) && !currentParent.isEmpty() && tok.value.indexOf('/') < 0)
                            ? currentParent + "/" + safe(tok.value)
                            : safe(tok.value);

                    int wordSz = Math.max(1, next.size);
                    int allocSz = allocation.getOrDefault(name, 0);
                    if (allocSz == 0) {
                        errors.add("No allocation for reference: " + name + line(tok));
                    } else if (allocSz < wordSz) {
                        errors.add("Reference width larger than allocation: " + name
                                + " requires " + wordSz + " byte(s) but alloc=" + allocSz + line(tok));
                    }
                }
            }

            // Control flow sanity: BRK shouldn't be followed by an immediate child label without newline
            if (tok.type == Definitions.TokenType.INSTR && "BRK".equalsIgnoreCase(tok.value)) {
                if (next.type == Definitions.TokenType.LABEL && next.size == 1) {
                    // allowed, but recommend a newline (style)
                }
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("Errors:\n");
            for (String e : errors) sb.append(" - ").append(e).append('\n');
            throw new RuntimeException(sb.toString());
        }
    }

    /**
     * Build allocation table from labels and following PAD/RAW segments.
     * Semantics:
     *  - PAD: allocate (value + 1)
     *  - RAW: allocate max(1, size)
     *  - Consecutive labels (&children) prior to one PAD/RAW share that allocation.
     */
    public static Map<String,Integer> buildAllocationTable(List<Token> tokens) {
        Map<String,Integer> table = new HashMap<>();
        if (tokens == null) return table;

        String currentParent = "";
        Deque<String> pending = new ArrayDeque<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);

            if (t.type == Definitions.TokenType.LABEL) {
                if (t.size == 2) { // @parent
                    currentParent = safe(t.value);
                } else { // &child
                    String cfqn = currentParent.isEmpty() ? safe(t.value) : currentParent + "/" + safe(t.value);
                    pending.add(cfqn);
                }
                continue;
            }

            if (t.type == Definitions.TokenType.PAD || t.type == Definitions.TokenType.RAW) {
                int allocSz;
                if (t.type == Definitions.TokenType.PAD) {
                    allocSz = parseHex(t.value) + 1;
                } else {
                    allocSz = Math.max(1, t.size);
                }
                while (!pending.isEmpty()) {
                    putAlloc(pending.removeFirst(), allocSz, table);
                }
            }
        }
        // Any trailing labels without a PAD/RAW allocation map to 0 by default (unallocated)
        return table;
    }

    /** Extract a friendly " on line X" suffix. */
    public static String getLineForToken(Token token) {
        return line(token);
    }

    /** Utility: map of original lines index by some key (kept for parity with original). */
    public static Map<String, List<Integer>> getLinesForTokens(String programText) {
        Map<String, List<Integer>> map = new HashMap<>();
        if (programText == null) return map;

        String[] lines = programText.split("\\R");
        int lineIdx = 1;
        for (String raw : lines) {
            map.computeIfAbsent(raw, k -> new ArrayList<>()).add(lineIdx++);
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    // private static Token makeEmpty() {
    //     Token t = new Token();
    //     t.type = Definitions.TokenType.EMPTY;
    //     t.value = "";
    //     t.size = 1;
    //     return t;
    // }
    private static Token makeEmpty() {
        // EMPTY token，size 设为 1，行号随便给 0（不参与报错定位）
        return new Token(Definitions.TokenType.EMPTY, "", 1, 0);
    }
    
    private static String pp(Token t) {
        if (t == null) return "∅";
        return t.type + (t.value != null ? "(" + t.value + ")" : "");
    }

    private static boolean isChildRef(Token t) {
        return t != null && t.type == Definitions.TokenType.REF && t.isChild == 1;
    }

    private static String base(String instr) {
        if (instr == null) return "";
        String s = instr.toUpperCase(Locale.ROOT);
        boolean changed;
        do {
            changed = false;
            if (s.endsWith("K")) { s = s.substring(0, s.length()-1); changed = true; }
            if (s.endsWith("R")) { s = s.substring(0, s.length()-1); changed = true; }
            if (s.endsWith("2")) { s = s.substring(0, s.length()-1); changed = true; }
        } while (changed);
        return s;
    }

    private static boolean isLdSt(String base) {
        return base.startsWith("LD") || (base.startsWith("ST") && !base.startsWith("STH"));
    }

    private static String safe(String s) { return (s == null) ? "" : s; }

    private static String line(Token t) {
        if (t == null || t.lineNum <= 0) return "";
        return " on line " + t.lineNum;
    }

    private static void putAlloc(String name, int size, Map<String,Integer> tbl) {
        if (name == null || name.isEmpty()) return;
        tbl.put(name, size);
    }

    private static int parseHex(String hex) {
        int n = 0;
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            int d = (c <= '9') ? (c - '0')
                    : (c <= 'F') ? (c - 'A' + 10)
                    : (c - 'a' + 10);
            n = (n << 4) | d;
        }
        return n;
    }

    @SuppressWarnings("unused")
    private static final Pattern HEX = Pattern.compile("^(?:0x)?[0-9a-fA-F]+$");
}
