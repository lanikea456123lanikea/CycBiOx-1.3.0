// TODO: [代码功能] 颜色工具类 (500+行)

package com.cellphenotype.qupath.utils;

// 颜色工具导入依赖模块
// JavaFX颜色系统 - Color类
// QuPath核心类 - GUI/ImageData/PathObject/PathClass
// Java集合类 - 颜色缓存和管理
// 工具类 - Random随机数生成
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javafx.scene.paint.Color;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

// TODO: [类] 颜色工具类

public class ColorUtils {

    // TODO: [工具] 颜色管理常量定义区域
    //   TODO: [工具] 颜色缓存表 - 细胞类型名称到颜色的映射缓存
    private static final Map<String, Integer> COLOR_CACHE = new HashMap<>();
    //   TODO: [工具] 随机数生成器 - 固定种子确保颜色生成一致性
    private static final Random RANDOM = new Random(42);

    //   TODO: [工具] 预定义细胞类型颜色表
    //     TODO: [工具] Unclassified灰色 - 普通未分类状态
    //     TODO: [工具] undefined灰白色 - 主要未分类状态
    // Other中灰色 - 其他类型默认颜色
    private static final Map<String, Integer> PREDEFINED_COLORS = Map.of(
        "Unclassified", 0x808080, // 灰色
        "undefined", 0xE0E0E0,    // 灰白色 - 主要的未分类状态 (更亮的灰白色)
        "Other", 0x606060         // 中灰色
    );

    // TODO: [方法] 获取细胞类型颜色

    public static Integer getCellTypeColor(String cellTypeName) {
        if (cellTypeName == null || cellTypeName.isEmpty()) {
            return PREDEFINED_COLORS.get("undefined");
        }

        // 检查预定义颜色
        if (PREDEFINED_COLORS.containsKey(cellTypeName)) {
            return PREDEFINED_COLORS.get(cellTypeName);
        }

        // 从缓存获取
        String cacheKey = "CellType:" + cellTypeName;
        if (COLOR_CACHE.containsKey(cacheKey)) {
            return COLOR_CACHE.get(cacheKey);
        }

        // 生成新颜色
        Integer color = generateDistinctColor(cellTypeName);
        COLOR_CACHE.put(cacheKey, color);
        return color;
    }

    // TODO: [方法] 获取分类颜色（Classification专用）
    public static Integer getClassificationColor(String classification) {
        if (classification == null || classification.isEmpty()) {
            return PREDEFINED_COLORS.get("Unclassified");
        }

        // 检查预定义颜色
        if (PREDEFINED_COLORS.containsKey(classification)) {
            return PREDEFINED_COLORS.get(classification);
        }

        // 从缓存获取（使用独立前缀区分Classification和CellType）
        String cacheKey = "Classification:" + classification;
        if (COLOR_CACHE.containsKey(cacheKey)) {
            return COLOR_CACHE.get(cacheKey);
        }

        // 生成新颜色
        Integer color = generateDistinctColor(classification + "_cls");
        COLOR_CACHE.put(cacheKey, color);
        return color;
    }

    // TODO: [方法] 创建或获取PathClass
    public static PathClass createOrGetPathClass(String cellTypeName) {
        if (cellTypeName == null || cellTypeName.isEmpty()) {
            return null;
        }

        Integer color = getCellTypeColor(cellTypeName);

        // Always create PathClass with our custom color for predefined types (especially "undefined")
        if (color != null && PREDEFINED_COLORS.containsKey(cellTypeName)) {
            return PathClass.fromString(cellTypeName, color);
        }

        // For non-predefined types, check if existing PathClass has color
        PathClass pathClass = PathClass.fromString(cellTypeName);
        if (color != null && pathClass.getColor() == null) {
            pathClass = PathClass.fromString(cellTypeName, color);
        }

        return pathClass;
    }

    // TODO: [方法] 创建或获取Classification PathClass（v1.3.0：无前缀）
    public static PathClass createOrGetClassificationPathClass(String classification) {
        if (classification == null || classification.isEmpty()) {
            return PathClass.fromString("Unclassified", PREDEFINED_COLORS.get("Unclassified"));
        }

        Integer color = getClassificationColor(classification);

        // v1.3.0: 直接使用分类名称，不添加前缀
        if (color != null && PREDEFINED_COLORS.containsKey(classification)) {
            return PathClass.fromString(classification, color);
        }

        PathClass pathClass = PathClass.fromString(classification);
        if (color != null && pathClass.getColor() == null) {
            pathClass = PathClass.fromString(classification, color);
        }

        return pathClass;
    }

    // TODO: [方法] 批量应用分类颜色（Classification专用）
    // v1.3.0新逻辑：使用组合PathClass，同时显示Classification和CellType
    public static void applyClassificationColors(Collection<PathObject> cells,
                                                Map<PathObject, String> classificationAssignments) {
        for (Map.Entry<PathObject, String> entry : classificationAssignments.entrySet()) {
            PathObject cell = entry.getKey();
            String classification = entry.getValue();

            // 读取已有的CellType（从Measurement）
            String cellType = getCellTypeFromMeasurement(cell);

            // 创建组合PathClass名称
            String combinedName = buildCombinedPathClassName(classification, cellType);

            // 使用Classification的颜色（因为是Load Classifier按钮触发）
            Integer color = getClassificationColor(classification);

            PathClass pathClass;
            if (color != null) {
                pathClass = PathClass.fromString(combinedName, color);
            } else {
                pathClass = PathClass.fromString(combinedName);
            }

            if (pathClass != null) {
                cell.setPathClass(pathClass);
            }

            // 存储到Measurement（用于CSV导出和恢复）
            cell.getMeasurementList().put("Classification_Info", classification.hashCode());

            // v1.3.0: 添加字符串到metadata，显示在Properties面板
            cell.getMetadata().put("classification", classification);
        }
    }

    // TODO: [方法] 批量应用细胞类型颜色（CellType专用）
    // v1.3.0新逻辑：使用组合PathClass，同时显示Classification和CellType
    public static void applyCellTypeColors(Collection<PathObject> cells,
                                         Map<PathObject, String> cellTypeAssignments) {
        for (Map.Entry<PathObject, String> entry : cellTypeAssignments.entrySet()) {
            PathObject cell = entry.getKey();
            String cellType = entry.getValue();

            // 读取已有的Classification（从metadata，保持不变）
            String classification = getClassificationFromMeasurement(cell);

            // 创建PathClass名称（只使用CellType，不使用Classification）
            String combinedName = buildCombinedPathClassName(classification, cellType);

            // 使用CellType的颜色（因为是Cell Classification按钮触发）
            Integer color = getCellTypeColor(cellType);

            PathClass pathClass;
            if (color != null) {
                pathClass = PathClass.fromString(combinedName, color);
            } else {
                pathClass = PathClass.fromString(combinedName);
            }

            if (pathClass != null) {
                cell.setPathClass(pathClass);
            }

            // 存储到Measurement（用于CSV导出）
            cell.getMeasurementList().put("CellType_Info", cellType.hashCode());

            // v1.3.0: 添加字符串到metadata，显示在Properties面板
            cell.getMetadata().put("celltype", cellType);

            // 关键：保留已有的classification metadata（独立不受影响）
            // classification在applyClassificationColors中已经设置，这里不覆盖
        }
    }

    // TODO: [方法] 构建组合PathClass名称（v1.3.0：无前缀，直接显示名称）
    private static String buildCombinedPathClassName(String classification, String cellType) {
        // v1.3.0修复：始终显示CellType，即使是"undefined"也显示
        // 完全独立，不使用Classification数据
        if (cellType != null && !cellType.isEmpty()) {
            return cellType;
        }

        // 都为空时返回默认值
        return "Unclassified";
    }

    // TODO: [方法] 从Metadata读取Classification
    private static String getClassificationFromMeasurement(PathObject cell) {
        // v1.3.0: 从metadata读取（因为PathClass不再有前缀）
        Object classificationObj = cell.getMetadata().get("classification");
        if (classificationObj != null) {
            return classificationObj.toString();
        }
        return null;
    }

    // TODO: [方法] 从Metadata读取CellType
    private static String getCellTypeFromMeasurement(PathObject cell) {
        // v1.3.0: 从metadata读取（因为PathClass不再有前缀）
        Object cellTypeObj = cell.getMetadata().get("celltype");
        if (cellTypeObj != null) {
            return cellTypeObj.toString();
        }
        return null;
    }

    // TODO: [方法] 同步QuPath显示数据
    public static void syncQuPathDisplay(ImageData<?> imageData) {
        if (imageData == null) {
            return;
        }

        UIUtils.runOnFXThread(() -> {
            try {
                // 触发层次结构更新事件
                imageData.getHierarchy().fireHierarchyChangedEvent(null);

                // 刷新GUI显示
                QuPathGUI qupath = QuPathGUI.getInstance();
                if (qupath != null && qupath.getViewer() != null) {
                    qupath.getViewer().repaint();
                }
            } catch (Exception e) {
                // 静默处理显示更新异常，不影响主要功能
                System.err.println("Warning: Failed to sync QuPath display: " + e.getMessage());
            }
        });
    }

    // TODO: [方法] 应用伪彩色显示
    public static void applyPseudoColors(ImageData<?> imageData,
                                       Collection<PathObject> objects) {
        if (imageData == null || objects == null) {
            return;
        }

        // 确保所有对象都有正确的颜色
        for (PathObject obj : objects) {
            PathClass pathClass = obj.getPathClass();
            if (pathClass != null && pathClass.getColor() == null) {
                String className = pathClass.getName();
                Integer color = getCellTypeColor(className);
                if (color != null) {
                    obj.setPathClass(PathClass.fromString(className, color));
                }
            }
        }

        // 同步显示
        syncQuPathDisplay(imageData);
    }

    // TODO: [方法] 重置颜色缓存
    public static void clearColorCache() {
        COLOR_CACHE.clear();
    }

    // TODO: [方法] 获取所有颜色
    public static Map<String, Integer> getAllColors() {
        Map<String, Integer> allColors = new HashMap<>(PREDEFINED_COLORS);
        allColors.putAll(COLOR_CACHE);
        return allColors;
    }

    // TODO: [方法] Color转换为RGB
    public static Integer colorToRGB(Color color) {
        if (color == null) {
            return null;
        }

        int red = (int) (color.getRed() * 255);
        int green = (int) (color.getGreen() * 255);
        int blue = (int) (color.getBlue() * 255);

        return (red << 16) | (green << 8) | blue;
    }

    // TODO: [方法] RGB转换为Color
    public static Color rgbToColor(Integer rgb) {
        if (rgb == null) {
            return null;
        }

        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        return Color.rgb(red, green, blue);
    }

    // 私有辅助方法

    // TODO: [方法] 生成区分颜色
    private static Integer generateDistinctColor(String name) {
        // 使用名称的哈希值作为种子，确保同名的颜色一致
        RANDOM.setSeed(name.hashCode());

        // 生成饱和度和亮度较高的颜色，确保可视性
        float hue = RANDOM.nextFloat();
        float saturation = 0.6f + RANDOM.nextFloat() * 0.4f; // 0.6-1.0
        float brightness = 0.5f + RANDOM.nextFloat() * 0.4f; // 0.5-0.9

        java.awt.Color awtColor = java.awt.Color.getHSBColor(hue, saturation, brightness);
        return awtColor.getRGB() & 0xFFFFFF; // 去除alpha通道
    }
}