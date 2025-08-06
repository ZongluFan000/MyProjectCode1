package yaku.uxntal;

import java.util.*;

public class Actions {
    // Action 元信息
    public static class Action {
        public final String name;
        public final int nArgs;
        public final boolean hasResult;
        public final boolean keepable;
        public final ActionExec exec;
        public Action(String name, int nArgs, boolean hasResult, boolean keepable, ActionExec exec) {
            this.name = name; this.nArgs = nArgs; this.hasResult = hasResult; this.keepable = keepable; this.exec = exec;
        }
    }
    @FunctionalInterface
    public interface ActionExec {
        Interpreter.StackElem apply(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm);
    }

    // 助记符映射
    public static final Map<Integer, String> OPCODE_MAP = new HashMap<>();
    static {
        OPCODE_MAP.put(0x00, "BRK");
        OPCODE_MAP.put(0x80, "LIT");
        OPCODE_MAP.put(0xA0, "LIT2");
        
        OPCODE_MAP.put(0x18, "ADD");
        OPCODE_MAP.put(0x38, "ADD2");
        OPCODE_MAP.put(0x19, "SUB");
        OPCODE_MAP.put(0x39, "SUB2");
        OPCODE_MAP.put(0x1A, "MUL");
        OPCODE_MAP.put(0x3A, "MUL2");
        OPCODE_MAP.put(0x1B, "DIV");
        OPCODE_MAP.put(0x3B, "DIV2");
        OPCODE_MAP.put(0x1C, "AND");
        OPCODE_MAP.put(0x3C, "AND2");
        OPCODE_MAP.put(0x1D, "ORA");
        OPCODE_MAP.put(0x3D, "ORA2");
        OPCODE_MAP.put(0x1E, "EOR");
        OPCODE_MAP.put(0x3E, "EOR2");
        OPCODE_MAP.put(0x1F, "SFT");
        OPCODE_MAP.put(0x3F, "SFT2");
        
        OPCODE_MAP.put(0x17, "DEO");
        OPCODE_MAP.put(0x37, "DEO2");
        OPCODE_MAP.put(0x16, "DEI");
        OPCODE_MAP.put(0x36, "DEI2");
        
        OPCODE_MAP.put(0x13, "STZ");
        OPCODE_MAP.put(0x33, "STZ2");
        OPCODE_MAP.put(0x12, "LDZ");
        OPCODE_MAP.put(0x32, "LDZ2");
        OPCODE_MAP.put(0x15, "STA");
        OPCODE_MAP.put(0x35, "STA2");
        OPCODE_MAP.put(0x14, "LDA");
        OPCODE_MAP.put(0x34, "LDA2");
        
        OPCODE_MAP.put(0x07, "STH");
        OPCODE_MAP.put(0x27, "STH2");
        OPCODE_MAP.put(0x05, "STR");
        OPCODE_MAP.put(0x25, "STR2");
        OPCODE_MAP.put(0x04, "LDR");
        OPCODE_MAP.put(0x24, "LDR2");
        
        OPCODE_MAP.put(0x02, "POP");
        OPCODE_MAP.put(0x22, "POP2");
        OPCODE_MAP.put(0x03, "DUP");
        OPCODE_MAP.put(0x23, "DUP2");
        OPCODE_MAP.put(0x01, "SWP");
        OPCODE_MAP.put(0x21, "SWP2");
        OPCODE_MAP.put(0x09, "OVR");
        OPCODE_MAP.put(0x29, "OVR2");
        OPCODE_MAP.put(0x0A, "ROT");
        OPCODE_MAP.put(0x2A, "ROT2");
        
        OPCODE_MAP.put(0x08, "EQU");
        OPCODE_MAP.put(0x28, "EQU2");
        OPCODE_MAP.put(0x09, "NEQ");
        OPCODE_MAP.put(0x29, "NEQ2");
        OPCODE_MAP.put(0x0A, "GTH");
        OPCODE_MAP.put(0x2A, "GTH2");
        OPCODE_MAP.put(0x0B, "LTH");
        OPCODE_MAP.put(0x2B, "LTH2");
        
        OPCODE_MAP.put(0x0C, "JMP");
        OPCODE_MAP.put(0x2C, "JMP2");
        OPCODE_MAP.put(0x0D, "JCN");
        OPCODE_MAP.put(0x2D, "JCN2");
        OPCODE_MAP.put(0x0E, "JSR");
        OPCODE_MAP.put(0x2E, "JSR2");
        OPCODE_MAP.put(0x0F, "STH");     // 和上面 0x07 一起保留
        OPCODE_MAP.put(0x2F, "STH2");
        


        OPCODE_MAP.put(0x01, "INC");     // INC 与 SWP 共用 0x01
        OPCODE_MAP.put(0x21, "INC2");    // INC2 与 SWP2 共用 0x21
        OPCODE_MAP.put(0x02, "NIP");     // NIP 与 POP 共用 0x02
        OPCODE_MAP.put(0x22, "NIP2");    // NIP2 与 POP2 共用 0x22



    }

    // 指令元信息表
    public static final Map<String, Action> ACTION_TABLE = new HashMap<>();
    static {
        // 控制
    ACTION_TABLE.put("BRK",   new Action("BRK",   0, false, false, Actions::brk));
    ACTION_TABLE.put("LIT",   new Action("LIT",   0, true,  false, Actions::lit));
    ACTION_TABLE.put("LIT2",  new Action("LIT2",  0, true,  false, (a,s,r,k,vm)->lit(a,2,r,k,vm)));

    // 算术
    ACTION_TABLE.put("ADD",   new Action("ADD",   2, true,  true,  Actions::add));
    ACTION_TABLE.put("ADD2",  new Action("ADD2",  2, true,  true,  (a,s,r,k,vm)->add(a,2,r,k,vm)));
    ACTION_TABLE.put("SUB",   new Action("SUB",   2, true,  true,  Actions::sub));
    ACTION_TABLE.put("SUB2",  new Action("SUB2",  2, true,  true,  (a,s,r,k,vm)->sub(a,2,r,k,vm)));
    ACTION_TABLE.put("MUL",   new Action("MUL",   2, true,  true,  Actions::mul));
    ACTION_TABLE.put("MUL2",  new Action("MUL2",  2, true,  true,  (a,s,r,k,vm)->mul(a,2,r,k,vm)));
    ACTION_TABLE.put("DIV",   new Action("DIV",   2, true,  true,  Actions::div));
    ACTION_TABLE.put("DIV2",  new Action("DIV2",  2, true,  true,  (a,s,r,k,vm)->div(a,2,r,k,vm)));

    // 位操作
    ACTION_TABLE.put("AND",   new Action("AND",   2, true,  true,  Actions::and));
    ACTION_TABLE.put("AND2",  new Action("AND2",  2, true,  true,  (a,s,r,k,vm)->and(a,2,r,k,vm)));
    ACTION_TABLE.put("ORA",   new Action("ORA",   2, true,  true,  Actions::ora));
    ACTION_TABLE.put("ORA2",  new Action("ORA2",  2, true,  true,  (a,s,r,k,vm)->ora(a,2,r,k,vm)));
    ACTION_TABLE.put("EOR",   new Action("EOR",   2, true,  true,  Actions::eor));
    ACTION_TABLE.put("EOR2",  new Action("EOR2",  2, true,  true,  (a,s,r,k,vm)->eor(a,2,r,k,vm)));
    ACTION_TABLE.put("SFT",   new Action("SFT",   2, true,  true,  Actions::sft));
    ACTION_TABLE.put("SFT2",  new Action("SFT2",  2, true,  true,  (a,s,r,k,vm)->sft(a,2,r,k,vm)));

    // 栈操作
    ACTION_TABLE.put("POP",   new Action("POP",   1, false, true,  Actions::pop));
    ACTION_TABLE.put("POP2",  new Action("POP2",  1, false, true,  (a,s,r,k,vm)->pop(a,2,r,k,vm)));
    ACTION_TABLE.put("DUP",   new Action("DUP",   1, false, true,  Actions::dup));
    ACTION_TABLE.put("DUP2",  new Action("DUP2",  1, false, true,  (a,s,r,k,vm)->dup(a,2,r,k,vm)));
    ACTION_TABLE.put("SWP",   new Action("SWP",   2, false, true,  Actions::swap));
    ACTION_TABLE.put("SWP2",  new Action("SWP2",  2, false, true,  (a,s,r,k,vm)->swap(a,2,r,k,vm)));
    ACTION_TABLE.put("OVR",   new Action("OVR",   2, false, true,  Actions::over));
    ACTION_TABLE.put("OVR2",  new Action("OVR2",  2, false, true,  (a,s,r,k,vm)->over(a,2,r,k,vm)));
    ACTION_TABLE.put("ROT",   new Action("ROT",   3, false, true,  Actions::rot));
    ACTION_TABLE.put("ROT2",  new Action("ROT2",  3, false, true,  (a,s,r,k,vm)->rot(a,2,r,k,vm)));
    ACTION_TABLE.put("NIP",   new Action("NIP",   2, false, true,  Actions::nip));
    ACTION_TABLE.put("NIP2",  new Action("NIP2",  2, false, true,  (a,s,r,k,vm)->nip(a,2,r,k,vm)));

    // 比较
    ACTION_TABLE.put("EQU",   new Action("EQU",   2, true,  true,  Actions::equ));
    ACTION_TABLE.put("EQU2",  new Action("EQU2",  2, true,  true,  (a,s,r,k,vm)->equ(a,2,r,k,vm)));
    ACTION_TABLE.put("NEQ",   new Action("NEQ",   2, true,  true,  Actions::neq));
    ACTION_TABLE.put("NEQ2",  new Action("NEQ2",  2, true,  true,  (a,s,r,k,vm)->neq(a,2,r,k,vm)));
    ACTION_TABLE.put("GTH",   new Action("GTH",   2, true,  true,  Actions::gth));
    ACTION_TABLE.put("GTH2",  new Action("GTH2",  2, true,  true,  (a,s,r,k,vm)->gth(a,2,r,k,vm)));
    ACTION_TABLE.put("LTH",   new Action("LTH",   2, true,  true,  Actions::lth));
    ACTION_TABLE.put("LTH2",  new Action("LTH2",  2, true,  true,  (a,s,r,k,vm)->lth(a,2,r,k,vm)));

    // 跳转与分支
    ACTION_TABLE.put("JMP",   new Action("JMP",   1, false, false, Actions::jmp));
    ACTION_TABLE.put("JMP2",  new Action("JMP2",  1, false, false, (a,s,r,k,vm)->jmp(a,2,r,k,vm)));
    ACTION_TABLE.put("JCN",   new Action("JCN",   2, false, false, Actions::jcn));
    ACTION_TABLE.put("JCN2",  new Action("JCN2",  2, false, false, (a,s,r,k,vm)->jcn(a,2,r,k,vm)));
    ACTION_TABLE.put("JSR",   new Action("JSR",   1, false, false, Actions::jsr));
    ACTION_TABLE.put("JSR2",  new Action("JSR2",  1, false, false, (a,s,r,k,vm)->jsr(a,2,r,k,vm)));

    // 存储相关
    ACTION_TABLE.put("LDZ",   new Action("LDZ",   1, true,  true,  Actions::ldz));
    ACTION_TABLE.put("LDZ2",  new Action("LDZ2",  1, true,  true,  (a,s,r,k,vm)->ldz(a,2,r,k,vm)));
    ACTION_TABLE.put("LDR",   new Action("LDR",   1, true,  true,  Actions::ldr));
    ACTION_TABLE.put("LDR2",  new Action("LDR2",  1, true,  true,  (a,s,r,k,vm)->ldr(a,2,r,k,vm)));
    ACTION_TABLE.put("LDA",   new Action("LDA",   1, true,  true,  Actions::lda));
    ACTION_TABLE.put("LDA2",  new Action("LDA2",  1, true,  true,  (a,s,r,k,vm)->lda(a,2,r,k,vm)));
    ACTION_TABLE.put("STZ",   new Action("STZ",   2, false, true,  Actions::stz));
    ACTION_TABLE.put("STZ2",  new Action("STZ2",  2, false, true,  (a,s,r,k,vm)->stz(a,2,r,k,vm)));
    ACTION_TABLE.put("STR",   new Action("STR",   2, false, true,  Actions::str));
    ACTION_TABLE.put("STR2",  new Action("STR2",  2, false, true,  (a,s,r,k,vm)->str(a,2,r,k,vm)));
    ACTION_TABLE.put("STA",   new Action("STA",   2, false, true,  Actions::sta));
    ACTION_TABLE.put("STA2",  new Action("STA2",  2, false, true,  (a,s,r,k,vm)->sta(a,2,r,k,vm)));

    // 栈切换
    ACTION_TABLE.put("STH",   new Action("STH",   1, false, true,  Actions::stash));
    ACTION_TABLE.put("STH2",  new Action("STH2",  1, false, true,  (a,s,r,k,vm)->stash(a,2,r,k,vm)));

    // 设备输入输出
    ACTION_TABLE.put("DEI",   new Action("DEI",   1, true,  true,  Actions::dei));
    ACTION_TABLE.put("DEI2",  new Action("DEI2",  1, true,  true,  (a,s,r,k,vm)->dei(a,2,r,k,vm)));
    ACTION_TABLE.put("DEO",   new Action("DEO",   2, false, true,  Actions::deo));
    ACTION_TABLE.put("DEO2",  new Action("DEO2",  2, false, true,  (a,s,r,k,vm)->deo(a,2,r,k,vm)));



        // 栈操作 - 增加 INC, INC2, NIP, NIP2
    ACTION_TABLE.put("INC",   new Action("INC",   1, false, true,  Actions::inc));
    ACTION_TABLE.put("INC2",  new Action("INC2",  1, false, true,  (a,s,r,k,vm)->inc(a,2,r,k,vm)));
    ACTION_TABLE.put("NIP",   new Action("NIP",   2, false, true,  Actions::nip));
    ACTION_TABLE.put("NIP2",  new Action("NIP2",  2, false, true,  (a,s,r,k,vm)->nip(a,2,r,k,vm)));

    }

    // ==== 指令实现 ====
    public static Interpreter.StackElem brk(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        System.exit(0); return null;
    }




    public static Interpreter.StackElem inc(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int val = args[0].value;
        int res = val + 1;
        if (sz == 1) {
            res &= 0xFF;
        } else {
            res &= 0xFFFF;
        }
        return new Interpreter.StackElem((short)res, sz);
    }
    


    
    public static Interpreter.StackElem pop(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return null;
    }
    public static Interpreter.StackElem nip(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[0]);
        return null;
    }
    public static Interpreter.StackElem swap(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[0]);
        st.addLast(args[1]);
        return null;
    }
    public static Interpreter.StackElem rot(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[1]);
        st.addLast(args[2]);
        st.addLast(args[0]);
        return null;
    }
    public static Interpreter.StackElem dup(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[0]);
        st.addLast(args[0]);
        return null;
    }
    public static Interpreter.StackElem over(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> st = (rs == 0 ? uxn.workStack : uxn.returnStack);
        st.addLast(args[1]);
        st.addLast(args[0]);
        st.addLast(args[1]);
        return null;
    }
    public static Interpreter.StackElem equ(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return new Interpreter.StackElem((short) (args[1].value == args[0].value ? 1 : 0), 1);
    }
    public static Interpreter.StackElem neq(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return new Interpreter.StackElem((short) (args[1].value != args[0].value ? 1 : 0), 1);
    }
    public static Interpreter.StackElem gth(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return new Interpreter.StackElem((short) (args[1].value > args[0].value ? 1 : 0), 1);
    }
    public static Interpreter.StackElem lth(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        return new Interpreter.StackElem((short) (args[1].value < args[0].value ? 1 : 0), 1);
    }
    public static Interpreter.StackElem jmp(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        if (sz == 1) {
            int rel = (byte) args[0].value;
            uxn.pc += rel;
        } else {
            uxn.pc = (args[0].value & 0xFFFF) - 1;
        }
        return null;
    }
    public static Interpreter.StackElem jcn(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int flag = args[1].value;
        if (flag != 0) {
            if (sz == 1) {
                int rel = (byte) args[0].value;
                uxn.pc += rel;
            } else {
                uxn.pc = (args[0].value & 0xFFFF) - 1;
            }
        }
        return null;
    }
    public static Interpreter.StackElem jsr(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int rel = (byte) args[0].value;
        uxn.returnStack.addLast(new Interpreter.StackElem((short) (uxn.pc + 1), 2));
        uxn.pc += rel;
        return null;
    }
    public static Interpreter.StackElem stash(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        Deque<Interpreter.StackElem> src = (rs == 0 ? uxn.workStack : uxn.returnStack);
        Deque<Interpreter.StackElem> dst = (rs == 0 ? uxn.returnStack : uxn.workStack);
        dst.addLast(args[0]);
        return null;
    }
    public static Interpreter.StackElem ldz(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int addr = args[0].value & 0xFFFF;
        return new Interpreter.StackElem(uxn.loadMemory(addr, sz), sz);
    }
    public static Interpreter.StackElem stz(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int addr = args[1].value & 0xFFFF;
        uxn.storeMemory(addr, args[0].value, sz);
        return null;
    }
    public static Interpreter.StackElem ldr(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int rel = (byte) args[0].value;
        int addr = uxn.pc + rel + 1;
        return new Interpreter.StackElem(uxn.loadMemory(addr, sz), sz);
    }
    public static Interpreter.StackElem str(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int rel = (byte) args[0].value;
        int addr = uxn.pc + rel + 1;
        uxn.storeMemory(addr, args[1].value, sz);
        return null;
    }
    public static Interpreter.StackElem lda(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int addr = args[0].value & 0xFFFF;
        return new Interpreter.StackElem(uxn.loadMemory(addr, sz), sz);
    }
    public static Interpreter.StackElem sta(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int addr = args[1].value & 0xFFFF;
        uxn.storeMemory(addr, args[0].value, sz);
        return null;
    }
    public static Interpreter.StackElem dei(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int port = args[0].value & 0xFF;
        // 常见端口
        if (port == 0x04) return new Interpreter.StackElem((short) uxn.workStack.size(), 1);
        if (port == 0x05) return new Interpreter.StackElem((short) uxn.returnStack.size(), 1);
        System.err.println("[Warn] DEI port not implemented: " + port + " at pc=" + uxn.pc);
        return new Interpreter.StackElem((short) 0, 1);
    }


    public static Interpreter.StackElem deo(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        
        int val  = args[0].value & ((sz == 2) ? 0xFFFF : 0xFF);
        int port = args[1].value & 0xFF;
        // 实际DEO行为实现（视你的模拟器架构）
        // 这里通常会调用 uxn.storeMemory(port, val, sz);
        System.out.printf("DEO CALLED port=%02x val=%04x sz=%d\n", port, val, sz);
        return null; // DEO 没有返回
        
        

    }
    
    
    
    public static Interpreter.StackElem add(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int a = args[0].value;
        int b = args[1].value;
        int res = (sz == 2) ? ((a & 0xFFFF) + (b & 0xFFFF)) : ((a & 0xFF) + (b & 0xFF));
        return new Interpreter.StackElem(res & ((sz == 2) ? 0xFFFF : 0xFF), sz);
        
    }
    public static Interpreter.StackElem sub(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value - args[0].value;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }
    public static Interpreter.StackElem mul(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int a = args[0].value;
        int b = args[1].value;
        int res = (sz == 2) ? ((a & 0xFFFF) * (b & 0xFFFF)) : ((a & 0xFF) * (b & 0xFF));
        return new Interpreter.StackElem(res & ((sz == 2) ? 0xFFFF : 0xFF), sz);
        
    }
    public static Interpreter.StackElem div(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        if (args[0].value == 0) throw new ArithmeticException("Divide by zero");
        int res = args[1].value / args[0].value;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }
    public static Interpreter.StackElem and(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value & args[0].value;
        return new Interpreter.StackElem((short) res, sz);
    }
    public static Interpreter.StackElem ora(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value | args[0].value;
        return new Interpreter.StackElem((short) res, sz);
    }
    public static Interpreter.StackElem eor(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int res = args[1].value ^ args[0].value;
        return new Interpreter.StackElem((short) res, sz);
    }
    public static Interpreter.StackElem sft(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int shift = args[0].value & 0xFF;
        int val = args[1].value & (sz == 1 ? 0xFF : 0xFFFF);
        int res;
        if (shift > 15) res = val << (shift >> 4);
        else res = val >> shift;
        return new Interpreter.StackElem((short) ((sz == 1) ? (res & 0xFF) : (res & 0xFFFF)), sz);
    }
    public static Interpreter.StackElem lit(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        // Interpreter 已实现，Actions中不处理
        return null;
    }
    public static Interpreter.StackElem jmi(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        int lo = uxn.memory[uxn.pc + 1] & 0xFF, hi = uxn.memory[uxn.pc + 2] & 0xFF;
        int rel = (short) ((hi << 8) | lo);
        uxn.pc = uxn.pc + 2 + rel;
        return null;
    }
    public static Interpreter.StackElem jsi(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        uxn.returnStack.addLast(new Interpreter.StackElem((short) (uxn.pc + 3), 2));
        int lo = uxn.memory[uxn.pc + 1] & 0xFF, hi = uxn.memory[uxn.pc + 2] & 0xFF;
        int rel = (short) ((hi << 8) | lo);
        uxn.pc = uxn.pc + 2 + rel;
        return null;
    }
    public static Interpreter.StackElem jci(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter uxn) {
        if (args[0].value != 0) {
            int lo = uxn.memory[uxn.pc + 1] & 0xFF, hi = uxn.memory[uxn.pc + 2] & 0xFF;
            int rel = (short) ((hi << 8) | lo);
            uxn.pc = uxn.pc + 2 + rel;
        } else {
            uxn.pc += 2;
        }
        return null;
    
    }

   

}



