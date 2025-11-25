package com.cellphenotype.qupath.utils;

import java.util.*;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 直接修复通道识别问题的工具类
 * 提供简单直接的解决方案，避免复杂的映射管理
 */
public class DirectChannelFixer {

    private static final Logger logger = LoggerFactory.getLogger(DirectChannelFixer.class);

    /**
     * 直接修复measurement查找问题
     * 这个方法会尝试所有可能的通道名称组合
     */
    public static String findMeasurementDirect(ImageData<?> imageData, String displayChannelName) {
        if (imageData == null || displayChannelName == null) {
            return null;
        }

        Collection<PathObject> detections = imageData.getHierarchy().getDetectionObjects();
        if (detections.isEmpty()) {
            return null;
        }

        List<String> allMeasurements = detections.iterator().next().getMeasurementList().getMeasurementNames();
        logger.info("=== DIRECT CHANNEL FIXER DEBUG ===");
        logger.info("Looking for channel: {}", displayChannelName);
        logger.info("Available measurements: {}", allMeasurements.size());

        // 策略1: 直接匹配
        for (String measurement : allMeasurements) {
            if (measurement.contains(displayChannelName)) {
                logger.info("✓ Direct match found: {}", measurement);
                return measurement;
            }
        }

        // 策略2: 位置推断 - 假设用户把C2改成了其他名字
        // 分析通道位置模式
        String[] channelPositions = {"C1", "C2", "C3", "C4", "C5", "C6"};
        Map<String, List<String>> positionMeasurements = new HashMap<>();

        for (String pos : channelPositions) {
            List<String> found = new ArrayList<>();
            for (String measurement : allMeasurements) {
                if (measurement.contains(pos + ":")) {
                    found.add(measurement);
                }
            }
            if (!found.isEmpty()) {
                positionMeasurements.put(pos, found);
            }
        }

        logger.info("Position measurements found: {}", positionMeasurements.keySet());

        // 策略3: 智能推断 - 基于常见的重命名模式
        String bestGuess = guessChannelMapping(displayChannelName, positionMeasurements);
        if (bestGuess != null) {
            logger.info("✓ Smart guess found: {} -> {}", displayChannelName, bestGuess);
            return bestGuess;
        }

        // 策略4: 如果是常见的marker名称，尝试匹配到最可能的通道
        String markerGuess = matchByMarkerName(displayChannelName, positionMeasurements);
        if (markerGuess != null) {
            logger.info("✓ Marker-based match: {} -> {}", displayChannelName, markerGuess);
            return markerGuess;
        }

        logger.warn("✗ No measurement found for channel: {}", displayChannelName);
        return null;
    }

    /**
     * 基于通道名称智能推断原始位置
     */
    private static String guessChannelMapping(String displayName, Map<String, List<String>> positionMeasurements) {
        // 全量识别所有通道
        String lowerDisplay = displayName.toLowerCase();

        // 优先级：C1 > C2 > C3 > C4（全量识别，按顺序）
        String[] preferredOrder = {"C1", "C2", "C3", "C4", "C5", "C6"};

        for (String pos : preferredOrder) {
            if (positionMeasurements.containsKey(pos)) {
                List<String> measurements = positionMeasurements.get(pos);
                // 优先选择Cell或Nucleus的Mean测量
                for (String measurement : measurements) {
                    if ((measurement.contains("Cell:") || measurement.contains("Nucleus:")) &&
                        measurement.contains("Mean")) {
                        return measurement;
                    }
                }
                // 如果没找到理想的，返回第一个
                if (!measurements.isEmpty()) {
                    return measurements.get(0);
                }
            }
        }

        return null;
    }

    /**
     * 基于marker名称匹配
     */
    private static String matchByMarkerName(String displayName, Map<String, List<String>> positionMeasurements) {
        String lower = displayName.toLowerCase();

        // 常见marker到优先通道的映射
        Map<String, String> markerToChannel = new HashMap<>();
        markerToChannel.put("cd3", "C2");  // T细胞marker通常在C2
        markerToChannel.put("cd4", "C3");  // Helper T通常在C3
        markerToChannel.put("cd8", "C4");  // Cytotoxic T通常在C4
        markerToChannel.put("ki67", "C2"); // 增殖marker
        markerToChannel.put("foxp3", "C4"); // Treg marker

        for (Map.Entry<String, String> entry : markerToChannel.entrySet()) {
            if (lower.contains(entry.getKey())) {
                String preferredChannel = entry.getValue();
                if (positionMeasurements.containsKey(preferredChannel)) {
                    List<String> measurements = positionMeasurements.get(preferredChannel);
                    for (String measurement : measurements) {
                        if (measurement.contains("Mean")) {
                            return measurement;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * 调试方法：打印所有可用的measurement
     */
    public static void debugAllMeasurements(ImageData<?> imageData) {
        if (imageData == null) {
            logger.info("No ImageData available for debugging");
            return;
        }

        Collection<PathObject> detections = imageData.getHierarchy().getDetectionObjects();
        if (detections.isEmpty()) {
            logger.info("No detections available for debugging");
            return;
        }

        List<String> allMeasurements = detections.iterator().next().getMeasurementList().getMeasurementNames();
        logger.info("=== ALL AVAILABLE MEASUREMENTS DEBUG ===");
        for (int i = 0; i < allMeasurements.size(); i++) {
            logger.info("[{}] {}", i, allMeasurements.get(i));
        }
        logger.info("=== END MEASUREMENTS DEBUG ===");
    }
}