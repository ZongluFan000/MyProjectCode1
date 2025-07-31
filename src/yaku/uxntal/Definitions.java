package yaku.uxntal;

import java.util.*;



public class Definitions {
    public enum TokenType {
        MAIN, LIT, INSTR, LABEL, REF, IREF, RAW, ADDR, PAD, LAMBDA, EMPTY,
        UNKNOWN, PLACEHOLDER, INCLUDE, STR, LD, ST
    }

    // Token type 
    public static final List<String> TOKEN_TYPE_NAMES = Arrays.asList(
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


    public static final int MAIN_ADDRESS = 0x0100;
    public static final int MEMORY_SIZE = 0x10000; // 64K

    // Tools and methodologies
    public static boolean isOpcode(String name) {
        return OPCODE_SET.contains(name.toUpperCase());
    }
    public static boolean isRefPrefix(String s) {
        return REF_TYPE_MAP.containsKey(s);
    }

    // static {
    //     System.out.println("JMI支持检测：" + OPCODE_MAP.get("JMI"));
    //     // ...后面原有内容...
    // }
}
