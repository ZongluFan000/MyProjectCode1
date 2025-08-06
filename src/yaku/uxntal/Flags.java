package yaku.uxntal;

import java.util.Map;

public class Flags {
    // 所有开关参数，全为 static 全局
    public static boolean WRS = false;   // Show working and return stacks at end
    public static boolean PQ  = false;   // Print generated code and quit
    public static boolean WW  = false;   // Fewer warnings and errors
    public static boolean IN  = false;   // Take input from stdin
    public static boolean EE  = false;   // Turn warnings into errors
    public static boolean FF  = false;   // Fatal mode - stop on first error
    public static boolean NSW = false;   // No warnings for stack mismatches
    public static boolean DBG = false;   // Debug mode
    public static int     VV  = 0;       // Verbosity level (0=quiet, 1=normal, 2=verbose, 3=very verbose)

    // Setter 
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

    // Getter
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

    //重置全部为默认值
    public static void resetFlags() {
        WRS = PQ = WW = IN = EE = FF = NSW = DBG = false;
        VV = 0;
    }

    
    public static void setFlagsFromOptions(Map<String, Object> options) {
        if (getBool(options, "showStacks")) WRS = true;
        if (getBool(options, "printAndQuit")) PQ = true;
        if (getBool(options, "fewerWarnings")) WW = true;
        if (getBool(options, "stdin")) IN = true;
        if (getBool(options, "errorsFromWarnings")) EE = true;
        if (getBool(options, "fatal")) FF = true;
        if (getBool(options, "noStackWarnings")) NSW = true;
        if (getBool(options, "debug")) DBG = true;
        if (options.containsKey("verbose") && options.get("verbose") != null)
            VV = parseInt(options.get("verbose"));
    }
    private static boolean getBool(Map<String, Object> options, String key) {
        Object v = options.get(key);
        if (v instanceof Boolean) return (Boolean)v;
        if (v instanceof String)  return Boolean.parseBoolean((String)v);
        return false;
    }
    private static int parseInt(Object obj) {
        if (obj instanceof Integer) return (Integer)obj;
        if (obj instanceof String)  return Integer.parseInt((String)obj);
        return 0;
    }

    //Flag便捷getter（对齐JS）
    public static boolean shouldShowStacks()         { return WRS; }
    public static boolean shouldPrintAndQuit()       { return PQ; }
    public static boolean shouldShowFewerWarnings()  { return WW; }
    public static boolean shouldUseStdin()           { return IN; }
    public static boolean shouldTreatWarningsAsErrors() { return EE; }
    public static boolean shouldStopOnFirstError()   { return FF; }
    public static boolean shouldSuppressStackWarnings() { return NSW; }
    public static boolean isDebugMode() {
        String dbgEnv = System.getenv("YAKU_DBG");
        return DBG || (dbgEnv != null && dbgEnv.equals("1"));
    }
    public static int getVerbosity() {
        String vEnv = System.getenv("YAKU_VERBOSE");
        if (vEnv != null) {
            try { return Integer.parseInt(vEnv); }
            catch (NumberFormatException e) { return 0; }
        }
        return VV;
    }

    // 单独暴露每个 flag 的 getter 方便调用
    public static boolean WRS() { return WRS; }
    public static boolean PQ()  { return PQ; }
    public static boolean WW()  { return WW; }
    public static boolean IN()  { return IN; }
    public static boolean EE()  { return EE; }
    public static boolean FF()  { return FF; }
    public static boolean NSW() { return NSW; }
    public static boolean DBG() { return isDebugMode(); }
    public static int VV()      { return getVerbosity(); }

    //调试输出当前所有 flags 状态，类似 debugFlags() 
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Flags: ");
        boolean any = false;
        if (WRS) { sb.append("WRS, "); any=true; }
        if (PQ)  { sb.append("PQ, "); any=true; }
        if (WW)  { sb.append("WW, "); any=true; }
        if (IN)  { sb.append("IN, "); any=true; }
        if (EE)  { sb.append("EE, "); any=true; }
        if (FF)  { sb.append("FF, "); any=true; }
        if (NSW) { sb.append("NSW, "); any=true; }
        if (DBG) { sb.append("DBG, "); any=true; }
        if (VV != 0) { sb.append("VV=").append(VV).append(", "); any=true; }
        if (!any) sb.append("none set");
        else sb.setLength(sb.length() - 2); // 去掉最后的逗号和空格
        return sb.toString();
    }
}
