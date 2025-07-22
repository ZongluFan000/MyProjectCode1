// package yaku.uxntal;

// import yaku.uxntal.Definitions.TokenType;

// public class Token {
//     public TokenType type;     // Token 类型
//     public String value;       // 指令名、标签名、引用名、数据内容等
//     public int size;           // 通常是数据/指令字节数、引用模式、或辅助标志
//     public int line;           // 源代码行号（方便错误定位）

//     // 构造方法
//     public Token(TokenType type, String value, int size, int line) {
//         this.type = type;
//         this.value = value;
//         this.size = size;
//         this.line = line;
//     }

//     public Token(TokenType type, String value) {
//         this(type, value, 0, -1);
//     }

//     public Token(TokenType type) {
//         this(type, "", 0, -1);
//     }

//     // 调试用字符串
//     @Override
//     public String toString() {
//         return String.format("Token{%s, '%s', sz=%d, line=%d}", type, value, size, line);
//     }
// }





package yaku.uxntal;

import java.util.Objects;
import yaku.uxntal.Definitions.TokenType;

/**
 * 完全还原 Perl 的 Token 结构
 */
public class Token {
    public TokenType type;
    public String value;     // 指令名、标签名、引用名、内容
    public int size;         // 数据字节、模式、标签/引用种类
    public int stack;        // r
    public int keep;         // k
    public int refType;      // 引用类型
    public int isChild;      // 是否子标签/引用
    public int line;         // 行号
    public boolean group = false; // 是否为 token group（如lambda/字符串等）
    private Token[] groupTokens = null;

    // 最常用构造器：Instr/Label/Ref等
    public Token(TokenType type, String value, int size, int stack, int keep, int line) {
        this.type = type;
        this.value = value;
        this.size = size;
        this.stack = stack;
        this.keep = keep;
        this.line = line;
    }
    // 构造 for LIT/RAW
    public Token(TokenType type, String value, int size, int line) {
        this(type, value, size, 0, 0, line);
    }
    // 构造 for simple（如 MAIN/ADDR/PAD/UNKNOWN/EMPTY）
    public Token(TokenType type, String value, int line) {
        this(type, value, 0, 0, 0, line);
    }
    // 构造 for REF（含refType/isChild）
    public Token(TokenType type, String value, int refType, int isChild, int line) {
        this(type, value, 0, 0, 0, line);
        this.refType = refType;
        this.isChild = isChild;
    }
    // 构造 group tokens
    public Token(Token[] groupTokens) {
        this.group = true;
        this.groupTokens = groupTokens;
    }
    public boolean isGroup() {
        return group;
    }
    public Token[] getGroup() {
        return groupTokens;
    }

    // Perl中的各种类型判别/isXxx方法
    public boolean isInstr() { return type == TokenType.INSTR; }
    public boolean isLit() { return type == TokenType.LIT; }
    public boolean isRaw() { return type == TokenType.RAW; }
    public boolean isLabel() { return type == TokenType.LABEL; }
    public boolean isRef() { return type == TokenType.REF; }
    public boolean isParentLabel() { return isLabel() && size == 2; }
    public boolean isChildLabel() { return isLabel() && size == 1; }
    public boolean isParentRef() { return isRef() && isChild == 0; }
    public boolean isChildRef() { return isRef() && isChild == 1; }
    public boolean hasKeep() { return keep == 1; }
    public boolean hasName(String name) { return value.equals(name); }

    // 便于Token间比较（tokensAreEqual/tokenEqual等）
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Token)) return false;
        Token t = (Token) o;
        return type == t.type &&
                Objects.equals(value, t.value) &&
                size == t.size &&
                stack == t.stack &&
                keep == t.keep &&
                refType == t.refType &&
                isChild == t.isChild &&
                line == t.line;
    }
    @Override
    public int hashCode() {
        return Objects.hash(type, value, size, stack, keep, refType, isChild, line);
    }
    @Override
    public String toString() {
        if (group) return "TokenGroup(" + java.util.Arrays.toString(groupTokens) + ")";
        return String.format("Token{%s, '%s', sz=%d, r=%d, k=%d, refType=%d, child=%d, line=%d}",
                type, value, size, stack, keep, refType, isChild, line);
    }
}

