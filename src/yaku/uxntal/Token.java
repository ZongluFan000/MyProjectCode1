package yaku.uxntal;

import java.util.*;


public final class Token {

    //Tuple fields 
    // [0] type: MAIN, LIT, INSTR, LABEL, REF, RAW, ADDR, PAD, EMPTY ...
    public Definitions.TokenType type;

    // [1] value: instruction mnemonic / label / literal string
    public String value;

    // [2] size: for INSTR/LIT -> word size 1 or 2; for LABEL -> parent=2, child=1
    public int size;

    // [3] stack: for INSTR/LIT -> r bit (0/1); for REF -> parent=0, child=1
    public int stack;

    // [4] keep: for INSTR -> k bit (0/1)
    public int keep;

    // [5] refType: REF only (dot/comma/semi/dash/underscore/equals/I ...)
    public int refType;

    // cache flag: child? (LABEL derived from size; REF derived from stack)
    public int isChild;

    // source line number (unified)
    public int lineNum;

    //ALU & commutative sets
    private static final Set<String> ALU_OPS = new HashSet<>(Arrays.asList(
        "ADD","SUB","MUL","DIV",
        "AND","ORA","EOR","SFT",
        "NEQ","EQU","GTH","LTH"
    ));

    private static final Set<String> COMMUTATIVE_BINARY_OPS = new HashSet<>(Arrays.asList(
        "ADD","MUL","AND","ORA","EOR","EQU","NEQ"
    ));

    //Constructors
    public Token(Definitions.TokenType type, String value, int size, int stack, int keep,
                 int refType, int isChild, int lineNum) {
        this.type = type;
        this.value = value;
        this.size = size;
        this.stack = stack;
        this.keep = keep;
        this.refType = refType;
        this.isChild = isChild;
        this.lineNum = lineNum;
        reconcileChildFlag();
    }

    public Token(Definitions.TokenType type, String value, int lineNum) {
        this(type, value, 0, 0, 0, 0, 0, lineNum);
    }

    public Token(Definitions.TokenType type, String value, int size, int lineNum) {
        this(type, value, size, 0, 0, 0, 0, lineNum);
    }

    // REF convenience
    public Token(Definitions.TokenType type, String value, int refType, int isChild, int lineNum) {
        this(type, value, 0, 0, 0, refType, isChild, lineNum);
    }

    // INSTR convenience
    public Token(Definitions.TokenType type, String value, int size, int stack, int keep, int lineNum) {
        this(type, value, size, stack, keep, 0, 0, lineNum);
    }

    // Keep isChild consistent with size/stack (LABEL/REF)
    private void reconcileChildFlag() {
        if (type == Definitions.TokenType.LABEL) {
            this.isChild = (this.size == 1) ? 1 : (this.size == 2 ? 0 : this.isChild);
        } else if (type == Definitions.TokenType.REF) {
            this.isChild = (this.stack == 1) ? 1 : 0;
        }
    }

    //Predicates
    public boolean isLit()          { return type == Definitions.TokenType.LIT; }
    public boolean isPadding()      { return type == Definitions.TokenType.PAD; }
    public boolean isInstr()        { return type == Definitions.TokenType.INSTR; }
    public boolean isRef()          { return type == Definitions.TokenType.REF; }
    public boolean isLabel()        { return type == Definitions.TokenType.LABEL; }

    public boolean isParentLabel()  { return isLabel() && size == 2; }
    public boolean isChildLabel()   { return isLabel() && size == 1; }
    public boolean isParentRef()    { return isRef()   && stack == 0; }
    public boolean isChildRef()     { return isRef()   && stack == 1; }

    public boolean isParent()       { return isParentLabel() || isParentRef(); }
    public boolean isChild()        { return isChildLabel()  || isChildRef(); }

    public boolean hasKeepMode()    { return keep == 1; }
    public boolean hasReturnMode()  { return stack == 1; } // r bit for INSTR/LIT

    public int getWordSz()          { return size; }
    public int getStackMode()       { return stack; }
    public int noKeep()             { return 1 - keep; }

    public boolean hasName(String name) {
        return Objects.equals(this.value, name);
    }

    
    public boolean isInstr(String opPrefix) {
        if (!isInstr() || value == null) return false;
        return value.toUpperCase().startsWith(opPrefix.toUpperCase());
    }

  
    public boolean isLoad()  { return isInstr("LD"); }
    public boolean isStore() { return isInstr("ST") && !isInstr("STH"); }

    // Control-flow families
    public boolean isCondJump() { return isInstr("JCI") || isInstr("JCN"); }
    public boolean isJump()     { return isInstr("JMI") || isInstr("JMP"); }
    public boolean isCall()     { return isInstr("JSI") || isInstr("JSR"); }

    // ALU family
    public boolean isOp() {
        return isInstr() && ALU_OPS.contains(stripSuffixFlags(value));
    }

    public boolean isCommBinOp() {
        return isInstr() && COMMUTATIVE_BINARY_OPS.contains(stripSuffixFlags(value));
    }

    //Equality helpers
    public static boolean sameWordSzAndStack(Token a, Token b) {
        return a != null && b != null && a.size == b.size && a.stack == b.stack;
    }

    // structural equality (ignore lineNum, like Dumper comparison)
    public static boolean tokenEqual(Token a, Token b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.type == b.type &&
               Objects.equals(a.value, b.value) &&
               a.size == b.size &&
               a.stack == b.stack &&
               a.keep == b.keep &&
               a.refType == b.refType &&
               a.isChild == b.isChild;
    }

    
    public static boolean tokensAreEqual(List<Token> tokens, int i, int[] idxPairs) {
        for (int k = 0; k < idxPairs.length; k += 2) {
            int l = i + idxPairs[k];
            int r = i + idxPairs[k + 1];
            if (l < 0 || r < 0 || l >= tokens.size() || r >= tokens.size()) return false;
            if (!tokenEqual(tokens.get(l), tokens.get(r))) return false;
        }
        return true;
    }

    //Type & mode
    public static final class TypeAndMode {
        public final String type;
        public final int mode;
        public TypeAndMode(String type, int mode) { this.type = type; this.mode = mode; }
    }

    public static TypeAndMode getTokenTypeAndMode(List<Token> tokens, int idx) {
        if (idx < 0 || idx >= tokens.size()) return new TypeAndMode("", 0);
        Token t = tokens.get(idx);
        String tname = t.value == null ? "" : t.value;
        // remove "ST" and "LD"
        String stripped = tname.replace("ST", "").replace("LD", "");
        int mode = t.size * 4 + t.stack * 2 + t.keep;
        return new TypeAndMode(stripped, mode);
    }

    // toInstrToken

    public static Token toInstrToken(String op, int lineNum) {
        if (op == null) return new Token(Definitions.TokenType.INSTR, "", 1, 0, 0, lineNum);
        String base = op.trim();
        int word = 1, r = 0, k = 0;

        // Strip flags from the end only, repeatedly: ...2, ...r, ...k
        boolean changed;
        do {
            changed = false;
            if (endsWithIgnoreCase(base, "k")) { k = 1; base = base.substring(0, base.length()-1); changed = true; }
            if (endsWithIgnoreCase(base, "r")) { r = 1; base = base.substring(0, base.length()-1); changed = true; }
            if (endsWithIgnoreCase(base, "2")) { word = 2; base = base.substring(0, base.length()-1); changed = true; }
        } while (changed);

        base = base.toUpperCase(Locale.ROOT);
        return new Token(Definitions.TokenType.INSTR, base, word, r, k, lineNum);
    }

    private static boolean endsWithIgnoreCase(String s, String suffix) {
        int n = s.length(), m = suffix.length();
        if (m > n) return false;
        return s.regionMatches(true, n - m, suffix, 0, m);
    }

    private static String stripSuffixFlags(String name) {
        if (name == null || name.isEmpty()) return "";
        String s = name;
        boolean changed;
        do {
            changed = false;
            if (s.length() > 0 && (s.charAt(s.length()-1) == 'k' || s.charAt(s.length()-1) == 'K')) { s = s.substring(0, s.length()-1); changed = true; }
            if (s.length() > 0 && (s.charAt(s.length()-1) == 'r' || s.charAt(s.length()-1) == 'R')) { s = s.substring(0, s.length()-1); changed = true; }
            if (s.length() > 0 && s.charAt(s.length()-1) == '2') { s = s.substring(0, s.length()-1); changed = true; }
        } while (changed);
        return s.toUpperCase(Locale.ROOT);
    }

    //Pretty print
    @Override
    public String toString() {
        return String.format(
            "Token{%s, '%s', sz=%d, stack=%d, keep=%d, refType=%d, isChild=%d, line=%d}",
            type, value, size, stack, keep, refType, isChild, lineNum
        );
    }
}
