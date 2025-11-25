package com.cellphenotype.qupath.model;

/**
 * 通道信息模型 - 分离显示名称和实际measurement名称
 * 解决用户修改通道名称后功能失效的问题
 */
public class ChannelInfo {

    private final String originalName;      // 原始QuPath通道名称 (如 "C2")
    private final String displayName;      // 用户显示名称 (如 "CD3")
    private final String measurementName;  // 实际的measurement名称 (如 "Cell: C2: Mean")
    private final int channelIndex;        // 通道索引 (0-based)

    public ChannelInfo(String originalName, String displayName, String measurementName, int channelIndex) {
        this.originalName = originalName;
        this.displayName = displayName;
        this.measurementName = measurementName;
        this.channelIndex = channelIndex;
    }

    /**
     * 创建修改显示名称后的新实例
     */
    public ChannelInfo withDisplayName(String newDisplayName) {
        return new ChannelInfo(this.originalName, newDisplayName, this.measurementName, this.channelIndex);
    }

    /**
     * 创建更新measurement名称后的新实例
     */
    public ChannelInfo withMeasurementName(String newMeasurementName) {
        return new ChannelInfo(this.originalName, this.displayName, newMeasurementName, this.channelIndex);
    }

    // Getters
    public String getOriginalName() { return originalName; }
    public String getDisplayName() { return displayName; }
    public String getMeasurementName() { return measurementName; }
    public int getChannelIndex() { return channelIndex; }

    /**
     * 获取用于QuPath配置的键名（使用显示名称）
     */
    public String getConfigKey() {
        return displayName;
    }

    /**
     * 获取用于measurement查找的名称（使用实际measurement）
     */
    public String getMeasurementKey() {
        return measurementName != null ? measurementName : originalName;
    }

    /**
     * 检查是否是用户修改过的通道（显示名称不等于原始名称）
     */
    public boolean isModified() {
        return !originalName.equals(displayName);
    }

    @Override
    public String toString() {
        return String.format("ChannelInfo{original='%s', display='%s', measurement='%s', index=%d}",
                           originalName, displayName, measurementName, channelIndex);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ChannelInfo that = (ChannelInfo) obj;
        return channelIndex == that.channelIndex &&
               originalName.equals(that.originalName) &&
               displayName.equals(that.displayName);
    }

    @Override
    public int hashCode() {
        return originalName.hashCode() * 31 + channelIndex;
    }
}