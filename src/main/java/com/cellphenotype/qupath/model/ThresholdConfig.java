// TODO: [代码功能] 阈值配置 (200+行)


package com.cellphenotype.qupath.model;

// TODO: [类定义] 阈值配置导入依赖模块
//   TODO: [类定义] Jackson JSON注解 - 序列化配置支持
//   TODO: [类定义] Java集合类 - Map数据结构
//   TODO: [类定义] 工具类 - Objects实用方法
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
     * TODO: [方法] 简化方法
     */

public class ThresholdConfig {
    
    /**
     * TODO: [方法] 简化方法
     */

    public enum Strategy {
        MANUAL("手动"),
        AUTO("自动");

        private final String displayName;

        Strategy(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * TODO: [方法] 简化方法
     */

    public static class ChannelThreshold {
        private final String measurement;
        private final double threshold;
        private final boolean enabled;

        @JsonCreator
        public ChannelThreshold(
                @JsonProperty("measurement") String measurement,
                @JsonProperty("threshold") double threshold,
                @JsonProperty("enabled") boolean enabled) {
            this.measurement = measurement;
            this.threshold = threshold;
            this.enabled = enabled;
        }

        public String getMeasurement() { return measurement; }
        public double getThreshold() { return threshold; }
        public boolean isEnabled() { return enabled; }

        public ChannelThreshold withThreshold(double newThreshold) {
            return new ChannelThreshold(measurement, newThreshold, enabled);
        }

        public ChannelThreshold withEnabled(boolean newEnabled) {
            return new ChannelThreshold(measurement, threshold, newEnabled);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChannelThreshold that = (ChannelThreshold) o;
            return Double.compare(that.threshold, threshold) == 0 &&
                   enabled == that.enabled &&
                   Objects.equals(measurement, that.measurement);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(measurement, threshold, enabled);
        }
    }
    
    private final String configName;
    private final Strategy strategy;
    private final Map<String, ChannelThreshold> channelThresholds;
    private final SegmentationModel segmentationModel; // v1.4.0: 新增分割模型字段

    @JsonCreator
    public ThresholdConfig(
            @JsonProperty("configName") String configName,
            @JsonProperty("strategy") Strategy strategy,
            @JsonProperty("channelThresholds") Map<String, ChannelThreshold> channelThresholds,
            @JsonProperty("segmentationModel") SegmentationModel segmentationModel) {
        this.configName = configName;
        this.strategy = strategy;
        this.channelThresholds = channelThresholds != null ? new HashMap<>(channelThresholds) : new HashMap<>();
        this.segmentationModel = segmentationModel != null ? segmentationModel : SegmentationModel.STARDIST; // 默认StarDist
    }

    // 兼容旧版本的构造器（无segmentationModel）
    public ThresholdConfig(
            String configName,
            Strategy strategy,
            Map<String, ChannelThreshold> channelThresholds) {
        this(configName, strategy, channelThresholds, SegmentationModel.STARDIST);
    }

    public ThresholdConfig(String configName) {
        this(configName, Strategy.MANUAL, new HashMap<>(), SegmentationModel.STARDIST);
    }
    
    public String getConfigName() { return configName; }
    public Strategy getStrategy() { return strategy; }
    public Map<String, ChannelThreshold> getChannelThresholds() { return new HashMap<>(channelThresholds); }
    public SegmentationModel getSegmentationModel() { return segmentationModel; } // v1.4.0

    public ThresholdConfig withConfigName(String newConfigName) {
        return new ThresholdConfig(newConfigName, strategy, channelThresholds, segmentationModel);
    }

    public ThresholdConfig withStrategy(Strategy newStrategy) {
        return new ThresholdConfig(configName, newStrategy, channelThresholds, segmentationModel);
    }

    public ThresholdConfig withSegmentationModel(SegmentationModel newModel) {
        return new ThresholdConfig(configName, strategy, channelThresholds, newModel);
    }

    public ThresholdConfig withChannelThreshold(String channel, ChannelThreshold threshold) {
        Map<String, ChannelThreshold> newThresholds = new HashMap<>(channelThresholds);
        newThresholds.put(channel, threshold);
        return new ThresholdConfig(configName, strategy, newThresholds, segmentationModel);
    }

    public ThresholdConfig removeChannelThreshold(String channel) {
        Map<String, ChannelThreshold> newThresholds = new HashMap<>(channelThresholds);
        newThresholds.remove(channel);
        return new ThresholdConfig(configName, strategy, newThresholds, segmentationModel);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThresholdConfig that = (ThresholdConfig) o;
        return Objects.equals(configName, that.configName) &&
               strategy == that.strategy &&
               Objects.equals(channelThresholds, that.channelThresholds) &&
               segmentationModel == that.segmentationModel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(configName, strategy, channelThresholds, segmentationModel);
    }

    @Override
    public String toString() {
        return "ThresholdConfig{" +
                "configName='" + configName + '\'' +
                ", strategy=" + strategy +
                ", channelThresholds=" + channelThresholds.size() +
                ", segmentationModel=" + segmentationModel +
                '}';
    }
}