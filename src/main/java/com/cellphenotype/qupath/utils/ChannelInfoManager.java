package com.cellphenotype.qupath.utils;

import com.cellphenotype.qupath.model.ChannelInfo;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.objects.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 通道信息管理器 - 解决QuPath原生通道命名机制
 * 关键设计：分离用户显示名称和实际measurement查找
 */
public class ChannelInfoManager {

    private static final Logger logger = LoggerFactory.getLogger(ChannelInfoManager.class);

    /**
     * 从ImageData创建ChannelInfo列表
     * 模拟QuPath原生行为：修改显示名称不影响measurement查找
     */
    public static List<ChannelInfo> createChannelInfos(ImageData<?> imageData) {
        List<ChannelInfo> channelInfos = new ArrayList<>();

        if (imageData == null) {
            // 返回默认通道
            return createDefaultChannelInfos();
        }

        try {
            List<ImageChannel> channels = imageData.getServer().getMetadata().getChannels();

            // 全量识别所有通道，不跳过DAPI
            for (int i = 0; i < channels.size(); i++) {
                ImageChannel channel = channels.get(i);
                String originalName = channel.getName();

                // 生成通道信息
                String displayName = originalName != null && !originalName.trim().isEmpty()
                                   ? originalName.trim()
                                   : "Channel " + (i + 1);

                // 查找对应的measurement名称
                String measurementName = findBestMeasurementName(imageData, originalName, i + 1);

                ChannelInfo channelInfo = new ChannelInfo(
                    originalName != null ? originalName : "C" + (i + 1),
                    displayName,
                    measurementName,
                    i
                );

                channelInfos.add(channelInfo);
                logger.info("✓ 通道 {}: {} -> {} (measurement: {})",
                           i + 1, originalName, displayName, measurementName);
            }

        } catch (Exception e) {
            logger.warn("无法从ImageData获取通道信息，使用默认配置: {}", e.getMessage());
            return createDefaultChannelInfos();
        }

        return channelInfos;
    }

    /**
     * 查找最佳的measurement名称
     * 使用多种策略确保找到正确的measurement
     */
    private static String findBestMeasurementName(ImageData<?> imageData, String channelName, int channelPosition) {
        Collection<PathObject> detections = imageData.getHierarchy().getDetectionObjects();
        if (detections.isEmpty()) {
            return null;
        }

        List<String> allMeasurements = detections.iterator().next().getMeasurementList().getMeasurementNames();

        // 策略1: 直接匹配通道名称
        if (channelName != null) {
            for (String measurement : allMeasurements) {
                if (measurement.contains(channelName) && measurement.contains("Mean")) {
                    return measurement;
                }
            }
        }

        // 策略2: 按位置匹配（C1, C2, C3, C4）
        String positionName = "C" + channelPosition;
        for (String measurement : allMeasurements) {
            if (measurement.contains(positionName + ":") && measurement.contains("Mean")) {
                return measurement;
            }
        }

        // 策略3: 按通道索引匹配
        String[] compartments = {"Cell", "Nucleus", "Cytoplasm"};
        for (String compartment : compartments) {
            String candidate = compartment + ": " + positionName + ": Mean";
            if (allMeasurements.contains(candidate)) {
                return candidate;
            }
        }

        logger.warn("未找到通道 '{}' (位置 {}) 对应的measurement", channelName, channelPosition);
        return null;
    }

    /**
     * 创建默认通道信息（无ImageData时使用）
     */
    private static List<ChannelInfo> createDefaultChannelInfos() {
        List<ChannelInfo> defaultChannels = new ArrayList<>();
        String[] defaultNames = {"FITC", "TRITC", "Cy5", "AF647", "PE"};

        for (int i = 0; i < defaultNames.length; i++) {
            ChannelInfo channelInfo = new ChannelInfo(
                "C" + (i + 2), // C2, C3, C4, C5, C6
                defaultNames[i],
                null, // 没有ImageData时无法确定measurement
                i + 1
            );
            defaultChannels.add(channelInfo);
        }

        return defaultChannels;
    }

    /**
     * 更新ChannelInfo的显示名称
     * 关键：保持原始名称和measurement名称不变
     */
    public static ChannelInfo updateDisplayName(ChannelInfo original, String newDisplayName) {
        if (original == null || newDisplayName == null) {
            return original;
        }

        ChannelInfo updated = original.withDisplayName(newDisplayName.trim());
        logger.info("更新通道显示名称: '{}' -> '{}' (保持measurement: '{}')",
                   original.getDisplayName(), newDisplayName, original.getMeasurementName());
        return updated;
    }

    /**
     * 获取用于分类的measurement名称
     * 这是解决问题的关键方法
     */
    public static String getMeasurementNameForClassification(ChannelInfo channelInfo) {
        if (channelInfo == null) {
            return null;
        }

        // 优先使用预解析的measurement名称
        String measurementName = channelInfo.getMeasurementName();
        if (measurementName != null) {
            logger.debug("使用预解析的measurement: {} -> {}",
                        channelInfo.getDisplayName(), measurementName);
            return measurementName;
        }

        // 回退到原始名称
        logger.debug("回退到原始名称: {} -> {}",
                    channelInfo.getDisplayName(), channelInfo.getOriginalName());
        return channelInfo.getOriginalName();
    }

    /**
     * 调试方法：打印所有通道信息
     */
    public static void debugChannelInfos(List<ChannelInfo> channelInfos) {
        logger.info("=== CHANNEL INFO DEBUG ===");
        for (int i = 0; i < channelInfos.size(); i++) {
            ChannelInfo info = channelInfos.get(i);
            logger.info("[{}] {}", i, info.toString());
            logger.info("    Config Key: '{}'", info.getConfigKey());
            logger.info("    Measurement Key: '{}'", info.getMeasurementKey());
            logger.info("    Modified: {}", info.isModified());
        }
        logger.info("=== END CHANNEL INFO DEBUG ===");
    }
}