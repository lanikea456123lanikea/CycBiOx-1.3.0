//TODO: [代码功能] 细胞表型数据模型 (200+行)

package com.cellphenotype.qupath.model;

// TODO: [导入] 模型类依赖
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
                // v1.7.8修复：IGNORE标记不影响匹配，marker可以为+或-
                logger.debug("   ⏭️  '{}' = IGNORE (可为阳性或阴性，不影响匹配)", marker);
                continue;
            }

            Boolean isPositive = markerPositiveStates.get(marker);
            if (isPositive == null) {
                // v1.7.8修复：如果Classification中缺少某个表型定义的非IGNORE标记
                // 则此表型不匹配（不是undefined，而是直接不匹配）
                logger.warn("   ❌ '{}' 在Classification中不存在! (表型要求此标记但Classification中缺失)", marker);
                logger.warn("   可用的markers: {}", markerPositiveStates.keySet());
                logger.warn("   表型 '{}' 不匹配: 缺少必需标记 '{}'", name, marker);
                return false;
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

    /**
     * v1.7.8新增：将表型定义转换为+/-格式字符串的集合，用于与Classification比较
     *
     * IGNORE标记的处理规则：
     * - POSITIVE → 显示为+
     * - NEGATIVE → 显示为-
     * - IGNORE → 累加两种情况（+ 和 -）
     *
     * 例如：
     * - {CD3=POSITIVE, CD31=IGNORE} → {"CD3+_CD31+", "CD3+"}
     * - {CD3=POSITIVE, CD31=POSITIVE, CD8=IGNORE} → {"CD3+_CD31+_CD8+", "CD3+_CD31+"}
     *
     * @return +/-格式的标记字符串集合
     */
    public Set<String> toClassificationStringSet() {
        Set<String> classificationStrings = new HashSet<>();

        // 分离markerStates为三组
        List<String> positiveMarkers = new ArrayList<>();
        List<String> negativeMarkers = new ArrayList<>();
        List<String> ignoreMarkers = new ArrayList<>();

        for (Map.Entry<String, MarkerState> entry : markerStates.entrySet()) {
            String marker = entry.getKey();
            MarkerState state = entry.getValue();

            if (state == MarkerState.POSITIVE) {
                positiveMarkers.add(marker + "+");
            } else if (state == MarkerState.NEGATIVE) {
                negativeMarkers.add(marker + "-");
            } else if (state == MarkerState.IGNORE) {
                ignoreMarkers.add(marker);
            }
        }

        // 计算IGNORE标记的组合数：2^n (n个IGNORE标记)
        int ignoreCount = ignoreMarkers.size();
        int combinationCount = 1 << ignoreCount; // 2^ignoreCount

        // 生成所有可能的组合
        for (int i = 0; i < combinationCount; i++) {
            List<String> markers = new ArrayList<>();

            // 添加必须为阳性的marker
            markers.addAll(positiveMarkers);

            // 添加必须为阴性的marker
            markers.addAll(negativeMarkers);

            // 添加IGNORE标记的组合
            for (int j = 0; j < ignoreCount; j++) {
                // 检查第j个bit是否设置
                boolean includePositive = (i & (1 << j)) != 0;
                String marker = ignoreMarkers.get(j);
                if (includePositive) {
                    markers.add(marker + "+");
                }
                // 如果不包含阳性，则不添加该marker（默认为阴性，不显示）
            }

            // 按字母顺序排序，确保一致性
            markers.sort(String::compareTo);
            String classification = String.join("_", markers);
            classificationStrings.add(classification);
        }

        logger.debug("🔍 [PHENOTYPE-CLASSIFICATION-SET] 表型 '{}' 生成 {} 个Classification组合:", name, classificationStrings.size());
        for (String classification : classificationStrings) {
            logger.debug("   - {}", classification);
        }

        return classificationStrings;
    }

    /**
     * v1.7.8: 保持向后兼容，返回第一个Classification字符串
     * @deprecated 使用 toClassificationStringSet() 替代
     */
    @Deprecated
    public String toClassificationString() {
        Set<String> classificationStrings = toClassificationStringSet();
        return classificationStrings.isEmpty() ? "" : classificationStrings.iterator().next();
    }

    /**
     * v1.7.8新增：直接与Classification字符串比较
     * 支持IGNORE标记的灵活匹配和顺序无关匹配：
     * - 表型定义中IGNORE的marker可以是+或-，不显示时默认为-
     * - Classification中的marker顺序不影响匹配
     *
     * @param classification Classification字符串
     * @return 是否匹配
     */
    public boolean matches(String classification) {
        if (classification == null || classification.trim().isEmpty()) {
            return false;
        }

        // 生成表型定义对应的所有Classification组合
        Set<String> phenotypeClassifications = toClassificationStringSet();

        // 解析输入的Classification为marker集合（顺序无关）
        Set<String> inputMarkers = parseClassificationToSet(classification);

        // 检查是否匹配任何组合
        for (String phenotypePattern : phenotypeClassifications) {
            Set<String> patternMarkers = parseClassificationToSet(phenotypePattern);

            if (inputMarkers.equals(patternMarkers)) {
                logger.debug("🔍 [PHENOTYPE-MATCH] 表型 '{}' 可能的Classification: {}, Classification: {}",
                            name, phenotypeClassifications, classification);
                logger.debug("   ✅ Classification 匹配表型 '{}'", name);
                return true;
            }
        }

        logger.debug("🔍 [PHENOTYPE-MATCH] 表型 '{}' 可能的Classification: {}, Classification: {}",
                    name, phenotypeClassifications, classification);
        logger.debug("   ❌ Classification 不匹配表型 '{}' 的任何组合", name);
        return false;
    }

    /**
     * v1.7.8: 解析Classification字符串为Set（顺序无关）
     * @param classification Classification字符串
     * @return marker的Set集合
     */
    private Set<String> parseClassificationToSet(String classification) {
        Set<String> markers = new HashSet<>();
        String[] parts = classification.split("_");
        for (String part : parts) {
            if (!part.isEmpty()) {
                markers.add(part);
            }
        }
        return markers;
    }

    /**
     * v1.7.8新增：解析Classification字符串为marker states
     *
     * @param classification Classification字符串
     * @return marker名称到阳性/阴性的映射
     */
    private Map<String, Boolean> parseClassification(String classification) {
        Map<String, Boolean> markerStates = new HashMap<>();
        String[] markers = classification.split("_");
        for (String marker : markers) {
            if (marker.isEmpty()) {
                continue;
            }
            if (marker.endsWith("+")) {
                String markerName = marker.substring(0, marker.length() - 1);
                markerStates.put(markerName, true);
            } else if (marker.endsWith("-")) {
                String markerName = marker.substring(0, marker.length() - 1);
                markerStates.put(markerName, false);
            }
        }
        return markerStates;
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