package yaku.uxntal;

import java.lang.reflect.*;
import java.util.*;
import java.nio.file.*;
import yaku.uxntal.units.UxnState;


public final class Main {

    public static void main(String[] args) {
        try {
            if (args.length >= 2 && "-r".equals(args[0])) {
                String talPath = args[1];
                System.out.println(">>");
                AsmBundle asm = assemble(talPath);
                runInterpreter(asm);
                return;
            }

            if (args.length >= 2 && "-a".equals(args[0])) {
                String talPath = args[1];
                String outPath = findOutPathArg(args, "-o");
                if (outPath == null) outPath = deriveRomPath(talPath);

                System.out.println(">>");
                AsmBundle asm = assemble(talPath);

                Path out = Paths.get(outPath);
                if (out.getParent() != null) Files.createDirectories(out.getParent());

                // 使用 Encoder.EncodeResult 上的方法
                if (!tryWriteRomViaEncodeResult(asm.encObj, out)) {
                    // 退化 1：尝试 romSlice()
                    byte[] slice = tryGetRomSlice(asm.encObj);
                    if (slice == null) {
                        // 退化 2：自己依据 memory + maxAddr 裁剪；再退化则从 0x0100 到末尾去掉尾部 0
                        Integer maxAddr = getFieldOrNull(asm.encObj, "maxAddr", Integer.class);
                        slice = trimRomFromMemory(asm.program, maxAddr);
                    }
                    Files.write(out, slice);
                }

                // 计算最终大小用于提示
                long size = Files.size(out);
                System.out.printf("Wrote ROM: %s (%d bytes)%n", out.toString(), size);
                return;
            }

            printUsage();
        } catch (Throwable t) {
            System.err.println("Error: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -cp out yaku.uxntal.Main -r <path/to/program.tal>");
        System.out.println("  java -cp out yaku.uxntal.Main -a <path/to/program.tal> [-o <output.rom>]");
    }

    private static String findOutPathArg(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) return args[i + 1];
        }
        return null;
    }

    private static String deriveRomPath(String talPath) {
        int dot = Math.max(talPath.lastIndexOf('.'), -1);
        int slash = Math.max(talPath.lastIndexOf('/'), talPath.lastIndexOf('\\'));
        if (dot > slash) return talPath.substring(0, dot) + ".rom";
        return talPath + ".rom";
    }

    //Assembly flow

    private static AsmBundle assemble(String talPath) throws Exception {
        UxnState ourUxn = new UxnState();

        
        Class<?> parserK = Class.forName("yaku.uxntal.Parser");
        Method pMethod = selectParserMethod(parserK);
        Object parserInstance = Modifier.isStatic(pMethod.getModifiers()) ? null : newInstance(parserK);

        Object[] pArgs = buildParserArgs(pMethod, talPath, ourUxn);
        Object parseRes = invoke(pMethod, parserInstance, pArgs);

        List<?> tokens = getFieldOrNull(parseRes, "tokens", List.class);
        if (tokens == null) throw new IllegalStateException("Parser result does not expose 'tokens'.");

        Object uxnObj = getFieldOrNull(parseRes, "uxn", Object.class);
        if (uxnObj == null && pArgs.length == 2) uxnObj = pArgs[1];

        // Encoder.encode(...)
        Class<?> encK = Class.forName("yaku.uxntal.Encoder");
        Object encRes = tryCallEncode(encK, tokens, uxnObj); // 可能是 byte[] 或 EncodeResult

        // program: 如果是 EncodeResult，抓 memory；否则直接就是 byte[]
        byte[] program = (encRes instanceof byte[])
                ? (byte[]) encRes
                : firstByteArrayField(encRes); // EncodeResult.memory

        if (program == null) throw new IllegalStateException("Encoder result does not contain program bytes.");

        Map<Integer, ?> revSym = getFieldOrNull(encRes, "reverseSymbolTable", Map.class);
        if (revSym == null && uxnObj != null) {
            revSym = getFieldOrNull(uxnObj, "reverseSymbolTable", Map.class);
        }
        if (revSym == null) revSym = Collections.emptyMap();

        return new AsmBundle(program, tokens, revSym, uxnObj, encRes);
    }

    //Parser selection helpers

    private static Method selectParserMethod(Class<?> parserK) {
        String[] names = {"parseProgram", "parseFile", "parse"};
        List<Method> candidates = new ArrayList<>();

        for (Method m : parserK.getDeclaredMethods()) {
            for (String want : names) if (m.getName().equals(want)) candidates.add(m);
        }
        if (candidates.isEmpty()) {
            for (Method m : parserK.getMethods()) {
                for (String want : names) if (m.getName().equals(want)) candidates.add(m);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No suitable Parser method found (parseProgram/parseFile/parse).");
        }

        candidates.sort((a, b) -> {
            int s = (Modifier.isStatic(b.getModifiers()) ? 1 : 0) - (Modifier.isStatic(a.getModifiers()) ? 1 : 0);
            if (s != 0) return s;
            int pc = b.getParameterCount() - a.getParameterCount();
            if (pc != 0) return pc;
            return namePriority(a.getName()) - namePriority(b.getName());
        });

        Method best = candidates.get(0);
        best.setAccessible(true);
        return best;
    }

    private static int namePriority(String name) {
        if ("parseProgram".equals(name)) return 0;
        if ("parseFile".equals(name)) return 1;
        if ("parse".equals(name)) return 2;
        return 3;
    }

    private static Object[] buildParserArgs(Method m, String talPath, UxnState ourUxn) throws Exception {
        Class<?>[] pt = m.getParameterTypes();
        if (pt.length == 1) {
            if (pt[0] != String.class) throw new IllegalStateException("Parser method takes 1 param but not String.");
            return new Object[]{ talPath };
        } else if (pt.length == 2) {
            if (pt[0] != String.class) throw new IllegalStateException("Parser method 1st param must be String.");
            Class<?> uxnt = pt[1];
            Object uxnArg;
            if (uxnt.isAssignableFrom(UxnState.class)) {
                uxnArg = ourUxn;
            } else {
                Constructor<?> c = uxnt.getDeclaredConstructor();
                c.setAccessible(true);
                uxnArg = c.newInstance();
            }
            return new Object[]{ talPath, uxnArg };
        } else {
            throw new IllegalStateException("Parser method param count not supported: " + pt.length);
        }
    }

    //Encoder reflection

    private static Object tryCallEncode(Class<?> encK, List<?> tokens, Object uxnObj) throws Exception {
        // Try static encode
        for (Method m : encK.getDeclaredMethods()) {
            if (!m.getName().equals("encode")) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 2 && List.class.isAssignableFrom(pt[0])) {
                m.setAccessible(true);
                return m.invoke(null, tokens, uxnObj);
            }
        }
        for (Method m : encK.getMethods()) {
            if (!m.getName().equals("encode")) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 2 && List.class.isAssignableFrom(pt[0])) {
                return m.invoke(null, tokens, uxnObj);
            }
        }

        // Try static encode(List)
        try {
            Method m = encK.getMethod("encode", List.class);
            return m.invoke(null, tokens);
        } catch (NoSuchMethodException ignore) {}

        // Try instance encode(...)
        Object enc = newInstance(encK);
        for (Method m : encK.getDeclaredMethods()) {
            if (!m.getName().equals("encode")) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 2 && List.class.isAssignableFrom(pt[0])) {
                m.setAccessible(true);
                return m.invoke(enc, tokens, uxnObj);
            }
            if (pt.length == 1 && List.class.isAssignableFrom(pt[0])) {
                m.setAccessible(true);
                return m.invoke(enc, tokens);
            }
        }
        for (Method m : encK.getMethods()) {
            if (!m.getName().equals("encode")) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 2 && List.class.isAssignableFrom(pt[0])) {
                return m.invoke(enc, tokens, uxnObj);
            }
            if (pt.length == 1 && List.class.isAssignableFrom(pt[0])) {
                return m.invoke(enc, tokens);
            }
        }

        throw new IllegalStateException("No suitable Encoder.encode(...) found.");
    }

    //Interpreter launch logic/

    private static void runInterpreter(AsmBundle asm) throws Exception {
        Class<?> ik = Class.forName("yaku.uxntal.Interpreter");

        // Attempt 0: Interpreter(byte[], List, Map).run()
        try {
            Constructor<?> c = ik.getConstructor(byte[].class, List.class, Map.class);
            Object vm = c.newInstance(asm.program, asm.tokens, asm.reverseSymbolTable);
            Method runM = ik.getMethod("run");
            runM.invoke(vm);
            return;
        } catch (NoSuchMethodException ignore) {}

        // Attempt 1: new Interpreter(byte[]).run()
        try {
            Constructor<?> c = ik.getConstructor(byte[].class);
            Object vm = c.newInstance(asm.program);
            Method runM = ik.getMethod("run");
            runM.invoke(vm);
            return;
        } catch (NoSuchMethodException ignore) {}

        // Attempt 2: Interpreter.runProgram(byte[])
        try {
            Method runProg = ik.getMethod("runProgram", byte[].class);
            runProg.invoke(null, asm.program);
            return;
        } catch (NoSuchMethodException ignore) {}

        
        try {
            
            try {
                Constructor<?> c = ik.getConstructor(UxnState.class);
                Object arg = (asm.uxnObj instanceof UxnState) ? asm.uxnObj : new UxnState();
                Object vm = c.newInstance(arg);
                Method runM = ik.getMethod("run");
                runM.invoke(vm);
                return;
            } catch (NoSuchMethodException ignored) {}

       
            if (asm.uxnObj != null) {
                for (Constructor<?> c : ik.getConstructors()) {
                    Class<?>[] pt = c.getParameterTypes();
                    if (pt.length == 1 && pt[0].getSimpleName().equals("UxnState") && pt[0].isInstance(asm.uxnObj)) {
                        Object vm = c.newInstance(asm.uxnObj);
                        Method runM = ik.getMethod("run");
                        runM.invoke(vm);
                        return;
                    }
                }
            }
        } catch (ReflectiveOperationException ignore) {}

        throw new IllegalStateException(
            "No compatible Interpreter found. Tried: " +
            "Interpreter(byte[], List, Map).run(), new Interpreter(byte[]).run(), " +
            "Interpreter.runProgram(byte[]), new Interpreter(UxnState).run(), " +
            "new Interpreter(<any UxnState-like>).run()."
        );
    }

    //utils

    private static Object newInstance(Class<?> k) {
        try {
            Constructor<?> c = k.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate " + k.getName() + " (needs no-arg ctor).", e);
        }
    }

    private static Object invoke(Method m, Object recv, Object[] args) {
        try {
            m.setAccessible(true);
            return m.invoke(recv, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(m.getDeclaringClass().getName() + "." + m.getName() + " invocation failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getFieldOrNull(Object o, String name, Class<T> type) {
        if (o == null) return null;
        try {
            Field f = findField(o.getClass(), name);
            f.setAccessible(true);
            Object v = f.get(o);
            if (v == null) return null;
            if (!type.isInstance(v)) return null;
            return (T) v;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Field findField(Class<?> k, String name) throws NoSuchFieldException {
        Class<?> cur = k;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static byte[] firstByteArrayField(Object o) throws Exception {
        if (o == null) return null;
        for (Field f : getAllFields(o.getClass())) {
            if (f.getType() == byte[].class) {
                f.setAccessible(true);
                return (byte[]) f.get(o);
            }
        }
        return null;
    }

    private static List<Field> getAllFields(Class<?> k) {
        List<Field> res = new ArrayList<>();
        Class<?> cur = k;
        while (cur != null) {
            res.addAll(Arrays.asList(cur.getDeclaredFields()));
            cur = cur.getSuperclass();
        }
        return res;
    }

    //ROM 写出与裁剪

    // 调用 EncodeResult.writeRom(Path)
    private static boolean tryWriteRomViaEncodeResult(Object encObj, Path out) {
        if (encObj == null) return false;
        try {
            Method m = encObj.getClass().getMethod("writeRom", Path.class);
            m.invoke(encObj, out);
            return true;
        } catch (ReflectiveOperationException ignore) {
            return false;
        }
    }

    // 若有 EncodeResult.romSlice() 则取之
    private static byte[] tryGetRomSlice(Object encObj) {
        if (encObj == null) return null;
        try {
            Method m = encObj.getClass().getMethod("romSlice");
            Object r = m.invoke(encObj);
            return (r instanceof byte[]) ? (byte[]) r : null;
        } catch (ReflectiveOperationException ignore) {
            return null;
        }
    }

    // 回退方案：从 memory 中按 ORIGIN 裁剪，优先使用 maxAddr 决定终止位置，并去掉尾部 0
    private static byte[] trimRomFromMemory(byte[] memory, Integer maxAddr) {
        final int ORIGIN = Definitions.MAIN_ADDRESS;
        if (memory == null || memory.length <= ORIGIN) return new byte[0];

        int end = (maxAddr != null) ? Math.max(ORIGIN, Math.min(memory.length, maxAddr + 1))
                                    : memory.length;

        while (end > ORIGIN && memory[end - 1] == 0) end--;
        return Arrays.copyOfRange(memory, ORIGIN, end);
    }

    //data holder

    private static final class AsmBundle {
        final byte[] program;                 // 通常是 64 KiB memory
        final List<?> tokens;
        final Map<Integer, ?> reverseSymbolTable;
        final Object uxnObj;
        final Object encObj;                  // 保留 Encoder.encode(...) 的原始返回，用于 -a

        AsmBundle(byte[] program, List<?> tokens, Map<Integer, ?> reverseSymbolTable, Object uxnObj, Object encObj) {
            this.program = Objects.requireNonNull(program, "program == null");
            this.tokens = (tokens != null) ? tokens : Collections.emptyList();
            this.reverseSymbolTable = (reverseSymbolTable != null) ? reverseSymbolTable : Collections.emptyMap();
            this.uxnObj = uxnObj;
            this.encObj = encObj;
        }
    }
}
