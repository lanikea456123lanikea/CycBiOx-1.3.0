// TODO: [代码功能] 表型管理器 (300+行)

package com.cellphenotype.qupath.model;

// TODO: [类定义] 表型管理器导入依赖模块
//   TODO: [类定义] Jackson JSON注解 - 序列化支持
//   TODO: [类定义] Java集合类 - 列表和映射管理
//   TODO: [类定义] 流式操作 - 排序和过滤功能
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
     * TODO: [方法] 简化方法
     */

public class PhenotypeManager {
    
    // TODO: [类定义] 管理器数据字段
    //   TODO: [类定义] 表型列表 - 细胞表型定义集合
    private final List<CellPhenotype> phenotypes;

    /**
     * TODO: [方法] 简化方法
     */

    @JsonCreator
    public PhenotypeManager(@JsonProperty("phenotypes") List<CellPhenotype> phenotypes) {
        this.phenotypes = phenotypes != null ? new ArrayList<>(phenotypes) : new ArrayList<>();
    }

    /**
     * TODO: [类定义] 默认构造器 - 空表型列表初始化
     */
    public PhenotypeManager() {
        this(new ArrayList<>());
    }
    
    public List<CellPhenotype> getPhenotypes() {
        return new ArrayList<>(phenotypes);
    }
    
    /**
     * TODO: [方法] 简化方法
     */

    public List<CellPhenotype> getPhenotypesByPriority() {
        return phenotypes.stream()
            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
            .collect(Collectors.toList());
    }
    
    public void addPhenotype(CellPhenotype phenotype) {
        phenotypes.add(phenotype);
    }
    
    public void removePhenotype(CellPhenotype phenotype) {
        phenotypes.remove(phenotype);
    }
    
    public void updatePhenotype(int index, CellPhenotype phenotype) {
        if (index >= 0 && index < phenotypes.size()) {
            phenotypes.set(index, phenotype);
        }
    }
    
    /**
     * Classify a cell based on its marker positive states
     * Returns the highest priority matching phenotype
     */
    public ClassificationResult classifyCell(Map<String, Boolean> markerPositiveStates) {
        for (CellPhenotype phenotype : getPhenotypesByPriority()) {
            if (phenotype.matches(markerPositiveStates)) {
                return new ClassificationResult(phenotype, markerPositiveStates);
            }
        }
        return new ClassificationResult(null, markerPositiveStates);
    }
    
    /**
     * Result of cell classification
     */
    public static class ClassificationResult {
        private final CellPhenotype phenotype;
        private final Map<String, Boolean> markerStates;
        private final String positiveProteins;
        
        public ClassificationResult(CellPhenotype phenotype, Map<String, Boolean> markerStates) {
            this.phenotype = phenotype;
            this.markerStates = new HashMap<>(markerStates);
            this.positiveProteins = generatePositiveProteinsString(markerStates);
        }
        
        private String generatePositiveProteinsString(Map<String, Boolean> markerStates) {
            return markerStates.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));
        }
        
        public CellPhenotype getPhenotype() { return phenotype; }
        public String getPhenotypeName() {
            return phenotype != null ? phenotype.getName() : "undefined";
        }
        public int getPhenotypePriority() { 
            return phenotype != null ? phenotype.getPriority() : 0; 
        }
        public Map<String, Boolean> getMarkerStates() { return new HashMap<>(markerStates); }
        public String getPositiveProteins() { return positiveProteins; }
        
        public boolean isClassified() {
            return phenotype != null;
        }
    }
    
    /**
     * Move phenotype up in priority (increase index)
     */
    public void movePhenotypeUp(int index) {
        if (index > 0 && index < phenotypes.size()) {
            CellPhenotype temp = phenotypes.get(index);
            phenotypes.set(index, phenotypes.get(index - 1));
            phenotypes.set(index - 1, temp);
        }
    }
    
    /**
     * Move phenotype down in priority (decrease index)
     */
    public void movePhenotypeDown(int index) {
        if (index >= 0 && index < phenotypes.size() - 1) {
            CellPhenotype temp = phenotypes.get(index);
            phenotypes.set(index, phenotypes.get(index + 1));
            phenotypes.set(index + 1, temp);
        }
    }
    
    public int size() {
        return phenotypes.size();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return phenotypes.isEmpty();
    }
}