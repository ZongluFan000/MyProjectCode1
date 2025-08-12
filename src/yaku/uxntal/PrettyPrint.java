package yaku.uxntal;

import java.util.*;
import yaku.uxntal.Definitions.TokenType;
import yaku.uxntal.units.UxnState;

/**
 * PrettyPrint
 *
 * This class merges the previous Java implementation with Perl-parity behaviors:
 * - Stateful prettyPrintStr() that reconstructs source-like output.
 * - JSI/JCI/JMI prefix state ("", "?", "!") applied to the following REF I.
 * - REF child references prepend '&' when isChild==1 and name has no '/'.
 * - Lambda sugar: REF I *_LAMBDA -> "{", LABEL &*_LAMBDA -> "}".
 * - MAIN and BRK newline rules aligned with Perl.
 * - ADDR prints a trailing space; PAD is non-zero-padded hex; STR prints raw.
 * - toHexBytes(...) uses byte-size semantics like Perl's toHex(n, sz).
 */
public class PrettyPrint {

    //Public API

    /**
     * Perl-like, stateful formatter (closest to original Tal source).
     * @param tokens token list
     * @param noNewline if true, avoid final newlines where Perl would add them
     */
    public static String prettyPrintStr(List<Token> tokens, boolean noNewline) {
        if (tokens == null || tokens.isEmpty()) return "";
        String nl = noNewline ? "" : "\n";

        StringBuilder out = new StringBuilder();
        String prefix = ""; // For JSI/JCI/JMI => "", "?", "!"
        String mws = " "; // default mid-white-space (space)
        boolean skip = false;

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t == null) continue;

            String s = "";
            mws = " ";
            skip = false;

            switch (t.type) {
                case MAIN: {
                    s = nl + "|0100" + nl;
                    mws = "";
                    break;
                }
                case LABEL: {
                    if (t.size == 1) { // child '&'
                        if (endsWithLambda(t.value)) {
                            s = "}";
                        } else {
                            // if previous isn't newline, start a new line (Perl often does this)
                            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') s = nl;
                            s += "&" + safe(t.value) + nl;
                        }
                    } else { // parent '@'
                        s = nl;
                        // Perl adds an extra newline if previous ended with !word (approximate with regex)
                        if (out.toString().matches(".*![\\w\\-]+\n?$")) s += nl;
                        s += "@" + safe(t.value);
                        boolean nextIsPad = (i + 1 < tokens.size() && tokens.get(i + 1) != null
                                && tokens.get(i + 1).type == TokenType.PAD);
                        s += nextIsPad ? "" : nl;
                        mws = ""; // align with Perl: no leading space for block labels
                    }
                    break;
                }
                case REF: {
                    String rt = refTypeString(t);
                    boolean isChild = (t.isChild == 1) && !containsSlash(t.value);
                    String maybeChild = isChild ? "&" : "";
                    if ("I".equals(rt)) {
                        if (endsWithLambda(t.value) || t.value.matches("\\d+_LAMBDA")) {
                            s = "{";
                        } else {
                            s = prefix + maybeChild + safe(t.value);
                        }
                        prefix = ""; // consume once
                    } else {
                        s = rt + maybeChild + safe(t.value);
                    }
                    break;
                }
                case INSTR: {
                    String name = safe(t.value);
                    if ("JSI".equals(name) || "JCI".equals(name) || "JMI".equals(name)) {
                        prefix = "JSI".equals(name) ? "" : ("JCI".equals(name) ? "?" : "!");
                        skip = true; // do not print these pseudo-instructions
                    } else {
                        s = name + (t.size == 2 ? "2" : "") + (t.stack == 1 ? "r" : "") + (t.keep == 1 ? "k" : "");
                        // Perl: newline if next is not INSTR
                        boolean nextIsInstr = (i + 1 < tokens.size() && tokens.get(i + 1) != null
                                && tokens.get(i + 1).type == TokenType.INSTR);
                        if (!nextIsInstr) s += nl;
                        // Perl: prepend a newline before BRK
                        if ("BRK".equals(name)) s = nl + s;
                    }
                    break;
                }
                case LIT: {
                    int bytes = (t.size == 2 ? 2 : 1);
                    s = "#" + toHexBytes(t.value, bytes);
                    break;
                }
                case RAW: {
                    int bytes = (t.size == 2 ? 2 : 1);
                    s = toHexBytes(t.value, bytes);
                    break;
                }
                case ADDR: {
                    s = "|" + toHexBytes(t.value, 2) + " "; // trailing space
                    break;
                }
                case PAD: {
                    // Perl: $%x (no zero padding)
                    long n = parseNumber(t.value);
                    s = "$" + Long.toHexString(n);
                    break;
                }
                case PLACEHOLDER:
                case STR:
                case INCLUDE: {
                    s = safe(t.value); // raw
                    break;
                }
                case EMPTY: {
                    skip = true;
                    break;
                }
                default: {
                    s = t.type + "(" + safe(t.value) + ")";
                }
            }

            if (!skip) out.append(mws).append(s);
        }
        return out.toString();
    }

    //Convenience 
    public static void prettyPrint(List<Token> tokens, boolean noNewline) {
        System.out.print(prettyPrintStr(tokens, noNewline));
        if (!noNewline) System.out.println();
    }

    //Single-token printer 
    public static String prettyPrintToken(Token t, boolean withIndex) {
        if (t == null) return "INVALID_TOKEN";
        String out;
        switch (t.type) {
            case MAIN:
                out = "|0100";
                break;
            case LABEL:
                if (t.size == 1) { // child '&'
                    out = endsWithLambda(t.value) ? "}" : "&" + safe(t.value);
                } else {
                    out = "@" + safe(t.value);
                }
                break;
            case REF: {
                String rt = refTypeString(t);
                boolean isChild = (t.isChild == 1) && !containsSlash(t.value);
                String maybeChild = isChild ? "&" : "";
                if ("I".equals(rt)) {
                    out = (endsWithLambda(t.value) || t.value.matches("\\d+_LAMBDA")) ? "{" : (maybeChild + safe(t.value));
                } else {
                    out = rt + maybeChild + safe(t.value);
                }
                break;
            }
            case INSTR:
                out = safe(t.value) + (t.size == 2 ? "2" : "") + (t.stack == 1 ? "r" : "") + (t.keep == 1 ? "k" : "");
                break;
            case LIT:
                out = "#" + toHexBytes(t.value, (t.size == 2 ? 2 : 1));
                break;
            case RAW:
                out = toHexBytes(t.value, (t.size == 2 ? 2 : 1));
                break;
            case ADDR:
                out = "|" + toHexBytes(t.value, 2) + " ";
                break;
            case PAD: {
                long n = parseNumber(t.value);
                out = "$" + Long.toHexString(n);
                break;
            }
            case STR:
            case INCLUDE:
                out = safe(t.value);
                break;
            case EMPTY:
                out = "";
                break;
            default:
                out = t.type + "(" + safe(t.value) + ")";
        }
        int __li = tokenLine(t);
        if (withIndex && __li >= 0) out += "[" + __li + "]";
        return out;
    }

    //Simple linear token list printer 
    public static String prettyPrintTokens(List<Token> tokens, boolean showIndices, int maxPerLine) {
        if (tokens == null || tokens.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Token token : tokens) {
            sb.append(prettyPrintToken(token, showIndices)).append(" ");
            count++;
            if (maxPerLine > 0 && count % maxPerLine == 0) sb.append("\n");
        }
        return sb.toString().trim();
    }

    //Grouped by token type 
    public static String prettyPrintGrouped(List<Token> tokens, boolean showTypes, boolean showIndices, String indent) {
        Map<TokenType, List<Token>> groups = new LinkedHashMap<>();
        for (Token token : tokens) groups.computeIfAbsent(token.type, k -> new ArrayList<>()).add(token);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<TokenType, List<Token>> e : groups.entrySet()) {
            sb.append(e.getKey()).append(" (").append(e.getValue().size()).append("):\n");
            for (Token token : e.getValue()) sb.append(indent).append(prettyPrintToken(token, showIndices)).append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    //UxnState helpers 

    public static String prettyPrintUxnState(UxnState uxn) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== UXN State ===\n");
        sb.append("PC: 0x").append(toHexBytes(uxn.pc, 2)).append("\n");
        sb.append("Free: 0x").append(toHexBytes(uxn.free, 2)).append("\n");
        sb.append("Has Main: ").append(uxn.hasMain).append("\n");
        try {
            sb.append("Working Stack (").append(uxn.stackPtr[0]).append("): ").append(uxn.stacksToString(0)).append("\n");
            sb.append("Return Stack (").append(uxn.stackPtr[1]).append("): ").append(uxn.stacksToString(1)).append("\n");
        } catch (Throwable ignore) { }
        // Avoid depending on symbolTable internals to keep this generic
        return sb.toString();
    }

    public static String prettyPrintMemory(UxnState uxn, int start, int end) {
        if (end <= start) end = Math.min(uxn.free, start + 256);
        StringBuilder sb = new StringBuilder();
        sb.append("=== Memory Dump (0x").append(toHexBytes(start, 2)).append(" - 0x").append(toHexBytes(end, 2)).append(") ===\n");
        for (int addr = start; addr < end; addr += 16) {
            StringBuilder lineBytes = new StringBuilder();
            StringBuilder lineChars = new StringBuilder();
            for (int i = 0; i < 16 && addr + i < end; i++) {
                int byteVal = 0;
                Object cell = uxn.memory[addr + i];
                if (cell instanceof Token) {
                    String val = ((Token) cell).value;
                    byteVal = (int) (parseNumber(val) & 0xFF);
                } else if (cell instanceof Integer) {
                    byteVal = (Integer) cell;
                }
                lineBytes.append(toHexBytes(byteVal, 1)).append(" ");
                lineChars.append((byteVal >= 32 && byteVal <= 126) ? (char) byteVal : '.');
            }
            sb.append(toHexBytes(addr, 2)).append(": ")
              .append(String.format("%-47s", lineBytes)).append(" |")
              .append(lineChars).append("|\n");
        }
        return sb.toString();
        }

    //Helpers

   
    private static int tokenLine(Token t) {
        try {
            java.lang.reflect.Field f = Token.class.getDeclaredField("line");
            f.setAccessible(true);
            return f.getInt(t);
        } catch (Throwable ignore) {
            return -1;
        }
    }

    private static String refTypeString(Token t) {
        try {
            if (t.refType >= 0 && t.refType < Definitions.REV_REF_TYPES.length)
                return Definitions.REV_REF_TYPES[t.refType];
        } catch (Throwable ignore) { }
        return "?";
    }

    private static boolean containsSlash(String s) {
        return s != null && s.indexOf('/') >= 0;
    }

    private static boolean endsWithLambda(String s) {
        return s != null && s.endsWith("_LAMBDA");
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    //Parse decimal or hex string, trimming optional _N suffix
    private static long parseNumber(String s) {
        if (s == null || s.isEmpty()) return 0L;
        String v = s;
        int us = v.lastIndexOf('_');
        if (us > 0 && us == v.length() - 2 && Character.isDigit(v.charAt(us + 1))) {
            v = v.substring(0, us); // drop _N suffix
        }
        try {
            if (v.matches("^-?\\d+$")) return Long.parseLong(v);
            return Long.parseLong(v, 16);
        } catch (Exception e) {
            return 0L;
        }
    }

    //format as hex with a given byte width 
    public static String toHexBytes(long n, int bytes) {
        long mod = 1L << (bytes * 8);
        if (n < 0) n = (n + mod) & (mod - 1);
        int digits = bytes * 2;
        return String.format("%0" + digits + "x", n & (mod - 1));
    }

    //Overload: parse then format with byte width
    public static String toHexBytes(String val, int bytes) {
        return toHexBytes(parseNumber(val), bytes);
    }

    //Compatibility with prior code 

    //Prior code used digit width; keep a wrapper for compatibility
    public static String toHex(String val, int digits) {
        long n = parseNumber(val);
        long mod = 1L << (digits * 4);
        if (n < 0) n = (n + mod) & (mod - 1);
        return String.format("%0" + digits + "x", n & (mod - 1));
    }

    public static String toHex(int val, int digits) {
        long n = val;
        long mod = 1L << (digits * 4);
        if (n < 0) n = (n + mod) & (mod - 1);
        return String.format("%0" + digits + "x", n & (mod - 1));
    }
}
