package com.github.game.cdda.sprite;

import com.github.game.engine.core.sprite.Sprite;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 生物精灵点阵数据 —— 为每种生物定义 16×16 俯视像素画。
 * <p>
 * 所有生物采用统一风格：俯视视角，面朝下方，透明背景，深色轮廓。
 * 每个精灵使用 3-5 色调色板，通过 {@link PixelArt#createSprite} 生成。
 * </p>
 * <p>
 * 点阵约定：{@code '.'} = 透明，其他字符对应调色板中的颜色。
 * 每行必须恰好 16 个字符，共 16 行。
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
    // 每行恰好 16 字符，共 16 行
    // '.' = 透明

    /**
     * 狼 —— 灰色犬科，尖耳，蓬松尾巴。
     */
    private static String[] wolfPattern() {
        return new String[]{
                "................", //  0
                ".....oo..oo.....", //  1  耳尖
                "....oddo.oddo...", //  2  耳朵
                "....odmmmmdo....", //  3  头顶
                "....odmlmlmdo...", //  4  眼睛
                ".....odmmno.....", //  5  鼻/口
                ".....odwwo......", //  6  白胸
                "....odmmmmdo....", //  7  肩部
                "...odmmmmmmmdo..", //  8  身体
                "...odmmmmmmmdo..", //  9  身体
                "....odmmmmdo....", // 10  臀部
                "....oddo.ddo....", // 11  腿+尾根
                "....odo..odo....", // 12  脚
                "....oo...dmoo...", // 13  尾
                ".........oddo...", // 14  尾尖
                "................", // 15
        };
    }

    /**
     * 狐狸 —— 橙色，尖耳，白胸，蓬松尾巴带白尖。
     */
    private static String[] foxPattern() {
        return new String[]{
                "................", //  0
                ".....oo..oo.....", //  1  耳尖
                "....oddo.oddo...", //  2  耳朵
                "....odmmmmdo....", //  3  头顶
                "....odmlmlmdo...", //  4  眼睛
                ".....odmno......", //  5  鼻
                ".....odwwo......", //  6  白胸
                "....odmmmmdo....", //  7  肩部
                "...odmmmmmmdo...", //  8  身体
                "...odmmmdddoo...", //  9  身体+尾
                "....odddwwdo....", // 10  尾巴白尖
                "....oddo.owwo...", // 11  腿+尾尖
                "....odo..owo....", // 12  脚
                "....oo...ooo....", // 13  尾末
                "................", // 14
                "................", // 15
        };
    }

    /**
     * 獾 —— 矮壮，面部白条，黑灰身体。
     */
    private static String[] badgerPattern() {
        return new String[]{
                "................", //  0
                "....oo....oo....", //  1  耳
                "...odwoo.dwoo...", //  2  头部+白条
                "...odwwwdwoo....", //  3  面部白条
                "...odeweldeo....", //  4  眼睛
                "....oddddo......", //  5  下巴
                "...oddddddddo...", //  6  宽肩
                "..oddddddddddo..", //  7  身体
                "..oddddddddddo..", //  8  身体
                "..oddddddddddo..", //  9  身体
                "...oddddddddo...", // 10  臀
                "....oddo.ddo....", // 11  腿
                "....odo..odo....", // 12  脚
                "....oo...oo.....", // 13
                "................", // 14
                "................", // 15
        };
    }

    /**
     * 兔子 —— 小圆身，长耳朵。
     */
    private static String[] rabbitPattern() {
        return new String[]{
                "................", //  0
                "......opo.......", //  1  耳尖
                ".....opppo......", //  2  耳朵
                ".....oppo.......", //  3  耳朵
                ".....odddo......", //  4  耳根/头
                "....odmmmmdo....", //  5  头
                "....odmlmlmdo...", //  6  眼睛
                ".....odmnod.....", //  7  鼻
                ".....odmmo......", //  8  身体
                "....odmmmmdo....", //  9  身体
                "....odmmmmdo....", // 10  身体
                "....odmmmmdo....", // 11  身体
                ".....od..do.....", // 12  小腿
                ".....oo..oo.....", // 13  脚
                "................", // 14
                "................", // 15
        };
    }

    /**
     * 野兔 —— 比兔子更大，更长耳朵。
     */
    private static String[] harePattern() {
        return new String[]{
                "................", //  0
                ".....op..po.....", //  1  长耳尖
                ".....opp.po.....", //  2  长耳
                ".....opp.po.....", //  3  长耳
                ".....opo.po.....", //  4  耳根
                "....odmmmmdo....", //  5  头
                "....odmlmlmdo...", //  6  眼睛
                ".....odmnod.....", //  7  鼻
                "....odmmmmdo....", //  8  身体
                "...odmmmmmmmdo..", //  9  身体（大）
                "...odmmmmmmmdo..", // 10  身体
                "....odmmmmdo....", // 11  身体
                "....oddo.ddo....", // 12  腿
                "....odo..odo....", // 13  脚
                "....oo...oo.....", // 14
                "................", // 15
        };
    }

    /**
     * 松鼠 —— 小身体，大蓬松尾巴弯曲向上。
     */
    private static String[] squirrelPattern() {
        return new String[]{
                "................", //  0
                ".......ooo......", //  1  尾尖
                "......oddddo....", //  2  尾巴
                ".....odmmmdo....", //  3  尾弯
                ".....odmmmddo...", //  4  尾+头
                ".....odmlledo...", //  5  眼+尾
                ".....odmmnddo...", //  6  鼻+尾
                ".....odwwdoo....", //  7  白胸
                ".....odmmmdo....", //  8  身体
                "......oddddo....", //  9  尾绕
                "......od.do.....", // 10  腿
                "......od.do.....", // 11  腿
                "......oo.oo.....", // 12  脚
                "................", // 13
                "................", // 14
                "................", // 15
        };
    }

    /**
     * 鹿 —— 金棕色，鹿角，纤细体型。
     */
    private static String[] deerPattern() {
        return new String[]{
                "...ao......oa...", //  0  鹿角尖
                "...aoo....ooa...", //  1  鹿角
                "....ao....oa....", //  2  鹿角基
                "....odmmmmdo....", //  3  头
                "....odmlmlmdo...", //  4  眼睛
                ".....odmno......", //  5  鼻
                ".....odddo......", //  6  颈
                "....odmmmmdo....", //  7  肩
                "...odmmmmmmmdo..", //  8  身体
                "...odmmwmmmmdo..", //  9  身体（白点）
                "...odmmmmmmmdo..", // 10  身体
                "....odmmmmdo....", // 11  臀
                "....oddo.ddo....", // 12  腿
                "....odo..odo....", // 13  脚
                "....oo...oo.....", // 14
                "................", // 15
        };
    }

    /**
     * 狍子 —— 比鹿小，鹿角更小，更纤细。
     */
    private static String[] roeDeerPattern() {
        return new String[]{
                "................", //  0
                ".....ao..oa.....", //  1  小鹿角
                ".....aoo.ooa....", //  2  角基
                "....odmmmmdo....", //  3  头
                "....odmlmlmdo...", //  4  眼睛
                ".....odmno......", //  5  鼻
                ".....odddo......", //  6  颈
                "....odmmmmdo....", //  7  肩
                "...odmmmmmmmdo..", //  8  身体
                "...odmmmmmmmdo..", //  9  身体
                "...odmmmmmmmdo..", // 10  身体
                "....odmmmmdo....", // 11  臀
                "....oddo.ddo....", // 12  腿
                "....odo..odo....", // 13  脚
                "....oo...oo.....", // 14
                "................", // 15
        };
    }

    /**
     * 野猪 —— 宽壮体型，深棕色，獠牙。
     */
    private static String[] boarPattern() {
        return new String[]{
                "................", //  0
                "....oo....oo....", //  1  耳
                "...odddoo.odo...", //  2  头+耳
                "...oddddddddo...", //  3  头
                "...oddtl.ltddo..", //  4  獠牙+眼
                "....odddnddo....", //  5  鼻
                "..oddddddddddo..", //  6  宽肩
                "..oddddddddddo..", //  7  身体
                "..oddddddddddo..", //  8  身体
                "..oddddddddddo..", //  9  身体
                "..oddddddddddo..", // 10  臀
                "...oddddddddo...", // 11  臀
                "....oddo.ddo....", // 12  腿
                "....odo..odo....", // 13  脚
                "....oo...oo.....", // 14
                "................", // 15
        };
    }

    /**
     * 盘羊 —— 健壮，弯曲羊角。
     */
    private static String[] mouflonPattern() {
        return new String[]{
                "...oho....oho...", //  0  角尖
                "..ohho..ohho....", //  1  弯角
                "..ohho..ohho....", //  2  角
                "...oo....oo.....", //  3  角基/耳
                "...odmmmmddo....", //  4  头
                "...odmlmlmdo....", //  5  眼睛
                "....odmnddo.....", //  6  鼻
                "..oddddddddddo..", //  7  宽肩
                "..oddddddddddo..", //  8  身体
                "..oddddddddddo..", //  9  身体
                "..oddddddddddo..", // 10  身体
                "...oddddddddo...", // 11  臀
                "....oddo.ddo....", // 12  腿
                "....odo..odo....", // 13  脚
                "....oo...oo.....", // 14
                "................", // 15
        };
    }

    /**
     * 玩家 —— 人形俯视，蓝色衣物。
     */
    private static String[] playerPattern() {
        return new String[]{
                "................", //  0
                ".....ohhhho.....", //  1  头发
                "....ohssssoo....", //  2  头+发
                "....osseeso.....", //  3  眼睛
                ".....ossssoo....", //  4  脸
                ".....ossso......", //  5  下巴
                "....odmmmmdo....", //  6  肩
                "...odmmmmmmdo...", //  7  上身
                "...odmlmlmdo....", //  8  衣服
                "...odmmmmmmdo...", //  9  下身
                "....odmmmmdo....", // 10  腿
                "....oddo.ddo....", // 11  腿
                "....oboo.obo....", // 12  靴
                "....oboo.obo....", // 13  靴
                "....oboo.obo....", // 14  靴底
                "................", // 15
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
