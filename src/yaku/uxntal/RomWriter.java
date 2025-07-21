package yaku.uxntal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RomWriter {
    /**
     * 将内存数据写成二进制 ROM 文件
     * @param memory   汇编得到的虚拟机内存（64K 字节）
     * @param filename 输出文件名（如 "out.rom"）
     * @throws IOException 写文件异常
     */
    public static void write(byte[] memory, String filename) throws IOException {
        Files.write(Paths.get(filename), memory);
    }
}
