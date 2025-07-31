// // InstrAction.java
// package yaku.uxntal;

// /**
//  * 函数式接口：封装每条指令的执行逻辑。
//  */
// @FunctionalInterface
// public interface InstrAction {
//     /**
//      * @param args  已 pop 出来的 nArgs 个 short（低位在 args[0]）
//      * @param sz    操作数长度：1 或 2
//      * @param rs    栈选择：0=工作栈,1=返回栈
//      * @param keep  keep 标志：0 或 1
//      * @param interp 当前 Interpreter 实例（含 memory, pc, 两个栈）
//      * @return      如果 hasResult，则返回要压回栈的 short；否则返回 null
//      */
//     Short apply(short[] args, int sz, int rs, int keep, Interpreter interp);
// }
