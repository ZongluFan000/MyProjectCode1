// package yaku.uxntal;

// import java.util.*;
// import static yaku.uxntal.Definitions.*;

// public class ErrorChecker {

//     public static class Error {
//         public final int line;
//         public final String message;

//         public Error(int line, String message) {
//             this.line = line;
//             this.message = message;
//         }

//         @Override
//         public String toString() {
//             return "第 " + line + " 行: " + message;
//         }
//     }

//     public List<Error> check(List<Token> tokens) {
//         List<Error> errors = new ArrayList<>();
//         Set<String> definedLabels = new HashSet<>();
//         Set<String> referencedLabels = new HashSet<>();
//         Set<String> duplicateLabels = new HashSet<>();

//         // 1. 记录所有定义和引用的标签，并检测指令合法性
//         for (Token t : tokens) {
//             switch (t.type) {
//                 case LABEL:
//                     if (definedLabels.contains(t.value)) {
//                         errors.add(new Error(t.line, "标签重复定义: " + t.value));
//                         duplicateLabels.add(t.value);
//                     } else {
//                         definedLabels.add(t.value);
//                     }
//                     break;
//                 case REF:
//                     referencedLabels.add(t.value);
//                     break;
//                 case INSTR:
//                     if (!Definitions.isOpcode(t.value)) {
//                         errors.add(new Error(t.line, "未知指令: " + t.value));
//                     }
//                     break;
//                 case LIT:
//                     try {
//                         Integer.parseInt(t.value, 16); // 检查十六进制数是否合法
//                     } catch (NumberFormatException ex) {
//                         errors.add(new Error(t.line, "非法常量: " + t.value));
//                     }
//                     break;
//                 default:
//                     // 其它类型暂不处理
//                     break;
//             }
//         }

//         // 2. 检查未定义引用
//         for (String ref : referencedLabels) {
//             if (!definedLabels.contains(ref)) {
//                 // 找到第一处引用位置
//                 int refLine = tokens.stream()
//                         .filter(t -> t.type == TokenType.REF && t.value.equals(ref))
//                         .findFirst()
//                         .map(t -> t.line)
//                         .orElse(-1);
//                 errors.add(new Error(refLine, "引用了未定义的标签: " + ref));
//             }
//         }

//         return errors;
//     }
// }


// package yaku.uxntal;

// import java.util.*;
// import static yaku.uxntal.Definitions.*;

// public class ErrorChecker {

//     public static class Error {
//         public final int line;
//         public final String message;
//         public Error(int line, String message) {
//             this.line = line;
//             this.message = message;
//         }
//         @Override
//         public String toString() { return "第 " + line + " 行: " + message; }
//     }

//     public static class Warning {
//         public final int line;
//         public final String message;
//         public Warning(int line, String message) {
//             this.line = line;
//             this.message = message;
//         }
//         @Override
//         public String toString() { return "第 " + line + " 行: " + message; }
//     }

//     // 辅助结构体用于分配表
//     public static class AllocationInfo {
//         public String name;
//         public int size;
//         public AllocationInfo(String name, int size) {
//             this.name = name; this.size = size;
//         }
//     }

//     // 主错误检查方法
//     public void check(List<Token> tokens) {
//         Map<String, Integer> allocationTable = buildAllocationTable(tokens);
//         boolean foundMain = false;
//         List<Error> errors = new ArrayList<>();
//         List<Warning> warnings = new ArrayList<>();
//         String currentParent = "";
//         for (int i = 0; i < tokens.size(); i++) {
//             Token token = tokens.get(i);
//             Token next = (i+1 < tokens.size()) ? tokens.get(i+1) : new Token(TokenType.EMPTY, "", 0);
//             Token prev = (i-1 >= 0) ? tokens.get(i-1) : new Token(TokenType.EMPTY, "", 0);

//             // 检查是否有MAIN（入口）
//             if (token.type == TokenType.MAIN) foundMain = true;

//             // 1. 零页raw检查（只有main未找到时才有意义）
//             if (!foundMain && token.type == TokenType.RAW) {
//                 errors.add(new Error(token.line, "禁止在零页(raw values in the zero page): " + token));
//             }
//             // 2. 检查LIT/RAW紧跟LD/ST指令
//             if ((token.type == TokenType.LIT || token.type == TokenType.RAW) &&
//                 next.type == TokenType.INSTR &&
//                 next.value.matches("(LD|ST)[ARZ]")) {
//                 errors.add(new Error(token.line, "常量紧跟 load/store 指令: " + token + " " + next));
//             }
//             // 3. 父标签跟踪
//             if (token.type == TokenType.LABEL && token.size == 2) {
//                 currentParent = token.value;
//             }
//             // 4. 引用类型与模式匹配、分配表相关
//             if (token.type == TokenType.REF) {
//                 // 检查引用与LD/ST模式匹配
//                 if (next.type == TokenType.INSTR && next.value.matches("(LD|ST)[ARZ]")) {
//                     char aMode = next.value.charAt(next.value.length() - 1);
//                     String[] accessMode = {"Z", "R", "A", "Z", "R", "A", ""};
//                     // refType: . , ; - _ =
//                     if (aMode != accessMode[token.refType].charAt(0)) {
//                         errors.add(new Error(next.line, "指令寻址模式与引用模式不兼容: " + next + " / " + token));
//                     }
//                     // 检查分配表
//                     String name = (token.isChild == 1 && !currentParent.isEmpty()) ? currentParent + "/" + token.value : token.value;
//                     int allocSz = allocationTable.getOrDefault(name, 0);
//                     int wordSz = next.size;
//                     if (token.refType < 3) { // 只对 .,; 检查分配表
//                         if (allocSz == 0) {
//                             errors.add(new Error(next.line, "未分配空间的引用: " + next + " / " + token + " <" + name + ">"));
//                         } else if (allocSz < wordSz) {
//                             errors.add(new Error(next.line, "分配为1字节，实际访问为2字节: " + next + " / " + token));
//                         } else if (allocSz > wordSz && allocSz == 2) {
//                             warnings.add(new Warning(next.line, "分配比实际访问大（如分配2字节访问1字节）: " + next + " / " + token));
//                         }
//                         // ST 指令 + 常量，检查大小
//                         if (next.value.startsWith("ST") && prev.type == TokenType.LIT) {
//                             if (prev.size > allocSz) {
//                                 warnings.add(new Warning(next.line, "分配空间小于常量大小: " + prev + " " + token + " " + next));
//                             }
//                             if (prev.size != wordSz) {
//                                 warnings.add(new Warning(next.line, "存储与常量大小不一致: " + prev + " " + token + " " + next));
//                             }
//                         }
//                     }
//                 }
//                 // JMP/JCN/JSR与引用模式匹配
//                 else if (next.type == TokenType.INSTR && next.value.matches("(JMP|JCN|JSR)")) {
//                     if (token.refType == 1 && next.size != 1) {
//                         errors.add(new Error(next.line, "跳转引用模式与指令大小不兼容: " + next + " / " + token));
//                     } else if (token.refType == 2 && next.size != 2) {
//                         errors.add(new Error(next.line, "跳转引用模式与指令大小不兼容: " + next + " / " + token));
//                     }
//                 }
//             }
//             // 5. SFT 检查
//             if (token.type == TokenType.LIT &&
//                 next.type == TokenType.INSTR &&
//                 "SFT".equals(next.value)) {
//                 if (token.size == 2) {
//                     errors.add(new Error(token.line, "SFT第二参数必须是byte: " + prev + " " + token + " " + next));
//                 }
//                 if ((prev.type == TokenType.LIT || (prev.type == TokenType.INSTR && prev.value.startsWith("LD"))) &&
//                     prev.size != next.size) {
//                     warnings.add(new Warning(token.line, "SFT模式与前一参数大小不一致: " + prev + " " + token + " " + next));
//                 }
//             }
//         }
//         // 输出警告与错误
//         for (Warning w : warnings) {
//             System.err.println("Warning: " + w);
//         }
//         for (Error e : errors) {
//             System.err.println("Error: " + e);
//         }
//         // 有错误直接终止
//         if (!errors.isEmpty()) throw new RuntimeException("汇编检测发现错误，见上方Error信息！");
//     }

//     // 构建分配表（按perl buildAllocationTable方式实现）
//     public static Map<String, Integer> buildAllocationTable(List<Token> tokens) {
//         Map<String, Integer> alloc = new HashMap<>();
//         String currentParent = "";
//         String currentCfqn = "";
//         String prevConsecutiveLabel = "";
//         for (int i = 0; i < tokens.size(); i++) {
//             Token token = tokens.get(i);
//             Token next = (i+1 < tokens.size()) ? tokens.get(i+1) : new Token(TokenType.EMPTY, "", 0);
//             if (token.type == TokenType.LABEL) {
//                 if (token.size == 2) { // parent
//                     currentParent = token.value;
//                     currentCfqn = "";
//                 } else { // child
//                     currentCfqn = currentParent + "/" + token.value;
//                 }
//                 if (next.type == TokenType.PAD) { // $ 分配
//                     if (!currentCfqn.isEmpty()) {
//                         alloc.put(currentCfqn, Integer.parseInt(next.value, 16));
//                     } else {
//                         alloc.put(currentParent, Integer.parseInt(next.value, 16));
//                     }
//                 } else if (next.type == TokenType.RAW) { // raw分配
//                     if (!currentCfqn.isEmpty()) {
//                         alloc.put(currentCfqn, next.size);
//                     } else {
//                         alloc.put(currentParent, next.size);
//                     }
//                 } else if (next.type == TokenType.LABEL) {
//                     prevConsecutiveLabel = (token.size == 2) ? currentParent : currentCfqn;
//                     continue;
//                 } else {
//                     if (!currentCfqn.isEmpty()) {
//                         alloc.put(currentCfqn, 0);
//                     } else {
//                         alloc.put(currentParent, 0);
//                     }
//                 }
//                 if (!prevConsecutiveLabel.isEmpty()) {
//                     if (!currentCfqn.isEmpty())
//                         alloc.put(prevConsecutiveLabel, alloc.getOrDefault(currentCfqn, 0));
//                     else
//                         alloc.put(prevConsecutiveLabel, alloc.getOrDefault(currentParent, 0));
//                     prevConsecutiveLabel = "";
//                 }
//             }
//         }
//         return alloc;
//     }
// }
