//TODO: [代码功能] 细胞表型数据模型 (200+行)

package com.cellphenotype.qupath.model;

// TODO: [导入] 模型类依赖
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TODO: [类定义] 细胞表型数据模型
 * TODO: [数据] 用户定义的细胞类型分类规则
 */
public class CellPhenotype {

    private static final Logger logger = LoggerFactory.getLogger(CellPhenotype.class);

    /**
     * TODO: [方法] 简化方法
     */

    public enum MarkerState {
        POSITIVE("阳性"),
        NEGATIVE("阴性"),
        IGNORE("无关");

        private final String displayName;

        MarkerState(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static MarkerState fromDisplayName(String displayName) {
            for (MarkerState state : values()) {
                if (state.displayName.equals(displayName)) {
                    return state;
                }
            }
            // 兼容旧的"无影响"显示名称
            if ("无影响".equals(displayName)) {
                return IGNORE;
            }
            throw new IllegalArgumentException("Unknown marker state: " + displayName);
        }
    }
    
    // TODO: [类定义] 表型数据字段定义
    //   TODO: [类定义] 表型名称 - 用户自定义的细胞类型名称
    private final String name;
    //   TODO: [类定义] 优先级 - 表型匹配优先级(数字越小优先级越高)
    private final int priority;
    //   TODO: [类定义] 标记状态映射 - 标记名称到状态的映射表
    private final Map<String, MarkerState> markerStates;

    /**
     * TODO: [方法] 简化方法
     */

    @JsonCreator
    public CellPhenotype(
            @JsonProperty("name") String name,
            @JsonProperty("priority") int priority,
            @JsonProperty("markerStates") Map<String, MarkerState> markerStates) {
        this.name = name;
        this.priority = priority;
        this.markerStates = markerStates != null ? new HashMap<>(markerStates) : new HashMap<>();
    }
    
    public CellPhenotype(String name, int priority) {
        this(name, priority, new HashMap<>());
    }
    
    public String getName() { return name; }
    public int getPriority() { return priority; }
    public Map<String, MarkerState> getMarkerStates() { return new HashMap<>(markerStates); }
    
    public MarkerState getMarkerState(String marker) {
        return markerStates.getOrDefault(marker, MarkerState.IGNORE);
    }
    
    public CellPhenotype withName(String newName) {
        return new CellPhenotype(newName, priority, markerStates);
    }
    
    public CellPhenotype withPriority(int newPriority) {
        return new CellPhenotype(name, newPriority, markerStates);
    }
    
    public CellPhenotype withMarkerState(String marker, MarkerState state) {
        Map<String, MarkerState> newStates = new HashMap<>(markerStates);
        newStates.put(marker, state);
        return new CellPhenotype(name, priority, newStates);
    }
    
    public CellPhenotype removeMarkerState(String marker) {
        Map<String, MarkerState> newStates = new HashMap<>(markerStates);
        newStates.remove(marker);
        return new CellPhenotype(name, priority, newStates);
    }
    
    /**
     * Build 18: 增强匹配逻辑，添加调试日志诊断不匹配问题
     */
    public boolean matches(Map<String, Boolean> markerPositiveStates) {
        logger.debug("🔍 [PHENOTYPE-MATCH] 检查表型 '{}' 是否匹配", name);
        logger.debug("   表型定义的markers: {}", markerStates.keySet());
        logger.debug("   细胞的markerStates: {}", markerPositiveStates.keySet());

        for (Map.Entry<String, MarkerState> entry : markerStates.entrySet()) {
            String marker = entry.getKey();
            MarkerState requiredState = entry.getValue();

            if (requiredState == MarkerState.IGNORE) {
                logger.debug("   ⏭️  '{}' = IGNORE (跳过)", marker);
                continue;
            }

            Boolean isPositive = markerPositiveStates.get(marker);
            if (isPositive == null) {
                logger.warn("   ⚠️  '{}' 在markerPositiveStates中不存在! (表型定义的marker未在阈值配置中)", marker);
                logger.warn("   可用的markers: {}", markerPositiveStates.keySet());
                continue; // Build 18: 继续检查其他markers
            }

            boolean matches = (requiredState == MarkerState.POSITIVE) == isPositive;
            if (!matches) {
                logger.debug("   ❌ '{}' 不匹配: 需要{}, 实际{}",
                           marker, requiredState, isPositive ? "阳性" : "阴性");
                return false;
            } else {
                logger.debug("   ✅ '{}' 匹配: {}", marker, requiredState);
            }
        }

        logger.debug("   🎯 表型 '{}' 完全匹配!", name);
        return true;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CellPhenotype that = (CellPhenotype) o;
        return priority == that.priority &&
               Objects.equals(name, that.name) &&
               Objects.equals(markerStates, that.markerStates);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, priority, markerStates);
    }
    
    @Override
    public String toString() {
        return "CellPhenotype{" +
                "name='" + name + '\'' +
                ", priority=" + priority +
                ", markerStates=" + markerStates +
                '}';
    }
}