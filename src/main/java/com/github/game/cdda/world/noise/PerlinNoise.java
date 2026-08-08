package com.github.game.cdda.world.noise;

import java.util.Random;

/**
 * 经典 Perlin 噪声实现（2D）。
 * 使用 Ken Perlin 改进版梯度噪声算法，种子化置换表保证确定性。
 *
 * 算法核心：
 * 1. 将输入坐标映射到网格单元
 * 2. 计算单元四个顶点到输入点的梯度贡献
 * 3. 使用 fade 函数平滑插值
 *
 * 输出范围约 [-1, 1]。
 */
public class PerlinNoise {

    /** 置换表（512 长度，前 256 个为 0-255 的排列，后 256 个为重复以简化取模） */
    private final int[] perm;

    /**
     * 使用指定种子创建 Perlin 噪声生成器。
     * 相同种子产生相同的噪声图案。
     */
    public PerlinNoise(long seed) {
        perm = new int[512];
        int[] base = new int[256];

        // 初始化 0-255 的排列
        for (int i = 0; i < 256; i++) {
            base[i] = i;
        }

        // Fisher-Yates 洗牌
        Random rng = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = base[i];
            base[i] = base[j];
            base[j] = tmp;
        }

        // 复制到 512 长度，避免取模运算
        for (int i = 0; i < 256; i++) {
            perm[i] = base[i];
            perm[i + 256] = base[i];
        }
    }

    /**
     * 2D Perlin 噪声。
     *
     * @param x X 坐标
     * @param y Y 坐标
     * @return 噪声值，约 [-1, 1] 范围
     */
    public double noise(double x, double y) {
        // 确定所在网格单元
        int xi = floor(x) & 255;
        int yi = floor(y) & 255;

        // 单元内相对坐标 [0, 1)
        double xf = x - floor(x);
        double yf = y - floor(y);

        // fade 曲线：6t^5 - 15t^4 + 10t^3（平滑插值，一阶和二阶导数为 0）
        double u = fade(xf);
        double v = fade(yf);

        // 四个顶点的哈希值
        int aa = perm[perm[xi] + yi];
        int ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi];
        int bb = perm[perm[xi + 1] + yi + 1];

        // 四个顶点的梯度贡献
        double g00 = grad(aa, xf, yf);
        double g10 = grad(ba, xf - 1, yf);
        double g01 = grad(ab, xf, yf - 1);
        double g11 = grad(bb, xf - 1, yf - 1);

        // 双线性插值
        double x0 = lerp(g00, g10, u);
        double x1 = lerp(g01, g11, u);
        return lerp(x0, x1, v);
    }

    /**
     * 分形布朗运动（fBm），叠加多个八度的噪声。
     * 每个八度频率增加、振幅衰减，产生更自然的地形。
     *
     * @param x           X 坐标
     * @param y           Y 坐标
     * @param octaves     八度数（层数，越多细节越丰富）
     * @param persistence 振幅衰减系数（通常 0.3-0.7）
     * @param lacunarity  频率倍增系数（通常 2.0）
     * @return 叠加后的噪声值
     */
    public double fbm(double x, double y, int octaves,
                      double persistence, double lacunarity) {
        double total = 0;
        double frequency = 1;
        double amplitude = 1;
        double maxValue = 0;  // 用于归一化到 [-1, 1]

        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }

        return total / maxValue;
    }

    /**
     * 平滑插值函数：6t^5 - 15t^4 + 10t^3。
     * 比传统的 3t^2 - 2t^3 更平滑（一阶和二阶导数在端点为 0）。
     */
    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** 线性插值 */
    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    /**
     * 根据哈希值选择梯度方向并计算点积。
     * 使用 4 个方向向量：(1,1), (-1,1), (1,-1), (-1,-1)。
     * 通过哈希值的低 2 位选择方向。
     */
    private static double grad(int hash, double x, double y) {
        switch (hash & 3) {
            case 0: return  x + y;   // (1, 1)
            case 1: return -x + y;   // (-1, 1)
            case 2: return  x - y;   // (1, -1)
            case 3: return -x - y;   // (-1, -1)
            default: return 0;       // 不会到达
        }
    }

    /** 整数地板（向下取整） */
    private static int floor(double x) {
        int i = (int) x;
        return (x < i) ? i - 1 : i;
    }
}
