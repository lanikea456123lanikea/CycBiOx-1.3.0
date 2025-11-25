// TODO: [代码功能] API接口 (2200+行) ⭐ 核心文件

package com.cellphenotype.qupath;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellphenotype.qupath.model.CellPhenotype;
import com.cellphenotype.qupath.model.PhenotypeManager;
import com.cellphenotype.qupath.model.ProjectConfig;
import com.cellphenotype.qupath.model.ThresholdConfig;
import com.cellphenotype.qupath.service.CellClassificationService;
import com.cellphenotype.qupath.utils.ColorUtils;
import com.cellphenotype.qupath.utils.MeasurementUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;

// TODO: [类] CellPhenotype核心API类

public class CellPhenotypeAPI {

    // TODO: [字段] 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(CellPhenotypeAPI.class);
    // TODO: [字段] JSON序列化器
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // TODO: [配置] 阈值配置管理

    // TODO: [方法] 创建阈值配置
    public static ThresholdConfig createThresholdConfig(String name) {
        return new ThresholdConfig(name);
    }

    /**
     * Create a new threshold configuration with strategy
     */
    public static ThresholdConfig createThresholdConfig(String name, ThresholdConfig.Strategy strategy) {
        return new ThresholdConfig(name).withStrategy(strategy);
    }

    /**
     * Add channel threshold to configuration
     */
    public static ThresholdConfig addChannelThreshold(ThresholdConfig config, String channel,
                                                     String measurement, double threshold, boolean enabled) {
        ThresholdConfig.ChannelThreshold channelThreshold =
            new ThresholdConfig.ChannelThreshold(measurement, threshold, enabled);
        return config.withChannelThreshold(channel, channelThreshold);
    }

    // TODO: [配置] 细胞表型管理

    /**
     * TODO: [方法] 创建细胞表型
     */
    public static CellPhenotype createPhenotype(String name, int priority) {
        return new CellPhenotype(name, priority);
    }

    /**
     * Add marker state to phenotype
     */
    public static CellPhenotype addMarkerState(CellPhenotype phenotype, String marker, String state) {
        CellPhenotype.MarkerState markerState = CellPhenotype.MarkerState.fromDisplayName(state);
        return phenotype.withMarkerState(marker, markerState);
    }

    /**
     * Create a phenotype manager with phenotypes
     */
    public static PhenotypeManager createPhenotypeManager(List<CellPhenotype> phenotypes) {
        PhenotypeManager manager = new PhenotypeManager();
        for (CellPhenotype phenotype : phenotypes) {
            manager.addPhenotype(phenotype);
        }
        return manager;
    }

    // TODO: [配置] 项目配置管理

    /**
     * TODO: [方法] 创建项目配置
     */
    public static ProjectConfig createProjectConfig(String name, ThresholdConfig thresholdConfig,
                                                   PhenotypeManager phenotypeManager) {
        return new ProjectConfig(name, thresholdConfig, phenotypeManager);
    }

    /**
     * Save project configuration to QuPath project directory
     */
    public static void saveProjectConfiguration(ProjectConfig config, File projectDirectory) throws IOException {
        File configFile = new File(projectDirectory, "cell_phenotype_config.json");
        OBJECT_MAPPER.writeValue(configFile, config);
        logger.info("Project configuration saved to: {}", configFile.getAbsolutePath());
    }

    /**
     * Load project configuration from QuPath project directory
     */
    public static ProjectConfig loadProjectConfiguration(File projectDirectory) throws IOException {
        File configFile = new File(projectDirectory, "cell_phenotype_config.json");
        if (!configFile.exists()) {
            throw new IOException("Configuration file not found: " + configFile.getAbsolutePath());
        }
        ProjectConfig config = OBJECT_MAPPER.readValue(configFile, ProjectConfig.class);
        logger.info("Project configuration loaded from: {}", configFile.getAbsolutePath());
        return config;
    }

    // TODO: [数据] 通道检测与管理

    /**
     * TODO: [方法] 获取可用通道（全量识别，不过滤DAPI）
     */
    public static List<String> getAvailableChannels(ImageData<?> imageData) {
        if (imageData == null || imageData.getServer() == null) {
            return getDefaultChannels();
        }

        // 全量返回所有通道，不进行DAPI过滤
        return imageData.getServer().getMetadata().getChannels().stream()
            .map(ImageChannel::getName)
            .collect(Collectors.toList());
    }

    /**
     * Get default channel names when no image is available
     */
    public static List<String> getDefaultChannels() {
        return Arrays.asList("marker1", "marker2", "marker3", "marker4", "marker5");
    }

    /**
     * Get available measurement types for channels
     */
    public static List<String> getAvailableMeasurementTypes() {
        return Arrays.asList(
            "Nucleus: Mean",
            "Nucleus: Median",
            "Nucleus: Max",
            "Cell: Mean",
            "Cell: Median",
            "Cell: Max",
            "Cytoplasm: Mean",
            "Cytoplasm: Median",
            "Cytoplasm: Max"
        );
    }

    // TODO: [算法] 细胞分类模块

    /**
     * TODO: [方法] 细胞分类
     */
    public static CellClassificationService.ClassificationResult classifyCells(
            ImageData<?> imageData, ProjectConfig config) {
        return CellClassificationService.classifyCells(
            imageData,
            config.getThresholdConfig(),
            config.getPhenotypeManager().getPhenotypes()
        );
    }

    /**
     * Classify cells with separate threshold config and phenotype manager
     */
    public static CellClassificationService.ClassificationResult classifyCells(
            ImageData<?> imageData, ThresholdConfig thresholdConfig, PhenotypeManager phenotypeManager) {
        return CellClassificationService.classifyCells(
            imageData, thresholdConfig, phenotypeManager.getPhenotypes()
        );
    }

    /**
     * Classify cells with performance optimization for large datasets (1M+ cells)
     * Uses the new unified classification service
     */
    public static CellClassificationService.ClassificationResult classifyCellsOptimized(
            ImageData<?> imageData,
            ThresholdConfig thresholdConfig,
            PhenotypeManager phenotypeManager) {

        logger.info("Starting optimized cell classification for large dataset");
        int cellCount = MeasurementUtils.getCellCount(imageData);
        logger.info("Processing {} cells with unified classification service", cellCount);

        return CellClassificationService.classifyCells(
            imageData, thresholdConfig, phenotypeManager.getPhenotypes()
        );
    }

    // TODO: [数据] 结果导出模块

    /**
     * TODO: [方法] 导出CSV结果
     */
    public static void exportResults(CellClassificationService.ClassificationResult result,
                                   File csvFile) throws IOException {
        try (FileWriter writer = new FileWriter(csvFile)) {
            // Write header
            writer.write("Cell_ID,X,Y,Classification,CellType\n");

            // Write data
            for (Map.Entry<qupath.lib.objects.PathObject, String> entry :
                 result.getClassificationResults().entrySet()) {
                qupath.lib.objects.PathObject cell = entry.getKey();
                String classification = entry.getValue();
                String cellType = result.getCellTypeResults().get(cell);

                writer.write(String.format("%s,%.2f,%.2f,%s,%s\n",
                    cell.getID(),
                    cell.getROI().getCentroidX(),
                    cell.getROI().getCentroidY(),
                    classification != null ? classification : "Unclassified",
                    cellType != null ? cellType : "undefined"
                ));
            }
        }
        logger.info("Results exported to: {}", csvFile.getAbsolutePath());
    }

    // TODO: [方法] 工具方法模块

    /**
     * TODO: [方法] 更新显示层级
     */
    public static void updateHierarchy(ImageData<?> imageData) {
        ColorUtils.syncQuPathDisplay(imageData);
    }

    /**
     * Get classification statistics from new result format
     */
    public static Map<String, Integer> getClassificationStatistics(
            CellClassificationService.ClassificationResult result) {
        return result.getStatisticsByCellType();
    }

    /**
     * Validate project configuration
     */
    public static List<String> validateConfiguration(ProjectConfig config) {
        List<String> issues = new ArrayList<>();

        // Check threshold config
        if (config.getThresholdConfig().getChannelThresholds().isEmpty()) {
            issues.add("No channel thresholds configured");
        }

        // Check phenotype manager
        if (config.getPhenotypeManager().isEmpty()) {
            issues.add("No phenotypes defined");
        }

        // Check for phenotype conflicts
        List<CellPhenotype> phenotypes = config.getPhenotypeManager().getPhenotypes();
        Set<String> names = new HashSet<>();
        for (CellPhenotype phenotype : phenotypes) {
            if (names.contains(phenotype.getName())) {
                issues.add("Duplicate phenotype name: " + phenotype.getName());
            }
            names.add(phenotype.getName());
        }

        return issues;
    }

    /**
     * Apply cell classification permanently using the new service
     */
    public static void applyCellClassification(ImageData<?> imageData, ThresholdConfig config,
                                               PhenotypeManager phenotypeManager) {
        CellClassificationService.classifyCells(
            imageData, config, phenotypeManager.getPhenotypes()
        );
    }

    /**
     * Get cell count from image data
     */
    public static int getCellCount(ImageData<?> imageData) {
        return MeasurementUtils.getCellCount(imageData);
    }

    /**
     * Check if measurement name is valid
     */
    public static boolean isValidMeasurementName(ImageData<?> imageData, String measurementName) {
        return MeasurementUtils.isValidMeasurementName(imageData, measurementName);
    }

    /**
     * Find measurement name for channel
     * @deprecated v1.4.0开始弃用，请使用 {@link MeasurementUtils#buildMeasurementName(String, String, com.cellphenotype.qupath.model.SegmentationModel)}
     */
    @Deprecated
    public static String findMeasurementName(ImageData<?> imageData, String channelName) {
        return MeasurementUtils.findMeasurementName(imageData, channelName);
    }
}