package yaku.uxntal;

import java.util.*;
import static yaku.uxntal.Definitions.*;

public class ErrorChecker {

    public static class Error {
        public final int line;
        public final String message;

        public Error(int line, String message) {
            this.line = line;
            this.message = message;
        }

        @Override
        public String toString() {
            return "第 " + line + " 行: " + message;
        }
    }

    public List<Error> check(List<Token> tokens) {
        List<Error> errors = new ArrayList<>();
        Set<String> definedLabels = new HashSet<>();
        Set<String> referencedLabels = new HashSet<>();
        Set<String> duplicateLabels = new HashSet<>();

        // 1. 记录所有定义和引用的标签，并检测指令合法性
        for (Token t : tokens) {
            switch (t.type) {
                case LABEL:
                    if (definedLabels.contains(t.value)) {
                        errors.add(new Error(t.line, "标签重复定义: " + t.value));
                        duplicateLabels.add(t.value);
                    } else {
                        definedLabels.add(t.value);
                    }
                    break;
                case REF:
                    referencedLabels.add(t.value);
                    break;
                case INSTR:
                    if (!Definitions.isOpcode(t.value)) {
                        errors.add(new Error(t.line, "未知指令: " + t.value));
                    }
                    break;
                case LIT:
                    try {
                        Integer.parseInt(t.value, 16); // 检查十六进制数是否合法
                    } catch (NumberFormatException ex) {
                        errors.add(new Error(t.line, "非法常量: " + t.value));
                    }
                    break;
                default:
                    // 其它类型暂不处理
                    break;
            }
        }

        // 2. 检查未定义引用
        for (String ref : referencedLabels) {
            if (!definedLabels.contains(ref)) {
                // 找到第一处引用位置
                int refLine = tokens.stream()
                        .filter(t -> t.type == TokenType.REF && t.value.equals(ref))
                        .findFirst()
                        .map(t -> t.line)
                        .orElse(-1);
                errors.add(new Error(refLine, "引用了未定义的标签: " + ref));
            }
        }

        return errors;
    }
}
