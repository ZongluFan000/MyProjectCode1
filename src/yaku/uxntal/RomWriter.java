package yaku.uxntal;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * RomWriter
 * 将内存内容导出为 .rom 文件（适用于 Uxn 虚拟机或硬件烧录）
 * 自动限制文件大小为 64KB，保证二进制格式正确。
 */
public class RomWriter {

    private static final int MAX_ROM_SIZE = 0x10000; // 64KB

    /**
     * 将内存数组写入 .rom 文件
     *
     * @param memory    内存数据（通常是 64KB 大小）
     * @param writeFile 是否实际写入文件（false 时仅调试输出）
     * @param filename  输出文件名
     * @throws IOException 文件写入异常
     */
    public static void memToRom(byte[] memory, boolean writeFile, String filename) throws IOException {
        if (!writeFile) {
            System.out.println("[RomWriter] 测试模式：未写入 ROM 文件 (" + filename + ")");
            return;
        }

        // 限制写入大小到 64KB
        int sizeToWrite = Math.min(memory.length, MAX_ROM_SIZE);

        try (FileOutputStream fos = new FileOutputStream(filename)) {
            fos.write(memory, 0, sizeToWrite);
        }

        // 调试输出
        System.out.printf(
            "[RomWriter] ROM 文件已写入：%s，大小 %d 字节 (0x%X)%n",
            filename, sizeToWrite, sizeToWrite
        );
    }
}
