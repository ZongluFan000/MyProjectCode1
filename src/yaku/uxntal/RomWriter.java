package yaku.uxntal;

import java.io.FileOutputStream;
import java.io.IOException;


//将内存内容导出为 .rom 文件（适用于 Uxn 虚拟机或硬件烧录）
 
public class RomWriter {

    public static void memToRom(byte[] memory, boolean writeFile, String filename) throws IOException {
        if (!writeFile) {
            System.out.println("[RomWriter] 测试模式：未写入 ROM 文件 (" + filename + ")");
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            fos.write(memory);
            System.out.println("[RomWriter] ROM 文件已写入：" + filename + "，大小 " + memory.length + " 字节");
        }
    }
}
