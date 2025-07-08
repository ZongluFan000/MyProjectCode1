package yaku.uxntal.model;



import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UxnVM {
    // 内存大小
    public static final int MEMORY_SIZE = 0x10000;
    
    // 内存数组
    private Token[] memory;
    
    // 栈结构：工作栈和返回栈
    private Deque<Token> workingStack;
    private Deque<Token> returnStack;
    
    // 程序计数器
    private int pc;
    
    // 符号表
    private Map<String, List<Object>> symbolTable;
    
    // 下一个空闲地址
    private int free;
    
    // Lambda计数器
    private int lambdaCount;
    
    // Lambda栈
    private Deque<Integer> lambdaStack;
    
    // 是否包含主程序
    private boolean hasMain;
    
    // 行号信息
    private Map<String, List<Integer>> linesForToken;
    private List<String> linesPerFile;
    private List<Object> lineIdxs;

    public UxnVM() {
        // 初始化内存
        memory = new Token[MEMORY_SIZE];
        for (int i = 0; i < MEMORY_SIZE; i++) {
            memory[i] = new Token(Token.EMPTY);
        }
        
        // 初始化栈
        workingStack = new ArrayDeque<>();
        returnStack = new ArrayDeque<>();
        
        // 初始化符号表
        symbolTable = new HashMap<>();
        symbolTable.put("Labels", new ArrayList<>());
        symbolTable.put("Refs", new ArrayList<>());
        
        // 初始化其他属性
        free = 0x0100; // 程序起始地址
        lambdaCount = 0;
        lambdaStack = new ArrayDeque<>();
        hasMain = false;
        linesForToken = new HashMap<>();
        linesPerFile = new ArrayList<>();
        lineIdxs = new ArrayList<>();
    }

    // Getters
    public Token[] getMemory() { return memory; }
    public Deque<Token> getWorkingStack() { return workingStack; }
    public Deque<Token> getReturnStack() { return returnStack; }
    public int getPc() { return pc; }
    public Map<String, List<Object>> getSymbolTable() { return symbolTable; }
    public int getFree() { return free; }
    public int getLambdaCount() { return lambdaCount; }
    public Deque<Integer> getLambdaStack() { return lambdaStack; }
    public boolean hasMain() { return hasMain; }
    public Map<String, List<Integer>> getLinesForToken() { return linesForToken; }
    public List<String> getLinesPerFile() { return linesPerFile; }
    public List<Object> getLineIdxs() { return lineIdxs; }

    // Setters
    public void setMemory(Token[] memory) { this.memory = memory; }
    public void setPc(int pc) { this.pc = pc; }
    public void setFree(int free) { this.free = free; }
    public void setLambdaCount(int lambdaCount) { this.lambdaCount = lambdaCount; }
    public void setHasMain(boolean hasMain) { this.hasMain = hasMain; }

    // 栈操作
    public void pushWorkingStack(Token token) {
        workingStack.push(token);
    }
    
    public Token popWorkingStack() {
        return workingStack.pop();
    }
    
    public void pushReturnStack(Token token) {
        returnStack.push(token);
    }
    
    public Token popReturnStack() {
        return returnStack.pop();
    }

    // 内存操作
    public Token loadToken(int address) {
        return memory[address];
    }
    
    public void storeToken(int address, Token token) {
        memory[address] = token;
    }
    
    // 符号表操作
    public void addLabel(String label, int address, Token token) {
        List<Object> labels = symbolTable.get("Labels");
        labels.add(label);
        labels.add(address);
        labels.add(token);
    }
    
    public void addReference(String ref, List<Integer> addresses, Token token) {
        List<Object> refs = symbolTable.get("Refs");
        refs.add(ref);
        refs.add(addresses);
        refs.add(token);
    }
    
    // Lambda操作
    public void pushLambda(int lambda) {
        lambdaStack.push(lambda);
    }
    
    public int popLambda() {
        return lambdaStack.pop();
    }
    
    // 运行程序
    public void runProgram() {
        pc = 0x0100; // 程序起始地址
        
        while (true) {
            Token token = loadToken(pc);
            executeInstruction(token);
            pc++;
            
            // 处理BRK指令
            if (token.isInstr() && "BRK".equals(token.getValue())) {
                break;
            }
        }
    }
    
    // 执行指令
    private void executeInstruction(Token token) {
        if (!token.isInstr()) return;
        
        String instruction = (String) token.getValue();
        int wordSize = token.getWordSize();
        
        switch (instruction) {
            case "ADD":
                executeAdd(wordSize);
                break;
            case "SUB":
                executeSub(wordSize);
                break;
            case "JMP":
                executeJmp(wordSize);
                break;
            case "JSR":
                executeJsr(wordSize);
                break;
            case "DEO":
                executeDeo(wordSize);
                break;
            case "BRK":
                // 停止执行
                break;
            // 添加其他指令处理...
        }
    }
    
    // 指令实现
    private void executeAdd(int wordSize) {
        Token b = popWorkingStack();
        Token a = popWorkingStack();
        
        int result = (int)a.getValue() + (int)b.getValue();
        if (wordSize == 1) result &= 0xFF;
        else result &= 0xFFFF;
        
        pushWorkingStack(new Token(Token.LIT, result, wordSize));
    }
    
    private void executeSub(int wordSize) {
        Token b = popWorkingStack();
        Token a = popWorkingStack();
        
        int result = (int)a.getValue() - (int)b.getValue();
        if (wordSize == 1) result &= 0xFF;
        else result &= 0xFFFF;
        
        pushWorkingStack(new Token(Token.LIT, result, wordSize));
    }
    
    private void executeJmp(int wordSize) {
        Token addressToken = popWorkingStack();
        pc = (int) addressToken.getValue();
    }
    
    private void executeJsr(int wordSize) {
        Token addressToken = popWorkingStack();
        pushReturnStack(new Token(Token.LIT, pc, 2));
        pc = (int) addressToken.getValue();
    }
    
    private void executeDeo(int wordSize) {
        Token valueToken = popWorkingStack();
        Token deviceToken = popWorkingStack();
        
        int device = (int) deviceToken.getValue();
        int value = (int) valueToken.getValue();
        
        // 设备输出处理
        if (device == 0x0f) { // System/state
            System.exit(value & 0x7f);
        } else if (device == 0x18) { // Console/write
            System.out.print((char) value);
        }
    }
    
    // 其他辅助方法
    public void initialize() {
        pc = 0x0100;
        workingStack.clear();
        returnStack.clear();
    }
}