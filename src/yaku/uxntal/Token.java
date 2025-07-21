package yaku.uxntal;

import yaku.uxntal.Definitions.TokenType;

public class Token {
    public TokenType type;     // Token 类型
    public String value;       // 指令名、标签名、引用名、数据内容等
    public int size;           // 通常是数据/指令字节数、引用模式、或辅助标志
    public int line;           // 源代码行号（方便错误定位）

    // 构造方法
    public Token(TokenType type, String value, int size, int line) {
        this.type = type;
        this.value = value;
        this.size = size;
        this.line = line;
    }

    public Token(TokenType type, String value) {
        this(type, value, 0, -1);
    }

    public Token(TokenType type) {
        this(type, "", 0, -1);
    }

    // 调试用字符串
    @Override
    public String toString() {
        return String.format("Token{%s, '%s', sz=%d, line=%d}", type, value, size, line);
    }
}
