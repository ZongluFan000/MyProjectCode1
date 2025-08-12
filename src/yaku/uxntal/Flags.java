package yaku.uxntal;

import java.util.Map;

public class Flags {
    // 全局开关
    public static boolean WRS = false;   // Show working and return stacks at end
    public static boolean PQ  = false;   // Print generated code and quit
    public static boolean WW  = false;   // Fewer warnings and errors
    public static boolean IN  = false;   // Take input from stdin
    public static boolean EE  = false;   // Turn warnings into errors
    public static boolean FF  = false;   // Fatal mode - stop on first error
    public static boolean NSW = false;   // No warnings for stack mismatches

    // 扩展：统一管理调试与详细级别
    public static boolean DBG = false;   // Debug mode
    public static int     VV  = 0;       // Verbosity level (0=quiet, 1=normal, 2=verbose, 3=very verbose)

    /**
     * 在程序启动早期调用一次：从环境变量加载“默认值快照”，之后不再直接读取环境变量。
     * 仅处理与 Perl 对齐的两项：YAKU_VERBOSE, YAKU_DBG。
     */
    public static void applyEnvDefaults() {
        String vEnv = System.getenv("YAKU_VERBOSE");
        if (vEnv != null) {
            try { VV = Integer.parseInt(vEnv.trim()); } catch (NumberFormatException ignored) {}
        }
        String dbgEnv = System.getenv("YAKU_DBG");
        if (isTruthy(dbgEnv)) {
            DBG = true;
        }
    }

    // --- 便捷 setter（对齐 Main 的调用） ---
    public static void setVerbosity(Object obj) {
        if (obj == null) return;
        try {
            if (obj instanceof Integer) VV = (Integer) obj;
            else if (obj instanceof String) VV = Integer.parseInt(((String)obj).trim());
        } catch (NumberFormatException ignored) {}
    }
    public static void setDebug(boolean v) { DBG = v; }

    // Setter（通用）
    public static void setFlag(String flagName, boolean value) {
        switch (flagName) {
            case "WRS": WRS = value; break;
            case "PQ":  PQ  = value; break;
            case "WW":  WW  = value; break;
            case "IN":  IN  = value; break;
            case "EE":  EE  = value; break;
            case "FF":  FF  = value; break;
            case "NSW": NSW = value; break;
            case "DBG": DBG = value; break;
            default: throw new IllegalArgumentException("Unknown flag: " + flagName);
        }
    }
    public static void setFlag(String flagName, int value) {
        if ("VV".equals(flagName)) {
            VV = value;
        } else {
            throw new IllegalArgumentException("Unknown int flag: " + flagName);
        }
    }

    // Getter（通用）
    public static boolean getFlag(String flagName) {
        switch (flagName) {
            case "WRS": return WRS;
            case "PQ":  return PQ;
            case "WW":  return WW;
            case "IN":  return IN;
            case "EE":  return EE;
            case "FF":  return FF;
            case "NSW": return NSW;
            case "DBG": return DBG;
            default: throw new IllegalArgumentException("Unknown flag: " + flagName);
        }
    }
    public static int getFlagInt(String flagName) {
        if ("VV".equals(flagName)) return VV;
        throw new IllegalArgumentException("Unknown int flag: " + flagName);
    }

    // 重置为默认值
    public static void resetFlags() {
        WRS = PQ = WW = IN = EE = FF = NSW = DBG = false;
        VV = 0;
    }

    /**
     * 从 options 设置（CLI/配置入口）。
     * 兼容两类键名：驼峰(showStacks) 和 连字符(show-stacks)。
     */
    public static void setFlagsFromOptions(Map<String, Object> options) {
        // booleans
        if (getBool(options, "show-stacks", "showStacks")) WRS = true;
        if (getBool(options, "print-and-quit", "printAndQuit")) PQ = true;
        if (getBool(options, "fewer-warnings", "fewerWarnings")) WW = true;
        if (getBool(options, "stdin")) IN = true;
        if (getBool(options, "errors-from-warnings", "errorsFromWarnings")) EE = true;
        if (getBool(options, "fatal")) FF = true;
        if (getBool(options, "no-stack-warnings", "noStackWarnings")) NSW = true;
        if (getBool(options, "debug")) DBG = true;

        // verbosity
        Object v = get(options, "verbose");
        if (v != null) setVerbosity(v);
    }

    // --- 便捷语义别名（Main/其他模块可直接用） ---
    public static boolean isDebug()               { return DBG; }
    public static int     verbosity()             { return VV; }
    public static boolean isFewerWarnings()       { return WW; }
    public static boolean isNSW()                 { return NSW; }
    public static boolean warningsBecomeErrors()  { return EE; }
    public static boolean stopOnFirstError()      { return FF; }

    // 旧版便捷 getter（保持不破坏现有调用）
    public static boolean shouldShowStacks()              { return WRS; }
    public static boolean shouldPrintAndQuit()            { return PQ; }
    public static boolean shouldShowFewerWarnings()       { return WW; }
    public static boolean shouldUseStdin()                { return IN; }
    public static boolean shouldTreatWarningsAsErrors()   { return EE; }
    public static boolean shouldStopOnFirstError()        { return FF; }
    public static boolean shouldSuppressStackWarnings()   { return NSW; }
    public static boolean isDebugMode()                   { return DBG; }
    public static int    getVerbosity()                   { return VV; }

    // 静态调试输出（无需实例化）
    public static String debugFlags() {
        return new Flags().toString();
    }

    // 人类可读输出
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Flags: ");
        boolean any = false;
        if (WRS) { sb.append("WRS, "); any = true; }
        if (PQ)  { sb.append("PQ, ");  any = true; }
        if (WW)  { sb.append("WW, ");  any = true; }
        if (IN)  { sb.append("IN, ");  any = true; }
        if (EE)  { sb.append("EE, ");  any = true; }
        if (FF)  { sb.append("FF, ");  any = true; }
        if (NSW) { sb.append("NSW, "); any = true; }
        if (DBG) { sb.append("DBG, "); any = true; }
        if (VV != 0) { sb.append("VV=").append(VV).append(", "); any = true; }
        if (!any) sb.append("none set");
        else sb.setLength(sb.length() - 2); // 去掉末尾逗号空格
        return sb.toString();
    }

    // ---- 私有辅助 ----
    private static boolean getBool(Map<String, Object> options, String... keys) {
        for (String k : keys) {
            Object v = options.get(k);
            if (v instanceof Boolean && (Boolean) v) return true;
            if (v instanceof String)  {
                String s = ((String)v).trim().toLowerCase();
                if ("1".equals(s) || "true".equals(s) || "yes".equals(s) || "y".equals(s) || "on".equals(s))
                    return true;
            }
        }
        return false;
    }
    private static Object get(Map<String, Object> options, String... keys) {
        for (String k : keys) if (options.containsKey(k)) return options.get(k);
        return null;
    }
    private static boolean isTruthy(String v) {
        if (v == null) return false;
        String s = v.trim().toLowerCase();
        return "1".equals(s) || "true".equals(s) || "yes".equals(s) || "y".equals(s) || "on".equals(s);
    }
}
