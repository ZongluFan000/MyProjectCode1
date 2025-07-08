package yaku.uxntal.model;

// import java.util.Arrays;


import java.util.HashMap;
import java.util.Map;

public class Token {
    // Token类型枚举
    public static final int MAIN = 0;
    public static final int LIT = 1;
    public static final int INSTR = 2;
    public static final int LABEL = 3;
    public static final int REF = 4;
    public static final int IREF = 5;
    public static final int RAM = 6;
    public static final int ADDR = 7;
    public static final int PAD = 8;
    public static final int EMPTY = 10;
    public static final int UNKNOWN = 11;
    public static final int INCLUDE = 13;
    public static final int STR = 14;
    public static final int LD = 15;
    public static final int ST = 16;
    public static final int PLACEHOLDER = 12;

    // 引用类型映射
    public static final Map<String, Integer> REF_TYPES = new HashMap<>();
    static {
        REF_TYPES.put(".", 0);
        REF_TYPES.put(",", 1);
        REF_TYPES.put(";", 2);
        REF_TYPES.put("-", 3);
        REF_TYPES.put("_", 4);
        REF_TYPES.put("=", 5);
        REF_TYPES.put("I", 6);
    }

    // 引用类型反向映射
    public static final String[] REV_REF_TYPES = {
        ".", ",", ";", "-", "_", "=", "I"
    };

    // 引用字大小
    public static final int[] REF_WORD_SIZES = {
        1, 1, 2, 1, 1, 1, 2
    };

    // 操作码映射
    public static final Map<String, Integer> OPCODES = new HashMap<>();
    static {
        String[] opcodes = {
            "BRK", "LIT", "INC", "POP", "NIP", "SWP", "ROT", "DUP", "OVR",
            "EQU", "NEQ", "GTH", "LTH", "JMP", "JCN", "JSR", "STH",
            "LDZ", "STZ", "LDR", "STR", "LDA", "STA", "DEI", "DEO",
            "ADD", "SUB", "MUL", "DIV", "AND", "ORA", "EOR", "SFT"
        };
        for (int i = 0; i < opcodes.length; i++) {
            OPCODES.put(opcodes[i], i);
        }
    }

    private int type;
    private Object value;
    private int wordSize;
    private int refType;
    private int isChild;
    private int stackMode;
    private int keepMode;
    private String stringValue;

    public Token(int type) {
        this.type = type;
    }

    public Token(int type, Object value) {
        this.type = type;
        this.value = value;
    }

    public Token(int type, Object value, int wordSize) {
        this.type = type;
        this.value = value;
        this.wordSize = wordSize;
    }

    public Token(int type, Object value, int wordSize, int stackMode, int keepMode) {
        this.type = type;
        this.value = value;
        this.wordSize = wordSize;
        this.stackMode = stackMode;
        this.keepMode = keepMode;
    }

    // Getters
    public int getType() { return type; }
    public Object getValue() { return value; }
    public int getWordSize() { return wordSize; }
    public int getRefType() { return refType; }
    public int getIsChild() { return isChild; }
    public int getStackMode() { return stackMode; }
    public int getKeepMode() { return keepMode; }
    public String getStringValue() { return stringValue; }

    // Setters
    public void setType(int type) { this.type = type; }
    public void setValue(Object value) { this.value = value; }
    public void setWordSize(int wordSize) { this.wordSize = wordSize; }
    public void setRefType(int refType) { this.refType = refType; }
    public void setIsChild(int isChild) { this.isChild = isChild; }
    public void setStackMode(int stackMode) { this.stackMode = stackMode; }
    public void setKeepMode(int keepMode) { this.keepMode = keepMode; }
    public void setStringValue(String stringValue) { this.stringValue = stringValue; }

    // 类型检查方法
    public boolean isMain() { return type == MAIN; }
    public boolean isLit() { return type == LIT; }
    public boolean isInstr() { return type == INSTR; }
    public boolean isLabel() { return type == LABEL; }
    public boolean isRef() { return type == REF; }
    public boolean isRam() { return type == RAM; }
    public boolean isAddr() { return type == ADDR; }
    public boolean isEmpty() { return type == EMPTY; }

    // 操作码相关方法
    public static int getOpcode(String instr) {
        return OPCODES.getOrDefault(instr, -1);
    }

    public static boolean isOpcode(String token) {
        return OPCODES.containsKey(token);
    }

    @Override
    public String toString() {
        return "Token{" +
                "type=" + type +
                ", value=" + value +
                ", wordSize=" + wordSize +
                ", refType=" + refType +
                ", isChild=" + isChild +
                ", stackMode=" + stackMode +
                ", keepMode=" + keepMode +
                '}';
    }
}