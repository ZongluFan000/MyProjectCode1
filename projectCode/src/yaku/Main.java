package yaku;

import java.util.List;

import yaku.uxntal.*;
import yaku.uxntal.model.UxnVM;
import yaku.uxntal.model.Token;

public class Main {
    public static void main(String[] args) {
        // 1. 初始化虚拟机
        UxnVM vm = new UxnVM();
        
        // 2. 解析.tal文件
        Parser parser = new Parser();
        List<Token> tokens = parser.parse("program.tal", vm);
        
        // 3. 编码到内存
        Encoder encoder = new Encoder();
        encoder.tokensToMemory(tokens, vm);
        
        // 4. 执行程序
        Interpreter interpreter = new Interpreter();
        interpreter.run(vm);
    }
}