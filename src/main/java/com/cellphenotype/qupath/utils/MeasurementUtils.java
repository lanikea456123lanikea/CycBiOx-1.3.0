
// TODO: [代码功能] 测量工具类 (700+行)

package com.cellphenotype.qupath.utils;

// 测量工具导入依赖模块
// QuPath核心依赖 - 测量系统/图像数据/路径对象
// Java集合类 - 数据处理和流式操作
import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellphenotype.qupath.model.SegmentationModel;

import java.util.*;
import java.util.stream.Collectors;

/**
     * TODO: [方法] 简化方法
     */

public class MeasurementUtils {

    private static final Logger logger = LoggerFactory.getLogger(MeasurementUtils.class);

    //  测量常量定义区域
    //  通道前缀优先级映射表（全量识别，按顺序排列）
    //  C1最高优先级 - 第一个通道（可能是DAPI或其他marker）
    //  C2第二优先级 - 第二个通道
    //  C3第三优先级 - 第三个通道
    //  C4第四优先级 - 第四个通道
    private static final Map<String, Integer> CHANNEL_PREFIX_PRIORITY = Map.of(
        "C1", 1,
        "C2", 2,
        "C3", 3,
        "C4", 4,
        "C5", 5
    );

    // ========== v1.4.0: 测量值直接从ImageData提取，不再使用固定格式构建 ==========

    // ========== 以下为旧版动态识别方法（v1.3.0及更早），已弃用 ==========

    //TODO: [方法] 简化方法 - 精准匹配，支持Unicode特殊符号

    /**
     * @deprecated v1.4.0开始弃用，请使用 {@link #buildMeasurementName(String, String, SegmentationModel)}
     */
    @Deprecated
    public static String findMeasurementName(ImageData<?> imageData, String channelName) {
        if (imageData == null || channelName == null || channelName.isEmpty()) {
            return null;
        }

        Collection<PathObject> detections = imageData.getHierarchy().getDetectionObjects();
        if (detections.isEmpty()) {
            return null;
        }

        PathObject firstObject = detections.iterator().next();
        List<String> measurementNames = firstObject.getMeasurementList().getMeasurementNames();

        // 1. 精确匹配（直接匹配）
        String exactMatch = findExactMatch(measurementNames, channelName);
        if (exactMatch != null) {
            logger.debug("✅ [EXACT-MATCH] 找到精确匹配: '{}' -> '{}'", channelName, exactMatch);
            return exactMatch;
        }

        // 2. 可能的名称匹配（基于标准格式）
        List<String> possibleNames = createPossibleMeasurementNames(channelName);
        for (String possibleName : possibleNames) {
            if (measurementNames.contains(possibleName)) {
                logger.debug("✅ [PATTERN-MATCH] 找到格式匹配: '{}' -> '{}'", channelName, possibleName);
                return possibleName;
            }
        }

        // 3. 精准Unicode匹配（删除部分匹配，只做精准匹配）
        return findExactUnicodeMatch(measurementNames, channelName);
    }

    /**
     * 为分类创建测量名称映射
     * @deprecated v1.4.0开始弃用，请使用 {@link #createMeasurementMapping(List, String, SegmentationModel)}
     */
    @Deprecated
    public static Map<String, String> createMeasurementMapping(ImageData<?> imageData, List<String> channelNames) {
        Map<String, String> mapping = new HashMap<>();

        for (String channelName : channelNames) {
            String measurementName = findMeasurementName(imageData, channelName);
            if (measurementName != null) {
                mapping.put(channelName, measurementName);
            }
        }

        return mapping;
    }

    /**
     * 获取所有检测对象的细胞数量
     */
    public static int getCellCount(ImageData<?> imageData) {
        if (imageData == null) {
            return 0;
        }
        return imageData.getHierarchy().getDetectionObjects().size();
    }

    /**
     * 检查测量名称是否有效
     */
    public static boolean isValidMeasurementName(ImageData<?> imageData, String measurementName) {
        if (imageData == null || measurementName == null) {
            return false;
        }

        Collection<PathObject> detections = imageData.getHierarchy().getDetectionObjects();
        if (detections.isEmpty()) {
            return false;
        }

        return detections.iterator().next().getMeasurementList().getMeasurementNames()
                .contains(measurementName);
    }


    // 私有辅助方法

    private static String findExactMatch(List<String> measurementNames, String channelName) {
        return measurementNames.contains(channelName) ? channelName : null;
    }

    private static List<String> createPossibleMeasurementNames(String channelName) {
        List<String> possibleNames = new ArrayList<>();

        // 使用原始通道名称（支持Unicode）
        String baseName = channelName;

        // 标准QuPath测量格式
        // Nucleus: <channel> mean, Cytoplasm: <channel> mean, Cell: <channel> mean
        String[] compartments = {"Nucleus", "Cytoplasm", "Cell"};
        String[] statistics = {"mean", "median", "max", "min", "std"};

        for (String compartment : compartments) {
            for (String stat : statistics) {
                // QuPath标准格式: "Nucleus: CD3 mean"
                possibleNames.add(compartment + ": " + baseName + " " + stat);
                // 大写格式: "Nucleus: CD3 Mean"
                possibleNames.add(compartment + ": " + baseName + " " + capitalize(stat));
            }
        }

        // 添加带通道前缀的格式（C1, C2, C3等）
        for (String prefix : CHANNEL_PREFIX_PRIORITY.keySet()) {
            for (String compartment : compartments) {
                for (String stat : statistics) {
                    possibleNames.add(compartment + ": " + prefix + ": " + baseName + " " + stat);
                    possibleNames.add(compartment + ": " + prefix + " " + stat);
                }
            }
        }

        return possibleNames;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 精准Unicode匹配 - 只做精准匹配，不做部分匹配
     * 支持Unicode特殊字符（如γ、α、β等）
     */
    private static String findExactUnicodeMatch(List<String> measurementNames, String channelName) {
        logger.debug("🔍 [EXACT-UNICODE-MATCH] 精准查找通道: '{}'", channelName);

        // 精准匹配候选列表
        List<String> exactCandidates = new ArrayList<>();

        for (String measurementName : measurementNames) {
            // 方式1: 精准包含检查（区分大小写）
            if (measurementName.contains(": " + channelName + " ")) {
                exactCandidates.add(measurementName);
                logger.debug("   ✅ 精准匹配（区分大小写）: '{}'", measurementName);
                continue;
            }

            // 方式2: 精准包含检查（不区分大小写，但完整匹配）
            // 分割measurement名称为tokens
            String[] tokens = measurementName.split("[:\\s]+");
            for (String token : tokens) {
                if (token.equals(channelName)) {
                    exactCandidates.add(measurementName);
                    logger.debug("   ✅ 精准Token匹配（完全相等）: '{}'", measurementName);
                    break;
                }
            }
        }

        if (exactCandidates.isEmpty()) {
            logger.warn("⚠️ [EXACT-UNICODE-MATCH] 未找到通道 '{}' 的精准匹配measurement", channelName);
            return null;
        }

        // 优先选择包含 "mean" 的测量名称
        String meanCandidate = exactCandidates.stream()
                .filter(name -> name.toLowerCase().contains("mean"))
                .findFirst()
                .orElse(null);

        if (meanCandidate != null) {
            logger.info("✅ [EXACT-UNICODE-MATCH] '{}' -> '{}' (Mean优先)", channelName, meanCandidate);
            return meanCandidate;
        }

        // 按通道前缀优先级排序
        String result = exactCandidates.stream()
                .min((a, b) -> {
                    int priorityA = getChannelPriority(a);
                    int priorityB = getChannelPriority(b);
                    return Integer.compare(priorityA, priorityB);
                })
                .orElse(exactCandidates.get(0));

        logger.info("✅ [EXACT-UNICODE-MATCH] '{}' -> '{}' (优先级匹配)", channelName, result);
        return result;
    }

    private static int getChannelPriority(String measurementName) {
        for (Map.Entry<String, Integer> entry : CHANNEL_PREFIX_PRIORITY.entrySet()) {
            if (measurementName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return Integer.MAX_VALUE; // 最低优先级
    }
}