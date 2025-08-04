package yaku.uxntal;

import java.util.*;
import yaku.uxntal.Definitions.TokenType;
import yaku.uxntal.units.UxnState;

public class PrettyPrint {

    // === 单Token美化 ===
    public static String prettyPrintToken(Token token, boolean withIndex) {
        if (token == null) return "INVALID_TOKEN";
        String result = "";
        switch (token.type) {
            case MAIN:
                result = "|0100";
                break;
            case LIT:
                result = "#" + toHex(token.value, token.size == 2 ? 4 : 2);
                break;
            case INSTR:
                result = token.value
                        + (token.size == 2 ? "2" : "")
                        + (token.stack == 1 ? "r" : "")
                        + (token.keep == 1 ? "k" : "");
                break;
            case LABEL:
                result = (token.size == 2 ? "@" : "&") + token.value;
                break;
            case REF:
                String refPrefix = (token.refType >= 0 && token.refType < Definitions.REV_REF_TYPES.length)
                        ? Definitions.REV_REF_TYPES[token.refType]
                        : "?";
                result = refPrefix + token.value;
                if (token.isChild == 1) result += " (child)";
                break;
            case RAW:
                result = toHex(token.value, token.size == 2 ? 4 : 2);
                break;
            case ADDR:
                result = "|" + toHex(token.value, 4);
                break;
            case PAD:
                result = "$" + toHex(token.value, 2);
                break;
            case EMPTY:
                result = "EMPTY";
                break;
            case STR:
                result = "\"" + token.value + "\"";
                break;
            case INCLUDE:
                result = token.value;
                break;
            default:
                result = token.type + "(" + token.value + ")";
        }
        // 可选加索引
        if (withIndex && token.line >= 0) {
            result += "[" + token.line + "]";
        }
        return result;
    }

    // === Tokens数组美化 ===
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

    // === 高级格式化，美化打印 tokens（支持分组、类型显示、缩进）===
    public static String prettyPrint(List<Token> tokens, boolean showTypes, boolean showIndices, boolean groupByType, int maxPerLine, String indent) {
        if (tokens == null || tokens.isEmpty()) return "No tokens";
        if (groupByType) return prettyPrintGrouped(tokens, showTypes, showIndices, indent);

        StringBuilder sb = new StringBuilder();
        int count = 0;
        String currentIndent = "";
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            String printed = prettyPrintToken(token, showIndices);
            if (showTypes) {
                printed = token.type + ":" + printed;
            }
            // 按类型缩进
            if (token.type == TokenType.ADDR || (token.type == TokenType.LABEL && token.size == 2)) {
                if (count > 0) sb.append("\n");
                currentIndent = "";
            } else if (token.type == TokenType.LABEL && token.size == 1) {
                if (count > 0) sb.append("\n");
                currentIndent = indent;
            }
            sb.append(currentIndent).append(printed).append(" ");
            count++;
            if (maxPerLine > 0 && count % maxPerLine == 0) sb.append("\n");
        }
        return sb.toString().trim();
    }

    // === 按类型分组打印 ===
    public static String prettyPrintGrouped(List<Token> tokens, boolean showTypes, boolean showIndices, String indent) {
        Map<TokenType, List<Token>> groups = new LinkedHashMap<>();
        for (Token token : tokens) {
            groups.computeIfAbsent(token.type, k -> new ArrayList<>()).add(token);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<TokenType, List<Token>> entry : groups.entrySet()) {
            sb.append(entry.getKey()).append(" (").append(entry.getValue().size()).append("):\n");
            for (Token token : entry.getValue()) {
                sb.append(indent).append(prettyPrintToken(token, showIndices)).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // === UXN状态美化 ===
    public static String prettyPrintUxnState(UxnState uxn) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== UXN State ===\n");
        sb.append("PC: 0x").append(toHex(uxn.pc, 4)).append("\n");
        sb.append("Free: 0x").append(toHex(uxn.free, 4)).append("\n");
        sb.append("Has Main: ").append(uxn.hasMain).append("\n");
        sb.append("Working Stack (").append(uxn.stackPtr[0]).append("): ").append(uxn.stacksToString(0)).append("\n");
        sb.append("Return Stack (").append(uxn.stackPtr[1]).append("): ").append(uxn.stacksToString(1)).append("\n");
        sb.append("Labels: ").append(uxn.symbolTable.Labels.size())
                .append(", References: ").append(uxn.symbolTable.Refs.size()).append("\n");
        int usedMemory = uxn.free, totalMemory = 0x10000;
        double usagePercent = totalMemory == 0 ? 0 : (usedMemory * 100.0 / totalMemory);
        sb.append("Memory: ").append(usedMemory).append("/").append(totalMemory)
                .append(" (").append(String.format("%.2f", usagePercent)).append("%)\n");
        return sb.toString();
    }

    // === UXN内存转储 ===
    public static String prettyPrintMemory(UxnState uxn, int start, int end) {
        if (end <= start) end = Math.min(uxn.free, start + 256);
        StringBuilder sb = new StringBuilder();
        sb.append("=== Memory Dump (0x").append(toHex(start, 4)).append(" - 0x")
                .append(toHex(end, 4)).append(") ===\n");
        for (int addr = start; addr < end; addr += 16) {
            StringBuilder lineBytes = new StringBuilder();
            StringBuilder lineChars = new StringBuilder();
            for (int i = 0; i < 16 && addr + i < end; i++) {
                Object tokenObj = uxn.memory[addr + i];
                int byteVal = 0;
                if (tokenObj instanceof Token) {
                    String val = ((Token) tokenObj).value;
                    try { byteVal = Integer.parseInt(val, 16); } catch (Exception ignore) {}
                } else if (tokenObj instanceof Integer) {
                    byteVal = (int) tokenObj;
                }
                lineBytes.append(toHex(Integer.toString(byteVal), 2)).append(" ");
                lineChars.append((byteVal >= 32 && byteVal <= 126) ? (char)byteVal : '.');
            }
            sb.append(toHex(addr, 4)).append(": ")
                    .append(String.format("%-47s", lineBytes)).append(" |").append(lineChars).append("|\n");
        }
        return sb.toString();
    }

    // === 进制字符串辅助 ===
    public static String toHex(String val, int sz) {
        int n;
        try {
            if (val.matches("^-?\\d+$")) {
                n = Integer.parseInt(val);
            } else {
                n = Integer.parseInt(val, 16);
            }
        } catch (NumberFormatException e) {
            n = 0;
        }
        int szx2 = sz;
        if (n < 0) n = (int)(Math.pow(2, sz * 4) + n);
        return String.format("%0" + szx2 + "x", n);
    }
    public static String toHex(int val, int sz) {
        int n = val;
        if (n < 0) n = (int)(Math.pow(2, sz * 4) + n);
        return String.format("%0" + sz + "x", n);
    }
}
