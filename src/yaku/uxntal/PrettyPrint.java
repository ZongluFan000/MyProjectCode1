package yaku.uxntal;

import java.util.List;
import static yaku.uxntal.Definitions.*;

public class PrettyPrint {
    // 打印整个 token 序列为字符串
    public static String prettyPrintStr(List<Token> tokens, boolean noNewline) {
        StringBuilder tokensStr = new StringBuilder();
        String nl = noNewline ? "" : "\n";
        int n_tokens = tokens.size();
        String prefix = "";
        boolean skip;
        String mws;
        String currentParent = "";
        for (int i = 0; i < n_tokens; i++) {
            Token token = tokens.get(i);
            if (token == null) continue;
            String tokenStr = "";
            mws = " ";
            skip = false;
            switch (token.type) {
                case MAIN:
                    tokenStr = nl + "|0100" + nl;
                    mws = "";
                    break;
                case EMPTY:
                    skip = true;
                    break;
                case LABEL:
                    if (token.size == 1) { // 子标签
                        // Lambda 特判
                        if (token.value.endsWith("_LAMBDA")) {
                            tokenStr = "}";
                        } else {
                            tokenStr = (tokensStr.length() > 0 && tokensStr.charAt(tokensStr.length()-1) == '\n' ? "&" : nl + "&") + token.value + nl;
                        }
                    } else if (token.size == 2) { // 父标签
                        tokenStr = nl + "@" + token.value + (
                                (n_tokens > 1 && (i+1 < tokens.size() && tokens.get(i+1).type == TokenType.PAD)) ? "" : nl
                        );
                        mws = "";
                        currentParent = token.value;
                    }
                    break;
                case REF:
                    // 子引用特殊前缀
                    String maybeChildRef = (token.isChild == 1 && !token.value.contains("/")) ? "&" : "";
                    String refTypeStr = "";
                    if (token.refType >= 0 && token.refType < Definitions.REV_REF_TYPES.length) {
                        refTypeStr = Definitions.REV_REF_TYPES[token.refType];
                    }
                    if ("I".equals(refTypeStr)) {
                        // Lambda 特判
                        tokenStr = (token.value.matches("\\d+_LAMBDA")) ? "{" : prefix + maybeChildRef + token.value;
                    } else {
                        tokenStr = refTypeStr + maybeChildRef + token.value;
                    }
                    prefix = "";
                    skip = false;
                    break;
                case INSTR:
                    // JSI/JCI/JMI 特判
                    if ("JSI".equals(token.value)) {
                        prefix = "";
                        skip = false;
                        break;
                    } else if ("JCI".equals(token.value)) {
                        prefix = "?";
                        skip = false;
                        break;
                    } else if ("JMI".equals(token.value)) {
                        prefix = "!";
                        skip = false;
                        break;
                    } else {
                        tokenStr = token.value
                                + (token.size == 2 ? "2" : "")
                                + (token.stack == 1 ? "r" : "")
                                + (token.keep == 1 ? "k" : "")
                                + ((i+1 < tokens.size() && tokens.get(i+1).type != TokenType.INSTR) ? nl : "");
                        if ("BRK".equals(token.value)) {
                            tokenStr = nl + tokenStr;
                        }
                        skip = false;
                    }
                    break;
                case LIT:
                    tokenStr = "#" + toHex(token.value, token.size);
                    break;
                case RAW:
                    tokenStr = toHex(token.value, token.size);
                    break;
                case ADDR:
                    tokenStr = "|" + String.format("%04x", Integer.parseInt(token.value, 16)) + " ";
                    break;
                case PAD:
                    tokenStr = "$" + Integer.toHexString(Integer.parseInt(token.value, 16));
                    break;
                case PLACEHOLDER:
                    tokenStr = "( " + token.value + " )";
                    break;
                case STR:
                case INCLUDE:
                    tokenStr = token.value;
                    break;
                default:
                    tokenStr = "TODO:" + token.toString();
            }
            if (!skip && !tokenStr.isEmpty()) {
                tokensStr.append(mws).append(tokenStr);
            }
        }
        return tokensStr.toString();
    }

    // 直接打印
    public static void prettyPrint(List<Token> tokens, boolean noNewline) {
        System.out.print(prettyPrintStr(tokens, noNewline));
    }

    // 打印单个 token
    public static String prettyPrintToken(Token token) {
        String tokenStr = "";
        switch (token.type) {
            case EMPTY:
                return "";
            case MAIN:
                tokenStr = "|0100";
                break;
            case LABEL:
                if (token.size == 1) { // 子标签
                    tokenStr = token.value.endsWith("_LAMBDA") ? "}" : "&" + token.value;
                } else if (token.size == 2) { // 父标签
                    tokenStr = "@" + token.value;
                }
                break;
            case REF:
                String maybeChildRef = (token.isChild == 1 && !token.value.contains("/")) ? "&" : "";
                String refTypeStr = "";
                if (token.refType >= 0 && token.refType < Definitions.REV_REF_TYPES.length) {
                    refTypeStr = Definitions.REV_REF_TYPES[token.refType];
                }
                if ("I".equals(refTypeStr)) {
                    tokenStr = token.value.endsWith("_LAMBDA") ? "{" : maybeChildRef + token.value;
                } else {
                    tokenStr = refTypeStr + maybeChildRef + token.value;
                }
                break;
            case INSTR:
                tokenStr = token.value
                        + (token.size == 2 ? "2" : "")
                        + (token.stack == 1 ? "r" : "")
                        + (token.keep == 1 ? "k" : "");
                break;
            case LIT:
                tokenStr = "#" + toHex(token.value, token.size);
                break;
            case RAW:
                tokenStr = toHex(token.value, token.size);
                break;
            case ADDR:
                tokenStr = "|" + String.format("%04x", Integer.parseInt(token.value, 16)) + " ";
                break;
            case PAD:
                tokenStr = "$" + Integer.toHexString(Integer.parseInt(token.value, 16));
                break;
            case PLACEHOLDER:
                tokenStr = "( " + token.value + " )";
                break;
            case STR:
            case INCLUDE:
                tokenStr = token.value;
                break;
            default:
                tokenStr = "TODO:" + token.toString();
        }
        return tokenStr;
    }

    // 进制转换（补0，支持负数补码、指定字节宽度）
    public static String toHex(String val, int sz) {
        int n;
        try {
            if (val.matches("^-?\\d+$")) {
                n = Integer.parseInt(val, 16);
            } else {
                n = Integer.parseInt(val);
            }
        } catch (NumberFormatException e) {
            n = 0;
        }
        int szx2 = sz * 2;
        if (n < 0) {
            n = (int) (Math.pow(2, 8 * sz) + n);
        }
        String fmt = "%0" + szx2 + "x";
        return String.format(fmt, n);
    }
}

