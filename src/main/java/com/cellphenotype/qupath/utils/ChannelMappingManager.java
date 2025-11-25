package com.cellphenotype.qupath.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局通道映射管理器
 * 用于在不同组件间共享通道名称映射信息，解决通道重命名后的识别问题
 */
public class ChannelMappingManager {

    // 使用ConcurrentHashMap确保线程安全
    private static final Map<String, Map<String, String>> PROJECT_MAPPINGS = new ConcurrentHashMap<>();

    /**
     * 设置项目的通道映射
     * @param projectId 项目标识符（可以使用ImageData的hash或路径）
     * @param channelMapping 通道映射表（新名称 -> 原名称）
     */
    public static void setChannelMapping(String projectId, Map<String, String> channelMapping) {
        if (projectId != null && channelMapping != null) {
            PROJECT_MAPPINGS.put(projectId, new HashMap<>(channelMapping));
        }
    }

    /**
     * 获取项目的通道映射
     * @param projectId 项目标识符
     * @return 通道映射表，如果不存在则返回null
     */
    public static Map<String, String> getChannelMapping(String projectId) {
        if (projectId == null) {
            return null;
        }
        Map<String, String> mapping = PROJECT_MAPPINGS.get(projectId);
        return mapping != null ? new HashMap<>(mapping) : null;
    }

    /**
     * 更新特定通道的映射
     * @param projectId 项目标识符
     * @param displayName 显示名称（新名称）
     * @param originalName 原始名称
     */
    public static void updateChannelMapping(String projectId, String displayName, String originalName) {
        if (projectId != null && displayName != null && originalName != null) {
            PROJECT_MAPPINGS.computeIfAbsent(projectId, k -> new HashMap<>()).put(displayName, originalName);
        }
    }

    /**
     * 移除项目的通道映射
     * @param projectId 项目标识符
     */
    public static void removeChannelMapping(String projectId) {
        if (projectId != null) {
            PROJECT_MAPPINGS.remove(projectId);
        }
    }

    /**
     * 清除所有映射
     */
    public static void clearAllMappings() {
        PROJECT_MAPPINGS.clear();
    }

    /**
     * 获取项目ID（基于图像路径或其他唯一标识）
     * @param imageData QuPath图像数据
     * @return 项目唯一标识符
     */
    public static String getProjectId(qupath.lib.images.ImageData<?> imageData) {
        if (imageData == null) {
            return "default";
        }

        try {
            // 尝试使用图像URI作为唯一标识
            if (imageData.getServer() != null && imageData.getServer().getURIs() != null && !imageData.getServer().getURIs().isEmpty()) {
                return imageData.getServer().getURIs().iterator().next().toString();
            }

            // 回退到使用哈希值
            return String.valueOf(imageData.hashCode());
        } catch (Exception e) {
            // 如果获取失败，使用默认标识
            return "default";
        }
    }

    /**
     * 调试方法：打印当前所有映射
     */
    public static void debugPrintMappings() {
        System.out.println("=== Channel Mapping Manager Debug ===");
        PROJECT_MAPPINGS.forEach((projectId, mapping) -> {
            System.out.println("Project: " + projectId);
            mapping.forEach((displayName, originalName) ->
                System.out.println("  " + displayName + " -> " + originalName));
        });
        System.out.println("=== End Debug ===");
    }
}