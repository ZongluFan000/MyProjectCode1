package yaku.util;


/**
 * Uxn虚拟机数字转换工具类
 * <p>
 * 提供Uxn虚拟机所需的数字转换工具方法，包括有符号/无符号字节和短整型的转换以及2的补码处理。
 * 所有方法均为静态方法，可直接通过类名调用。
 * </p>
 */
public final class NumberUtils {

    // 私有构造方法防止实例化
    private NumberUtils() {
        throw new AssertionError("Cannot instantiate utility class");
    }

    /**
     * 将有符号字节转换为2的补码表示的无符号字节值
     *
     * @param b 要转换的有符号字节值
     * @return 转换后的无符号字节值(0-255)
     * @example 
     * <pre>
     * byte signed = -1; // 0xFF
     * int unsigned = signedByteToByte2sComp(signed); // 返回255
     * </pre>
     */
    public static int signedByteToByte2sComp(byte b) {
        return b & 0xFF;
    }

    /**
     * 将2的补码表示的无符号字节值转换为有符号字节
     *
     * @param b 要转换的无符号字节值(0-255)
     * @return 转换后的有符号字节值
     * @throws IllegalArgumentException 如果输入值不在0-255范围内
     * @example 
     * <pre>
     * int unsigned = 255;
     * byte signed = byte2sCompToSignedByte(unsigned); // 返回-1
     * </pre>
     */
    public static byte byte2sCompToSignedByte(int b) {
        if (b < 0 || b > 255) {
            throw new IllegalArgumentException("Input value must be between 0 and 255");
        }
        return (byte) ((b < 0x80) ? b : b - 0x100);
    }

    /**
     * 将有符号短整型转换为2的补码表示的无符号短整型值
     *
     * @param s 要转换的有符号短整型值
     * @return 转换后的无符号短整型值(0-65535)
     * @example 
     * <pre>
     * short signed = -1; // 0xFFFF
     * int unsigned = signedShortToShort2sComp(signed); // 返回65535
     * </pre>
     */
    public static int signedShortToShort2sComp(short s) {
        return s & 0xFFFF;
    }

    /**
     * 将2的补码表示的无符号短整型值转换为有符号短整型
     *
     * @param s 要转换的无符号短整型值(0-65535)
     * @return 转换后的有符号短整型值
     * @throws IllegalArgumentException 如果输入值不在0-65535范围内
     * @example 
     * <pre>
     * int unsigned = 65535;
     * short signed = short2sCompToSignedShort(unsigned); // 返回-1
     * </pre>
     */
    public static short short2sCompToSignedShort(int s) {
        if (s < 0 || s > 65535) {
            throw new IllegalArgumentException("Input value must be between 0 and 65535");
        }
        return (short) ((s < 0x8000) ? s : s - 0x10000);
    }

    /**
     * 将两个无符号字节组合成一个无符号短整型值
     *
     * @param hi 高位字节(0-255)
     * @param lo 低位字节(0-255)
     * @return 组合后的无符号短整型值(0-65535)
     * @throws IllegalArgumentException 如果任一输入值不在0-255范围内
     * @example 
     * <pre>
     * int hi = 0x12;
     * int lo = 0x34;
     * int result = unsignedBytesToUnsignedShort(hi, lo); // 返回0x1234
     * </pre>
     */
    public static int unsignedBytesToUnsignedShort(int hi, int lo) {
        if (hi < 0 || hi > 255 || lo < 0 || lo > 255) {
            throw new IllegalArgumentException("Byte values must be between 0 and 255");
        }
        return (hi << 8) | lo;
    }

    /**
     * 将无符号短整型值拆分为两个无符号字节
     *
     * @param value 要拆分的无符号短整型值(0-65535)
     * @return 包含高位和低位字节的数组，格式为[hi, lo]
     * @throws IllegalArgumentException 如果输入值不在0-65535范围内
     * @example 
     * <pre>
     * int value = 0x1234;
     * int[] bytes = unsignedShortToUnsignedBytes(value); // 返回[0x12, 0x34]
     * </pre>
     */
    public static int[] unsignedShortToUnsignedBytes(int value) {
        if (value < 0 || value > 65535) {
            throw new IllegalArgumentException("Input value must be between 0 and 65535");
        }
        return new int[]{(value >>> 8) & 0xFF, value & 0xFF};
    }

    /**
     * 将2的补码表示的短整型值拆分为两个字节
     *
     * @param value 要拆分的短整型值
     * @return 包含高位和低位字节的数组，格式为[hi, lo]
     * @example 
     * <pre>
     * short value = -1; // 0xFFFF
     * int[] bytes = short2sComptToBytes2sComp(value); // 返回[0xFF, 0xFF]
     * </pre>
     */
    public static int[] short2sComptToBytes2sComp(short value) {
        return new int[]{(value >> 8) & 0xFF, value & 0xFF};
    }

    /**
     * 将两个字节组合成2的补码表示的短整型值
     *
     * @param hi 高位字节(0-255)
     * @param lo 低位字节(0-255)
     * @return 组合后的短整型值
     * @throws IllegalArgumentException 如果任一输入值不在0-255范围内
     * @example 
     * <pre>
     * int hi = 0xFF;
     * int lo = 0xFF;
     * short result = bytes2sCompToShort2sComp(hi, lo); // 返回-1
     * </pre>
     */
    public static short bytes2sCompToShort2sComp(int hi, int lo) {
        if (hi < 0 || hi > 255 || lo < 0 || lo > 255) {
            throw new IllegalArgumentException("Byte values must be between 0 and 255");
        }
        return (short) ((hi << 8) | lo);
    }

    /**
     * 将两个无符号字节组合成2的补码短整型值
     *
     * @param hi 高位字节(0-255)
     * @param lo 低位字节(0-255)
     * @return 组合后的有符号短整型值
     * @throws IllegalArgumentException 如果任一输入值不在0-255范围内
     * @example 
     * <pre>
     * int hi = 0xFF;
     * int lo = 0xFF;
     * short result = bytes2sCompToSignedShort(hi, lo); // 返回-1
     * </pre>
     */
    public static short bytes2sCompToSignedShort(int hi, int lo) {
        if (hi < 0 || hi > 255 || lo < 0 || lo > 255) {
            throw new IllegalArgumentException("Byte values must be between 0 and 255");
        }
        int value = (hi << 8) | lo;
        return (short) ((value < 0x8000) ? value : value - 0x10000);
    }

    /**
     * 将数值转换为指定长度的十六进制字符串
     *
     * @param value 要转换的数值
     * @param size 数值大小(1=字节，2=短整型)
     * @return 指定长度的十六进制字符串(小写)
     * @throws IllegalArgumentException 如果size不是1或2
     * @example 
     * <pre>
     * int value = 255;
     * String hex1 = toHex(value, 1); // 返回"ff"
     * String hex2 = toHex(value, 2); // 返回"00ff"
     * </pre>
     */
    public static String toHex(int value, int size) {
        if (size != 1 && size != 2) {
            throw new IllegalArgumentException("Size must be 1 (byte) or 2 (short)");
        }
        
        int digits = size * 2;
        
        if (value < 0) {
            // 处理负数：转换为2的补码
            value = (1 << (size * 8)) + value;
        }
        
        return String.format("%0" + digits + "x", value);
    }
    
    /**
     * 将字节值转换为2位十六进制字符串
     *
     * @param value 字节值
     * @return 2位十六进制字符串(小写)
     * @example 
     * <pre>
     * int value = 255;
     * String hex = byteToHex(value); // 返回"ff"
     * </pre>
     */
    public static String byteToHex(int value) {
        return String.format("%02x", value & 0xFF);
    }
    
    /**
     * 将短整型值转换为4位十六进制字符串
     *
     * @param value 短整型值
     * @return 4位十六进制字符串(小写)
     * @example 
     * <pre>
     * int value = 65535;
     * String hex = shortToHex(value); // 返回"ffff"
     * </pre>
     */
    public static String shortToHex(int value) {
        return String.format("%04x", value & 0xFFFF);
    }
}