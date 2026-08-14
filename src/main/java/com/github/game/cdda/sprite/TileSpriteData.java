package com.github.game.cdda.sprite;

import com.github.game.engine.core.sprite.Sprite;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 地形精灵纹理数据 —— 为每种地形类型定义 32×32 可爱抽象像素纹理。
 * <p>
 * 简单地形使用 {@link PixelArt#createTextured} 基于底色 + 叠加纹理生成；
 * 复杂地形（树、水等）使用完整点阵模式。
 * </p>
 * <p>
 * 风格：抽象色块 + 简单点缀，可爱抽象风。
 * </p>
 */
public final class TileSpriteData {

    private TileSpriteData() {}

    // ==================== 纹理叠加图案 ====================
    // 每行 32 字符，共 32 行

    /** 草地纹理 —— 稀疏浅色小草点缀 */
    private static final String[] GRASS_OVERLAY = {
            "................................",
            "..........l.....................",
            "................................",
            "....................l...........",
            "................................",
            ".......l........................",
            "................................",
            "..........................l.....",
            "................................",
            "............l...................",
            "................................",
            "..................l.............",
            "................................",
            ".......l........................",
            "................................",
            ".........................l......",
            "................................",
            "..........l.....................",
            "................................",
            "......................l.........",
            "................................",
            ".......l........................",
            "................................",
            "..................l.............",
            "................................",
            ".........................l......",
            "................................",
            "..........l.....................",
            "................................",
            "......................l.........",
            "................................",
            "................................",
    };

    /** 泥土纹理 —— 深浅棕色斑点 */
    private static final String[] DIRT_OVERLAY = {
            "................................",
            "................................",
            "..............l.................",
            "................................",
            "................................",
            ".....................l..........",
            "................................",
            "..........l.....................",
            "................................",
            "................................",
            "..................l.............",
            "................................",
            "................................",
            ".......l........................",
            "................................",
            "..........................l.....",
            "................................",
            "................................",
            "............l...................",
            "................................",
            "................................",
            ".....................l..........",
            "................................",
            "..........l.....................",
            "................................",
            "......................l.........",
            "................................",
            "................................",
            "...............l................",
            "................................",
            "................................",
            "................................",
    };

    /** 沙地纹理 —— 细微纹理 */
    private static final String[] SAND_OVERLAY = {
            "................................",
            "......l...........l.............",
            "................................",
            ".....................l..........",
            "................................",
            "..........l.....................",
            "................................",
            ".......l...........l............",
            "................................",
            ".....................l..........",
            "................................",
            "..........l.....................",
            "................................",
            "......l...........l.............",
            "................................",
            ".....................l..........",
            "................................",
            "..........l.....................",
            "................................",
            ".......l...........l............",
            "................................",
            ".....................l..........",
            "................................",
            "..........l.....................",
            "................................",
            "......l...........l.............",
            "................................",
            ".....................l..........",
            "................................",
            "..........l.....................",
            "................................",
            "................................",
    };

    /** 水面纹理 —— 简洁波纹线 */
    private static final String[] WATER_OVERLAY = {
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "..l..l..l..l..l..l..l..l..l..l..",
            "................................",
            "................................",
            "................................",
            "................................",
            "....l..l..l..l..l..l..l..l..l...",
            "................................",
            "................................",
            "................................",
            "................................",
            "..l..l..l..l..l..l..l..l..l..l..",
            "................................",
            "................................",
            "................................",
            "................................",
            "....l..l..l..l..l..l..l..l..l...",
            "................................",
            "................................",
            "................................",
            "................................",
            "..l..l..l..l..l..l..l..l..l..l..",
            "................................",
            "................................",
            "................................",
    };

    /** 石地纹理 —— 简洁裂纹 */
    private static final String[] STONE_OVERLAY = {
            "................................",
            "................................",
            ".............o..................",
            "................................",
            "..................o.............",
            "................................",
            "..........o.....................",
            "................................",
            ".......................o........",
            "................................",
            "........o.......................",
            "................................",
            "...................o............",
            "................................",
            "..........o.....................",
            "................................",
            "......................o.........",
            "................................",
            ".............o..................",
            "................................",
            "..................o.............",
            "................................",
            "..........o.....................",
            "................................",
            ".......................o........",
            "................................",
            "........o.......................",
            "................................",
            "...................o............",
            "................................",
            "................................",
            "................................",
    };

    /** 高草纹理 —— 竖线条 */
    private static final String[] TALL_GRASS_OVERLAY = {
            "................................",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
            "......l.....l.....l.....l.......",
    };

    /** 花朵纹理 —— 几朵小花 */
    private static final String[] FLOWER_OVERLAY = {
            "................................",
            "................................",
            "................................",
            "................................",
            "...........ppp..................",
            "..........ppppp.................",
            "...........ppp..................",
            "................................",
            "................................",
            "................................",
            ".....................yyy........",
            "....................yyyyy.......",
            ".....................yyy........",
            "................................",
            "................................",
            "................................",
            "........rrr.....................",
            ".......rrrrr....................",
            "........rrr.....................",
            "................................",
            "................................",
            "................................",
            "....................ppp.........",
            "...................ppppp........",
            "....................ppp.........",
            "................................",
            "................................",
            "..............yyy...............",
            ".............yyyyy..............",
            "..............yyy...............",
            "................................",
            "................................",
    };

    /** 灌木纹理 —— 圆润丛生 */
    private static final String[] BUSH_OVERLAY = {
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "..........oooo..................",
            "........oolllloo................",
            ".......ollollllo................",
            ".......ollollllo................",
            ".......oolllllo.................",
            ".........oooo...................",
            "................................",
            "................................",
            "................................",
            "......................ooo.......",
            "....................oolloo......",
            "...................ollollllo....",
            "...................ollollllo....",
            "...................oolllllo.....",
            ".....................ooo........",
            "................................",
            "................................",
            "................................",
            "................................",
            "..........oooo..................",
            "........oolllloo................",
            ".......ollollllo................",
            ".......ollollllo................",
            ".......oolllllo.................",
            ".........oooo...................",
            "................................",
    };

    /** 树木纹理 —— 俯视圆形树冠 + 树干 */
    private static final String[] TREE_OVERLAY = {
            "................................",
            "................................",
            "................................",
            "................................",
            "............oooo................",
            "..........oolllloo..............",
            "........oolllllllllo............",
            "........ollolllllllo............",
            ".......ollllllllllllo...........",
            ".......ollllllllllllo...........",
            ".......ollllllllllllo...........",
            ".......oollllllllllo............",
            "........oolllllllo..............",
            "..........ooooo.................",
            "............dd..................",
            "...........ddd..................",
            "..........ddddd.................",
            "...........ddd..................",
            "............dd..................",
            "............dd..................",
            "............dd..................",
            "............dd..................",
            "...........ddd..................",
            "............dd..................",
            "............dd..................",
            "............dd..................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
    };

    /** 泥地纹理 —— 湿润斑点 */
    private static final String[] MUD_OVERLAY = {
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "............l...................",
            "................................",
            "................................",
            "................................",
            "................................",
            ".....................l..........",
            "................................",
            "................................",
            "................................",
            "..........l.....................",
            "................................",
            "................................",
            "................................",
            "................................",
            "..................l.............",
            "................................",
            "................................",
            "................................",
            "................................",
            ".......l........................",
            "................................",
            "................................",
            "................................",
            "..........................l.....",
            "................................",
            "................................",
            "................................",
    };

    /** 枯树纹理 */
    private static final String[] WITHERED_TREE_OVERLAY = {
            "................................",
            "................................",
            "................................",
            "................................",
            "............oooo................",
            "..........ooddlldo..............",
            "........ooddllodddo.............",
            "........odllolldddo.............",
            ".......odllllllllddo............",
            ".......odollllllddo.............",
            "........odollllldo..............",
            "........ooddlldo................",
            "..........oodo..................",
            "............dd..................",
            "...........ddd..................",
            "..........ddddd.................",
            "...........ddd..................",
            "............dd..................",
            "............dd..................",
            "............dd..................",
            "............dd..................",
            "...........ddd..................",
            "............dd..................",
            "............dd..................",
            "............dd..................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
    };

    /** 芦苇纹理 —— 竖线 */
    private static final String[] REEDS_OVERLAY = {
            "................................",
            "................................",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "..l.........l.........l.........",
            "................................",
            "................................",
            "................................",
            "................................",
    };

    /** 墙壁纹理 —— 简化砖块图案 */
    private static final String[] WALL_OVERLAY = {
            "oooooooooooooooooooooooooooooooo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "lollllllllllllllollllllllllllllo",
            "lollllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "lollllllllllllllollllllllllllllo",
            "lollllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "lollllllllllllllollllllllllllllo",
            "lollllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
    };

    /** 栅栏纹理 —— 竖条+横档 */
    private static final String[] FENCE_OVERLAY = {
            "................................",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....dmmmmmmmmmmmmmd.............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....dmmmmmmmmmmmmmd.............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....dmmmmmmmmmmmmmd.............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....dmmmmmmmmmmmmmd.............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "....d............d..............",
            "................................",
    };

    /** 门纹理 */
    private static final String[] DOOR_OVERLAY = {
            "................................",
            "......ooooooooooooo.............",
            "......olllllllllllo.............",
            "......olllllllllllo.............",
            "......olllllllllllo.............",
            "......olllllllllllo.............",
            "......olllllllllllo.............",
            "......olllllllllllo.............",
            "......olllllllllllo.............",
            "......olllllllllllo.............",
            "......olllllllllllo.............",
            "......olllllllllllo.............",
            "......olllllllllllo.............",
            "......olllllllllllo.............",
            "......olllllllllllo.............",
            "......ooooooooooooo.............",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
            "................................",
    };

    /** 地板纹理 —— 木板 */
    private static final String[] FLOOR_OVERLAY = {
            "oooooooooooooooooooooooooooooooo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
            "olllllllllllllllollllllllllllllo",
            "olllllllllllllllollllllllllllllo",
            "oooooooooooooooooooooooooooooooo",
    };

    // ==================== 调色板 ====================

    private static Map<Character, Color> grassPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(100, 200, 40));    // 亮绿点缀
        return p;
    }

    private static Map<Character, Color> dirtPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(180, 125, 75));    // 亮棕
        return p;
    }

    private static Map<Character, Color> sandPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(225, 200, 155));   // 亮沙
        return p;
    }

    private static Map<Character, Color> waterPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(90, 160, 240));    // 亮蓝波纹
        return p;
    }

    private static Map<Character, Color> stonePalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(130, 130, 130));   // 裂纹暗色
        return p;
    }

    private static Map<Character, Color> tallGrassPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(130, 220, 120));   // 亮绿草叶
        return p;
    }

    private static Map<Character, Color> flowerPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('p', new Color(220, 50, 180));    // 粉花
        p.put('y', new Color(240, 220, 50));    // 黄花
        p.put('r', new Color(220, 50, 50));     // 红花
        return p;
    }

    private static Map<Character, Color> bushPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(0, 90, 0));        // 暗绿轮廓
        p.put('l', new Color(50, 170, 50));     // 亮绿
        return p;
    }

    private static Map<Character, Color> treePalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(0, 80, 0));        // 暗绿轮廓
        p.put('l', new Color(30, 150, 30));     // 亮绿树冠
        p.put('d', new Color(110, 70, 35));     // 棕色树干
        return p;
    }

    private static Map<Character, Color> witheredTreePalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(100, 65, 30));     // 暗褐轮廓
        p.put('l', new Color(155, 110, 55));    // 浅褐
        p.put('d', new Color(100, 65, 30));     // 树干
        return p;
    }

    private static Map<Character, Color> reedsPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(80, 175, 70));     // 芦苇绿
        return p;
    }

    private static Map<Character, Color> wallPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(120, 120, 120));   // 暗色砖缝
        p.put('l', new Color(195, 195, 195));   // 亮色砖面
        return p;
    }

    private static Map<Character, Color> fencePalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(100, 55, 20));     // 暗边框
        p.put('d', new Color(139, 69, 19));     // 木色
        p.put('m', new Color(170, 100, 45));    // 横档
        return p;
    }

    private static Map<Character, Color> doorPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(110, 60, 25));     // 门框
        p.put('l', new Color(180, 110, 55));    // 门面
        return p;
    }

    private static Map<Character, Color> floorPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(110, 110, 110));   // 木板缝
        p.put('l', new Color(165, 165, 165));   // 木板面
        return p;
    }

    private static Map<Character, Color> witheredBushPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(95, 70, 40));      // 暗褐
        p.put('l', new Color(155, 120, 70));    // 浅褐
        return p;
    }

    private static Map<Character, Color> deadGrassPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(200, 178, 118));   // 枯黄点缀
        return p;
    }

    private static Map<Character, Color> mudPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('l', new Color(145, 120, 85));    // 湿泥亮色
        return p;
    }

    // ==================== 公开 API ====================

    /**
     * 生成所有地形精灵。
     *
     * @return ID → Sprite 映射（ID 格式：{@code tile.<name>}）
     */
    public static Map<String, Sprite> createAllTileSprites() {
        Map<String, Sprite> sprites = new HashMap<>();
        int size = 32;

        // 基础地形 —— 底色 + 纹理叠加
        sprites.put("tile.grass",
                PixelArt.createTextured("tile.grass",
                        new Color(76, 180, 0), GRASS_OVERLAY, grassPalette(), size));
        sprites.put("tile.dirt",
                PixelArt.createTextured("tile.dirt",
                        new Color(160, 110, 60), DIRT_OVERLAY, dirtPalette(), size));
        sprites.put("tile.sand",
                PixelArt.createTextured("tile.sand",
                        new Color(210, 185, 140), SAND_OVERLAY, sandPalette(), size));
        sprites.put("tile.water",
                PixelArt.createTextured("tile.water",
                        new Color(60, 130, 220), WATER_OVERLAY, waterPalette(), size));
        sprites.put("tile.stone",
                PixelArt.createTextured("tile.stone",
                        new Color(160, 160, 160), STONE_OVERLAY, stonePalette(), size));

        // 植被
        sprites.put("tile.tree",
                PixelArt.createTextured("tile.tree",
                        new Color(0, 120, 0), TREE_OVERLAY, treePalette(), size));
        sprites.put("tile.bush",
                PixelArt.createTextured("tile.bush",
                        new Color(0, 128, 0), BUSH_OVERLAY, bushPalette(), size));
        sprites.put("tile.flower",
                PixelArt.createTextured("tile.flower",
                        new Color(76, 180, 0), FLOWER_OVERLAY, flowerPalette(), size));
        sprites.put("tile.tall_grass",
                PixelArt.createTextured("tile.tall_grass",
                        new Color(100, 200, 100), TALL_GRASS_OVERLAY, tallGrassPalette(), size));

        // 建筑
        sprites.put("tile.wall",
                PixelArt.createTextured("tile.wall",
                        new Color(160, 160, 160), WALL_OVERLAY, wallPalette(), size));
        sprites.put("tile.fence",
                PixelArt.createTextured("tile.fence",
                        new Color(76, 180, 0), FENCE_OVERLAY, fencePalette(), size));
        sprites.put("tile.door",
                PixelArt.createTextured("tile.door",
                        new Color(160, 82, 45), DOOR_OVERLAY, doorPalette(), size));
        sprites.put("tile.floor",
                PixelArt.createTextured("tile.floor",
                        new Color(128, 128, 128), FLOOR_OVERLAY, floorPalette(), size));

        // 特殊地形
        sprites.put("tile.reeds",
                PixelArt.createTextured("tile.reeds",
                        new Color(60, 130, 220), REEDS_OVERLAY, reedsPalette(), size));
        sprites.put("tile.mud",
                PixelArt.createTextured("tile.mud",
                        new Color(120, 100, 70), MUD_OVERLAY, mudPalette(), size));
        sprites.put("tile.withered_tree",
                PixelArt.createTextured("tile.withered_tree",
                        new Color(139, 90, 43), WITHERED_TREE_OVERLAY, witheredTreePalette(), size));
        sprites.put("tile.withered_bush",
                PixelArt.createTextured("tile.withered_bush",
                        new Color(128, 100, 60), BUSH_OVERLAY, witheredBushPalette(), size));
        sprites.put("tile.dead_grass",
                PixelArt.createTextured("tile.dead_grass",
                        new Color(180, 160, 100), GRASS_OVERLAY, deadGrassPalette(), size));

        return sprites;
    }
}
