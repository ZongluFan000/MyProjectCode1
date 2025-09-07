package yaku.uxntal;

import java.util.*;
import yaku.uxntal.Definitions.TokenType;

/**
 * PrettyPrint (no UxnState dependency)
 *
 * Utilities to render tokens and memory/state for reports and debugging.
 * This file intentionally avoids any reference to UxnState so that the
 * project can compile even when UxnState.java is removed.
 */
public final class PrettyPrint {

    private PrettyPrint() {}

 

    /** Reconstruct a compact source-like string from tokens. */
    public static String prettyPrintStr(List<Token> tokens, boolean noNewline) {
        if (tokens == null || tokens.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        String sep = "";
        String currentParent = "";

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t == null || t.type == TokenType.EMPTY) continue;

            // Basic line breaking heuristics:
            // - MAIN emits a fresh line and a |0100 sentinel
            // - Parent LABEL (@foo) starts a new line
            // - BRK ends the line
            switch (t.type) {
                case MAIN:
                    if (sb.length() > 0 && sb.charAt(sb.length()-1) != '\n') sb.append('\n');
                    sb.append("|0100");
                    if (!noNewline) sb.append('\n');
                    sep = "";
                    continue;

                case LABEL:
                    if (t.size == 2) { // @parent
                        currentParent = safe(t.value);
                        if (sb.length() > 0 && sb.charAt(sb.length()-1) != '\n') sb.append('\n');
                        sb.append("@").append(currentParent);
                        if (!noNewline) sb.append('\n');
                        sep = "";
                        continue;
                    } else { // &child
                        if (sb.length() > 0 && sb.charAt(sb.length()-1) != '\n') sb.append('\n');
                        sb.append("&").append(safe(t.value));
                        if (!noNewline) sb.append('\n');
                        sep = "";
                        continue;
                    }

                default:
                    break;
            }

            // Regular token: append with space
            if (!sep.isEmpty()) sb.append(sep);
            sb.append(ppTokenAtom(t, currentParent));
            sep = " ";

            // add newline after BRK
            if (t.type == TokenType.INSTR && "BRK".equalsIgnoreCase(t.value)) {
                if (!noNewline) { sb.append('\n'); sep = ""; }
            }
        }
        return sb.toString();
    }

    /** Print to stdout. */
    public static void prettyPrint(List<Token> tokens, boolean noNewline) {
        System.out.print(prettyPrintStr(tokens, noNewline));
    }

    /** Pretty print a single token (diagnostic). */
    public static String prettyPrintToken(Token t, boolean withIndex) {
        if (t == null) return "∅";
        String core = String.format("%s('%s', sz=%d, r=%d, k=%d, refType=%d, isChild=%d, line=%d)",
                t.type, t.value, t.size, t.stack, t.keep, t.refType, t.isChild, t.lineNum);
        return withIndex ? core : core;
    }

    /** Render a token list with optional indices and a maximum count per line. */
    public static String prettyPrintTokens(List<Token> tokens, boolean showIndices, int maxPerLine) {
        if (tokens == null) return "";
        if (maxPerLine <= 0) maxPerLine = Integer.MAX_VALUE;
        StringBuilder sb = new StringBuilder();
        int n = tokens.size();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(' ');
            if (showIndices) sb.append('[').append(i).append("] ");
            sb.append(ppTokenAtom(tokens.get(i), ""));
            if ((i + 1) % maxPerLine == 0) sb.append('\n');
        }
        if (sb.length() > 0 && sb.charAt(sb.length()-1) != '\n') sb.append('\n');
        return sb.toString();
    }

    /** Group tokens by type for debugging (counts only). */
    public static String prettyPrintGrouped(List<Token> tokens, boolean showTypes, boolean showIndices, String indent) {
        if (tokens == null) return "";
        Map<TokenType, List<Token>> groups = new EnumMap<>(TokenType.class);
        for (Token t : tokens) {
            groups.computeIfAbsent(t.type, k -> new ArrayList<>()).add(t);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<TokenType, List<Token>> e : groups.entrySet()) {
            sb.append(e.getKey()).append(" (").append(e.getValue().size()).append("):\n");
            for (int i = 0; i < e.getValue().size(); i++) {
                sb.append(indent == null ? "" : indent);
                if (showIndices) sb.append('[').append(i).append("] ");
                sb.append(ppTokenAtom(e.getValue().get(i), ""));
                sb.append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

  

    /** Render a compact CPU-state snapshot without depending on UxnState. */
    public static String prettyPrintState(int pc, int free, boolean hasMain,
                                          Deque<?> workingStack, Deque<?> returnStack) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== UXN State ===\n");
        sb.append("PC:  0x").append(toHexBytes(pc, 2)).append('\n');
        sb.append("End: 0x").append(toHexBytes(free, 2)).append('\n');
        sb.append("Has Main: ").append(hasMain).append('\n');
        sb.append("Working Stack: ").append(stackToString(workingStack)).append('\n');
        sb.append("Return  Stack: ").append(stackToString(returnStack)).append('\n');
        return sb.toString();
    }

    /** Hex-dump a slice of memory without depending on UxnState. */
    public static String prettyPrintMemory(byte[] memory, int start, int end) {
        if (memory == null) return "(memory=null)";
        int n = memory.length;
        if (start < 0) start = 0;
        if (end <= start || end > n) end = Math.min(n, start + 256);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Memory Dump (0x").append(toHexBytes(start, 2))
          .append(" - 0x").append(toHexBytes(end, 2)).append(") ===\n");

        for (int addr = start; addr < end; addr += 16) {
            StringBuilder lineBytes = new StringBuilder();
            StringBuilder lineChars = new StringBuilder();
            int limit = Math.min(16, end - addr);
            for (int i = 0; i < limit; i++) {
                int b = memory[addr + i] & 0xFF;
                if (i > 0) lineBytes.append(' ');
                lineBytes.append(toHexBytes(b, 1));
                char c = (b >= 32 && b <= 126) ? (char)b : '.';
                lineChars.append(c);
            }
            sb.append(toHexBytes(addr, 2)).append(": ")
              .append(padRight(lineBytes.toString(), 16 * 3 - 1))
              .append("  ")
              .append(lineChars)
              .append('\n');
        }
        return sb.toString();
    }

   

    private static String ppTokenAtom(Token t, String currentParent) {
        if (t == null) return "";
        switch (t.type) {
            case MAIN: return "|0100";
            case LABEL:
                return (t.size == 2 ? "@" : "&") + safe(t.value);
            case LIT:
                return "#" + toHexBytes(parseNumber(t.value), t.size);
            case RAW:
                return t.value; // already hex string of 2 or 4 nibbles
            case ADDR:
                return "|" + t.value;
            case PAD:
                return "$" + t.value;
            case STR:
                return "\"" + (t.value == null ? "" : t.value) + "\"";
            case REF:
            case IREF:
                String rune = refRune(t.refType);
                String name = safe(t.value);
                if (t.isChild == 1 && name.indexOf('/') < 0 && currentParent != null && !currentParent.isEmpty()) {
                    name = currentParent + "/" + name;
                }
                if (t.refType == 6) { // IREF special "I" rune
                    return "I" + name;
                }
                return rune + name;
            case INSTR:
                String suffix = (t.size == 2 ? "2" : "") + (t.stack == 1 ? "r" : "") + (t.keep == 1 ? "k" : "");
                return t.value + suffix;
            default:
                return t.type + (t.value != null ? "(" + t.value + ")" : "");
        }
    }

    private static String refRune(int refType) {
        if (refType >= 0 && refType < Definitions.REV_REF_TYPES.length) {
            return Definitions.REV_REF_TYPES[refType];
        }
        return ".";
    }

    private static String safe(String s) { return (s == null) ? "" : s; }

    private static String padRight(String s, int len) {
        if (s.length() >= len) return s;
        StringBuilder sb = new StringBuilder(len);
        sb.append(s);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }

    /** Parse numeric string in decimal/hex ("0x" or without) used by some tokens. */
    private static long parseNumber(String val) {
        if (val == null || val.isEmpty()) return 0L;
        String s = val.trim();
        int base = 10;
        int i = 0;
        if (s.startsWith("0x") || s.startsWith("0X")) {
            base = 16; i = 2;
        }
        long n = 0;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            int d;
            if (c >= '0' && c <= '9') d = c - '0';
            else if (c >= 'a' && c <= 'f') d = c - 'a' + 10;
            else if (c >= 'A' && c <= 'F') d = c - 'A' + 10;
            else continue;
            n = (n * base) + d;
        }
        return n;
    }

    /** Hex string of exactly {@code bytes} bytes (2*bytes hex digits), wrapping like unsigned. */
    public static String toHexBytes(long n, int bytes) {
        long mod = 1L << (bytes * 8);
        long v = ((n % mod) + mod) % mod;
        String s = Long.toHexString(v).toLowerCase(Locale.ROOT);
        int digits = bytes * 2;
        if (s.length() < digits) {
            StringBuilder sb = new StringBuilder(digits);
            for (int i = s.length(); i < digits; i++) sb.append('0');
            sb.append(s);
            return sb.toString();
        }
        if (s.length() > digits) return s.substring(s.length() - digits);
        return s;
    }

    public static String toHexBytes(String val, int bytes) {
        return toHexBytes(parseNumber(val), bytes);
    }

    public static String toHex(String val, int digits) {
        long n = parseNumber(val);
        long mod = 1L << (digits * 4);
        if (n < 0) n = (n + mod) & (mod - 1);
        String s = Long.toHexString(n & (mod - 1));
        if (s.length() < digits) {
            StringBuilder sb = new StringBuilder(digits);
            for (int i = s.length(); i < digits; i++) sb.append('0');
            sb.append(s);
            return sb.toString();
        }
        if (s.length() > digits) return s.substring(s.length() - digits);
        return s;
    }

    public static String toHex(int val, int digits) {
        long n = val;
        long mod = 1L << (digits * 4);
        if (n < 0) n = (n + mod) & (mod - 1);
        String s = Long.toHexString(n & (mod - 1));
        if (s.length() < digits) {
            StringBuilder sb = new StringBuilder(digits);
            for (int i = s.length(); i < digits; i++) sb.append('0');
            sb.append(s);
            return sb.toString();
        }
        if (s.length() > digits) return s.substring(s.length() - digits);
        return s;
    }

    private static String stackToString(Deque<?> stack) {
        if (stack == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object o : stack) {
            if (!first) sb.append(", ");
            first = false;
            if (o instanceof Number) {
                long v = ((Number) o).longValue();
                sb.append("0x").append(toHexBytes(v, 1));
            } else if (o instanceof Token) {
                Token t = (Token) o;
                sb.append(ppTokenAtom(t, ""));
            } else {
                sb.append(String.valueOf(o));
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
