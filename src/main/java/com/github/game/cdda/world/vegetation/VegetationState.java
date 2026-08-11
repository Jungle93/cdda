package com.github.game.cdda.world.vegetation;

import com.github.game.cdda.game.PlantGrowthConstants;

/**
 * 植被生长状态。
 *
 * <p>记录每个瓦片上植被的生长详情，与 {@link VegetationMap} 的物种 ID 配合使用。
 * VegetationMap 存储"是什么物种"，GrowthState 存储"长势如何"。
 *
 * <p>当瓦片无植被时，对应位置的生长状态为 null。
 */
public class VegetationState {

    /** 物种 ID（如 "oak", "birch"） */
    public String speciesId;

    /** 当前生长阶段 */
    public GrowthStage stage;

    /** 健康度（0~1）— 肥力充足时为 1.0，不足时逐渐降低 */
    public double health;

    /** 总生长天数（游戏日） */
    public int totalGrowthDays;

    /** 上次消耗肥力的天数（用于避免重复消耗） */
    public int lastFertilityConsumeDay;

    /** 创建时间（总游戏秒数，用于计算绝对年龄） */
    public long birthTotalSeconds;

    public VegetationState(String speciesId) {
        this.speciesId = speciesId;
        this.stage = GrowthStage.GERMINATING;
        this.health = 1.0;
        this.totalGrowthDays = 0;
        this.lastFertilityConsumeDay = -1;
        this.birthTotalSeconds = 0;
    }

    public VegetationState(String speciesId, long birthTotalSeconds) {
        this.speciesId = speciesId;
        this.stage = GrowthStage.GERMINATING;
        this.health = 1.0;
        this.totalGrowthDays = 0;
        this.lastFertilityConsumeDay = -1;
        this.birthTotalSeconds = birthTotalSeconds;
    }

    /**
     * 获取植物类型字符串（从物种 ID 推断）。
     * 如果物种已注册，使用注册的 type；否则从 ID 推断。
     */
    public String getPlantType() {
        VegetationDefinition def = VegetationRegistry.getById(speciesId);
        if (def != null && def.type != null) {
            return def.type.name().toLowerCase();
        }
        // 从 ID 推断
        return inferPlantType(speciesId);
    }

    /**
     * 从物种 ID 推断植物类型。
     */
    private String inferPlantType(String id) {
        // 常见后缀/前缀匹配
        if (id.contains("tree") || id.contains("oak") || id.contains("pine")
                || id.contains("birch") || id.contains("fir") || id.contains("beech")
                || id.contains("willow") || id.contains("palm") || id.contains("cedar")) {
            return "tree";
        }
        if (id.contains("shrub") || id.contains("bush") || id.contains("hazel")
                || id.contains("holly") || id.contains("gorse") || id.contains("heather")
                || id.contains("berry")) {
            return "shrub";
        }
        if (id.contains("flower") || id.contains("bloom") || id.contains("rose")) {
            return "flower";
        }
        if (id.contains("moss") || id.contains("lichen")) {
            return "moss";
        }
        // 默认按草处理
        return "grass";
    }

    /**
     * 检查当前肥力是否满足当前生长阶段的需求。
     *
     * @param fertility 当前地块肥力
     * @return true 如果肥力足够
     */
    public boolean isFertilitySufficient(double fertility) {
        String plantType = getPlantType();
        double required = switch (stage) {
            case GERMINATING -> PlantGrowthConstants.getGerminateFertility(plantType);
            case SEEDLING -> PlantGrowthConstants.getSeedlingFertility(plantType);
            case GROWING -> PlantGrowthConstants.getGrowingFertility(plantType);
            case MATURE -> PlantGrowthConstants.getMatureFertility(plantType);
            case WITHERED -> 0;
        };
        return fertility >= required;
    }

    /**
     * 获取当前阶段每日肥力消耗量。
     */
    public double getDailyFertilityCost() {
        if (stage.isDead()) return 0;
        String plantType = getPlantType();
        return PlantGrowthConstants.getDailyFertilityCost(plantType);
    }

    /**
     * 判断是否应该进入下一个生长阶段。
     */
    public boolean shouldAdvanceStage() {
        if (stage.isDead()) return false;
        int duration = stage.getDurationDays(getPlantType());
        return totalGrowthDays >= duration;
    }

    /**
     * 进入下一个生长阶段。
     */
    public void advanceStage() {
        if (stage.isDead()) return;
        stage = switch (stage) {
            case GERMINATING -> GrowthStage.SEEDLING;
            case SEEDLING -> GrowthStage.GROWING;
            case GROWING -> GrowthStage.MATURE;
            case MATURE -> GrowthStage.WITHERED; // 寿命到期
            case WITHERED -> GrowthStage.WITHERED;
        };
    }

    /**
     * 标记为枯萎。
     */
    public void wither() {
        this.stage = GrowthStage.WITHERED;
        this.health = 0.0;
    }

    @Override
    public String toString() {
        return String.format("VegetationState{species=%s, stage=%s, health=%.2f, days=%d}",
                speciesId, stage, health, totalGrowthDays);
    }
}
