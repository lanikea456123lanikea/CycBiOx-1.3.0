package com.cellphenotype.qupath.model;

/**
 * 分割模型枚举
 * 定义支持的细胞分割模型
 *
 * 重要说明 - QuPath测量格式（基于实际数据）：
 *
 * 1. StarDist: "Nucleus: CD68: Mean" (Compartment: Channel: Statistic，冒号分隔)
 *    - Compartments: Nucleus, Cytoplasm, Membrane, Cell
 *    - Statistics: Mean, Median, Min, Max, Std.Dev.
 *
 * 2. Cellpose: "CD68: Nucleus: Mean" (Channel: Compartment: Statistic，通道名在前)
 *    - Compartments: Nucleus, Cytoplasm, Membrane, Cell
 *    - Statistics: Mean, Median, Min, Max, Std.Dev.
 *
 * 3. InstanSeg: "Cell: CD68: Mean" (Compartment: Channel: Statistic，Cell优先)
 *    - Compartments: Cell, Nucleus, Cytoplasm, Membrane
 *    - Statistics: Mean, Median, Min, Max, Std.Dev.
 *    - 自动阈值计算使用Cell数据
 *
 * 4. QuPath Detection: "Nucleus: CD68 mean" (Compartment: Channel statistic，空格分隔，小写)
 *    - Compartments: Nucleus, Cell, Cytoplasm
 *    - Statistics: mean, std dev, max, min
 */
public enum SegmentationModel {
    STARDIST("StarDist", "Nucleus"),
    CELLPOSE("Cellpose", "Nucleus"),
    INSTANSEG("InstanSeg", "Cell"),
    QUPATH_DETECTION("QuPath Detection", "Nucleus");

    private final String displayName;
    private final String defaultCompartment;

    SegmentationModel(String displayName, String defaultCompartment) {
        this.displayName = displayName;
        this.defaultCompartment = defaultCompartment;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMeasurementPrefix() {
        return defaultCompartment;
    }

    public static SegmentationModel fromDisplayName(String name) {
        if (name == null) {
            return STARDIST;
        }
        for (SegmentationModel model : values()) {
            if (model.displayName.equals(name)) {
                return model;
            }
        }
        return STARDIST;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
