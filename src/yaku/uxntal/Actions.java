package yaku.uxntal;

import java.util.*;


public class Actions {

    //Types 
    public static class Action {
        public final String name;
        public final int nArgs;         // how many args Interpreter will pass in
        public final boolean hasResult; // whether to push a result
        public final ActionExec exec;
        public Action(String name, int nArgs, boolean hasResult, ActionExec exec) {
            this.name = name; this.nArgs = nArgs; this.hasResult = hasResult; this.exec = exec;
        }
    }
    @FunctionalInterface
    public interface ActionExec {
        Interpreter.StackElem apply(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm);
    }

    private static int maskBySize(int v, int sz) { return v & (sz == 1 ? 0xFF : 0xFFFF); }

    //Action table 
    public static final Map<String, Action> ACTION_TABLE = new HashMap<>();
    static {
        // Control
        ACTION_TABLE.put("BRK", new Action("BRK", 0, false, Actions::brk));
        // LIT 不在这里处理

        // Arithmetic / Logic / Shift
        ACTION_TABLE.put("INC", new Action("INC", 1, true,  Actions::inc));
        ACTION_TABLE.put("ADD", new Action("ADD", 2, true,  Actions::add));
        ACTION_TABLE.put("SUB", new Action("SUB", 2, true,  Actions::sub));
        ACTION_TABLE.put("MUL", new Action("MUL", 2, true,  Actions::mul));
        ACTION_TABLE.put("DIV", new Action("DIV", 2, true,  Actions::div));
        ACTION_TABLE.put("AND", new Action("AND", 2, true,  Actions::and));
        ACTION_TABLE.put("ORA", new Action("ORA", 2, true,  Actions::ora));
        ACTION_TABLE.put("EOR", new Action("EOR", 2, true,  Actions::eor));
        ACTION_TABLE.put("SFT", new Action("SFT", 2, true,  Actions::sft)); // Perl: amt>15 左移(amt>>4)，否则右移 amt

        // Compare 
        ACTION_TABLE.put("EQU", new Action("EQU", 2, true,  Actions::equ));
        ACTION_TABLE.put("NEQ", new Action("NEQ", 2, true,  Actions::neq));
        ACTION_TABLE.put("GTH", new Action("GTH", 2, true,  Actions::gth));
        ACTION_TABLE.put("LTH", new Action("LTH", 2, true,  Actions::lth));

        // Branch / Call
        ACTION_TABLE.put("JMP", new Action("JMP", 1, false, Actions::jmp));
        ACTION_TABLE.put("JCN", new Action("JCN", 2, false, Actions::jcn));
        ACTION_TABLE.put("JSR", new Action("JSR", 1, false, Actions::jsr));
        ACTION_TABLE.put("JMI", new Action("JMI", 0, false, Actions::jmi));
        ACTION_TABLE.put("JSI", new Action("JSI", 0, false, Actions::jsi));
        ACTION_TABLE.put("JCI", new Action("JCI", 1, false, Actions::jci));

        // Memory参数顺序与 Perl 对齐：先地址、后值
        ACTION_TABLE.put("LDZ", new Action("LDZ", 1, true,  Actions::ldz));
        ACTION_TABLE.put("LDR", new Action("LDR", 1, true,  Actions::ldr));
        ACTION_TABLE.put("LDA", new Action("LDA", 1, true,  Actions::lda));
        ACTION_TABLE.put("STZ", new Action("STZ", 2, false, Actions::stz));
        ACTION_TABLE.put("STR", new Action("STR", 2, false, Actions::str));
        ACTION_TABLE.put("STA", new Action("STA", 2, false, Actions::sta));

        // Device I/O
        ACTION_TABLE.put("DEI", new Action("DEI", 1, true,  Actions::dei));
        ACTION_TABLE.put("DEO", new Action("DEO", 2, false, Actions::deo));
    }

    //Implementations 

    //Control
    private static Interpreter.StackElem brk(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        vm.requestHalt();
        return null;
    }

    //Branch / Call 
    private static Interpreter.StackElem jmp(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        if (sz == 1) vm.jumpRel8(args[0].value);
        else         vm.jumpAbs(args[0].value);
        return null;
    }
    private static Interpreter.StackElem jcn(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int cond = args[1].value & 0xFF; // Perl: 第二个是条件
        if (cond != 0) {
            if (sz == 1) vm.jumpRel8(args[0].value);
            else         vm.jumpAbs(args[0].value);
        }
        return null;
    }
    private static Interpreter.StackElem jsr(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        vm.pushReturnAddr(vm.getPc() + 1);
        if (sz == 1) vm.jumpRel8(args[0].value);
        else         vm.jumpAbs(args[0].value);
        return null;
    }
    // JMI/JSI/JCI 使用相对 short基准=pc+3
    private static Interpreter.StackElem jmi(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int rel = (short) vm.readShortLE((vm.getPc() + 1) & 0xFFFF);
        vm.jumpRelShort(rel);
        return null;
    }
    private static Interpreter.StackElem jsi(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        vm.pushReturnAddr(vm.getPc() + 3);
        return jmi(args, sz, rs, keep, vm);
    }
    private static Interpreter.StackElem jci(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        if ( (args[0].value & 0xFF) != 0 ) return jmi(args, sz, rs, keep, vm);
        vm.setPcRaw(vm.getPc() + 2); // skip the 2-byte immediate
        return null;
    }

    //ALU
    private static Interpreter.StackElem inc(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int res = maskBySize(args[0].value + 1, sz);
        return new Interpreter.StackElem(res, sz);
    }
    private static Interpreter.StackElem add(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int res = maskBySize(args[1].value, sz) + maskBySize(args[0].value, sz);
        return new Interpreter.StackElem(maskBySize(res, sz), sz);
    }
    private static Interpreter.StackElem sub(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int res = maskBySize(args[1].value, sz) - maskBySize(args[0].value, sz);
        return new Interpreter.StackElem(maskBySize(res, sz), sz);
    }
    private static Interpreter.StackElem mul(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int res = maskBySize(args[1].value, sz) * maskBySize(args[0].value, sz);
        return new Interpreter.StackElem(maskBySize(res, sz), sz);
    }
    private static Interpreter.StackElem div(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int b = maskBySize(args[0].value, sz);
        if (b == 0) throw new ArithmeticException("Divide by zero");
        int res = maskBySize(args[1].value, sz) / b;
        return new Interpreter.StackElem(maskBySize(res, sz), sz);
    }
    private static Interpreter.StackElem and(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int res = maskBySize(args[1].value, sz) & maskBySize(args[0].value, sz);
        return new Interpreter.StackElem(maskBySize(res, sz), sz);
    }
    private static Interpreter.StackElem ora(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int res = maskBySize(args[1].value, sz) | maskBySize(args[0].value, sz);
        return new Interpreter.StackElem(maskBySize(res, sz), sz);
    }
    private static Interpreter.StackElem eor(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int res = maskBySize(args[1].value, sz) ^ maskBySize(args[0].value, sz);
        return new Interpreter.StackElem(maskBySize(res, sz), sz);
    }
    // Perl SFT：amt>15 左移(amt>>4)，否则右移 amt
    private static Interpreter.StackElem sft(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int amt = args[0].value & 0xFF;
        int v   = maskBySize(args[1].value, sz);
        int res = (amt > 15)
                ? maskBySize((v << ((amt >> 4) & 0x0F)), sz)
                : maskBySize((v >>> (amt & 0x0F)),       sz);
        return new Interpreter.StackElem(res, sz);
    }
    private static Interpreter.StackElem equ(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int res = (maskBySize(args[1].value, sz) == maskBySize(args[0].value, sz)) ? 1 : 0;
        return new Interpreter.StackElem(res & 0xFF, 1);
    }
    private static Interpreter.StackElem neq(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int res = (maskBySize(args[1].value, sz) != maskBySize(args[0].value, sz)) ? 1 : 0;
        return new Interpreter.StackElem(res & 0xFF, 1);
    }
    private static Interpreter.StackElem gth(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int res = (Integer.compareUnsigned(maskBySize(args[1].value, sz), maskBySize(args[0].value, sz)) > 0) ? 1 : 0;
        return new Interpreter.StackElem(res & 0xFF, 1);
    }
    private static Interpreter.StackElem lth(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int res = (Integer.compareUnsigned(maskBySize(args[1].value, sz), maskBySize(args[0].value, sz)) < 0) ? 1 : 0;
        return new Interpreter.StackElem(res & 0xFF, 1);
    }

    //Memory
    private static Interpreter.StackElem ldz(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int zp = args[0].value & 0xFF; // zero-page
        return new Interpreter.StackElem(vm.loadMemory(zp, sz), sz);
    }
    private static Interpreter.StackElem ldr(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int base = (vm.getPc() + 1) & 0xFFFF;
        int addr = (base + (byte)args[0].value) & 0xFFFF;
        return new Interpreter.StackElem(vm.loadMemory(addr, sz), sz);
    }
    private static Interpreter.StackElem lda(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int addr = args[0].value & 0xFFFF;
        return new Interpreter.StackElem(vm.loadMemory(addr, sz), sz);
    }
    private static Interpreter.StackElem stz(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int addr = args[0].value & 0xFF;   // zero-page
        int val  = args[1].value;
        vm.storeMemory(addr, val, sz);
        return null;
    }
    private static Interpreter.StackElem str(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int base = (vm.getPc() + 1) & 0xFFFF;
        int addr = (base + (byte)args[0].value) & 0xFFFF;
        int val  = args[1].value;
        vm.storeMemory(addr, val, sz);
        return null;
    }
    private static Interpreter.StackElem sta(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int addr = args[0].value & 0xFFFF;
        int val  = args[1].value;
        vm.storeMemory(addr, val, sz);
        return null;
    }

    //Device I/O
    private static Interpreter.StackElem deo(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int port = args[0].value & 0xFF;                  // device
        int val  = maskBySize(args[1].value, sz);         // data
        if (port == 0x18) { // Console/write
            System.out.print((char) (val & 0xFF));
        } else if (port == 0x0F) { // System/state
            if ((val & 0xFF) != 0) System.exit(val & 0x7F);
        }
        return null;
    }
    private static Interpreter.StackElem dei(Interpreter.StackElem[] args, int sz, int rs, int keep, Interpreter vm) {
        int port = args[0].value & 0xFF;
        if (port == 0x04)      return new Interpreter.StackElem(vm.getWorkStackSize()   & 0xFF, 1); // System/wst
        else if (port == 0x05) return new Interpreter.StackElem(vm.getReturnStackSize() & 0xFF, 1); // System/rst
        else                   return new Interpreter.StackElem(0, 1); // 未实现端口 -> 0
    }
}
