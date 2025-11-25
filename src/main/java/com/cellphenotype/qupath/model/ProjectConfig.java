// TODO: [代码功能] 项目配置 (200+行)

package com.cellphenotype.qupath.model;

// TODO: [类定义] 项目配置导入依赖模块
//   TODO: [类定义] Jackson JSON注解 - 序列化配置支持
//   TODO: [类定义] 数据模型依赖 - ThresholdConfig/PhenotypeManager
//   TODO: [类定义] 工具类 - Objects实用方法
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
     * TODO: [方法] 简化方法
     */

public class ProjectConfig {

    // TODO: [类定义] 项目配置数据字段
    //   TODO: [类定义] 配置名称 - 项目配置标识符
    //   TODO: [类定义] 阈值配置 - ThresholdConfig组件
    //   TODO: [类定义] 表型管理器 - PhenotypeManager组件
    private final String configName;
    private final ThresholdConfig thresholdConfig;
    private final PhenotypeManager phenotypeManager;
    
    /**
     * TODO: [方法] 简化方法
     */

    @JsonCreator
    public ProjectConfig(
            @JsonProperty("configName") String configName,
            @JsonProperty("thresholdConfig") ThresholdConfig thresholdConfig,
            @JsonProperty("phenotypeManager") PhenotypeManager phenotypeManager) {
        this.configName = configName != null ? configName : "Default Configuration";
        this.thresholdConfig = thresholdConfig != null ? thresholdConfig : new ThresholdConfig(this.configName);
        this.phenotypeManager = phenotypeManager != null ? phenotypeManager : new PhenotypeManager();
    }
    
    /**
     * TODO: [类定义] 简单构造器 - 配置名称初始化
     */
    public ProjectConfig(String configName) {
        this(configName, null, null);
    }
    
    /**
     * TODO: [类定义] 配置访问器方法组 - 不可变对象数据访问
     */
    public String getConfigName() { return configName; }
    public ThresholdConfig getThresholdConfig() { return thresholdConfig; }
    public PhenotypeManager getPhenotypeManager() { return phenotypeManager; }
    
    /**
     * TODO: [方法] 简化方法
     */

    public ProjectConfig withConfigName(String newConfigName) {
        return new ProjectConfig(newConfigName,
                               thresholdConfig.withConfigName(newConfigName),
                               phenotypeManager);
    }
    
    public ProjectConfig withThresholdConfig(ThresholdConfig newThresholdConfig) {
        return new ProjectConfig(configName, newThresholdConfig, phenotypeManager);
    }
    
    public ProjectConfig withPhenotypeManager(PhenotypeManager newPhenotypeManager) {
        return new ProjectConfig(configName, thresholdConfig, newPhenotypeManager);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectConfig that = (ProjectConfig) o;
        return Objects.equals(configName, that.configName) &&
               Objects.equals(thresholdConfig, that.thresholdConfig) &&
               Objects.equals(phenotypeManager, that.phenotypeManager);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(configName, thresholdConfig, phenotypeManager);
    }
    
    @Override
    public String toString() {
        return "ProjectConfig{" +
                "configName='" + configName + '\'' +
                ", thresholdConfig=" + thresholdConfig +
                ", phenotypesCount=" + phenotypeManager.size() +
                '}';
    }
}