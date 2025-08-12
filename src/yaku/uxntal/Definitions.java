package yaku.uxntal;

import java.util.*;
import java.util.stream.Collectors;

public class Definitions {

    //Token & Ref Types

    public enum TokenType {
        MAIN, LIT, INSTR, LABEL, REF, IREF, RAW, ADDR, PAD, LAMBDA, EMPTY,
        UNKNOWN, PLACEHOLDER, INCLUDE, STR, LD, ST
    }

    public static final List<String> TOKEN_TYPE_NAMES = List.of(
        "MAIN","LIT","INSTR","LABEL","REF","IREF","RAW","ADDR","PAD",
        "LAMBDA","EMPTY","UNKNOWN","PLACEHOLDER","INCLUDE","STR","LD","ST"
    );

    public enum RefType { DOT, COMMA, SEMI, DASH, UNDERSCORE, EQUALS, IMMED }

    public static final Map<String, Integer> REF_TYPE_MAP = new HashMap<>();
    static {
        REF_TYPE_MAP.put(".", 0);
        REF_TYPE_MAP.put(",", 1);
        REF_TYPE_MAP.put(";", 2);
        REF_TYPE_MAP.put("-", 3);
        REF_TYPE_MAP.put("_", 4);
        REF_TYPE_MAP.put("=", 5);
        REF_TYPE_MAP.put("I", 6);
    }

    public static final String[] REV_REF_TYPES = {".", ",", ";", "-", "_", "=", "I"};
    public static final int[] REF_WORD_SIZES   = { 1,   1,   2,   1,   1,   1,   2 };

    //Memory & Addresses

    public static final int MAIN_ADDRESS = 0x0100;
    public static final int MEMORY_SIZE  = 0x10000;

    //Opcode Base

    public static final Map<String, Integer> BASE_OPCODE_MAP = new HashMap<>();
    static {
        String[] baseOps = {
            "BRK",/*"LIT",*/"INC","POP","NIP","SWP","ROT","DUP","OVR",
            "EQU","NEQ","GTH","LTH","JMP","JCN","JSR","STH",
            "LDZ","STZ","LDR","STR","LDA","STA","DEI","DEO",
            "ADD","SUB","MUL","DIV","AND","ORA","EOR","SFT"
        };
        for (int i = 0; i < baseOps.length; i++) BASE_OPCODE_MAP.put(baseOps[i], i);
        BASE_OPCODE_MAP.put("BRK", 0x00);
        BASE_OPCODE_MAP.put("LIT", 0x80);
        BASE_OPCODE_MAP.put("JCI", 0x20);
        BASE_OPCODE_MAP.put("JMI", 0x40);
        BASE_OPCODE_MAP.put("JSI", 0x60);
    }

    //Mode Bits

    public static final int MODE_KEEP   = 0x10; // k
    public static final int MODE_SHORT  = 0x20; // s
    public static final int MODE_RETURN = 0x40; // r

    //Encode helpers

    
    public static int getOpcodeByte(String name, boolean shortMode, boolean returnMode, boolean keepMode) {
        String n = name.toUpperCase();
        Integer base = BASE_OPCODE_MAP.get(n);
        if (base == null) throw new RuntimeException("Unknown base opcode: " + name);

        int opcode;
        if (n.equals("BRK") || n.equals("JCI") || n.equals("JMI") || n.equals("JSI")) {
            opcode = base;
        } else if (n.equals("LIT")) {
            opcode = 0x80;
            if (shortMode)  opcode |= MODE_SHORT;
            if (returnMode) opcode |= MODE_RETURN;
            // keepMode ignored for LIT
        } else {
            opcode = base; // ← 普通指令低 5 位就是基础码，不需要左移
            if (shortMode)  opcode |= MODE_SHORT;
            if (returnMode) opcode |= MODE_RETURN;
            if (keepMode)   opcode |= MODE_KEEP;
        }
        return opcode & 0xFF;
    }

    public static int getOpcodeByte(String name, int size, int r, int k) {
        return getOpcodeByte(name, size == 2, r != 0, k != 0);
    }

    
    public static int getOpcode(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Opcode name is empty");
        }
        String key = normalizeInstrName(name);

        // 立即/特殊（不带模式位）
        if ("JCI".equals(key)) return 0x20;
        if ("JMI".equals(key)) return 0x40;
        if ("JSI".equals(key)) return 0x60;
        if ("LIT".equals(key)) return 0x80;

        Integer v = BASE_OPCODE_MAP.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Unknown opcode: " + name + " (normalized: " + key + ")");
        }
        // 对普通指令，这里应返回低 5 位基础码（0..31）
        // BASE_OPCODE_MAP 中普通指令存放的就是索引 0..31
        // （BRK 为 0 也符合该约定）
        return v & 0x1F;
    }

    /** 归一化指令名：
     * 大写
     * '?' -> JCI, '!' -> JMI
     *  去掉末尾模式后缀字符集合 { '2','K','R' }（任意顺序与数量）
     */
    private static String normalizeInstrName(String name) {
        String n = name.trim().toUpperCase(Locale.ROOT);
        if ("?".equals(n)) return "JCI";
        if ("!".equals(n)) return "JMI";
        while (!n.isEmpty()) {
            char c = n.charAt(n.length() - 1);
            if (c == '2' || c == 'K' || c == 'R') {
                n = n.substring(0, n.length() - 1);
            } else break;
        }
        return n;
    }

    //Sets & Signatures（保留）

    public static final Set<String> OPCODE_SET = new HashSet<>(Arrays.asList(
        "BRK","LIT","INC","POP","NIP","SWP","ROT","DUP","OVR",
        "EQU","NEQ","GTH","LTH","JMP","JCN","JSR","STH",
        "LDZ","STZ","LDR","STR","LDA","STA","DEI","DEO",
        "ADD","SUB","MUL","DIV","AND","ORA","EOR","SFT",
        "JSI","JCI","JMI"
    ));

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

    // 仅名字集合；保留以兼容其它模块
    public static final Set<String> ALU_OPS = new HashSet<>();
    static {
        ALU_OPS.addAll(COMMUTATIVE_BINARY_OPS);
        ALU_OPS.addAll(Arrays.asList("INC","DIV","SUB","SFT","LTH","GTH"));
    }

    // 各类签名（分析/静态检查用，解释器不直接用）
    public static final Map<String, int[]> STACK_OP_SIGNATURES = Map.ofEntries(
        Map.entry("POP", new int[]{1, 0}),
        Map.entry("NIP", new int[]{2, 1}),
        Map.entry("SWP", new int[]{2, 2}),
        Map.entry("ROT", new int[]{3, 3}),
        Map.entry("DUP", new int[]{1, 2}),
        Map.entry("OVR", new int[]{2, 3}),
        Map.entry("STH", new int[]{1, 0})
    );
    public static final Map<String, int[]> CMP_OP_SIGNATURES = Map.ofEntries(
        Map.entry("EQU", new int[]{2, 1}),
        Map.entry("NEQ", new int[]{2, 1}),
        Map.entry("GTH", new int[]{2, 1}),
        Map.entry("LTH", new int[]{2, 1})
    );
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
    public static final Map<String, int[]> MEM_OP_SIGNATURES = Map.ofEntries(
        Map.entry("LDA", new int[]{2, 0, 1}),
        Map.entry("STA", new int[]{2, 1, 0}),
        Map.entry("LDR", new int[]{1, 0, 1}),
        Map.entry("STR", new int[]{1, 1, 0}),
        Map.entry("LDZ", new int[]{1, 0, 1}),
        Map.entry("STZ", new int[]{1, 1, 0})
    );
    public static final Map<String, int[]> OPERATIONS = new HashMap<>();
    static {
        OPERATIONS.putAll(STACK_OP_SIGNATURES);
        OPERATIONS.putAll(NONSTACK_OP_SIGNATURES);
        OPERATIONS.putAll(CMP_OP_SIGNATURES);
    }
    public static final Map<String, int[]> BIN_OPS = new HashMap<>();
    static {
        for (String s : COMMUTATIVE_BINARY_OPS) BIN_OPS.put(s, new int[]{2,1});
        for (String s : CMP_OP_SIGNATURES.keySet()) BIN_OPS.put(s, new int[]{2,1});
        BIN_OPS.put("DIV", new int[]{2,1});
    }

    

    // 1位宽整形所需：每条“栈操作”需要的元素个数（字节数 = 个数 * token.size） 
    public static final Map<String, int[]> stack_operations = new HashMap<>();
    static {
        stack_operations.put("DUP", new int[]{1});
        stack_operations.put("POP", new int[]{1});
        stack_operations.put("NIP", new int[]{2});
        stack_operations.put("SWP", new int[]{2});
        stack_operations.put("ROT", new int[]{3});
        stack_operations.put("OVR", new int[]{2});
        stack_operations.put("STH", new int[]{1});
    }

    // 2解释器用 bin_ops.containsKey(name) 来判断是否忽略“期望 byte 却弹出 short”的提示
    public static final Map<String, Boolean> bin_ops = new HashMap<>();
    static {
        for (String k : BIN_OPS.keySet()) bin_ops.put(k, Boolean.TRUE);
    }

    //3) 表驱动 ALU：含 nArgs 与可执行实现
    @FunctionalInterface public interface AluFn { int apply(List<Integer> args, int sz); }
    public static final class AluSpec {
        public final int nArgs; public final AluFn fn;
        public AluSpec(int nArgs, AluFn fn){ this.nArgs=nArgs; this.fn=fn; }
        public int apply(List<Integer> a, int sz){ return fn.apply(a, sz); }
    }
    public static final Map<String, AluSpec> alu_ops = new HashMap<>();
    static {
        // 遮罩
        java.util.function.BiFunction<Integer,Integer,Integer> maskTo =
            (v, sz) -> (sz==2) ? (v & 0xFFFF) : (v & 0xFF);

        // 二元算术/逻辑
        alu_ops.put("ADD", new AluSpec(2, (a,sz)-> maskTo.apply(a.get(0)+a.get(1), sz)));
        alu_ops.put("SUB", new AluSpec(2, (a,sz)-> maskTo.apply(a.get(0)-a.get(1), sz)));
        alu_ops.put("MUL", new AluSpec(2, (a,sz)-> maskTo.apply(a.get(0)*a.get(1), sz)));
        alu_ops.put("DIV", new AluSpec(2, (a,sz)-> {
            int b=a.get(1);
            if ((sz==2 && (b&0xFFFF)==0) || (sz==1 && (b&0xFF)==0)) return 0;
            return (sz==2) ? (((a.get(0)&0xFFFF)/(b&0xFFFF)) & 0xFFFF)
                        : (((a.get(0)&0xFF)  /(b&0xFF))   & 0xFF);
        }));
        alu_ops.put("AND", new AluSpec(2, (a,sz)-> maskTo.apply(a.get(0)&a.get(1), sz)));
        alu_ops.put("ORA", new AluSpec(2, (a,sz)-> maskTo.apply(a.get(0)|a.get(1), sz)));
        alu_ops.put("EOR", new AluSpec(2, (a,sz)-> maskTo.apply(a.get(0)^a.get(1), sz)));

        // 单参
        alu_ops.put("INC", new AluSpec(1, (a,sz)-> maskTo.apply(a.get(0)+1, sz)));

        // 比较（解释器会把结果按 size=1 压栈）
        alu_ops.put("EQU", new AluSpec(2, (a,sz)-> eqMask(a.get(0), a.get(1), sz) ? 1 : 0));
        alu_ops.put("NEQ", new AluSpec(2, (a,sz)-> eqMask(a.get(0), a.get(1), sz) ? 0 : 1));
        alu_ops.put("GTH", new AluSpec(2, (a,sz)-> cmpU(a.get(0),a.get(1),sz)>0 ? 1:0));
        alu_ops.put("LTH", new AluSpec(2, (a,sz)-> cmpU(a.get(0),a.get(1),sz)<0 ? 1:0));

        // SFT: 高4位左移，低4位右移（先左再右）
        alu_ops.put("SFT", new AluSpec(2, (a,sz)-> {
            int spec = a.get(0) & 0xFF, L = (spec>>>4) & 0x0F, R = spec & 0x0F;
            if (sz==2){ int v=a.get(1)&0xFFFF; int out=(v<<L)&0xFFFF; out=(out&0xFFFF)>>>R; return out&0xFFFF; }
            else      { int v=a.get(1)&0xFF;   int out=(v<<L)&0xFF;   out=(out&0xFF)>>>R;   return out&0xFF; }
        }));
    }
    private static boolean eqMask(int a,int b,int sz){ return (sz==2)?((a&0xFFFF)==(b&0xFFFF)):((a&0xFF)==(b&0xFF)); }
    private static int cmpU(int a,int b,int sz){
        return (sz==2)? Integer.compareUnsigned(a&0xFFFF,b&0xFFFF)
                    : Integer.compareUnsigned(a&0xFF,  b&0xFF);
    }

    //Jump helpers

    public static int[] jumpOpArgBytes(String op, int w) {
        String n = op.toUpperCase();
        switch (n) {
            case "JMP": return new int[]{w, 0};
            case "JSR": return new int[]{w, 0};
            case "JCN": return new int[]{w + 1, 0};
            case "JCI": return new int[]{1, 0};
            case "JSI": return new int[]{0, 0};
            case "JMI": return new int[]{0, 0};
            default:    return new int[]{0, 0};
        }
    }

    //Allowed function calls
    public static final class FnSig {
        public final int[] in;  public final int out;
        public FnSig(int[] in, int out) { this.in = in; this.out = out; }
        @Override public String toString() { return "FnSig(in=" + Arrays.toString(in) + ", out=" + out + ")"; }
    }

    public static final Map<String, FnSig> ALLOWED_FUNCTION_CALLS = new LinkedHashMap<>();
    static {
        ALLOWED_FUNCTION_CALLS.put("mul2", new FnSig(new int[]{2,2}, 2));
        ALLOWED_FUNCTION_CALLS.put("div2", new FnSig(new int[]{2,2}, 2));
        ALLOWED_FUNCTION_CALLS.put("neg2", new FnSig(new int[]{2},   2));
        ALLOWED_FUNCTION_CALLS.put("gt2",  new FnSig(new int[]{2,2}, 1));
        ALLOWED_FUNCTION_CALLS.put("gte2", new FnSig(new int[]{2,2}, 1));
        ALLOWED_FUNCTION_CALLS.put("lt2",  new FnSig(new int[]{2,2}, 1));
        ALLOWED_FUNCTION_CALLS.put("lte2", new FnSig(new int[]{2,2}, 1));

        ALLOWED_FUNCTION_CALLS.put("mul",  new FnSig(new int[]{1,1}, 1));
        ALLOWED_FUNCTION_CALLS.put("div",  new FnSig(new int[]{1,1}, 1));
        ALLOWED_FUNCTION_CALLS.put("neg",  new FnSig(new int[]{1},   1));
        ALLOWED_FUNCTION_CALLS.put("gt",   new FnSig(new int[]{1,1}, 1));
        ALLOWED_FUNCTION_CALLS.put("gte",  new FnSig(new int[]{1,1}, 1));
        ALLOWED_FUNCTION_CALLS.put("lt",   new FnSig(new int[]{1,1}, 1));
        ALLOWED_FUNCTION_CALLS.put("lte",  new FnSig(new int[]{1,1}, 1));

        ALLOWED_FUNCTION_CALLS.put("not",        new FnSig(new int[]{1}, 1));
        ALLOWED_FUNCTION_CALLS.put("print-char", new FnSig(new int[]{1}, 0));
    }



    //Token model
    public static final class Token {
        // 基本分类：INSTR / LIT / LABEL / ...
        public TokenType type;

        // 指令名或字面量字符串（例如 "ADD"、"BRK"、标签名等）
        public String value;

        // 指令宽度：1=byte，2=short（对应 s 模式）
        public int size;

        // 目标栈：0=work，1=return（对应 r 模式）
        public int stack;

        // 保留模式：0/1（对应 k 模式）
        public int keep;

        // 源码行号
        public int lineNum;

        public Token() {}

        public Token(TokenType type, String value, int size, int stack, int keep, int lineNum) {
            this.type = type;
            this.value = value;
            this.size = size;
            this.stack = stack;
            this.keep = keep;
            this.lineNum = lineNum;
        }

        @Override public String toString() {
            return "Token{type="+type+", value="+value+", size="+size+
                ", stack="+stack+", keep="+keep+", line="+lineNum+"}";
        }
    }

    //Utilitie

    public static boolean isOpcode(String name) {
        return OPCODE_SET.contains(name != null ? name.toUpperCase() : "");
    }

    public static boolean isRefPrefix(String s) {
        return REF_TYPE_MAP.containsKey(s);
    }

    public static boolean isTokenType(List<?> token, TokenType type) {
        return token != null && !token.isEmpty() && token.get(0) == type;
    }

    public static <V> Map<String,V> upperCaseKeys(Map<String,V> in) {
        return in.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().toUpperCase(), Map.Entry::getValue, (a,b)->b, LinkedHashMap::new));
    }
}
