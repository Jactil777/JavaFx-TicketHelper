package com.jactil.javafx.tickethelper.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 应用图标生成工具
 * 运行此类的 main 方法即可在 resources/images/ 目录下生成 app.png 和 app.ico
 * 图标设计：蓝色渐变背景 + 白色火车票 + 列车符号
 */
public class IconGenerator {

    private static final int SIZE = 256;

    public static void main(String[] args) throws IOException {
        String dir = "src/main/resources/images";
        new File(dir).mkdirs();

        BufferedImage icon = generateIcon(SIZE);

        // 保存 PNG
        ImageIO.write(icon, "PNG", new File(dir + "/app.png"));
        System.out.println("PNG icon saved: " + dir + "/app.png");

        // 保存 ICO
        saveAsIco(icon, dir + "/app.ico");
        System.out.println("ICO icon saved: " + dir + "/app.ico");

        System.out.println("Done! Use app.ico for jpackage --icon parameter.");
    }

    /**
     * 生成图标图像
     * 设计：蓝色渐变背景 + 白色圆角矩形（火车票）+ 列车符号
     */
    public static BufferedImage generateIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. 蓝色渐变背景（圆角矩形）
        int margin = 0;
        GradientPaint bgGradient = new GradientPaint(
                0, 0, new Color(30, 100, 200),
                size, size, new Color(10, 50, 150)
        );
        g.setPaint(bgGradient);
        g.fillRoundRect(margin, margin, size - margin * 2, size - margin * 2, size / 8, size / 8);

        // 2. 白色火车票形状（圆角矩形 + 两侧半圆缺口）
        int ticketMargin = size / 8;
        int ticketW = size - ticketMargin * 2;
        int ticketH = size / 2;
        int ticketY = (size - ticketH) / 2 + size / 10;
        int cornerRadius = size / 12;

        g.setColor(new Color(255, 255, 255, 240));
        g.fillRoundRect(ticketMargin, ticketY, ticketW, ticketH, cornerRadius, cornerRadius);

        // 两侧半圆缺口（模拟火车票打孔效果）
        g.setColor(new Color(30, 100, 200));
        int notchRadius = size / 16;
        // 左侧缺口
        g.fillOval(ticketMargin - notchRadius / 2, ticketY + ticketH / 2 - notchRadius / 2, notchRadius, notchRadius);
        // 右侧缺口
        g.fillOval(ticketMargin + ticketW - notchRadius / 2, ticketY + ticketH / 2 - notchRadius / 2, notchRadius, notchRadius);

        // 3. 虚线分隔线
        g.setColor(new Color(180, 200, 230, 180));
        g.setStroke(new BasicStroke(size / 128, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{size / 32}, 0));
        int dashY = ticketY + ticketH / 2;
        g.drawLine(ticketMargin + cornerRadius, dashY, ticketMargin + ticketW / 2 - notchRadius, dashY);
        g.drawLine(ticketMargin + ticketW / 2 + notchRadius, dashY, ticketMargin + ticketW - cornerRadius, dashY);

        // 4. 列车符号（简化的高铁图标）
        g.setColor(new Color(30, 100, 200));
        int trainSize = size / 5;
        int trainX = ticketMargin + ticketW / 2 - trainSize / 2;
        int trainY = ticketY + ticketH / 2 - trainSize / 2 - trainSize / 4;

        // 车头（圆角矩形）
        g.fillRoundRect(trainX, trainY, trainSize, trainSize * 3 / 4, trainSize / 4, trainSize / 4);

        // 车窗
        g.setColor(new Color(200, 220, 255));
        int windowSize = trainSize / 4;
        g.fillRoundRect(trainX + trainSize / 6, trainY + trainSize / 6, windowSize, windowSize, windowSize / 4, windowSize / 4);
        g.fillRoundRect(trainX + trainSize / 2 - windowSize / 2, trainY + trainSize / 6, windowSize, windowSize, windowSize / 4, windowSize / 4);

        // 5. 底部文字 "12306"
        g.setColor(new Color(255, 255, 255, 200));
        g.setFont(new Font("Arial", Font.BOLD, size / 8));
        FontMetrics fm = g.getFontMetrics();
        String text = "12306";
        int textW = fm.stringWidth(text);
        g.drawString(text, (size - textW) / 2, ticketY + ticketH + size / 8);

        g.dispose();
        return img;
    }

    /**
     * 将 BufferedImage 保存为 ICO 格式
     * ICO 格式：6字节文件头 + 16字节目录项 + BMP图像数据
     */
    private static void saveAsIco(BufferedImage img, String path) throws IOException {
        int w = img.getWidth();
        int h = img.getHeight();

        // 提取像素数据（BGRA 格式，ICO 使用 BGRA）
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);

        // BMP 图像数据（不含文件头，从信息头开始）
        // BITMAPINFOHEADER: 40 bytes
        // Pixel data: w * h * 4 (BGRA)
        // Mask data: w * h / 8 (1-bit alpha mask, XOR mask)
        int rowBytes = w * 4;
        int pixelDataSize = rowBytes * h;
        int maskRowBytes = ((w + 31) / 32) * 4;
        int maskDataSize = maskRowBytes * h;
        int imageSize = 40 + pixelDataSize + maskDataSize;

        try (FileOutputStream fos = new FileOutputStream(path)) {
            // === ICO 文件头 (6 bytes) ===
            writeShort(fos, 0);      // Reserved
            writeShort(fos, 1);      // Type: 1 = ICO
            writeShort(fos, 1);      // Count: 1 image

            // === ICO 目录项 (16 bytes) ===
            fos.write(w >= 256 ? 0 : w);  // Width (0 = 256)
            fos.write(h >= 256 ? 0 : h);  // Height
            fos.write(0);                   // Color palette
            fos.write(0);                   // Reserved
            writeShort(fos, 1);             // Color planes
            writeShort(fos, 32);            // Bits per pixel
            writeInt(fos, imageSize);       // Size of image data
            writeInt(fos, 22);              // Offset to image data (6 + 16 = 22)

            // === BMP 信息头 (40 bytes) ===
            writeInt(fos, 40);              // Header size
            writeInt(fos, w);               // Width
            writeInt(fos, h * 2);           // Height (doubled for XOR + AND masks)
            writeShort(fos, 1);             // Color planes
            writeShort(fos, 32);            // Bits per pixel
            writeInt(fos, 0);               // Compression (none)
            writeInt(fos, pixelDataSize + maskDataSize); // Image size
            writeInt(fos, 0);               // X pixels per meter
            writeInt(fos, 0);               // Y pixels per meter
            writeInt(fos, 0);               // Colors in palette
            writeInt(fos, 0);               // Important colors

            // === 像素数据 (BGRA, bottom-to-top) ===
            for (int y = h - 1; y >= 0; y--) {
                for (int x = 0; x < w; x++) {
                    int argb = pixels[y * w + x];
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    // ICO uses premultiplied alpha for BGRA
                    fos.write(b);
                    fos.write(g);
                    fos.write(r);
                    fos.write(a);
                }
            }

            // === AND 掩码 (1-bit, 表示透明区域) ===
            for (int y = 0; y < h; y++) {
                for (int xByte = 0; xByte < maskRowBytes; xByte++) {
                    fos.write(0);  // All zeros = all opaque
                }
            }
        }
    }

    private static void writeShort(FileOutputStream fos, int value) throws IOException {
        fos.write(value & 0xFF);
        fos.write((value >> 8) & 0xFF);
    }

    private static void writeInt(FileOutputStream fos, int value) throws IOException {
        fos.write(value & 0xFF);
        fos.write((value >> 8) & 0xFF);
        fos.write((value >> 16) & 0xFF);
        fos.write((value >> 24) & 0xFF);
    }
}
