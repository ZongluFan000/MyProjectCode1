package yaku.uxntal;

import java.util.*;

public class Definitions {
    // Token type definition
    public enum TokenType {
        MAIN,      // Main Program Entry
        INSTR,     // Directives
        LIT,       // stack constant 
        LABEL,     //  
        REF,       // 
        RAW,       // 
        ADDR,      // assembly starting address
        PAD,       // 填充
        EMPTY,     // 空（未用）
        UNKNOWN    // 未知
    }

    // Uxntal 指令全集（32条助记符）
    public static final Set<String> OPCODES = new HashSet<>(Arrays.asList(
        "BRK", "INC", "POP", "NIP", "SWP", "ROT", "DUP", "OVR",
        "EQU", "NEQ", "GTH", "LTH", "JMP", "JCN", "JSR", "STH",
        "LDZ", "STZ", "LDR", "STR", "LDA", "STA", "DEI", "DEO",
        "ADD", "SUB", "MUL", "DIV", "AND", "ORA", "EOR", "SFT",

        "LIT"

    ));

    // 指令助记符到指令码（opcode）的映射表
    public static final Map<String, Integer> OPCODE_MAP = new HashMap<>();
    static {
        OPCODE_MAP.put("BRK", 0x00); OPCODE_MAP.put("INC", 0x01); OPCODE_MAP.put("POP", 0x02); OPCODE_MAP.put("NIP", 0x03);
        OPCODE_MAP.put("SWP", 0x04); OPCODE_MAP.put("ROT", 0x05); OPCODE_MAP.put("DUP", 0x06); OPCODE_MAP.put("OVR", 0x07);
        OPCODE_MAP.put("EQU", 0x08); OPCODE_MAP.put("NEQ", 0x09); OPCODE_MAP.put("GTH", 0x0A); OPCODE_MAP.put("LTH", 0x0B);
        OPCODE_MAP.put("JMP", 0x0C); OPCODE_MAP.put("JCN", 0x0D); OPCODE_MAP.put("JSR", 0x0E); OPCODE_MAP.put("STH", 0x0F);
        OPCODE_MAP.put("LDZ", 0x10); OPCODE_MAP.put("STZ", 0x11); OPCODE_MAP.put("LDR", 0x12); OPCODE_MAP.put("STR", 0x13);
        OPCODE_MAP.put("LDA", 0x14); OPCODE_MAP.put("STA", 0x15); OPCODE_MAP.put("DEI", 0x16); OPCODE_MAP.put("DEO", 0x17);
        OPCODE_MAP.put("ADD", 0x18); OPCODE_MAP.put("SUB", 0x19); OPCODE_MAP.put("MUL", 0x1A); OPCODE_MAP.put("DIV", 0x1B);
        OPCODE_MAP.put("AND", 0x1C); OPCODE_MAP.put("ORA", 0x1D); OPCODE_MAP.put("EOR", 0x1E); OPCODE_MAP.put("SFT", 0x1F);


        OPCODE_MAP.put("LIT", 0x80); // 0x80 是 Uxn 标准LIT指令
    }

    // 引用类型
    public enum RefType {
        ZPAGE,    // .  零页
        REL,      // ,  相对
        ABS,      // ;  绝对
        CHILD,    // _  子标签
        NEG,      // -  负号/特殊
        EQ,       // =  等号/特殊
        IMMED     // ？/！直接跳转立即数
    }

    // 引用前缀到类型的映射
    public static final Map<String, RefType> REF_TYPE_MAP = new HashMap<>();
    static {
        REF_TYPE_MAP.put(".", RefType.ZPAGE);
        REF_TYPE_MAP.put(",", RefType.REL);
        REF_TYPE_MAP.put(";", RefType.ABS);
        REF_TYPE_MAP.put("_", RefType.CHILD);
        REF_TYPE_MAP.put("-", RefType.NEG);
        REF_TYPE_MAP.put("=", RefType.EQ);
    }

    // 主程序默认入口地址
    public static final int MAIN_ADDRESS = 0x0100;

    // 内存参数
    public static final int MEMORY_SIZE = 0x10000; // 64K

    // 判断是否为有效指令名
    public static boolean isOpcode(String name) {
        return OPCODES.contains(name.toUpperCase());
    }

    // 判断字符串是否为有效引用前缀
    public static boolean isRefPrefix(String s) {
        return REF_TYPE_MAP.containsKey(s);
    }
}
