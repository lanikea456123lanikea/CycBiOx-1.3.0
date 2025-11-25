
// TODO: [代码功能] 细胞分类服务 (1200+行) ⭐ 核心文件

package com.cellphenotype.qupath.service;

// TODO: [导入] 服务依赖模块
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.cellphenotype.qupath.model.CellPhenotype;
import com.cellphenotype.qupath.model.ThresholdConfig;
import com.cellphenotype.qupath.utils.ColorUtils;
import com.cellphenotype.qupath.utils.MeasurementUtils;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;

/**
     * TODO: [方法] 简化方法
     */

public class CellClassificationService {

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

        detections.parallelStream().forEach(detection -> {
            // Build 17: 使用getCellMarkerStates确保与Load Classifier一致
            // 关键：这里使用的thresholdConfig必须与Load Classifier时使用的完全相同
            Map<String, Boolean> markerStates = getCellMarkerStates(detection, thresholdConfig, measurementMapping);

            String cellType = classifyPhenotypeFromStates(markerStates, sortedPhenotypes);
            if (cellType != null) {
                results.put(detection, cellType);
                // TODO: [存储] 设置 CellType_Info 测量值
                detection.getMeasurementList().put("CellType_Info", (double)cellType.hashCode());
            }
        });

        // 统计结果
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
                continue;
            }

            double value = detection.getMeasurementList().get(measurementName);
            boolean isPositive = !Double.isNaN(value) && value > threshold.getThreshold();
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
}