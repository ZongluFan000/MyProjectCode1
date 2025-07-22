// package yaku.uxntal;

// import java.util.*;

// public class Definitions {
//     // Token type definition
//     public enum TokenType {
//         MAIN,      // Main Program Entry
//         INSTR,     // Directives
//         LIT,       // stack constant 
//         LABEL,     //  
//         REF,       // 
//         RAW,       // 
//         ADDR,      // assembly starting address
//         PAD,       // 填充
//         EMPTY,     // 空（未用）
//         UNKNOWN    // 未知
//     }

//     // Uxntal 指令全集（32条助记符）
//     public static final Set<String> OPCODES = new HashSet<>(Arrays.asList(
//         "BRK", "INC", "POP", "NIP", "SWP", "ROT", "DUP", "OVR",
//         "EQU", "NEQ", "GTH", "LTH", "JMP", "JCN", "JSR", "STH",
//         "LDZ", "STZ", "LDR", "STR", "LDA", "STA", "DEI", "DEO",
//         "ADD", "SUB", "MUL", "DIV", "AND", "ORA", "EOR", "SFT",

//         "LIT"

//     ));

//     // 指令助记符到指令码（opcode）的映射表
//     public static final Map<String, Integer> OPCODE_MAP = new HashMap<>();
//     static {
//         OPCODE_MAP.put("BRK", 0x00); OPCODE_MAP.put("INC", 0x01); OPCODE_MAP.put("POP", 0x02); OPCODE_MAP.put("NIP", 0x03);
//         OPCODE_MAP.put("SWP", 0x04); OPCODE_MAP.put("ROT", 0x05); OPCODE_MAP.put("DUP", 0x06); OPCODE_MAP.put("OVR", 0x07);
//         OPCODE_MAP.put("EQU", 0x08); OPCODE_MAP.put("NEQ", 0x09); OPCODE_MAP.put("GTH", 0x0A); OPCODE_MAP.put("LTH", 0x0B);
//         OPCODE_MAP.put("JMP", 0x0C); OPCODE_MAP.put("JCN", 0x0D); OPCODE_MAP.put("JSR", 0x0E); OPCODE_MAP.put("STH", 0x0F);
//         OPCODE_MAP.put("LDZ", 0x10); OPCODE_MAP.put("STZ", 0x11); OPCODE_MAP.put("LDR", 0x12); OPCODE_MAP.put("STR", 0x13);
//         OPCODE_MAP.put("LDA", 0x14); OPCODE_MAP.put("STA", 0x15); OPCODE_MAP.put("DEI", 0x16); OPCODE_MAP.put("DEO", 0x17);
//         OPCODE_MAP.put("ADD", 0x18); OPCODE_MAP.put("SUB", 0x19); OPCODE_MAP.put("MUL", 0x1A); OPCODE_MAP.put("DIV", 0x1B);
//         OPCODE_MAP.put("AND", 0x1C); OPCODE_MAP.put("ORA", 0x1D); OPCODE_MAP.put("EOR", 0x1E); OPCODE_MAP.put("SFT", 0x1F);


//         OPCODE_MAP.put("LIT", 0x80); // 0x80 是 Uxn 标准LIT指令
//     }

//     // 引用类型
//     public enum RefType {
//         ZPAGE,    // .  零页
//         REL,      // ,  相对
//         ABS,      // ;  绝对
//         CHILD,    // _  子标签
//         NEG,      // -  负号/特殊
//         EQ,       // =  等号/特殊
//         IMMED     // ？/！直接跳转立即数
//     }

//     // 引用前缀到类型的映射
//     public static final Map<String, RefType> REF_TYPE_MAP = new HashMap<>();
//     static {
//         REF_TYPE_MAP.put(".", RefType.ZPAGE);
//         REF_TYPE_MAP.put(",", RefType.REL);
//         REF_TYPE_MAP.put(";", RefType.ABS);
//         REF_TYPE_MAP.put("_", RefType.CHILD);
//         REF_TYPE_MAP.put("-", RefType.NEG);
//         REF_TYPE_MAP.put("=", RefType.EQ);
//     }

//     // 主程序默认入口地址
//     public static final int MAIN_ADDRESS = 0x0100;

//     // 内存参数
//     public static final int MEMORY_SIZE = 0x10000; // 64K

//     // 判断是否为有效指令名
//     public static boolean isOpcode(String name) {
//         return OPCODES.contains(name.toUpperCase());
//     }

//     // 判断字符串是否为有效引用前缀
//     public static boolean isRefPrefix(String s) {
//         return REF_TYPE_MAP.containsKey(s);
//     }
// }




package yaku.uxntal;

import java.util.*;

public class Definitions {
    public enum TokenType {
        MAIN, LIT, INSTR, LABEL, REF, IREF, RAW, ADDR, PAD, LAMBDA, EMPTY,
        UNKNOWN, PLACEHOLDER, INCLUDE, STR, LD, ST
    }

    // Token type 字符串到枚举的映射
    public static final List<String> TOKEN_TYPE_NAMES = Arrays.asList(
        "MAIN","LIT","INSTR","LABEL","REF","IREF","RAW","ADDR","PAD",
        "LAMBDA","EMPTY","UNKNOWN","PLACEHOLDER","INCLUDE","STR","LD","ST"
    );

    // 引用类型定义
    public enum RefType {
        DOT, COMMA, SEMI, DASH, UNDERSCORE, EQUALS, IMMED
    }

    public static final Map<String, Integer> REF_TYPE_MAP = new HashMap<>();
    static {
        REF_TYPE_MAP.put(".", 0); // dot/zeropage
        REF_TYPE_MAP.put(",", 1); // comma/rel
        REF_TYPE_MAP.put(";", 2); // semi/abs
        REF_TYPE_MAP.put("-", 3); // dash/raw zero
        REF_TYPE_MAP.put("_", 4); // underscore/raw rel
        REF_TYPE_MAP.put("=", 5); // equals/raw abs
        REF_TYPE_MAP.put("I", 6); // immediate
    }
    public static final String[] REV_REF_TYPES = {".",",",";","-","_","=","I"};
    public static final int[] REF_WORD_SIZES = {1,1,2,1,1,1,2};

    // 指令全集
    public static final Set<String> OPCODE_SET = new HashSet<>(Arrays.asList(
        "BRK","LIT","INC","POP","NIP","SWP","ROT","DUP","OVR",
        "EQU","NEQ","GTH","LTH","JMP","JCN","JSR","STH",
        "LDZ","STZ","LDR","STR","LDA","STA","DEI","DEO",
        "ADD","SUB","MUL","DIV","AND","ORA","EOR","SFT"
    ));

    // 指令名到操作码
    public static final Map<String, Integer> OPCODE_MAP = new HashMap<>();
    static {
        String[] opcodes = {
            "BRK","INC","POP","NIP","SWP","ROT","DUP","OVR",
            "EQU","NEQ","GTH","LTH","JMP","JCN","JSR","STH",
            "LDZ","STZ","LDR","STR","LDA","STA","DEI","DEO",
            "ADD","SUB","MUL","DIV","AND","ORA","EOR","SFT","LIT"
        };
        int[] codes = {
            0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,
            0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F,
            0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17,
            0x18,0x19,0x1A,0x1B,0x1C,0x1D,0x1E,0x1F,0x80
        };
        for (int i=0; i<opcodes.length; ++i) {
            OPCODE_MAP.put(opcodes[i], codes[i]);
        }
    }

    // 指令分类（举例，需根据 Perl 补全）
    public static final Set<String> COMMUTATIVE_BINARY_OPS = new HashSet<>(Arrays.asList(
        "ADD","MUL","AND","ORA","EOR","NEQ","EQU"
    ));
    public static final Set<String> ALU_OPS = new HashSet<>();
    static {
        ALU_OPS.addAll(COMMUTATIVE_BINARY_OPS);
        ALU_OPS.addAll(Arrays.asList("INC","DIV","SUB","SFT","LTH","GTH"));
    }
    public static final Set<String> CMP_OPS = new HashSet<>(Arrays.asList(
        "EQU","NEQ","GTH","LTH"
    ));
    public static final Set<String> JUMP_OPS = new HashSet<>(Arrays.asList(
        "JCI","JMI","JMP","JCN","JSI","JSR"
    ));
    public static final Set<String> STACK_OPS = new HashSet<>(Arrays.asList(
        "POP","NIP","SWP","ROT","DUP","OVR","STH"
    ));

    // 其它参数
    public static final int MAIN_ADDRESS = 0x0100;
    public static final int MEMORY_SIZE = 0x10000; // 64K

    // 工具方法
    public static boolean isOpcode(String name) {
        return OPCODE_SET.contains(name.toUpperCase());
    }
    public static boolean isRefPrefix(String s) {
        return REF_TYPE_MAP.containsKey(s);
    }
}
