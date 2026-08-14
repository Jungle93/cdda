package com.github.game.cdda.sprite;

import com.github.game.engine.core.sprite.Sprite;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 生物精灵点阵数据 —— 为每种生物定义 32×32 俯视可爱抽象像素画。
 * <p>
 * 风格：大头大眼、圆润身体、极简色块，俯视视角面朝下方。
 * 每个精灵使用 3-5 色调色板，通过 {@link PixelArt#createSprite} 生成。
 * </p>
 * <p>
 * 点阵约定：{@code '.'} = 透明，其他字符对应调色板中的颜色。
 * 每行必须恰好 32 个字符，共 32 行。
 * </p>
 */
public final class CreatureSpriteData {

    private CreatureSpriteData() {}

    // ==================== 调色板定义 ====================

    /** 狼 - 灰色犬科 */
    private static Map<Character, Color> wolfPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(50, 50, 55));
        p.put('d', new Color(85, 85, 90));
        p.put('m', new Color(130, 130, 135));
        p.put('l', new Color(175, 175, 178));
        p.put('e', new Color(15, 15, 15));
        p.put('n', new Color(30, 25, 25));
        p.put('w', new Color(205, 200, 190));
        return p;
    }

    /** 狐狸 - 橙色犬科 */
    private static Map<Character, Color> foxPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(100, 45, 10));
        p.put('d', new Color(170, 80, 20));
        p.put('m', new Color(210, 110, 35));
        p.put('l', new Color(235, 155, 60));
        p.put('e', new Color(15, 15, 15));
        p.put('n', new Color(25, 20, 15));
        p.put('w', new Color(240, 235, 225));
        return p;
    }

    /** 獾 - 黑白灰 */
    private static Map<Character, Color> badgerPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(30, 30, 30));
        p.put('d', new Color(50, 50, 50));
        p.put('m', new Color(110, 110, 110));
        p.put('l', new Color(170, 170, 170));
        p.put('w', new Color(240, 240, 240));
        p.put('e', new Color(10, 10, 10));
        return p;
    }

    /** 兔子 - 米黄色 */
    private static Map<Character, Color> rabbitPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(130, 105, 80));
        p.put('d', new Color(170, 145, 115));
        p.put('m', new Color(200, 180, 155));
        p.put('l', new Color(225, 210, 190));
        p.put('e', new Color(20, 15, 15));
        p.put('p', new Color(220, 170, 160));
        p.put('n', new Color(190, 140, 130));
        return p;
    }

    /** 野兔 - 棕褐色 */
    private static Map<Character, Color> harePalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(110, 85, 60));
        p.put('d', new Color(140, 110, 78));
        p.put('m', new Color(170, 140, 105));
        p.put('l', new Color(200, 175, 145));
        p.put('e', new Color(20, 15, 15));
        p.put('p', new Color(200, 155, 140));
        p.put('n', new Color(160, 120, 100));
        return p;
    }

    /** 松鼠 - 棕色 */
    private static Map<Character, Color> squirrelPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(90, 55, 25));
        p.put('d', new Color(120, 75, 35));
        p.put('m', new Color(160, 105, 50));
        p.put('l', new Color(195, 145, 80));
        p.put('e', new Color(15, 10, 10));
        p.put('w', new Color(225, 210, 185));
        return p;
    }

    /** 鹿 - 金棕色 */
    private static Map<Character, Color> deerPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(100, 70, 35));
        p.put('d', new Color(135, 95, 50));
        p.put('m', new Color(180, 140, 80));
        p.put('l', new Color(210, 180, 130));
        p.put('e', new Color(15, 12, 10));
        p.put('a', new Color(90, 65, 35));
        p.put('w', new Color(235, 220, 195));
        return p;
    }

    /** 狍子 - 浅棕色 */
    private static Map<Character, Color> roeDeerPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(95, 70, 40));
        p.put('d', new Color(125, 90, 55));
        p.put('m', new Color(160, 120, 70));
        p.put('l', new Color(195, 165, 120));
        p.put('e', new Color(15, 12, 10));
        p.put('a', new Color(80, 58, 30));
        return p;
    }

    /** 野猪 - 深棕色 */
    private static Map<Character, Color> boarPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(60, 40, 25));
        p.put('d', new Color(85, 58, 35));
        p.put('m', new Color(120, 82, 50));
        p.put('l', new Color(155, 115, 75));
        p.put('e', new Color(15, 10, 10));
        p.put('t', new Color(230, 225, 210));
        p.put('n', new Color(100, 70, 50));
        return p;
    }

    /** 盘羊 - 暗褐色 */
    private static Map<Character, Color> mouflonPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(70, 50, 30));
        p.put('d', new Color(100, 75, 48));
        p.put('m', new Color(140, 110, 75));
        p.put('l', new Color(175, 148, 110));
        p.put('e', new Color(15, 12, 10));
        p.put('h', new Color(200, 185, 155));
        p.put('n', new Color(80, 55, 35));
        return p;
    }

    /** 玩家 - 人形 */
    private static Map<Character, Color> playerPalette() {
        Map<Character, Color> p = new HashMap<>();
        p.put('o', new Color(35, 35, 70));
        p.put('s', new Color(220, 185, 155));
        p.put('d', new Color(50, 70, 130));
        p.put('m', new Color(70, 95, 165));
        p.put('l', new Color(100, 130, 195));
        p.put('e', new Color(20, 20, 20));
        p.put('h', new Color(75, 50, 30));
        p.put('b', new Color(55, 40, 28));
        return p;
    }

    // ==================== 点阵模式 ====================
    // 每行恰好 32 字符，共 32 行
    // '.' = 透明

    /**
     * 狼 —— 灰色，大圆眼，三角耳，蓬松尾巴。
     * 对称设计，以列 15-16 为中心轴。
     */
    private static String[] wolfPattern() {
        return new String[]{
                "................................",
                "................................",
                "................................",
                "..........oo..........oo........",
                ".........oddd........oddd.......",
                ".........oddd........oddd.......",
                "........oddddmmmmmmdddodo.......",
                ".......odmmmmmmmmmmmmmmdoo......",
                ".......odmmllmeeemlmmmdo........",
                "........odmmnnnnnmmmmmdo........",
                ".........odwwwwwmmmmmdo.........",
                ".........odmmmmmmmmmmdo.........",
                "........odmmmmmmmmmmmmdo........",
                ".......odmmmmmmmmmmmmmmdo.......",
                ".......odmmmmmmmmmmmmmmdo.......",
                ".......odmmmmmmmmmmmmmmdo.......",
                ".......odmmmmmmmmmmmmmmdo.......",
                ".......odmmmmmmmmmmmmmmdo.......",
                ".......odmmmmmmmmmmmmmmdo.......",
                ".......odmmmmmmmmmmmmmmdo.......",
                ".......odmmmmmmmmmmmmmmdo.......",
                "........odmmmmmmmmmmmmdo........",
                "........odmmmmmddmmmmmdo........",
                "........oddddd..odddddd.........",
                ".........oddo....oddo...........",
                ".........oddo....oddo...........",
                ".........oddo....oddo...........",
                "..........odo....odo............",
                "..........odo....odo............",
                "..........oo......oo............",
                "................................",
                "................................",
        };
    }

    /**
     * 狐狸 —— 橙色，三角耳，蓬松尾巴带白尖。
     */
    private static String[] foxPattern() {
        return new String[]{
                "................................",
                "................................",
                "................................",
                "..........oo..........oo........",
                ".........oddd........oddd.......",
                ".........oddd........oddd.......",
                "........oddddmmmmmmdddodo.......",
                ".......odmmmmmmmmmmmmmmdoo......",
                ".......odmmllmeeemlmmmdo........",
                "........odmmnnnnnmmmmmdo........",
                ".........odwwwwwmmmmmdo.........",
                ".........odmmmmmmmmmmdo.........",
                "........odmmmmmmmmmmmmdo........",
                ".......odmmmmmmmmmmmmmmdo.......",
                ".......odmmmmmmmmmmmmmmdo.......",
                ".......odmmmmmmmmmmmmmmdo.......",
                ".......odmmmmmmmmmmmmmmdo.......",
                ".......odmmmmmmmmmmmmmmdo.......",
                ".......odmmmmmmmmmmmmmmdo.......",
                "........odmmmmdddmmmmmmdo.......",
                ".........odddwwwwwdddmmdoo......",
                "..........odddwwwwwdddo.........",
                "..........odddwwwwwdddo.........",
                "..........odddwwwwwdddo.........",
                "..........odddwwwwwdddo.........",
                "...........odddwwdddo...........",
                "...........odo....odo...........",
                "...........oo......oo...........",
                "................................",
                "................................",
                "................................",
                "................................",
        };
    }

    /**
     * 獾 —— 矮壮，面部白色条纹，黑灰身体。
     */
    private static String[] badgerPattern() {
        return new String[]{
                "................................",
                "................................",
                "................................",
                ".........oo..........oo.........",
                "........oddd........oddd........",
                "........odwwd......odwwd........",
                "........odwwwd....odwwwd........",
                "........odwwwwd..odwwwwd........",
                "........odwweeeewwewwd..........",
                ".........odwwddddwwddo..........",
                "........oddddddddddddddo........",
                ".......odddddddddddddddddo......",
                ".......odddddddddddddddddo......",
                ".......odddddddddddddddddo......",
                ".......odddddddddddddddddo......",
                ".......odddddddddddddddddo......",
                ".......odddddddddddddddddo......",
                ".......odddddddddddddddddo......",
                ".......odddddddddddddddddo......",
                ".......odddddddddddddddddo......",
                "........odddddddddddddddo.......",
                "........odddddddddddddddo.......",
                ".........odddddddddddddo........",
                "..........odddddddddddo.........",
                "..........oddo....oddo..........",
                "..........oddo....oddo..........",
                "..........oddo....oddo..........",
                "...........odo....odo...........",
                "...........odo....odo...........",
                "...........oo......oo...........",
                "................................",
                "................................",
        };
    }

    /**
     * 兔子 —— 小圆身，长耳朵，粉色内耳。
     */
    private static String[] rabbitPattern() {
        return new String[]{
                "................................",
                "................................",
                "................................",
                "................................",
                "..........ooooo....ooooo........",
                ".........opppppo..opppppo.......",
                ".........opppppo..opppppo.......",
                ".........opppppo..opppppo.......",
                ".........opppppo..opppppo.......",
                ".........opppppo..opppppo.......",
                "..........ooppoo..ooppoo........",
                "..........oddddd..oddddd........",
                ".........odmmmmmmmmmmmmdo.......",
                ".........odmmlmmeemlmmmmdo......",
                "..........odmmnnnnnmmmmmdo......",
                "...........odmmmmmmmmmmdo.......",
                "...........odmmmmmmmmmmdo.......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmddmmmmmmdo.....",
                "...........oddo..oddo...........",
                "...........oddo..oddo...........",
                "...........odo....odo...........",
                "...........odo....odo...........",
                "...........oo......oo...........",
                "................................",
                "................................",
        };
    }

    /**
     * 野兔 —— 比兔子更大，更长耳朵。
     */
    private static String[] harePattern() {
        return new String[]{
                "................................",
                "................................",
                "................................",
                "................................",
                "..........ooooo....ooooo........",
                ".........opppppo..opppppo.......",
                ".........opppppo..opppppo.......",
                ".........opppppo..opppppo.......",
                ".........opppppo..opppppo.......",
                ".........opppppo..opppppo.......",
                ".........opppppo..opppppo.......",
                ".........opppppo..opppppo.......",
                "..........ooppoo..ooppoo........",
                "..........oddddd..oddddd........",
                ".........odmmmmmmmmmmmmdo.......",
                ".........odmmlmmeemlmmmmdo......",
                "..........odmmnnnnnmmmmmdo......",
                "...........odmmmmmmmmmmdo.......",
                "...........odmmmmmmmmmmdo.......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmmmmmmmmdo......",
                "..........odmmmmmddmmmmmmdo.....",
                "...........oddo..oddo...........",
                "...........oddo..oddo...........",
                "...........odo....odo...........",
                "...........odo....odo...........",
                "...........oo......oo...........",
                "................................",
        };
    }

    /**
     * 松鼠 —— 小身体，大卷曲尾巴带白尖。
     */
    private static String[] squirrelPattern() {
        return new String[]{
                "................................",
                "................................",
                "................................",
                "................................",
                "...........ooo..................",
                "..........oddddo................",
                ".........odmmmmmdo..............",
                ".........odmmmmmdo..............",
                ".........odmmmmddo..............",
                ".........odmmllmddo.............",
                ".........odmmmmnddo.............",
                ".........odwwwdddo..............",
                ".........odmmmmmdo..............",
                ".........odmmmmmdo..............",
                ".........odmmlmmdo..............",
                ".........odmmmmddo..............",
                "..........odmmmmddo.............",
                "..........odmmmddo..............",
                "...........oddddo...............",
                "...........odddo................",
                "...........oddo.................",
                "...........oddo.................",
                "...........oddo.................",
                "...........oddo.................",
                "...........oddo.................",
                "...........oddo.................",
                "...........oddo.................",
                "............oo..................",
                "................................",
                "................................",
                "................................",
                "................................",
        };
    }

    /**
     * 鹿 —— 金棕色，分叉鹿角，优雅体型，白色斑点。
     * 鹿角对称分叉设计。
     */
    private static String[] deerPattern() {
        return new String[]{
                "................................",
                "................................",
                "................................",
                "................................",
                "........a..........a............",
                ".......aaa........aaa...........",
                ".......ao..........oa...........",
                ".......ao..........oa...........",
                ".......ao..........oa...........",
                ".......ao..........oa...........",
                ".......ao..........oa...........",
                "........odmmmmmmmmmmmdo.........",
                ".......odmmlmmeemlmmmmdo........",
                "........odmmnnnnnmmmmmmdo.......",
                ".........oddddddddddddd.........",
                ".........odmmmmmmmmmmmmdo.......",
                ".........odmmmmmmmmmmmmdo.......",
                "........odmmmmmmmmmmmmmmdo......",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                ".......odmmmmwwmmmmmmmmmmdo.....",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                "........odmmmmmddmmmmmmmdo......",
                ".........oddddd..odddddd........",
                "..........oddo....oddo..........",
                "..........odo......odo..........",
                "..........odo......odo..........",
                "..........oo........oo..........",
                "................................",
        };
    }

    /**
     * 狍子 —— 比鹿小，更小鹿角。
     */
    private static String[] roeDeerPattern() {
        return new String[]{
                "................................",
                "................................",
                "................................",
                "................................",
                "................................",
                "........a..........a............",
                ".......aaa........aaa...........",
                ".......ao..........oa...........",
                ".......ao..........oa...........",
                ".......ao..........oa...........",
                "........odmmmmmmmmmmmdo.........",
                ".......odmmlmmeemlmmmmdo........",
                "........odmmnnnnnmmmmmmdo.......",
                ".........oddddddddddddd.........",
                ".........odmmmmmmmmmmmmdo.......",
                ".........odmmmmmmmmmmmmdo.......",
                "........odmmmmmmmmmmmmmmdo......",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                ".......odmmmmmmmmmmmmmmmmdo.....",
                "........odmmmmmddmmmmmmmdo......",
                ".........oddddd..odddddd........",
                "..........oddo....oddo..........",
                "..........odo......odo..........",
                "..........odo......odo..........",
                "..........oo........oo..........",
                "................................",
                "................................",
        };
    }

    /**
     * 野猪 —— 宽胖体型，深棕色，小獠牙，宽鼻子。
     */
    private static String[] boarPattern() {
        return new String[]{
                "................................",
                "................................",
                "................................",
                "..........oo..........oo........",
                ".........oddo........oddo.......",
                ".........odddddddddddddd........",
                ".........odddddddddddddd........",
                "........odddtllldlltdddo........",
                "........odddddndddnddddo........",
                "........oddddddddddddddo........",
                ".......odddddddddddddddddo......",
                ".......odddddddddddddddddo......",
                "......odddddddddddddddddddo.....",
                "......odddddddddddddddddddo.....",
                "......odddddddddddddddddddo.....",
                "......odddddddddddddddddddo.....",
                "......odddddddddddddddddddo.....",
                "......odddddddddddddddddddo.....",
                "......odddddddddddddddddddo.....",
                "......odddddddddddddddddddo.....",
                "......odddddddddddddddddddo.....",
                ".......odddddddddddddddddo......",
                ".......odddddddddddddddddo......",
                "........oddddddddddddddo........",
                ".........oddddd..ddddd..........",
                "..........oddo....oddo..........",
                "..........oddo....oddo..........",
                "..........oddo....oddo..........",
                "...........odo....odo...........",
                "...........oo......oo...........",
                "................................",
                "................................",
        };
    }

    /**
     * 盘羊 —— 健壮，螺旋弯角。
     */
    private static String[] mouflonPattern() {
        return new String[]{
                "................................",
                "................................",
                "................................",
                "................................",
                "................................",
                "......ohho..........ohho........",
                ".....ohhoh..........ohho........",
                ".....ohho..........ohho.........",
                "......oo............oo..........",
                "......odmmmmmmmmmmmmmdo.........",
                ".....odmmlmmeemlmmmmmmdo........",
                "......odmmnnnnnmmmmmmddo........",
                ".....odddddddddddddddddo........",
                "....odddddddddddddddddddo.......",
                "....odddddddddddddddddddo.......",
                "...odddddddddddddddddddddo......",
                "...odddddddddddddddddddddo......",
                "...odddddddddddddddddddddo......",
                "...odddddddddddddddddddddo......",
                "...odddddddddddddddddddddo......",
                "...odddddddddddddddddddddo......",
                "...odddddddddddddddddddddo......",
                "....odddddddddddddddddddo.......",
                "....odddddddddddddddddddo.......",
                ".....odddddddddddddddddo........",
                ".....oddddd..ddddddd............",
                "......oddo....oddo..............",
                "......oddo....oddo..............",
                "......odo....odo................",
                "......odo....odo................",
                "......oo......oo................",
                "................................",
        };
    }

    /**
     * 玩家 —— 人形俯视，可爱抽象，蓝色衣物，棕色靴子。
     * 对称设计。
     */
    private static String[] playerPattern() {
        return new String[]{
                "................................",
                "................................",
                "................................",
                "................................",
                "..........ohhhhhho..............",
                ".........ohssssssso.............",
                ".........ohssseesso.............",
                ".........ohsssssso..............",
                "..........ossssso...............",
                "..........oddddddo..............",
                ".........odmmmmmmmdo............",
                ".........odmmlmmlmdo............",
                ".........odmmmmmmmdo............",
                ".........odmmmmmmmdo............",
                "........odmmmmmmmmmmmdo.........",
                "........odmmmmmmmmmmmdo.........",
                "........odmmmmmmmmmmmdo.........",
                "........odmmmmmmmmmmmdo.........",
                "........odmmmmmmmmmmmdo.........",
                "........odmmmmmmmmmmmdo.........",
                "........odmmmmmmmmmmmdo.........",
                "........odmmmmmmmmmmmdo.........",
                "........odmmmmmmmmmmmdo.........",
                ".........odmmmmmmmmmmdo.........",
                ".........odmmmmmmmmmmdo.........",
                ".........odmmmmddmmmmmdo........",
                "..........oddo....oddo..........",
                "..........oddo....oddo..........",
                "..........obbo....obbo..........",
                "..........obbo....obbo..........",
                "..........oboo....oboo..........",
                "..........oooo....oooo..........",
        };
    }

    // ==================== 公开 API ====================

    /**
     * 生成所有生物精灵（含玩家）。
     *
     * @return ID → Sprite 映射
     */
    public static Map<String, Sprite> createAllCreatureSprites() {
        Map<String, Sprite> sprites = new HashMap<>();

        sprites.put("creature.wolf",
                PixelArt.createSprite("creature.wolf", wolfPattern(), wolfPalette()));
        sprites.put("creature.fox",
                PixelArt.createSprite("creature.fox", foxPattern(), foxPalette()));
        sprites.put("creature.badger",
                PixelArt.createSprite("creature.badger", badgerPattern(), badgerPalette()));
        sprites.put("creature.rabbit",
                PixelArt.createSprite("creature.rabbit", rabbitPattern(), rabbitPalette()));
        sprites.put("creature.hare",
                PixelArt.createSprite("creature.hare", harePattern(), harePalette()));
        sprites.put("creature.squirrel",
                PixelArt.createSprite("creature.squirrel", squirrelPattern(), squirrelPalette()));
        sprites.put("creature.deer",
                PixelArt.createSprite("creature.deer", deerPattern(), deerPalette()));
        sprites.put("creature.roe_deer",
                PixelArt.createSprite("creature.roe_deer", roeDeerPattern(), roeDeerPalette()));
        sprites.put("creature.boar",
                PixelArt.createSprite("creature.boar", boarPattern(), boarPalette()));
        sprites.put("creature.mouflon",
                PixelArt.createSprite("creature.mouflon", mouflonPattern(), mouflonPalette()));

        // 玩家
        sprites.put("player",
                PixelArt.createSprite("player", playerPattern(), playerPalette()));

        return sprites;
    }
}
