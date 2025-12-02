
// TODO: [代码功能] 细胞分类服务 (1200+行) ⭐ 核心文件

package com.cellphenotype.qupath.service;

// TODO: [导入] 服务依赖模块
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellphenotype.qupath.model.CellPhenotype;
import com.cellphenotype.qupath.model.ThresholdConfig;
import com.cellphenotype.qupath.utils.ColorUtils;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;

/**
     * TODO: [方法] 简化方法
     */

public class CellClassificationService {

    private static final Logger logger = LoggerFactory.getLogger(CellClassificationService.class);

    /**
     * TODO: [数据] 分类结果数据类
     */
    public static class ClassificationResult {
        private final Map<PathObject, String> classificationResults;
        private final Map<PathObject, String> cellTypeResults;
        private final Map<String, Integer> statisticsByClassification;
        private final Map<String, Integer> statisticsByCellType;

        public ClassificationResult(Map<PathObject, String> classificationResults,
                                  Map<PathObject, String> cellTypeResults,
                                  Map<String, Integer> statisticsByClassification,
                                  Map<String, Integer> statisticsByCellType) {
            this.classificationResults = classificationResults;
            this.cellTypeResults = cellTypeResults;
            this.statisticsByClassification = statisticsByClassification;
            this.statisticsByCellType = statisticsByCellType;
        }

        // TODO: [方法] 结果访问器
        public Map<PathObject, String> getClassificationResults() { return classificationResults; }
        public Map<PathObject, String> getCellTypeResults() { return cellTypeResults; }
        public Map<String, Integer> getStatisticsByClassification() { return statisticsByClassification; }
        public Map<String, Integer> getStatisticsByCellType() { return statisticsByCellType; }
    }

    /**
     * TODO: [方法] 完整分类流程执行
     */
    public static ClassificationResult classifyCells(ImageData<?> imageData,
                                                   ThresholdConfig thresholdConfig,
                                                   List<CellPhenotype> phenotypes) {
        if (imageData == null || thresholdConfig == null || phenotypes == null) {
            return createEmptyResult();
        }

        Collection<PathObject> detections = imageData.getHierarchy().getDetectionObjects();
        if (detections.isEmpty()) {
            return createEmptyResult();
        }

        // TODO: [映射] 创建测量名称映射
        // v1.4.0: 使用SegmentationModel构建固定前缀的测量名称
        List<String> channelNames = new ArrayList<>(thresholdConfig.getChannelThresholds().keySet());
        Map<String, String> measurementMapping = buildMeasurementMapping(channelNames, thresholdConfig);

        // TODO: [处理] 执行阈值分类
        Map<PathObject, String> classificationResults = performThresholdClassification(
                detections, thresholdConfig, measurementMapping);

        // TODO: [处理] 执行细胞表型分类
        Map<PathObject, String> cellTypeResults = performPhenotypeClassification(
                detections, thresholdConfig, measurementMapping, phenotypes);

        // TODO: [处理] 应用分类结果到细胞对象
        applyClassificationResults(classificationResults, cellTypeResults);

        // TODO: [计算] 计算统计信息
        Map<String, Integer> classificationStats = calculateStatistics(classificationResults);
        Map<String, Integer> cellTypeStats = calculateStatistics(cellTypeResults);

        // TODO: [刷新] 同步显示
        ColorUtils.syncQuPathDisplay(imageData);

        return new ClassificationResult(classificationResults, cellTypeResults,
                                      classificationStats, cellTypeStats);
    }

    /**
     * TODO: [方法] 阈值分类执行
     */
    public static Map<PathObject, String> performThresholdClassification(
            Collection<PathObject> detections,
            ThresholdConfig thresholdConfig,
            Map<String, String> measurementMapping) {

        Map<PathObject, String> results = new ConcurrentHashMap<>();

        // TODO: [性能] 并行处理提高性能
        detections.parallelStream().forEach(detection -> {
            String classificationResult = classifySingleCell(detection, thresholdConfig, measurementMapping);
            if (classificationResult != null) {
                results.put(detection, classificationResult);
                // TODO: [存储] 设置 Classification_Info 测量值
                detection.getMeasurementList().put("Classification_Info", (double)classificationResult.hashCode());
            }
        });

        return results;
    }

    /**
     * TODO: [方法] 细胞表型分类执行
     * Build 17: 确保使用与Load Classifier完全相同的阈值配置
     */
    public static Map<PathObject, String> performPhenotypeClassification(
            Collection<PathObject> detections,
            ThresholdConfig thresholdConfig,
            Map<String, String> measurementMapping,
            List<CellPhenotype> phenotypes) {

        Map<PathObject, String> results = new ConcurrentHashMap<>();

        // TODO: [排序] 按优先级排序表型
        List<CellPhenotype> sortedPhenotypes = phenotypes.stream()
                .sorted(Comparator.comparingInt(CellPhenotype::getPriority))
                .collect(Collectors.toList());

        // 添加调试日志
        System.out.println("=== Cell Phenotype Classification Debug ===");
        System.out.println("Total detections: " + detections.size());
        System.out.println("Total phenotypes: " + sortedPhenotypes.size());
        for (CellPhenotype pheno : sortedPhenotypes) {
            System.out.println("  Phenotype: " + pheno.getName() + " (priority: " + pheno.getPriority() + ")");
            System.out.println("    Marker states: " + pheno.getMarkerStates());
        }

        // v1.7.8性能优化：对于小数据集使用串行处理，大数据集使用并行处理
        // 选中细胞数量通常较少，使用串行处理更高效
        if (detections.size() < 100) {
            // 小数据集：串行处理，避免parallelStream的开销
            for (PathObject detection : detections) {
            // v1.7.8修复：使用字典形式（Map<String, Boolean>）进行比较，而不是字符串
            // 用户要求："表型定义和classification匹配问题，出错，字典形式精确匹配"
            Map<String, Boolean> markerStates = parseClassificationFromCell(detection, measurementMapping);

            String cellType = classifyPhenotypeFromStates(markerStates, sortedPhenotypes);

            if (cellType != null && !"undefined".equals(cellType)) {
                logger.debug("✅ [MATCH-SUCCESS] 细胞ID: {} -> 表型: {}",
                           detection.getID(), cellType);
            }

            if (cellType != null) {
                results.put(detection, cellType);
                // TODO: [存储] 设置 CellType_Info 测量值
                detection.getMeasurementList().put("CellType_Info", (double)cellType.hashCode());

                // v1.7.8修复：同时设置PathClass，确保export时能正确读取cellType
                // 这是关键的修复：export时依赖cell.getPathClass()获取cellType
                // 如果不设置PathClass，export时会显示"undefined"
                qupath.lib.objects.classes.PathClass pathClass = qupath.lib.objects.classes.PathClass.fromString(cellType);
                detection.setPathClass(pathClass);
            }
            }
        } else {
            // 大数据集：并行处理，利用多核CPU
            detections.parallelStream().forEach(detection -> {
                // v1.7.8修复：使用字典形式（Map<String, Boolean>）进行比较，而不是字符串
                Map<String, Boolean> markerStates = parseClassificationFromCell(detection, measurementMapping);

                logger.info("🔍 [MATCH-DEBUG] 细胞ID: {}, MarkerStates: {}",
                           detection.getID(), markerStates);

                String cellType = classifyPhenotypeFromStates(markerStates, sortedPhenotypes);

                if (cellType != null && !"undefined".equals(cellType)) {
                    logger.info("✅ [MATCH-SUCCESS] 细胞ID: {} -> 表型: {}",
                               detection.getID(), cellType);
                } else {
                    logger.warn("❌ [MATCH-FAILED] 细胞ID: {} -> undefined (无匹配的表型)",
                               detection.getID());
                }

                if (cellType != null) {
                    results.put(detection, cellType);
                    detection.getMeasurementList().put("CellType_Info", (double)cellType.hashCode());
                    qupath.lib.objects.classes.PathClass pathClass = qupath.lib.objects.classes.PathClass.fromString(cellType);
                    detection.setPathClass(pathClass);
                }
            });
        }
        Map<String, Long> stats = results.values().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                type -> type,
                java.util.stream.Collectors.counting()
            ));
        System.out.println("Classification results:");
        stats.forEach((type, count) -> System.out.println("  " + type + ": " + count));
        System.out.println("===========================================");

        return results;
    }

    /**
     * TODO: [方法] 应用分类结果 - 支持独立显示
     * @param classificationResults Classification结果映射
     * @param cellTypeResults CellType结果映射
     * @param displayMode 显示模式："classification" 或 "celltype"
     */
    public static void applyClassificationResults(Map<PathObject, String> classificationResults,
                                                Map<PathObject, String> cellTypeResults,
                                                String displayMode) {
        if ("classification".equalsIgnoreCase(displayMode)) {
            // 显示Classification伪彩
            ColorUtils.applyClassificationColors(classificationResults.keySet(), classificationResults);
        } else {
            // 显示CellType伪彩（默认）
            ColorUtils.applyCellTypeColors(cellTypeResults.keySet(), cellTypeResults);
        }
    }

    /**
     * TODO: [方法] 应用分类结果（兼容旧接口，默认显示CellType）
     */
    public static void applyClassificationResults(Map<PathObject, String> classificationResults,
                                                Map<PathObject, String> cellTypeResults) {
        applyClassificationResults(classificationResults, cellTypeResults, "celltype");
    }

    /**
     * TODO: [方法] 仅应用Classification结果（Load Classifier专用）
     */
    public static void applyClassificationResultsOnly(Map<PathObject, String> classificationResults) {
        ColorUtils.applyClassificationColors(classificationResults.keySet(), classificationResults);
    }

    /**
     * TODO: [方法] 仅应用CellType结果（Cell Classification专用）
     */
    public static void applyCellTypeResultsOnly(Map<PathObject, String> cellTypeResults) {
        ColorUtils.applyCellTypeColors(cellTypeResults.keySet(), cellTypeResults);
    }

    /**
     * TODO: [方法] 获取细胞标记状态
     */
    public static Map<String, Boolean> getCellMarkerStates(PathObject detection,
                                                          ThresholdConfig thresholdConfig,
                                                          Map<String, String> measurementMapping) {
        Map<String, Boolean> markerStates = new HashMap<>();

        for (Map.Entry<String, ThresholdConfig.ChannelThreshold> entry :
                thresholdConfig.getChannelThresholds().entrySet()) {

            String channelName = entry.getKey();
            ThresholdConfig.ChannelThreshold threshold = entry.getValue();

            if (!threshold.isEnabled()) {
                continue;
            }

            String measurementName = measurementMapping.get(channelName);
            if (measurementName == null) {
                logger.warn("⚠️ [MEASUREMENT-MAP] 通道 '{}' 的测量名称未找到!", channelName);
                continue;
            }

            double value = detection.getMeasurementList().get(measurementName);
            boolean isPositive = !Double.isNaN(value) && value > threshold.getThreshold();

            // v1.7.8: 添加详细日志查看每个marker的计算过程 (改为INFO级别以便查看)
            logger.info("🔬 [MEASUREMENT-DETAIL] 通道: {}, 测量值: {}, 阈值: {}, 结果: {}",
                        channelName, value, threshold.getThreshold(), isPositive ? "阳性(+)" : "阴性(-)");

            markerStates.put(channelName, isPositive);
        }

        return markerStates;
    }

    // TODO: [方法] 私有辅助方法

    private static ClassificationResult createEmptyResult() {
        return new ClassificationResult(
                new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>());
    }

    private static String classifySingleCell(PathObject detection,
                                           ThresholdConfig thresholdConfig,
                                           Map<String, String> measurementMapping) {
        Map<String, Boolean> markerStates = getCellMarkerStates(detection, thresholdConfig, measurementMapping);

        if (markerStates.isEmpty()) {
            return "Unclassified";
        }

        // TODO: [生成] 组合标签 (如 "CD3+_CD4+_CD8-")
        return markerStates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + (entry.getValue() ? "+" : "-"))
                .collect(Collectors.joining("_"));
    }

    /**
     * Build 17: 从marker states分类表型
     * 关键：markerStates必须来自getCellMarkerStates()，确保与Load Classifier一致
     */
    private static String classifyPhenotypeFromStates(Map<String, Boolean> markerStates,
                                                     List<CellPhenotype> sortedPhenotypes) {
        if (markerStates.isEmpty()) {
            return "Unclassified";
        }

        // TODO: [查找] 按优先级查找第一个匹配的表型
        for (CellPhenotype phenotype : sortedPhenotypes) {
            if (phenotype.matches(markerStates)) {
                return phenotype.getName();
            }
        }

        return "undefined";
    }

    private static Map<String, Integer> calculateStatistics(Map<PathObject, String> results) {
        return results.values().stream()
                .collect(Collectors.groupingBy(
                        result -> result,
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
    }

    /**
     * v1.4.0: 构建测量名称映射
     * @param channelNames 通道名称列表
     * @param thresholdConfig 阈值配置（包含分割模型和测量类型）
     * @return 通道名称到完整测量名称的映射
     */
    private static Map<String, String> buildMeasurementMapping(List<String> channelNames,
                                                                ThresholdConfig thresholdConfig) {
        Map<String, String> mapping = new HashMap<>();

        for (String channelName : channelNames) {
            ThresholdConfig.ChannelThreshold channelThreshold = thresholdConfig.getChannelThresholds().get(channelName);
            if (channelThreshold == null) {
                continue;
            }

            // getMeasurement()已经返回完整的测量名称（如"Cell: CD68: Mean"）
            String measurementName = channelThreshold.getMeasurement();

            if (measurementName != null) {
                mapping.put(channelName, measurementName);
            }
        }

        return mapping;
    }

    /**
     * v1.7.8修复：从细胞中解析已保存的Classification结果
     * 用户说："Classification中如果标签是CD3+，表明已经高于阈值，只需要后续和celltype自定义比对而已"
     * 用户进一步说明：一个细胞只会有一个标识符
     * - "CD3+" 代表的是：CD3+NK1.1-CD8-
     * - "unclassified" 代表的是：全阴性（CD3-NK1.1-CD8-）
     *
     * @param detection 细胞对象
     * @return marker名称到阳性/阴性的映射
     */
    /**
     * v1.7.8: 单标识符逻辑
     * 一个细胞只会有一个标识符，需要将这个标识符转换为所有marker的state
     * 例如：
     * - "CD3+" → {CD3=true, 所有其他marker=false}
     * - "unclassified" → {所有marker=false}
     *
     * @param detection 细胞对象
     * @param measurementMapping 测量值映射，用于获取所有可能的marker
     * @return marker名称到阳性/阴性的映射
     */
    private static Map<String, Boolean> parseClassificationFromCell(PathObject detection, Map<String, String> measurementMapping) {
        Map<String, Boolean> markerStates = new HashMap<>();

        // 从metadata中读取classification
        Object classificationObj = detection.getMetadata().get("classification");
        String classification = classificationObj != null ? classificationObj.toString() : null;

        // 从PathClass中读取classification（备用）
        if (classification == null && detection.getPathClass() != null) {
            classification = detection.getPathClass().getName();
        }

        // 如果没有classification，返回空映射
        if (classification == null || classification.trim().isEmpty()) {
            return markerStates;
        }

        // 从measurementMapping中获取所有可能的marker名称
        Set<String> allMarkers = measurementMapping != null ? measurementMapping.keySet() : new HashSet<>();

        // 特殊情况：unclassified 解析为所有marker都是false
        if ("unclassified".equalsIgnoreCase(classification)) {
            for (String marker : allMarkers) {
                markerStates.put(marker, false);
            }
            return markerStates;
        }

        // 检查是否是单标识符（不以_分隔，只有+或-）
        if (!classification.contains("_") && (classification.endsWith("+") || classification.endsWith("-"))) {
            // 单标识符：例如 "CD3+" 或 "CD8-"
            String markerName = classification.substring(0, classification.length() - 1);
            boolean isPositive = classification.endsWith("+");

            // 遍历所有marker
            for (String marker : allMarkers) {
                if (marker.equals(markerName)) {
                    // 标识符对应的marker设为指定值
                    markerStates.put(marker, isPositive);
                } else {
                    // 其他marker设为false（阴性）
                    markerStates.put(marker, false);
                }
            }
        } else {
            // 兼容旧的多标识符格式（如"CD3+_CD4+_CD8-"）
            String[] markers = classification.split("_");
            for (String marker : markers) {
                if (marker.isEmpty()) {
                    continue;
                }

                // 检查标记结尾是+还是-
                if (marker.endsWith("+")) {
                    // 阳性：去掉+号
                    String markerName = marker.substring(0, marker.length() - 1);
                    markerStates.put(markerName, true);
                } else if (marker.endsWith("-")) {
                    // 阴性：去掉-号
                    String markerName = marker.substring(0, marker.length() - 1);
                    markerStates.put(markerName, false);
                }
            }
        }

        return markerStates;
    }
}