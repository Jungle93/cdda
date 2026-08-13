package com.github.game.cdda.sprite;

import com.github.game.engine.core.sprite.Sprite;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * 精灵导出工具 —— 将内置图形包的所有精灵导出为 PNG 文件，用于预览和调试。
 * <p>
 * 用法：直接运行 main 方法，精灵 PNG 文件将输出到 {@code sprites/preview/} 目录。
 * 每种生物和地形各生成一个放大版的 PNG（16x 放大 = 256x256 像素），
 * 另有一张汇总图。
 * </p>
 */
public class SpriteExporter {

    /** 放大倍数 */
    private static final int SCALE = 16;

    /** 输出目录 */
    private static final String OUTPUT_DIR = "sprites/preview";

    /**
     * 导出所有内置精灵到 PNG 文件。
     */
    public static void exportAll() throws IOException {
        BuiltinSpritePack pack = new BuiltinSpritePack();
        File outputDir = new File(OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        int tileSize = pack.getTileSize();
        int exportSize = tileSize * SCALE;

        System.out.println("导出精灵到 " + outputDir.getAbsolutePath());
        System.out.println("图形包: " + pack.getName() + " (" + pack.getId() + ")");
        System.out.println("瓦片尺寸: " + tileSize + "px，导出尺寸: " + exportSize + "px (" + SCALE + "x 放大)");
        System.out.println("精灵总数: " + pack.getSpriteIds().size());
        System.out.println("----");

        // 分类导出
        int creatureCount = 0;
        int tileCount = 0;
        int playerCount = 0;

        for (String id : pack.getSpriteIds()) {
            Sprite sprite = pack.getSprite(id);
            if (sprite == null) continue;

            BufferedImage scaled = scaleUp(sprite.getImage(), SCALE);
            String filename = id.replace('.', '_') + ".png";
            File outputFile = new File(outputDir, filename);
            ImageIO.write(scaled, "PNG", outputFile);

            if (id.startsWith("creature.")) {
                creatureCount++;
            } else if (id.startsWith("tile.")) {
                tileCount++;
            } else if (id.equals("player")) {
                playerCount++;
            }

            System.out.println("  " + id + " → " + filename);
        }

        // 生成汇总图
        generateSummary(pack, outputDir, exportSize);

        System.out.println("----");
        System.out.println("导出完成: " + creatureCount + " 生物, " + playerCount + " 玩家, " + tileCount + " 地形");
        System.out.println("输出目录: " + outputDir.getAbsolutePath());
    }

    /**
     * 生成汇总图 —— 所有精灵排列在一张图上。
     */
    private static void generateSummary(BuiltinSpritePack pack, File outputDir, int exportSize)
            throws IOException {

        Map<String, Sprite> sprites = new java.util.LinkedHashMap<>();
        // 按类别排序
        for (String id : pack.getSpriteIds()) {
            sprites.put(id, pack.getSprite(id));
        }

        int cols = 6;
        int rows = (sprites.size() + cols - 1) / cols;
        int gap = 4;
        int cellSize = exportSize + gap;

        BufferedImage summary = new BufferedImage(
                cols * cellSize + gap,
                rows * cellSize + gap + 40,  // 额外空间放标题
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = summary.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // 白色背景
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, summary.getWidth(), summary.getHeight());

        // 标题
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString("CDDA 内置图形包预览 (" + pack.getSpriteIds().size() + " 精灵)", gap, 28);

        // 绘制每个精灵
        int i = 0;
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (Map.Entry<String, Sprite> entry : sprites.entrySet()) {
            int col = i % cols;
            int row = i / cols;
            int x = gap + col * cellSize;
            int y = 40 + gap + row * cellSize;

            // 绘制精灵（放大版）
            BufferedImage scaled = scaleUp(entry.getValue().getImage(), SCALE);
            g.drawImage(scaled, x, y, null);

            // 绘制 ID 标签
            g.setColor(new Color(80, 80, 80));
            String label = entry.getKey();
            if (label.length() > 18) label = label.substring(0, 18);
            g.drawString(label, x, y + exportSize + 12);

            i++;
        }

        g.dispose();

        File summaryFile = new File(outputDir, "_summary.png");
        ImageIO.write(summary, "PNG", summaryFile);
        System.out.println("  汇总图 → _summary.png");
    }

    /**
     * 将图像按比例放大（最近邻插值，保持像素感）。
     */
    private static BufferedImage scaleUp(BufferedImage source, int scale) {
        int w = source.getWidth() * scale;
        int h = source.getHeight() * scale;
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        return result;
    }

    /**
     * 主方法 —— 导出所有精灵。
     */
    public static void main(String[] args) throws IOException {
        exportAll();
    }
}
