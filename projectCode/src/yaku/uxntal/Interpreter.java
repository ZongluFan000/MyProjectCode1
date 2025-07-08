package yaku.uxntal;


import yaku.uxntal.model.UxnVM;
import yaku.uxntal.model.Token;

public class Interpreter {
    public void run(UxnVM vm) {
        // 实现Perl中runProgram的逻辑
        while(true) {
            Token token = vm.getMemory()[vm.getPc()];
            executeInstruction(token, vm);
            vm.setPc(vm.getPc() + 1);
        }
    }
    
    private void executeInstruction(Token token, UxnVM vm) {
        // 实现各种指令的执行逻辑
    }
}