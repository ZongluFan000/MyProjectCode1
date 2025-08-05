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


        public static final Map<String, Integer> OPCODE_MAP = new HashMap<>();
        static {
            // -- Stack --
            OPCODE_MAP.put("BRK", 0x00);
            OPCODE_MAP.put("LIT", 0x80);
            OPCODE_MAP.put("INC", 0x08);
            OPCODE_MAP.put("POP", 0x10);
            OPCODE_MAP.put("NIP", 0x18);
            OPCODE_MAP.put("SWP", 0x20);
            OPCODE_MAP.put("ROT", 0x28);
            OPCODE_MAP.put("DUP", 0x30);
            OPCODE_MAP.put("OVR", 0x38);

            // -- Logic --
            OPCODE_MAP.put("EQU", 0x40);
            OPCODE_MAP.put("NEQ", 0x48);
            OPCODE_MAP.put("GTH", 0x50);
            OPCODE_MAP.put("LTH", 0x58);

            // -- Flow --
            OPCODE_MAP.put("JMP", 0x60);
            OPCODE_MAP.put("JCN", 0x68);
            OPCODE_MAP.put("JSR", 0x70);
            OPCODE_MAP.put("STH", 0x78);

            // -- Memory --
            OPCODE_MAP.put("LDZ", 0x80);
            OPCODE_MAP.put("STZ", 0x88);
            OPCODE_MAP.put("LDR", 0x90);
            OPCODE_MAP.put("STR", 0x98);
            OPCODE_MAP.put("LDA", 0xA0);
            OPCODE_MAP.put("STA", 0xA8);

            // -- Devices --
            OPCODE_MAP.put("DEI", 0xB0);
            OPCODE_MAP.put("DEO", 0xB8);

            // -- Math --
            OPCODE_MAP.put("ADD", 0xC0);
            OPCODE_MAP.put("SUB", 0xC8);
            OPCODE_MAP.put("MUL", 0xD0);
            OPCODE_MAP.put("DIV", 0xD8);
            OPCODE_MAP.put("AND", 0xE0);
            OPCODE_MAP.put("ORA", 0xE8);
            OPCODE_MAP.put("EOR", 0xF0);
            OPCODE_MAP.put("SFT", 0xF8);


            
            OPCODE_MAP.put("LIT2", 0xA0);
            

        }

        // 其它结构保留不变（分类/签名/辅助函数）

        // directives
        public static final Set<String> OPCODE_SET = new HashSet<>(Arrays.asList(
            "BRK","LIT","INC","POP","NIP","SWP","ROT","DUP","OVR",
            "EQU","NEQ","GTH","LTH","JMP","JCN","JSR","STH",
            "LDZ","STZ","LDR","STR","LDA","STA","DEI","DEO",
            "ADD","SUB","MUL","DIV","AND","ORA","EOR","SFT",

            "JSI","JCI","JMI"


        ));

        // Instruction name to opcode

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
