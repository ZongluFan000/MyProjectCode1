package yaku.uxntal;

import java.util.*;



public class Definitions {
    public enum TokenType {
        MAIN, LIT, INSTR, LABEL, REF, IREF, RAW, ADDR, PAD, LAMBDA, EMPTY,
        UNKNOWN, PLACEHOLDER, INCLUDE, STR, LD, ST
    }

    // Token type 
    public static final List<String> TOKEN_TYPE_NAMES = List.of(
        "MAIN","LIT","INSTR","LABEL","REF","IREF","RAW","ADDR","PAD",
        "LAMBDA","EMPTY","UNKNOWN","PLACEHOLDER","INCLUDE","STR","LD","ST"
    );

    // Reference Type
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

    // directives
    public static final Set<String> OPCODE_SET = new HashSet<>(Arrays.asList(
        "BRK","LIT","INC","POP","NIP","SWP","ROT","DUP","OVR",
        "EQU","NEQ","GTH","LTH","JMP","JCN","JSR","STH",
        "LDZ","STZ","LDR","STR","LDA","STA","DEI","DEO",
        "ADD","SUB","MUL","DIV","AND","ORA","EOR","SFT",

        "JSI","JCI","JMI"


    ));

    // Instruction name to opcode
    public static final Map<String, Integer> OPCODE_MAP = new HashMap<>();
    static {
        String[] opcodes = {
            "BRK","INC","POP","NIP","SWP","ROT","DUP","OVR",
            "EQU","NEQ","GTH","LTH","JMP","JCN","JSR","STH",
            "LDZ","STZ","LDR","STR","LDA","STA","DEI","DEO",
            "ADD","SUB","MUL","DIV","AND","ORA","EOR","SFT","LIT",

            "JSI","JCI","JMI"

        };
        int[] codes = {
            0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,
            0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F,
            0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17,
            0x18,0x19,0x1A,0x1B,0x1C,0x1D,0x1E,0x1F,0x80,

            0x2E,0x2D, // 新增，对应JSI和JCI

            0x3C


        };
        for (int i=0; i<opcodes.length; ++i) {
            OPCODE_MAP.put(opcodes[i], codes[i]);
        }
    }

    // Classification of commands (according to Perl)
    public static final Set<String> COMMUTATIVE_BINARY_OPS = new HashSet<>(Arrays.asList(
        "ADD","MUL","AND","ORA","EOR","NEQ","EQU"
    ));

    public static final Set<String> CMP_OPS = new HashSet<>(Arrays.asList(
        "EQU","NEQ","GTH","LTH"
    ));
    public static final Set<String> JUMP_OPS = new HashSet<>(Arrays.asList(
        "JCI","JMI","JMP","JCN","JSI","JSR"
    ));
    public static final Set<String> STACK_OPS = new HashSet<>(Arrays.asList(
        "POP","NIP","SWP","ROT","DUP","OVR","STH"
    ));
    public static final Set<String> ALU_OPS = new HashSet<>();
    static {
        ALU_OPS.addAll(COMMUTATIVE_BINARY_OPS);
        ALU_OPS.addAll(Arrays.asList("INC","DIV","SUB","SFT","LTH","GTH"));
    }



    
    public static final Map<String, int[]> STACK_OP_SIGNATURES = Map.ofEntries(
        Map.entry("POP", new int[]{1, 0}),
        Map.entry("NIP", new int[]{2, 1}),
        Map.entry("SWP", new int[]{2, 2}),
        Map.entry("ROT", new int[]{3, 3}),
        Map.entry("DUP", new int[]{1, 2}),
        Map.entry("OVR", new int[]{2, 3}),
        Map.entry("STH", new int[]{1, 0})
    );
    /** 比较操作签名: 指令 -> [in, out] */
    public static final Map<String, int[]> CMP_OP_SIGNATURES = Map.ofEntries(
        Map.entry("EQU", new int[]{2, 1}),
        Map.entry("NEQ", new int[]{2, 1}),
        Map.entry("GTH", new int[]{2, 1}),
        Map.entry("LTH", new int[]{2, 1})
    );
    /** ALU/非堆栈操作签名: 指令 -> [in, out] */
    public static final Map<String, int[]> NONSTACK_OP_SIGNATURES = Map.ofEntries(
        Map.entry("INC", new int[]{1, 1}),
        Map.entry("DEO", new int[]{2, 0}),
        Map.entry("DEI", new int[]{1, 1}),
        Map.entry("ADD", new int[]{2, 1}),
        Map.entry("SUB", new int[]{2, 1}),
        Map.entry("MUL", new int[]{2, 1}),
        Map.entry("DIV", new int[]{2, 1}),
        Map.entry("AND", new int[]{2, 1}),
        Map.entry("ORA", new int[]{2, 1}),
        Map.entry("EOR", new int[]{2, 1}),
        Map.entry("SFT", new int[]{2, 1})
    );
    /** 内存相关操作（对照 JS 的 memOperations）: 指令 -> [addr_sz, in, out] */
    public static final Map<String, int[]> MEM_OP_SIGNATURES = Map.ofEntries(
        Map.entry("LDA", new int[]{2, 0, 1}),
        Map.entry("STA", new int[]{2, 1, 0}),
        Map.entry("LDR", new int[]{1, 0, 1}),
        Map.entry("STR", new int[]{1, 1, 0}),
        Map.entry("LDZ", new int[]{1, 0, 1}),
        Map.entry("STZ", new int[]{1, 1, 0})
    );

    /** 主程序入口地址、内存大小等 */
    public static final int MAIN_ADDRESS = 0x0100;
    public static final int MEMORY_SIZE = 0x10000;

    // ======= 辅助函数/判别方法（和 JS 功能完全等价）=======

    public static boolean isOpcode(String name) {
        return OPCODE_SET.contains(name != null ? name.toUpperCase() : "");
    }
    public static boolean isRefPrefix(String s) {
        return REF_TYPE_MAP.containsKey(s);
    }

    /** 判别 token 是否为指定类型（适用于 List<Object> 结构） */
    public static boolean isTokenType(List<?> token, TokenType type) {
        return token != null && !token.isEmpty() && token.get(0) == type;
    }
    // 可根据需要扩展更多 isXXX(token, type) 判别

    // 空 token 常量（如需要 List<Object> 结构支持）
    public static final List<Object> EMPTY_TOKEN = List.of(TokenType.EMPTY, 0, 1);

}
