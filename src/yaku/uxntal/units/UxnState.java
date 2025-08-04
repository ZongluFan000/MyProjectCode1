package yaku.uxntal.units;

import yaku.uxntal.Definitions;
import yaku.uxntal.Token;
import java.util.*;


/**
 * UXN Virtual Machine State Management (Java 版本)
 * 管理虚拟机的内存、栈、符号表、寄存器等所有状态
 */
public class UxnState {
    // 64KB内存：每个位置放一个 Token
    public Token[] memory = new Token[0x10000];

    // 工作栈、返回栈（泛型Object/Token/自定义，依用法可调整）
    public List<Object>[] stacks = new ArrayList[]{ new ArrayList<>(), new ArrayList<>() };

    // 栈指针
    public int[] stackPtr = new int[]{0, 0};

    // 程序计数器
    public int pc = 0;

    // 符号表
    public SymbolTable symbolTable = new SymbolTable();

    // 反向符号表
    public Map<Integer, Object[]> reverseSymbolTable = new HashMap<>();

    // 分配表
    public Map<String, Integer> allocationTable = new HashMap<>();

    // 第一个未用内存
    public int free = 0;

    // lambda支持
    public Stack<Object> lambdaStack = new Stack<>();
    public int lambdaCount = 0;

    // 主程序flag
    public int hasMain = 0;

    // 行号映射
    public List<Integer> lineIdxs = new ArrayList<>();

    // token-行号映射
    public Map<Integer, Integer> linesForToken = new HashMap<>();

    // 内部符号表结构
    public static class SymbolTable {
        public Map<String, Object[]> Labels = new HashMap<>(); // label -> [pc, token]
        public Map<String, Object[]> Refs = new HashMap<>();   // ref -> [List<pc>, token]
    }

    /** 构造函数，hasMain可选 */
    public UxnState() { this(0); }
    public UxnState(int hasMain) {
        this.hasMain = hasMain;
        reset();
    }

    /** 重置状态 */
    public void reset() {
        for (int i = 0; i < 0x10000; i++) {
            // 按 Definitions.EMPTY_TOKEN 生成新的 Token
            this.memory[i] = new Token(Definitions.TokenType.EMPTY, "", 0, 0);
        }
        stacks[0].clear();
        stacks[1].clear();
        stackPtr[0] = stackPtr[1] = 0;
        pc = 0;
        symbolTable.Labels.clear();
        symbolTable.Refs.clear();
        reverseSymbolTable.clear();
        allocationTable.clear();
        free = 0;
        lambdaStack.clear();
        lambdaCount = 0;
        lineIdxs.clear();
        linesForToken.clear();
    }

    /** 栈操作 */
    public void pushStack(int stackIndex, Object value) {
        if (stackPtr[stackIndex] >= 255)
            throw new RuntimeException("Stack " + stackIndex + " overflow");
        stacks[stackIndex].add(value);
        stackPtr[stackIndex]++;
    }

    public Object popStack(int stackIndex) {
        if (stackPtr[stackIndex] <= 0)
            throw new RuntimeException("Stack " + stackIndex + " underflow");
        stackPtr[stackIndex]--;
        return stacks[stackIndex].remove(stacks[stackIndex].size() - 1);
    }

    public Object peekStack(int stackIndex) {
        if (stackPtr[stackIndex] <= 0)
            throw new RuntimeException("Stack " + stackIndex + " is empty");
        return stacks[stackIndex].get(stacks[stackIndex].size() - 1);
    }

    public int getStackDepth(int stackIndex) {
        return stackPtr[stackIndex];
    }

    /** 地址是否合法 */
    public boolean isValidAddress(int address) {
        return address >= 0 && address < 0x10000;
    }

    /** 内存读写 */
    public Token readMemory(int address) {
        if (!isValidAddress(address))
            throw new RuntimeException("Invalid memory address: " + address);
        return memory[address];
    }

    public void writeMemory(int address, Token token) {
        if (!isValidAddress(address))
            throw new RuntimeException("Invalid memory address: " + address);
        memory[address] = token;
    }

    /** 添加符号表项 */
    public void addSymbol(String name, int address, Token token, boolean isLabel) {
        if (isLabel) {
            symbolTable.Labels.put(name, new Object[]{address, token});
        } else {
            if (!symbolTable.Refs.containsKey(name)) {
                symbolTable.Refs.put(name, new Object[]{new ArrayList<Integer>(), token});
            }
            ((List<Integer>)symbolTable.Refs.get(name)[0]).add(address);
        }
    }

    /** 获取符号表项 */
    public Object[] getSymbol(String name, boolean isLabel) {
        if (isLabel) return symbolTable.Labels.getOrDefault(name, null);
        else         return symbolTable.Refs.getOrDefault(name, null);
    }

    /** 添加分配信息 */
    public void addAllocation(String name, int size) {
        allocationTable.put(name, size);
    }

    /** 获取分配大小 */
    public int getAllocationSize(String name) {
        return allocationTable.getOrDefault(name, 0);
    }

    /** 调试输出 */
    public String debugUxnState() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("PC: 0x%04X\n", pc));
        sb.append(String.format("Free: 0x%04X\n", free));
        sb.append(String.format("Working Stack (%d): %s\n", stackPtr[0], stackToString(stacks[0])));
        sb.append(String.format("Return Stack (%d): %s\n", stackPtr[1], stackToString(stacks[1])));
        sb.append(String.format("Labels: %d\n", symbolTable.Labels.size()));
        sb.append(String.format("References: %d\n", symbolTable.Refs.size()));
        return sb.toString();
    }

    private String stackToString(List<Object> stack) {
        List<String> parts = new ArrayList<>();
        for (Object v : stack) {
            if (v instanceof Token) {
                Token t = (Token)v;
                parts.add(t.value + "(" + t.size + ")");
            } else {
                parts.add(v.toString());
            }
        }
        return parts.toString();
    }

    /** 让外部直接访问工作栈/返回栈字符串 */
    public String stacksToString(int which) {
        if (which == 0 || which == 1) {
            return stackToString(this.stacks[which]);
        }
        return "";
    }

}
