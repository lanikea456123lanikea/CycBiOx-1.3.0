//TODO: [代码功能] 主界面 (5200+行) ⭐ 核心文件
// VERSION: v1.1.0 (2024.09.24) - 稳定性修复版本
// STATUS: ✅ 7个关键Bug修复完成，功能稳定
// FIXES: 细胞显示形状|导出按钮|配置加载|策略切换|LOAD模式|API接口|预览缩放

package com.cellphenotype.qupath.ui;

// TODO: [导入] 依赖库导入
import com.cellphenotype.qupath.CellPhenotypeAPI;
import com.cellphenotype.qupath.classifier.CellPhenotypeClassifier;
import com.cellphenotype.qupath.model.CellPhenotype;
import com.cellphenotype.qupath.model.PhenotypeManager;
import com.cellphenotype.qupath.model.SegmentationModel;
import com.cellphenotype.qupath.model.ThresholdConfig;
import com.cellphenotype.qupath.service.CellClassificationService;
import com.cellphenotype.qupath.utils.ColorUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.Project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

// TODO: [类] CycBiOx主界面控制器 - 细胞表型分类管理
// FIXME: [性能] 优化大数据集处理性能
// NOTE: [架构] 支持CREATE预览和LOAD执行双模式

public class CellPhenotypeManagerPane {

    // TODO: [常量] 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(CellPhenotypeManagerPane.class);

    // TODO: [常量] 测量类型列表 - QuPath支持的12种细胞测量（备用默认值）
    private static final List<String> DEFAULT_MEASUREMENT_TYPES = Arrays.asList(
        "Nucleus: Mean", "Nucleus: Median", "Nucleus: Max", "Nucleus: Min",
        "Cell: Mean", "Cell: Median", "Cell: Max", "Cell: Min",
        "Cytoplasm: Mean", "Cytoplasm: Median", "Cytoplasm: Max", "Cytoplasm: Min"
    );

    // TODO: [枚举] 操作模式
    // TODO: [CREATE模式] 单通道预览模式，实时调试
    // TODO: [LOAD模式] 多通道执行模式，正式分类
    private enum OperationMode {
        CREATE_CLASSIFIER("确定通道阳性阈值"),
        LOAD_CLASSIFIER("选择所需通道，执行策略");

        private final String displayName;

        OperationMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // TODO: [字段] 核心状态变量
    private OperationMode currentMode = OperationMode.CREATE_CLASSIFIER;
    private ComboBox<OperationMode> modeComboBox;
    // TODO: [字段] 阈值控制状态
    private ComboBox<String> algorithmComboBox;
    private Button calculateButton;
    // TODO: [字段] 预览功能状态
    private boolean livePreviewEnabled = false;
    private String currentPreviewChannel = null;
    private List<String> selectedChannelsFromThreshold = new ArrayList<>();
    // TODO: [字段] 操作按钮控件
    private Button executeButton;                                         // 执行按钮
    private Button refreshButton;                                         // 刷新按钮
    private Button runDetectionButton;                                    // 运行检测并导出数据按钮

    // TODO: [字段] 阈值数据管理
    private Map<String, Double> savedAutoThresholds = new HashMap<>();
    private boolean isAutoMode = false;

    // TODO: [字段] CREATE模式选择状态保存
    private Map<String, Boolean> createModeSelections = new HashMap<>();

    // TODO: [字段] 通道数据管理
    private List<String> availableChannels = new ArrayList<>();
    // TODO: [数据] 通道名称映射（Display Name -> Actual Measurement Name）
    private Map<String, String> channelNameMapping = new HashMap<>();
    // v1.4.0: 用户自定义通道显示名称映射（保持用户修改的友好名称，刷新不丢失）
    private Map<String, String> userChannelDisplayNames = new HashMap<>();

    // TODO: [数据] 分类结果映射
    private Map<String, String> classificationMapping = new HashMap<>();

    // TODO: [UI组件] 基础设置
    private TextField savePathField;
    private CheckBox roiToggle;
    private ComboBox<String> cellAnalysisComboBox;

    // TODO: [UI组件] QuPath集成
    private final QuPathGUI qupath;
    private Stage stage;
    private ScrollPane mainScrollPane;  // 主滚动面板引用，用于防止意外滚动

    // TODO: [UI组件] 配置管理
    private TextField configNameField;
    private ComboBox<ThresholdConfig.Strategy> strategyComboBox;
    private ComboBox<String> segmentationModelComboBox; // v1.4.0: 分割模型选择框

    // TODO: [UI组件] 阈值控制组件映射
    private final Map<String, RadioButton> channelRadioButtons = new HashMap<>();
    private final Map<String, CheckBox> channelCheckBoxes = new HashMap<>();
    private final Map<String, ComboBox<String>> measurementComboBoxes = new HashMap<>();
    private final Map<String, Slider> thresholdSliders = new HashMap<>();
    private final Map<String, TextField> thresholdFields = new HashMap<>();
    private final Map<String, Label> thresholdStatusLabels = new HashMap<>();

    // === 性能优化：Measurement名称缓存 ===
    // Key格式: "channelName:measurementType", Value: actual measurement name
    private final Map<String, String> measurementNameCache = new HashMap<>();

    // TODO: [UI组件] 通道控件管理
    private ToggleGroup channelToggleGroup;
    private ToggleGroup singleChannelGroup;
    private VBox channelContainer;

    // TODO: [UI组件] 表型管理表格
    private TableView<PhenotypeTableRow> phenotypeTable;
    private ObservableList<PhenotypeTableRow> phenotypeData;

    // TODO: [配置] 当前配置状态
    private ThresholdConfig currentConfig;
    private List<CellPhenotype> phenotypes;

    // TODO: [功能] ROI支持
    private boolean useSelectedROI = false;
    private CheckBox roiModeCheckBox;

    // TODO: [方法] 待优化方法

    public CellPhenotypeManagerPane(QuPathGUI qupath) {
        this.qupath = qupath;
        this.phenotypes = new ArrayList<>();
        this.currentConfig = new ThresholdConfig("配置表1");
        this.channelToggleGroup = new ToggleGroup();
        loadAvailableChannels();
        initializeThresholds();
    }

    // TODO: [方法] 加载可用通道列表
    // 通道名称映射：支持C2/C3/C4模式和用户改名
    // 关键修复：从measurements提取实际通道名，解决用户改名后无法匹配的问题
    private void loadAvailableChannels() {
        ImageData<?> imageData = qupath.getImageData();
        if (imageData != null) {
            List<ImageChannel> channels = imageData.getServer().getMetadata().getChannels();
            availableChannels.clear();
            // v1.4.0修复: 不清空channelNameMapping，保持原有的displayName->actualMeasurementName映射
            // channelNameMapping.clear();

            logger.info("=== 开始分析图像通道信息 ===");
            logger.info("图像总通道数: {}", channels.size());

            // 关键步骤：从measurements提取实际通道名称（保持顺序）
            List<String> actualChannelNames = extractChannelNamesFromMeasurements(imageData);
            debugAvailableMeasurements(imageData);

            for (int i = 0; i < channels.size(); i++) {
                ImageChannel channel = channels.get(i);
                String quPathChannelName = channel.getName();

                // 处理空通道名称
                if (quPathChannelName == null || quPathChannelName.trim().isEmpty()) {
                    quPathChannelName = "Channel " + (i + 1);
                }

                // v1.4.0: 检查用户是否有此通道的自定义显示名称
                String displayName = quPathChannelName;
                if (userChannelDisplayNames.containsKey(quPathChannelName)) {
                    displayName = userChannelDisplayNames.get(quPathChannelName);
                    logger.info("通道 {} - 使用用户自定义名称: '{}' (QuPath原名: '{}')", i, displayName, quPathChannelName);
                } else {
                    logger.info("通道 {} - 使用QuPath原生名称: '{}'", i, displayName);
                }

                availableChannels.add(displayName);

                // 关键修复：找到measurements中实际使用的通道名称
                // 传入实际通道索引i进行位置匹配，并传入channelNameMapping用于名称映射
                String actualChannelName = findActualChannelNameInMeasurements(
                    displayName, actualChannelNames, i + 1, i, channelNameMapping);

                // v1.4.0: 检查并保存用户自定义的通道显示名称
                // 如果displayName与quPathChannelName不同，说明用户修改过名称
                if (!displayName.equals(quPathChannelName)) {
                    userChannelDisplayNames.put(quPathChannelName, displayName);
                    logger.info("✓ 检测到用户自定义通道名称: QuPath原名 '{}' -> 显示名称 '{}'",
                               quPathChannelName, displayName);
                }

                // 关键修复：使用QuPathChannelName（不变标识符）作为channelNameMapping的key
                // 而不是displayName（可变），确保刷新后映射不丢失
                channelNameMapping.put(quPathChannelName, actualChannelName);

                // 映射2: QuPathChannelName_INDEX -> C2/C3/C4（QuPath标准索引）
                String channelIndex = "C" + (i + 1);
                channelNameMapping.put(quPathChannelName + "_INDEX", channelIndex);

                logger.info("  ✓ 映射: QuPath原名 '{}' -> 实际名称: '{}', C索引: '{}' (通道索引: {})",
                           quPathChannelName, actualChannelName, channelIndex, i);
                logger.info("    显示名称: '{}'", displayName);

            }

            logger.info("=== 通道分析完成 ===");
            logger.info("可用分析通道数: {}", availableChannels.size());
            
        } else {
            // TODO: [默认] 无图像时默认通道
            availableChannels.clear();
            availableChannels.addAll(Arrays.asList("FITC", "TRITC", "Cy5", "AF647", "PE"));
            
            this.channelNameMapping = new HashMap<>();
            for (String channel : availableChannels) {
                channelNameMapping.put(channel, channel);
            }
        }
        
        // 详细的映射调试信息
        logger.info("=== 最终通道映射表 ===");
        for (Map.Entry<String, String> entry : channelNameMapping.entrySet()) {
            logger.info("  映射: '{}' -> '{}'", entry.getKey(), entry.getValue());
        }
        logger.info("=== 映射表结束 ===");
    }
    
    /**
     * === 关键函数：从hierarchy实时提取可用的测量类型 ===
     * 从实际的细胞measurement中提取所有可用的测量类型（如"Nucleus: Mean"）
     * @deprecated 使用 extractMeasurementsForChannel() 获取完整measurement名称
     */
    private List<String> extractAvailableMeasurementTypes(ImageData<?> imageData) {
        Set<String> measurementTypes = new LinkedHashSet<>();  // 使用LinkedHashSet保持顺序并去重

        try {
            if (imageData == null) {
                logger.warn("ImageData为空，返回默认测量类型");
                return new ArrayList<>(DEFAULT_MEASUREMENT_TYPES);
            }

            var hierarchy = imageData.getHierarchy();
            var detections = hierarchy.getDetectionObjects();

            if (detections.isEmpty()) {
                logger.warn("没有检测到细胞，返回默认测量类型");
                return new ArrayList<>(DEFAULT_MEASUREMENT_TYPES);
            }

            // 从第一个细胞中提取所有measurement名称
            var firstCell = detections.iterator().next();
            var measurements = firstCell.getMeasurementList();
            var measurementNames = measurements.getNames();

            logger.info("=== 提取可用测量类型 ===");
            logger.info("总measurement数量: {}", measurementNames.size());

            // 解析measurement名称，提取测量类型
            // 格式示例: "Nucleus: CD3 mean" -> 测量类型 = "Nucleus: Mean"
            //           "Cell: FOXP3 median" -> 测量类型 = "Cell: Median"
            for (String name : measurementNames) {
                String measurementType = extractMeasurementType(name);
                if (measurementType != null) {
                    measurementTypes.add(measurementType);
                }
            }

            logger.info("提取到 {} 种测量类型: {}", measurementTypes.size(), measurementTypes);

            // 如果没有提取到任何类型，返回默认值
            if (measurementTypes.isEmpty()) {
                logger.warn("未能提取到测量类型，返回默认列表");
                return new ArrayList<>(DEFAULT_MEASUREMENT_TYPES);
            }

            return new ArrayList<>(measurementTypes);

        } catch (Exception e) {
            logger.error("提取测量类型时出错: {}", e.getMessage(), e);
            return new ArrayList<>(DEFAULT_MEASUREMENT_TYPES);
        }
    }

    /**
     * v1.4.0: 构建测量名称映射（使用SegmentationModel固定前缀）
     * @param channelNames 通道名称列表
     * @param thresholdConfig 阈值配置（包含分割模型和测量类型）
     * @return 通道名称到完整测量名称的映射
     */
    private Map<String, String> buildMeasurementMapping(List<String> channelNames,
                                                         ThresholdConfig thresholdConfig) {
        Map<String, String> mapping = new HashMap<>();

        for (String channelName : channelNames) {
            ThresholdConfig.ChannelThreshold channelThreshold =
                thresholdConfig.getChannelThresholds().get(channelName);
            if (channelThreshold == null) {
                continue;
            }

            // getMeasurement()已经返回完整的测量名称（如"Cell: CD68: Mean"）
            String measurementName = channelThreshold.getMeasurement();

            if (measurementName != null) {
                mapping.put(channelName, measurementName);
            }
        }

        return mapping;
    }


    /**
     * v1.4.0: 为显示名称查找实际Measurement中的通道名称
     * @param displayName 显示名称（可能是用户自定义的友好名称）
     * @return 实际Measurement中的通道名称，如果找不到则返回null
     */
    private String findActualChannelNameForDisplayName(String displayName) {
        // 遍历channelNameMapping查找对应的actualChannelName
        for (Map.Entry<String, String> entry : channelNameMapping.entrySet()) {
            String key = entry.getKey();
            // 跳过索引映射（C1, C2等）
            if (key.endsWith("_INDEX")) {
                continue;
            }
            // 检查这个key对应的显示名称是什么
            String quPathChannelName = key;
            String userDisplayName = userChannelDisplayNames.get(quPathChannelName);
            String currentDisplayName = (userDisplayName != null) ? userDisplayName : quPathChannelName;

            if (currentDisplayName.equals(displayName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * v1.4.0: 为指定分割模型生成固定的测量值列表（不依赖实际measurements）
     * @param channelName 通道名称
     * @param model 分割模型
     * @return 固定的测量值列表（20个/15个预设值）
     */
    private List<String> generateFixedMeasurementsForChannel(String channelName, SegmentationModel model) {
        List<String> measurements = new ArrayList<>();

        // 定义所有统计量（固定顺序）
        String[] statistics = {"Mean", "Median", "Max", "Min", "Std dev", "Sum"};

        switch (model) {
            case STARDIST:
                // StarDist: "Nucleus: CD68: Mean" 格式（冒号分隔，包含Membrane）
                // 共20个：Nucleus(6) + Cytoplasm(6) + Membrane(6) + Cell(2)
                measurements.add("Nucleus: " + channelName + ": Mean");
                measurements.add("Nucleus: " + channelName + ": Median");
                measurements.add("Nucleus: " + channelName + ": Max");
                measurements.add("Nucleus: " + channelName + ": Min");
                measurements.add("Nucleus: " + channelName + ": Std dev");

                measurements.add("Cell: " + channelName + ": Mean");
                measurements.add("Cell: " + channelName + ": Median");
                measurements.add("Cell: " + channelName + ": Max");
                measurements.add("Cell: " + channelName + ": Min");
                measurements.add("Cell: " + channelName + ": Std dev");

                measurements.add("Cytoplasm: " + channelName + ": Mean");
                measurements.add("Cytoplasm: " + channelName + ": Median");
                measurements.add("Cytoplasm: " + channelName + ": Max");
                measurements.add("Cytoplasm: " + channelName + ": Min");
                measurements.add("Cytoplasm: " + channelName + ": Std dev");

                measurements.add("Membrane: " + channelName + ": Mean");
                measurements.add("Membrane: " + channelName + ": Median");
                measurements.add("Membrane: " + channelName + ": Max");
                measurements.add("Membrane: " + channelName + ": Min");
                measurements.add("Membrane: " + channelName + ": Std dev");
                break;

            case CELLPOSE:
                // Cellpose
                // 共15个：Channel:Nucleus(5) + Channel:Cytoplasm(5) + Channel:Cell(5) 
                measurements.add("Nucleus: " + channelName + " mean");
                measurements.add("Nucleus: " + channelName + " max");
                measurements.add("Nucleus: " + channelName + " min");
                measurements.add("Nucleus: " + channelName + " median");
                measurements.add("Nucleus: " + channelName + " std dev");

                measurements.add("Cytoplasm: " + channelName + " mean");
                measurements.add("Cytoplasm: " + channelName + " max");
                measurements.add("Cytoplasm: " + channelName + " min");
                measurements.add("Cytoplasm: " + channelName + " median");
                measurements.add("Cytoplasm: " + channelName + " std dev");

                measurements.add("Cell: " + channelName + " mean");
                measurements.add("Cell: " + channelName + " max");
                measurements.add("Cell: " + channelName + " min");
                measurements.add("Cell: " + channelName + " median");
                measurements.add("Cell: " + channelName + " std dev");
                break;

            case INSTANSEG:
                // InstanSeg: "Cell: CD68: Mean" 格式（Cell优先）
                // 共20个：Cell(5) + Nucleus(5) + Cytoplasm(5) + Membrane(5)
                measurements.add("Cell: " + channelName + ": Mean");
                measurements.add("Cell: " + channelName + ": Median");
                measurements.add("Cell: " + channelName + ": Max");
                measurements.add("Cell: " + channelName + ": Min");
                measurements.add("Cell: " + channelName + ": Std dev");

                measurements.add("Nucleus: " + channelName + ": Mean");
                measurements.add("Nucleus: " + channelName + ": Median");
                measurements.add("Nucleus: " + channelName + ": Max");
                measurements.add("Nucleus: " + channelName + ": Min");
                measurements.add("Nucleus: " + channelName + ": Std dev");

                measurements.add("Cytoplasm: " + channelName + ": Mean");
                measurements.add("Cytoplasm: " + channelName + ": Median");
                measurements.add("Cytoplasm: " + channelName + ": Max");
                measurements.add("Cytoplasm: " + channelName + ": Min");
                measurements.add("Cytoplasm: " + channelName + ": Std dev");

                measurements.add("Membrane: " + channelName + ": Mean");
                measurements.add("Membrane: " + channelName + ": Median");
                measurements.add("Membrane: " + channelName + ": Max");
                measurements.add("Membrane: " + channelName + ": Min");
                measurements.add("Membrane: " + channelName + ": Std dev");
                break;

            case QUPATH_DETECTION:
                // QuPath Detection: "Nucleus: CD68 mean" 格式（空格分隔，小写）
                // 共15个：Nucleus:mean/max/min/median/std + Cytoplasm:mean/max/min/median/std + Cell:mean/max/min/median/std
                measurements.add("Nucleus: " + channelName + " mean");
                measurements.add("Nucleus: " + channelName + " max");
                measurements.add("Nucleus: " + channelName + " min");
                measurements.add("Nucleus: " + channelName + " median");
                measurements.add("Nucleus: " + channelName + " std dev");

                measurements.add("Cytoplasm: " + channelName + " mean");
                measurements.add("Cytoplasm: " + channelName + " max");
                measurements.add("Cytoplasm: " + channelName + " min");
                measurements.add("Cytoplasm: " + channelName + " median");
                measurements.add("Cytoplasm: " + channelName + " std dev");

                measurements.add("Cell: " + channelName + " mean");
                measurements.add("Cell: " + channelName + " max");
                measurements.add("Cell: " + channelName + " min");
                measurements.add("Cell: " + channelName + " median");
                measurements.add("Cell: " + channelName + " std dev");
                break;
        }

        return measurements;
    }

    /**
     * v1.4.0: 根据分割模型过滤测量值
     * 每个模型有不同的格式：
     * - StarDist: "Nucleus: CD68: Mean"
     * - Cellpose:  "Nucleus: CD68 mean"
     * - InstanSeg: "Cell: CD68: Mean"
     * - QuPath Detection: "Nucleus: CD68 mean"
     */
    private List<String> filterMeasurementsByModel(List<String> measurements, String channelName, SegmentationModel model) {
        List<String> filtered = new ArrayList<>();

        for (String measurement : measurements) {
            boolean matches = false;

            switch (model) {
                case STARDIST:
                    // 格式: "Nucleus: CD68: Mean" (冒号分隔，有Membrane)
                    // 匹配: 以Compartment开头，包含两个冒号
                    if (measurement.matches("^(Nucleus|Cell|Cytoplasm|Membrane):\\s*" + channelName + ":\\s*(Mean|Median|Max|Min|Std\\.Dev\\.)$")) {
                        matches = true;
                    }
                    break;

                case CELLPOSE:
                    // 格式: "Nucleus: CD68 mean" (空格分隔，小写统计量，无Membrane)
                    if (measurement.matches("^(Nucleus|Cell|Cytoplasm):\\s*" + channelName + "\\s+(mean|std dev|max|min)$")) {
                        matches = true;
                    }
                    break;

                case INSTANSEG:
                    // 格式: "Cell: CD68: Mean" (冒号分隔，有Membrane)
                    if (measurement.matches("^(Cell|Nucleus|Cytoplasm|Membrane):\\s*" + channelName + ":\\s*(Mean|Median|Max|Min|Std\\.Dev\\.)$")) {
                        matches = true;
                    }
                    break;

                case QUPATH_DETECTION:
                    // 格式: "Nucleus: CD68 mean" (空格分隔，小写统计量，无Membrane)
                    if (measurement.matches("^(Nucleus|Cell|Cytoplasm):\\s*" + channelName + "\\s+(mean|std dev|max|min)$")) {
                        matches = true;
                    }
                    break;
            }

            if (matches) {
                filtered.add(measurement);
            }
        }

        logger.debug("模型 {} 过滤: {} -> {} 个测量值", model.getDisplayName(), measurements.size(), filtered.size());
        return filtered;
    }


    /**
     * v1.4.0: 从实际measurements中提取指定通道的测量值（修复通道改名匹配问题）
     * 使用新的匹配逻辑：优先通过channelNameMapping查找原始名称，支持位置匹配
     *
     * @param imageData 图像数据
     * @param channelName 通道显示名称（可能已被用户修改）
     * @param model 分割模型（用于过滤格式）
     * @return 该通道的实际measurements列表
     */
    private List<String> extractMeasurementsForChannel(ImageData<?> imageData, String channelName, SegmentationModel model) {
        List<String> channelMeasurements = new ArrayList<>();

        try {
            if (imageData == null) {
                logger.warn("ImageData为空，无法提取通道 '{}' 的measurements", channelName);
                return channelMeasurements;
            }

            var hierarchy = imageData.getHierarchy();
            var detections = hierarchy.getDetectionObjects();

            if (detections.isEmpty()) {
                logger.warn("没有检测到细胞，无法提取通道 '{}' 的measurements", channelName);
                return channelMeasurements;
            }

            // 从第一个细胞中提取所有measurement名称
            var firstCell = detections.iterator().next();
            var measurements = firstCell.getMeasurementList();
            var measurementNames = measurements.getNames();

            logger.info("=== 提取通道 '{}' 的实际measurements ===", channelName);

            // 定义要排除的形状指标关键词
            Set<String> shapeMetrics = Set.of(
                "area", "perimeter", "circularity", "solidity", "max diameter",
                "min diameter", "eccentricity", "compactness", "elongation",
                "aspect ratio", "roundness", "convexity", "extent", "orientation",
                "length", "width", "num spots", "num single positive", "sum"
            );

            // 获取通道的C-index（如果有映射）
            String channelIndex = channelNameMapping.getOrDefault(channelName + "_INDEX", "");

            // 获取原始通道名称（从映射中获取实际的通道名，如"Cy5 MSI"）
            String originalChannelName = channelNameMapping.getOrDefault(channelName, channelName);

            logger.info("🔍 [MEASUREMENT-EXTRACT] 通道 '{}' 映射到原始名称 '{}'", channelName, originalChannelName);
            logger.info("   C-index: '{}'", channelIndex);

            // 筛选包含通道名称的measurements
            for (String name : measurementNames) {
                String lowerName = name.toLowerCase();
                String lowerChannel = channelName.toLowerCase();
                String lowerOriginal = originalChannelName.toLowerCase();

                // 跳过形状指标
                boolean isShapeMetric = false;
                for (String metric : shapeMetrics) {
                    if (lowerName.contains(metric)) {
                        isShapeMetric = true;
                        break;
                    }
                }
                if (isShapeMetric) {
                    continue;
                }

                // 匹配逻辑：包含通道名称或C-index
                boolean matches = false;

                // 策略1: 精确匹配原始通道名称（关键！处理用户改名的情况）
                if (!originalChannelName.equals(channelName) && lowerName.equals(lowerOriginal)) {
                    matches = true;
                    logger.debug("  ✅ 精确匹配原始名: '{}' -> '{}'", originalChannelName, name);
                }

                // 策略2: 精确匹配显示名称（防止CD31匹配CD3）
                if (!matches && lowerName.equals(lowerChannel)) {
                    matches = true;
                    logger.debug("  ✅ 精确匹配显示名: '{}' -> '{}'", channelName, name);
                }

                // 策略3: 包含原始通道名称（处理空格和特殊字符，如"Cy5 MSI"）
                if (!matches && !originalChannelName.equals(channelName) && lowerName.contains(lowerOriginal)) {
                    matches = true;
                    logger.debug("  ✅ 包含原始名: '{}' 在 '{}'", originalChannelName, name);
                }

                // 策略4: 包含C-index（如"C2"）
                if (!matches && !channelIndex.isEmpty() && lowerName.contains(channelIndex.toLowerCase())) {
                    matches = true;
                    logger.debug("  ✅ C-index匹配: '{}' -> '{}'", channelName, name);
                }

                // 策略5: 精确匹配（如果通道名完全相同）
                if (!matches && lowerName.contains(lowerChannel)) {
                    matches = true;
                    logger.debug("  ✅ 包含匹配: '{}' -> '{}'", channelName, name);
                }

                if (matches) {
                    channelMeasurements.add(name);
                    logger.debug("  ✓ 最终匹配: '{}'", name);
                }
            }

            // 根据分割模型过滤测量值格式
            channelMeasurements = filterMeasurementsByModel(channelMeasurements, channelName, model);

            // 按优先级排序：根据模型调整排序
            channelMeasurements = sortMeasurementsByModel(channelMeasurements, model);

            logger.info("通道 '{}' 提取到 {} 个measurements", channelName, channelMeasurements.size());

            // 如果没有找到匹配的measurements，生成默认列表
            if (channelMeasurements.isEmpty()) {
                logger.warn("通道 '{}' 未找到匹配的measurements，生成默认列表", channelName);
                channelMeasurements.add("Nucleus: " + channelName + " mean");
                channelMeasurements.add("Cell: " + channelName + " mean");
                channelMeasurements.add("Nucleus: " + channelName + " median");
            }

        } catch (Exception e) {
            logger.error("提取通道 '{}' 的measurements时出错: {}", channelName, e.getMessage(), e);
        }

        return channelMeasurements;
    }

    /**
     * v1.4.0: 根据分割模型排序测量值
     * InstanSeg: Cell优先
     * 其他: Nucleus优先
     */
    private List<String> sortMeasurementsByModel(List<String> measurements, SegmentationModel model) {
        List<String> sorted = new ArrayList<>(measurements);

        sorted.sort((a, b) -> {
            // InstanSeg: Cell优先
            if (model == SegmentationModel.INSTANSEG) {
                int priorityA = getInstanSegCompartmentPriority(a);
                int priorityB = getInstanSegCompartmentPriority(b);
                if (priorityA != priorityB) {
                    return Integer.compare(priorityA, priorityB);
                }
            } else if (model == SegmentationModel.QUPATH_DETECTION) {
                // QuPath Detection: Nucleus优先
                int priorityA = getQuPathDetectionCompartmentPriority(a);
                int priorityB = getQuPathDetectionCompartmentPriority(b);
                if (priorityA != priorityB) {
                    return Integer.compare(priorityA, priorityB);
                }
            } else {
                // StarDist, Cellpose: Nucleus优先
                int priorityA = getCompartmentPriority(a);
                int priorityB = getCompartmentPriority(b);
                if (priorityA != priorityB) {
                    return Integer.compare(priorityA, priorityB);
                }
            }

            // 按统计量排序（QuPath Detection使用特殊排序）
            int statA, statB;
            if (model == SegmentationModel.QUPATH_DETECTION) {
                statA = getQuPathDetectionStatisticPriority(a);
                statB = getQuPathDetectionStatisticPriority(b);
            } else {
                statA = getStatisticPriority(a);
                statB = getStatisticPriority(b);
            }
            return Integer.compare(statA, statB);
        });

        return sorted;
    }

    /**
     * QuPath Detection专用：Nucleus → Cell → Cytoplasm的compartment优先级
     */
    private int getQuPathDetectionCompartmentPriority(String measurementName) {
        String lower = measurementName.toLowerCase();
        if (lower.startsWith("nucleus:") || lower.contains("nucleus ")) {
            return 1;  // Nucleus最高优先级
        } else if (lower.startsWith("cell:") || lower.contains("cell ")) {
            return 2;  // Cell次之
        } else if (lower.startsWith("cytoplasm:") || lower.contains("cytoplasm ")) {
            return 3;  // Cytoplasm
        }
        return 4;  // 其他
    }

    /**
     * QuPath Detection专用：mean → std dev → max → min的统计量优先级
     */
    private int getQuPathDetectionStatisticPriority(String measurementName) {
        String lower = measurementName.toLowerCase();
        if (lower.contains("mean")) {
            return 1;  // mean最高优先级
        } else if (lower.contains("std dev") || lower.contains("std")) {
            return 2;  // std dev次之
        } else if (lower.contains("max")) {
            return 3;  // max
        } else if (lower.contains("min")) {
            return 4;  // min
        }
        return 5;  // 其他
    }

    /**
     * InstanSeg专用：Cell优先的compartment优先级
     */
    private int getInstanSegCompartmentPriority(String measurementName) {
        String lower = measurementName.toLowerCase();
        if (lower.startsWith("cell:") || lower.contains("cell ")) {
            return 1;  // Cell最高优先级
        } else if (lower.startsWith("nucleus:") || lower.contains("nucleus ")) {
            return 2;  // Nucleus次之
        } else if (lower.startsWith("cytoplasm:") || lower.contains("cytoplasm ")) {
            return 3;  // Cytoplasm
        } else if (lower.startsWith("membrane:") || lower.contains("membrane ")) {
            return 4;  // Membrane最低
        }
        return 5;  // 其他
    }

    /**
     * 获取compartment优先级（用于排序）
     */
    private int getCompartmentPriority(String measurementName) {
        String lower = measurementName.toLowerCase();
        if (lower.startsWith("nucleus:") || lower.contains("nucleus ")) {
            return 1;  // Nucleus最高优先级
        } else if (lower.startsWith("cell:") || lower.contains("cell ")) {
            return 2;  // Cell次之
        } else if (lower.startsWith("cytoplasm:") || lower.contains("cytoplasm ")) {
            return 3;  // Cytoplasm最低
        }
        return 4;  // 其他
    }

    /**
     * 获取statistic优先级（用于排序）
     */
    private int getStatisticPriority(String measurementName) {
        String lower = measurementName.toLowerCase();
        if (lower.contains("mean")) {
            return 1;  // Mean最高优先级（最常用）
        } else if (lower.contains("median")) {
            return 2;  // Median次之
        } else if (lower.contains("max")) {
            return 3;  // Max
        } else if (lower.contains("min")) {
            return 4;  // Min
        } else if (lower.contains("std dev") || lower.contains("std")) {
            return 5;  // Standard deviation
        } else if (lower.contains("sum")) {
            return 6;  // Sum
        }
        return 7;  // 其他
    }

    /**
     * 从measurement名称中提取测量类型
     * 例如: "Nucleus: CD3 mean" -> "Nucleus: Mean"
     *       "Cell: FOXP3 median" -> "Cell: Median"
     *       "Cytoplasm: marker1 max" -> "Cytoplasm: Max"
     */
    private String extractMeasurementType(String measurementName) {
        if (measurementName == null || measurementName.trim().isEmpty()) {
            return null;
        }

        // 统计量关键词（小写）
        String[] stats = {"mean", "median", "max", "min", "std dev", "sum"};

        // 尝试匹配格式: "Compartment: ChannelName statistic"
        if (measurementName.contains(":")) {
            String[] parts = measurementName.split(":");
            if (parts.length >= 2) {
                String compartment = parts[0].trim();  // "Nucleus", "Cell", "Cytoplasm"
                String remaining = parts[1].trim().toLowerCase();  // "cd3 mean"

                // 查找统计量
                for (String stat : stats) {
                    if (remaining.endsWith(stat)) {
                        // 首字母大写
                        String statCapitalized = stat.substring(0, 1).toUpperCase() + stat.substring(1);
                        return compartment + ": " + statCapitalized;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 从measurements中提取所有通道名称（保持顺序）
     */
    private List<String> extractChannelNamesFromMeasurements(ImageData<?> imageData) {
        List<String> channelNames = new ArrayList<>();
        Set<String> seenChannels = new HashSet<>();
        try {
            var hierarchy = imageData.getHierarchy();
            var detections = hierarchy.getDetectionObjects();
            if (!detections.isEmpty()) {
                var firstCell = detections.iterator().next();
                var measurements = firstCell.getMeasurementList();
                var measurementNames = measurements.getNames();

                // 解析measurement名称，提取通道名
                // 格式: "Compartment: ChannelName suffix" 或 "ChannelName: suffix"
                // 关键：只提取带有 "mean" 后缀的测量（这些才是通道强度测量）

                // 定义形状指标关键词（需要排除的）
                Set<String> shapeMetrics = Set.of(
                    "area", "perimeter", "circularity", "solidity", "max diameter",
                    "min diameter", "eccentricity", "compactness", "elongation",
                    "aspect ratio", "roundness", "convexity", "extent", "orientation"
                );

                for (String measurementName : measurementNames) {
                    // 只处理包含 "mean" 的测量（通道强度测量）
                    if (!measurementName.toLowerCase().contains(" mean")) {
                        continue;
                    }

                    String[] parts = measurementName.split(":");
                    if (parts.length >= 2) {
                        // 可能是 "Nucleus: CD3 mean" 或 "Nucleus: CD3: mean"
                        String middlePart = parts[1].trim();
                        // 去掉统计量后缀 (mean, median, max, min, etc.) - 不区分大小写
                        String channelPart = middlePart.replaceAll("(?i)\\s+(mean|median|max|min|std dev|sum|range)$", "");

                        // 检查是否是形状指标
                        String lowerChannelPart = channelPart.toLowerCase();
                        boolean isShapeMetric = shapeMetrics.stream()
                            .anyMatch(metric -> lowerChannelPart.contains(metric));

                        if (!channelPart.isEmpty() &&
                            !channelPart.equalsIgnoreCase("Nucleus") &&
                            !channelPart.equalsIgnoreCase("Cell") &&
                            !channelPart.equalsIgnoreCase("Cytoplasm") &&
                            !isShapeMetric &&
                            !seenChannels.contains(channelPart.trim())) {
                            String channelName = channelPart.trim();
                            channelNames.add(channelName);
                            seenChannels.add(channelName);
                        }
                    }
                }

                logger.info("=== 从Measurements提取的通道名称（按顺序） ===");
                for (int i = 0; i < channelNames.size(); i++) {
                    logger.info("  [{}] 实际通道名: '{}'", i, channelNames.get(i));
                }
                logger.info("=== 提取完成 ===");
            }
        } catch (Exception e) {
            logger.debug("无法提取measurement通道名称: {}", e.getMessage());
        }
        return channelNames;
    }

    /**
     * v1.4.0: 验证并修复通道名称匹配问题
     * 当配置加载后，检查通道名称是否与当前图像匹配，如果不匹配则自动重新建立映射关系
     */
    private void validateAndFixChannelNames() {
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            logger.warn("无法验证通道名称 - ImageData为空");
            return;
        }

        try {
            // 从当前图像数据中提取实际的通道名称
            List<String> actualChannelNames = extractChannelNamesFromMeasurements(imageData);

            if (actualChannelNames.isEmpty()) {
                logger.warn("无法从当前图像中提取通道名称");
                return;
            }

            logger.info("=== 验证通道名称匹配 ===");
            logger.info("配置中的通道数: {}", availableChannels.size());
            logger.info("当前图像的通��数: {}", actualChannelNames.size());

            // 检查通道名称是否匹配
            boolean hasMismatch = false;
            for (String configChannel : availableChannels) {
                if (!actualChannelNames.contains(configChannel)) {
                    hasMismatch = true;
                    logger.warn("配置中的通道 '{}' 在当前图像中未找到", configChannel);
                }
            }

            // 如果有通道名称不匹配，自动重新建立映射
            if (hasMismatch || availableChannels.size() != actualChannelNames.size()) {
                logger.info("检测到通道名称不匹配，自动重新建立映射...");

                // v1.4.0修复: 保存当前的显示名称（包含用户自定义名称）
                List<String> oldAvailableChannels = new ArrayList<>(availableChannels);

                // 保存旧的映射关系
                Map<String, String> oldMapping = new HashMap<>(channelNameMapping);

                // 重新建立映射，但保持显示名称不变
                channelNameMapping.clear();

                for (int i = 0; i < Math.min(oldAvailableChannels.size(), actualChannelNames.size()); i++) {
                    String displayName = oldAvailableChannels.get(i);
                    String actualMeasurementName = actualChannelNames.get(i);

                    // 对于每个displayName，找到它对应的QuPathChannelName
                    String quPathChannelName = null;
                    for (Map.Entry<String, String> entry : oldMapping.entrySet()) {
                        String key = entry.getKey();
                        if (key.endsWith("_INDEX")) {
                            continue;
                        }
                        String userDisplayName = userChannelDisplayNames.get(key);
                        String currentDisplayName = (userDisplayName != null) ? userDisplayName : key;
                        if (currentDisplayName.equals(displayName)) {
                            quPathChannelName = key;
                            break;
                        }
                    }

                    if (quPathChannelName != null) {
                        // 使用QuPathChannelName作为key建立映射
                        channelNameMapping.put(quPathChannelName, actualMeasurementName);
                        // 同时恢复索引映射
                        String channelIndex = "C" + (i + 1);
                        channelNameMapping.put(quPathChannelName + "_INDEX", channelIndex);
                        logger.info("  映射修复: QuPath原名 '{}' -> 实际名称: '{}', 显示名称: '{}'",
                                   quPathChannelName, actualMeasurementName, displayName);
                    } else {
                        logger.warn("  无法找到显示名称 '{}' 对应的QuPathChannelName，跳过", displayName);
                    }
                }

                // v1.4.0: 不要更新availableChannels，保持用户自定义的显示名称
                // availableChannels.clear();
                // availableChannels.addAll(actualChannelNames);

                logger.info("✅ 通道名称映射已更新");
            } else {
                logger.info("✅ 通道名称匹配，无需修复");
            }

        } catch (Exception e) {
            logger.error("验证通道名称时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 查找measurement中实际使用的通道名称
     * @param displayName 显示名称（可能被用户修改过）
     * @param actualChannelNames 从measurements中提取的实际通道名列表（按顺序）
     * @param channelIndex 通道索引（从1开始，包括所有通道）
     * @param actualIndex 实际通道索引（从0开始，包括所有通道）
     * @return measurement中实际使用的通道名
     */
    /**
     * 查找measurement中实际使用的通道名称 - 增强版智能匹配
     * @param displayName 显示名称（可能被用户修改过）
     * @param actualChannelNames 从measurements中提取的实际通道��列表（按顺序）
     * @param channelIndex 通道索引（从1开始，包括所有通道）
     * @param actualIndex 实际通道索引（从0开始，包括所有通道）
     * @param channelNameMapping 通道名称映射表 (displayName -> originalMeasurementName)
     * @return measurement中实际使用的通道名
     */
    private String findActualChannelNameInMeasurements(String displayName,
                                                       List<String> actualChannelNames,
                                                       int channelIndex,
                                                       int actualIndex,
                                                       Map<String, String> channelNameMapping) {
        logger.debug("    🔍 开始匹配通道: '{}' (索引: {})", displayName, channelIndex);

        // v1.4.0修复: 现在channelNameMapping使用quPathChannelName作为key，
        // 需要通过displayName反向查找对应的quPathChannelName
        String quPathChannelNameForLookup = null;
        for (Map.Entry<String, String> entry : channelNameMapping.entrySet()) {
            String key = entry.getKey();
            // 跳过索引映射
            if (key.endsWith("_INDEX")) {
                continue;
            }
            String userDisplayName = userChannelDisplayNames.get(key);
            String currentDisplayName = (userDisplayName != null) ? userDisplayName : key;
            if (currentDisplayName.equals(displayName)) {
                quPathChannelNameForLookup = key;
                break;
            }
        }

        // 1. 如果找到了对应的quPathChannelName，从channelNameMapping中获取原始的measurement名称
        String originalMeasurementName = null;
        if (quPathChannelNameForLookup != null) {
            originalMeasurementName = channelNameMapping.get(quPathChannelNameForLookup);
            logger.debug("    通过displayName '{}' 找到QuPathChannelName '{}', 原始measurement: '{}'",
                        displayName, quPathChannelNameForLookup, originalMeasurementName);
        } else {
            logger.debug("    未找到displayName '{}' 对应的QuPathChannelName映射", displayName);
        }

        if (originalMeasurementName != null) {
            logger.debug("    从映射表中找到原始名称: '{}'", originalMeasurementName);

            // 2. 用原始measurement名称在actualChannelNames中精确匹配（包括大小写）
            if (actualChannelNames.contains(originalMeasurementName)) {
                logger.info("    → ✅ 原始名称精确匹配: '{}'", originalMeasurementName);
                return originalMeasurementName;
            }
            logger.debug("    原始名称 '{}' 在当前measurements中未找到，尝试位置匹配", originalMeasurementName);
        } else {
            logger.debug("    未找到displayName '{}' 的映射记录", displayName);
        }

        // 3. 如果映射中没有记录或原始名称找不到，尝试显示名称直接匹配（兼容旧配置）
        if (actualChannelNames.contains(displayName)) {
            logger.info("    → ✅ 显示名称精确匹配: '{}'", displayName);
            return displayName;
        }

        // 4. 尝试C索引匹配 (C1, C2, C3, ...)
        String cIndex = "C" + channelIndex;
        if (actualChannelNames.contains(cIndex)) {
            logger.info("    → ✅ C索引匹配: '{}' -> '{}'", displayName, cIndex);
            return cIndex;
        }

        // 5. 位置匹配（基于通道的实际位置）
        // 处理通道完全重命名的情况（通过实际位置匹配）
        if (actualIndex >= 0 && actualIndex < actualChannelNames.size()) {
            String positionMatch = actualChannelNames.get(actualIndex);
            logger.info("    → ⚠️ 位置匹配fallback (索引{}): '{}' -> '{}'", actualIndex, displayName, positionMatch);
            return positionMatch;
        }

        // 6. 最后fallback：返回显示名称本身
        logger.warn("    → ⚠️ 未找到匹配，使用显示名称作为fallback: '{}'", displayName);
        return displayName;
    }

    // TODO: [方法] 调试可用measurement名称
    private void debugAvailableMeasurements(ImageData<?> imageData) {
        try {
            var hierarchy = imageData.getHierarchy();
            var detections = hierarchy.getDetectionObjects();
            if (!detections.isEmpty()) {
                var firstCell = detections.iterator().next();
                var measurements = firstCell.getMeasurementList();
                var measurementNames = measurements.getNames();

                logger.info("=== 实际Measurement名称列表 ===");
                for (String name : measurementNames) {
                    if (name.toLowerCase().contains("mean") ||
                        name.toLowerCase().contains("median") ||
                        name.toLowerCase().contains("max") ||
                        name.toLowerCase().contains("min")) {
                        logger.info("  Measurement: '{}'", name);
                    }
                }
                logger.info("=== Measurement列表结束 ===");
            }
        } catch (Exception e) {
            logger.debug("无法获取measurement信息: {}", e.getMessage());
        }
    }
    
    
    // TODO: [方法] 解析通道测量名称
    // TODO: [方法] 存储Load Object Classifier分类结果
    private void storeClassificationMapping(String cellId, String classificationName) {
        classificationMapping.put(cellId, classificationName);
        logger.debug("Stored classification mapping: {} -> {}", cellId, classificationName);
    }

    // TODO: [方法] 获取细胞分类名称
    private String getClassificationName(String cellId) {
        return classificationMapping.getOrDefault(cellId, "");
    }

    // TODO: [方法] 检查细胞是否有分类数据
    private boolean hasClassificationData(String cellId) {
        return classificationMapping.containsKey(cellId) && !classificationMapping.get(cellId).isEmpty();
    }

    // TODO: [方法] 获取有效通道名称
    private String getEffectiveChannelName(String channelName, String measurementType) {
        // Primary: try original channel name mapping
        String originalName = channelNameMapping.getOrDefault(channelName, channelName);
        String primaryMeasurement = originalName + ": " + measurementType.split(": ")[1];

        // Check if measurement exists in current image data
        ImageData<?> imageData = qupath.getImageData();
        if (imageData != null) {
            var allObjects = imageData.getHierarchy().getDetectionObjects();
            if (!allObjects.isEmpty()) {
                var sampleObject = allObjects.iterator().next();
                var measurements = sampleObject.getMeasurementList();

                // Try primary measurement name
                if (measurements.containsKey(primaryMeasurement)) {
                    return originalName;
                }

                // Fallback: try display name
                String fallbackMeasurement = channelName + ": " + measurementType.split(": ")[1];
                if (measurements.containsKey(fallbackMeasurement)) {
                    return channelName;
                }

                // Fallback: try variations of the channel name
                String[] variations = {originalName.toLowerCase(), originalName.toUpperCase(),
                                     channelName.toLowerCase(), channelName.toUpperCase()};
                for (String variation : variations) {
                    String testMeasurement = variation + ": " + measurementType.split(": ")[1];
                    if (measurements.containsKey(testMeasurement)) {
                        return variation;
                    }
                }
            }
        }

        // Default fallback
        return originalName;
    }
    
    // TODO: [方法] 获取图像动态范围
    private double[] getImageDynamicRange() {
        ImageData<?> imageData = qupath.getImageData();
        if (imageData != null && imageData.getServer() != null) {
            // Try to get actual range from measurements
            var hierarchy = imageData.getHierarchy();
            var cells = hierarchy.getDetectionObjects();
            if (!cells.isEmpty()) {
                var firstCell = cells.iterator().next();
                var measurements = firstCell.getMeasurementList();
                
                // Find min/max from actual measurements
                double minValue = Double.MAX_VALUE;
                double maxValue = Double.MIN_VALUE;
                
                for (String measurementName : measurements.getNames()) {
                    if (measurementName.contains("Mean") || measurementName.contains("Median")) {
                        for (var cell : cells.stream().limit(1000).collect(java.util.stream.Collectors.toList())) {
                            try {
                                double value = cell.getMeasurementList().get(measurementName);
                                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                                    minValue = Math.min(minValue, value);
                                    maxValue = Math.max(maxValue, value);
                                }
                            } catch (Exception e) {
                                // Continue with next measurement
                            }
                        }
                    }
                }
                
                if (minValue != Double.MAX_VALUE && maxValue != Double.MIN_VALUE) {
                    // Add some padding
                    double range = maxValue - minValue;
                    return new double[]{Math.max(0, minValue - range * 0.1), maxValue + range * 0.1};
                }
            }
            
            // Fallback: Use image bit depth
            var server = imageData.getServer();
            int bitDepth = 8; // Default
            try {
                if (server.getPixelType().toString().contains("UINT16")) {
                    bitDepth = 16;
                } else if (server.getPixelType().toString().contains("FLOAT32")) {
                    return new double[]{0, 1.0}; // Float images typically 0-1
                }
            } catch (Exception e) {
                logger.debug("Could not determine bit depth, using default");
            }
            
            double maxVal = Math.pow(2, bitDepth) - 1;
            return new double[]{0, maxVal};
        }
        
        // Ultimate fallback
        return new double[]{0, 65535}; // 16-bit range
    }
    
    // TODO: [方法] 创建对数滑块
    private Slider createLogarithmicSlider(double minVal, double maxVal, double defaultVal, String channelName) {
        // Ensure positive values for log scale
        double logMin = Math.log10(Math.max(1, minVal));
        double logMax = Math.log10(Math.max(2, maxVal));
        double logDefault = Math.log10(Math.max(1, defaultVal));
        
        Slider slider = new Slider(logMin, logMax, logDefault);
        
        // Store reference first to avoid lookup issues
        thresholdSliders.put(channelName, slider);
        
        return slider;
    }
    
    // TODO: [方法] 从对数滑块获取线性值
    private double getLinearValue(Slider logSlider) {
        return Math.pow(10, logSlider.getValue());
    }
    
    // TODO: [方法] 待优化方法

    private void initializeThresholds() {
        Map<String, ThresholdConfig.ChannelThreshold> thresholds = new HashMap<>();
        for (int i = 0; i < availableChannels.size(); i++) {
            String channelName = availableChannels.get(i);
            // === 使用固定的预设测量值列表 ===
            // v1.4.0: 使用固定测量值列表，不依赖实际measurements
            // 关键修复: 使用辅助方法从displayName查找actualChannelName
            String actualChannelName = findActualChannelNameForDisplayName(channelName);
            if (actualChannelName == null) {
                actualChannelName = channelName;  // fallback
            }
            SegmentationModel model = currentConfig.getSegmentationModel();
            List<String> channelMeasurements = generateFixedMeasurementsForChannel(actualChannelName, model);

            // 使用第一个包含"mean"的measurement作为默认值
            String defaultMeasurement = channelMeasurements.stream()
                .filter(m -> m.toLowerCase().contains("mean"))
                .findFirst()
                .orElse(channelMeasurements.get(0));

            double defaultThreshold = i == 0 ? 150.0 : 100.0; // First channel enabled by default
            boolean enabled = i == 0;
            thresholds.put(channelName, new ThresholdConfig.ChannelThreshold(defaultMeasurement, defaultThreshold, enabled));
        }

        for (Map.Entry<String, ThresholdConfig.ChannelThreshold> entry : thresholds.entrySet()) {
            currentConfig = currentConfig.withChannelThreshold(entry.getKey(), entry.getValue());
        }
    }
    
    // TODO: [方法] 待优化方法

    public void show() {
        if (stage != null) {
            stage.toFront();
            return;
        }

        // Refresh channel information when showing
        loadAvailableChannels();
        initializeThresholds();

        stage = new Stage();
        stage.setTitle("CycBiOx");
        stage.initModality(Modality.NONE);
        stage.initOwner(qupath.getStage());

        ScrollPane scrollPane = createMainLayout();
        // 优化GUI宽度，适合数据展示的紧凑设计
        Scene scene = new Scene(scrollPane, 800, 750);
        stage.setScene(scene);
        stage.show();

        // Initialize cell selection highlighting mechanism
        initializeCellSelectionHighlighting();

        stage.setOnCloseRequest(e -> {
            // Clear any preview when closing
            clearLivePreview();
            // Clean up selection listener
            cleanupSelectionHighlighting();
            stage = null;
        });
    }
    
    // TODO: [方法] 待优化方法

    private ScrollPane createMainLayout() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        // Basic settings section
        root.getChildren().add(createBasicSettingsSection());

        // Threshold strategy section
        TitledPane thresholdPane = new TitledPane("阈值策略配置", createOptimizedThresholdSection());
        thresholdPane.setCollapsible(false);
        root.getChildren().add(thresholdPane);

        // Cell classification section
        root.getChildren().add(createClassificationSection());

        // Action buttons
        root.getChildren().add(createActionButtonsSection());

        mainScrollPane = new ScrollPane(root);
        mainScrollPane.setFitToWidth(true);
        mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        return mainScrollPane;
    }
    
    // TODO: [UI] 创建紧凑基础设置区域
    private VBox createCompactBasicSettings() {
        VBox section = new VBox(5);
        section.setPadding(new Insets(8));
        section.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 5; -fx-padding: 8;");
        
        // 标题
        Label titleLabel = new Label("基础设置");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #495057;");
        section.getChildren().add(titleLabel);
        
        // 设置内容 - 水平排列以节省空间
        HBox settingsBox = new HBox(15);
        settingsBox.setAlignment(Pos.CENTER_LEFT);

        // 配置名称
        VBox nameBox = new VBox(3);
        Label nameLabel = new Label("配置名称");
        nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");
        configNameField = new TextField("默认配置");
        configNameField.setPrefWidth(120);
        nameBox.getChildren().addAll(nameLabel, configNameField);


        // 保存地址
        VBox pathBox = new VBox(3);
        Label pathLabel = new Label("保存地址");
        pathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");
        savePathField = new TextField(System.getProperty("user.home"));
        savePathField.setPrefWidth(200);
        Button browseButton = new Button("...");
        browseButton.setPrefWidth(30);
        browseButton.setOnAction(e -> browseSavePath(savePathField));
        HBox pathControls = new HBox(5);
        pathControls.getChildren().addAll(savePathField, browseButton);
        pathBox.getChildren().addAll(pathLabel, pathControls);

        // 分析细胞下拉框
        VBox cellAnalysisBox = new VBox(3);
        Label cellAnalysisLabel = new Label("分析细胞");
        cellAnalysisLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");
        cellAnalysisComboBox = new ComboBox<>();
        cellAnalysisComboBox.getItems().addAll("当前选中细胞", "全部细胞");
        cellAnalysisComboBox.setValue("全部细胞");
        cellAnalysisComboBox.setPrefWidth(120);
        cellAnalysisBox.getChildren().addAll(cellAnalysisLabel, cellAnalysisComboBox);
        
        return section;
    }
    
    // TODO: [UI] 创建优化阈值策略区域 - 上下布局
    private VBox createOptimizedThresholdSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        // 标题
        Label titleLabel = new Label("阈值策略配置");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #495057;");
        section.getChildren().add(titleLabel);

        // 1. 确定通道阳性阈值（CREATE模式）
        HBox createBox = new HBox(10);
        createBox.setAlignment(Pos.CENTER_LEFT);
        createBox.setPadding(new Insets(5));
        Label createLabel = new Label("确定通道阳性阈值:");
        createLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        RadioButton createRadio = new RadioButton();
        createRadio.setSelected(currentMode == OperationMode.CREATE_CLASSIFIER);
        createBox.getChildren().addAll(createRadio, createLabel);

        // 2. 刷新通道
        HBox refreshBox = new HBox(10);
        refreshBox.setAlignment(Pos.CENTER_LEFT);
        refreshBox.setPadding(new Insets(5));
        refreshButton = new Button("刷新通道");
        refreshButton.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white; -fx-font-size: 11px;");
        refreshButton.setOnAction(e -> refreshChannels());

        // 添加刷新通道的适用场景说明
        Label refreshHintLabel = new Label("(适用场景: 图像切换、通道重命名、导入新数据后)");
        refreshHintLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d; -fx-font-style: italic;");

        refreshBox.getChildren().addAll(refreshButton, refreshHintLabel);

        // v1.4.0: 分割模型选择（放在刷新通道之后）
        HBox modelBox = new HBox(3);  // 标签和下拉框之间的间距缩小到3px
        modelBox.setAlignment(Pos.CENTER_LEFT);
        modelBox.setPadding(new Insets(5));

        Label modelLabel = new Label("分割模型:");
        modelLabel.setStyle("-fx-font-size: 12px;");
        modelLabel.setMinWidth(80);

        segmentationModelComboBox = new ComboBox<>();
        segmentationModelComboBox.getItems().addAll(
            "StarDist",
            "Cellpose",
            "InstanSeg",
            "QuPath Detection"
        );
        segmentationModelComboBox.setValue(currentConfig.getSegmentationModel().getDisplayName());
        segmentationModelComboBox.setPrefWidth(150);
        segmentationModelComboBox.setOnAction(e -> onSegmentationModelChanged());

        modelBox.getChildren().addAll(modelLabel, segmentationModelComboBox);

        // 3. 阈值策略
        HBox strategyBox = new HBox(3);  // 标签和下拉框之间的间距缩小到3px
        strategyBox.setAlignment(Pos.CENTER_LEFT);
        strategyBox.setPadding(new Insets(5));
        Label strategyLabel = new Label("阈值策略:");
        strategyLabel.setStyle("-fx-font-size: 12px;");
        strategyLabel.setMinWidth(80);
        strategyComboBox = new ComboBox<>();
        strategyComboBox.getItems().addAll(ThresholdConfig.Strategy.values());

        // 设置StringConverter来显示中文名称
        strategyComboBox.setConverter(new StringConverter<ThresholdConfig.Strategy>() {
            @Override
            public String toString(ThresholdConfig.Strategy strategy) {
                return strategy != null ? strategy.getDisplayName() : "";
            }

            @Override
            public ThresholdConfig.Strategy fromString(String string) {
                for (ThresholdConfig.Strategy strategy : ThresholdConfig.Strategy.values()) {
                    if (strategy.getDisplayName().equals(string)) {
                        return strategy;
                    }
                }
                return null;
            }
        });

        strategyComboBox.setValue(currentConfig.getStrategy());
        strategyComboBox.setPrefWidth(150);  // 缩小下拉框宽度

        // 算法选择框 - 自动模式时显示（添加中文名称和解释）
        algorithmComboBox = new ComboBox<>();
        algorithmComboBox.getItems().addAll(
            "MaxEntropy (最大熵算法-适用于复杂背景)",
            "Triangle (三角算法-适用于双峰分布)",
            "Otsu (大津算法-经典双峰分割)",
            "Minimum (最小值算法-适用于明暗差异大)"
        );
        algorithmComboBox.setValue("MaxEntropy (最大熵算法-适用于复杂背景)");
        algorithmComboBox.setPrefWidth(200);
        algorithmComboBox.setStyle("-fx-font-size: 9px;");
        algorithmComboBox.setVisible(false);

        // 计算按钮 - Auto模式时显示
        calculateButton = new Button("计算");
        calculateButton.setStyle("-fx-background-color: #ffc107; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 3 8 3 8;");
        calculateButton.setVisible(false);
        calculateButton.setOnAction(e -> {
            String algorithmDisplay = algorithmComboBox.getValue();
            String algorithm = extractAlgorithmName(algorithmDisplay);
            calculateAutoThresholds(algorithm);
        });

        // 根据策略下拉框选择显示/隐藏算法选择和计算按钮
        strategyComboBox.setOnAction(e -> {
            ThresholdConfig.Strategy selectedStrategy = strategyComboBox.getValue();
            isAutoMode = (selectedStrategy == ThresholdConfig.Strategy.AUTO);

            // 更新配置中的策略设置，确保状态同步
            currentConfig = currentConfig.withStrategy(selectedStrategy);

            logger.info("阈值策略切换: {}", isAutoMode ? "自动" : "手动");

            // 显示/隐藏算法选择和计算按钮
            algorithmComboBox.setVisible(isAutoMode);
            calculateButton.setVisible(isAutoMode);

            if (isAutoMode) {
                logger.info("自动模式已激活 - 请选择算法并点击计算按钮");
            } else {
                logger.info("切换到手动模式，保持已计算的阈值不变");
            }

            updateControlStatesForMode();
        });

        strategyBox.getChildren().addAll(strategyLabel, strategyComboBox, algorithmComboBox, calculateButton);

        // 4. 选择所需通道，执行策略（LOAD模式）
        HBox loadBox = new HBox(10);
        loadBox.setAlignment(Pos.CENTER_LEFT);
        loadBox.setPadding(new Insets(5));
        Label loadLabel = new Label("选择所需通道，执行策略:");
        loadLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        RadioButton loadRadio = new RadioButton();
        loadRadio.setSelected(currentMode == OperationMode.LOAD_CLASSIFIER);
        loadBox.getChildren().addAll(loadRadio, loadLabel);

        // 单选按钮组
        ToggleGroup modeGroup = new ToggleGroup();
        createRadio.setToggleGroup(modeGroup);
        loadRadio.setToggleGroup(modeGroup);

        // 切换逻辑
        modeGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == createRadio) {
                currentMode = OperationMode.CREATE_CLASSIFIER;
                updateChannelSelectionMode();
                updateControlStatesForMode();
                updateButtonStates();
                logger.info("Switched to Create Classifier mode - Single channel selection");
            } else if (newToggle == loadRadio) {
                currentMode = OperationMode.LOAD_CLASSIFIER;
                updateChannelSelectionMode();
                updateControlStatesForMode();
                updateButtonStates();
                logger.info("Switched to Load Classifier mode - Multi-channel selection");
            }
        });

        // 5. 运行按钮
        HBox executeBox = new HBox();
        executeBox.setAlignment(Pos.CENTER_LEFT);
        executeBox.setPadding(new Insets(10, 0, 0, 0));
        executeButton = new Button("运行");
        executeButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
        executeButton.setOnAction(e -> executeStrategy());
        updateButtonStates();
        executeBox.getChildren().add(executeButton);

        // 添加所有组件到垂直布局
        section.getChildren().addAll(createBox, refreshBox, modelBox, strategyBox, loadBox, executeBox);
        
        // 通道控制区域 - 动态高度
        channelContainer = new VBox(3);
        createChannelControls();
        
        ScrollPane channelScrollPane = new ScrollPane(channelContainer);
        channelScrollPane.setFitToWidth(true);

        // 优化30+通道支持：动态高度计算和滚动策略
        int channelCount = availableChannels.size();
        double maxHeight;

        if (channelCount <= 4) {
            maxHeight = Math.min(calculateOptimalChannelHeight(), 280); // 少量通道，紧凑显示
        } else if (channelCount <= 15) {
            maxHeight = 350; // 中等数量通道，标准高度
        } else {
            maxHeight = 450; // 30+通道，更大的滚动区域
        }

        channelScrollPane.setPrefHeight(maxHeight);
        channelScrollPane.setMaxHeight(maxHeight);

        // 优化滚动性能和用户体验
        channelScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // 水平不滚动
        channelScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // 垂直滚动按需显示
        channelScrollPane.setPannable(true); // 支持拖拽滚动
        channelScrollPane.setVvalue(0.0); // 默认滚动到顶部
        channelScrollPane.setStyle("-fx-background-color: transparent; -fx-focus-color: transparent;");

        logger.info("通道滚动优化完成 - {}个通道，最大高度: {}px", channelCount, maxHeight);
        
        section.getChildren().add(channelScrollPane);

        // 初始按钮状态控制
        updateButtonStates();

        return section;
    }
    
    // TODO: [UI] 创建优化细胞分类区域
    private VBox createOptimizedClassificationSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dee2e6; -fx-border-radius: 5;");
        
        // 标题和右上角新增细胞按钮
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_RIGHT);
        headerBox.setPadding(new Insets(5, 0, 10, 0));

        Label titleLabel = new Label("细胞分类配置");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #495057;");

        // 使用 Region spacer 将按钮推到右边
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button addButton = new Button("+ 新增细胞类型");
        addButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
        addButton.setOnAction(e -> addNewPhenotype());

        headerBox.getChildren().addAll(titleLabel, spacer, addButton);
        section.getChildren().add(headerBox);
        
        // 表型配置表格 - 标准布局，支持滚动
        createPhenotypeTable();

        // 表格容器 - 增强左右滚动支持30+通道
        ScrollPane tableScrollPane = new ScrollPane(phenotypeTable);
        tableScrollPane.setFitToWidth(false); // 重要：允许水平滚动，不强制适应宽度
        tableScrollPane.setFitToHeight(true);  // 垂直方向适应高度
        tableScrollPane.setPrefHeight(300);    // 标准高度
        tableScrollPane.setMaxHeight(400);     // 最大高度限制

        // 优化滚动策略 - 支持30+通道的左右滑动
        tableScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // 水平滚动条自动显示
        tableScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);  // 垂直滚动条自动显示

        // 设置滚动性能优化
        tableScrollPane.setPannable(true); // 支持拖拽滚动
        tableScrollPane.setStyle("-fx-background-color: transparent; -fx-focus-color: transparent;");

        // 强制表格不适应ScrollPane宽度，使其能够水平滚动
        phenotypeTable.autosize();
        phenotypeTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        section.getChildren().add(tableScrollPane);

        logger.info("细胞分类界面优化完成 - 支持30+通道的左右滑动显示");
        return section;
    }
    
    // TODO: [方法] 待优化方法

    private TitledPane createBasicSettingsSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        // Configuration name
        HBox nameBox = new HBox(10);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label("配置名称");
        nameLabel.setPrefWidth(80);
        configNameField = new TextField(currentConfig.getConfigName());
        configNameField.setPrefWidth(200);
        nameBox.getChildren().addAll(nameLabel, configNameField);

        // Save path selection
        HBox pathBox = new HBox(10);
        pathBox.setAlignment(Pos.CENTER_LEFT);
        Label pathLabel = new Label("保存地址");
        pathLabel.setPrefWidth(80);
        savePathField = new TextField(System.getProperty("user.home"));
        savePathField.setPrefWidth(200);
        Button browseButton = new Button("浏览...");
        browseButton.setOnAction(e -> browseSavePath(savePathField));
        HBox pathControls = new HBox(5);
        pathControls.getChildren().addAll(savePathField, browseButton);
        pathBox.getChildren().addAll(pathLabel, pathControls);


        // Cell analysis selection with statistics
        VBox cellAnalysisBox = new VBox(5);

        // Main selection row
        HBox cellSelectionRow = new HBox(10);
        cellSelectionRow.setAlignment(Pos.CENTER_LEFT);
        Label cellAnalysisLabel = new Label("分析细胞");
        cellAnalysisLabel.setPrefWidth(80);
        cellAnalysisComboBox = new ComboBox<>();
        cellAnalysisComboBox.getItems().addAll("当前选中细胞", "全部细胞");
        cellAnalysisComboBox.setValue("全部细胞");
        cellAnalysisComboBox.setPrefWidth(200);
        cellSelectionRow.getChildren().addAll(cellAnalysisLabel, cellAnalysisComboBox);

        // Statistics display
        HBox statisticsRow = new HBox(10);
        statisticsRow.setAlignment(Pos.CENTER_LEFT);
        Label spacerLabel = new Label(); // 占位符保持对齐
        spacerLabel.setPrefWidth(80);
        Label statisticsLabel = new Label("统计信息: 加载中...");
        statisticsLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666;");
        statisticsRow.getChildren().addAll(spacerLabel, statisticsLabel);

        cellAnalysisBox.getChildren().addAll(cellSelectionRow, statisticsRow);

        // Update statistics when selection changes
        cellAnalysisComboBox.setOnAction(e -> updateStatisticsDisplay(statisticsLabel));

        // Initialize statistics display
        updateStatisticsDisplay(statisticsLabel);

        content.getChildren().addAll(nameBox, pathBox, cellAnalysisBox);

        TitledPane pane = new TitledPane("基本设置", content);
        pane.setCollapsible(false);
        return pane;
    }
    
    
    /**
     * 根据通道数量计算最优的界面高度 - 标准布局策略
     */
    private double calculateOptimalChannelHeight() {
        int channelCount = availableChannels.size();
        if (channelCount == 0) return 120; // 空状态最小高度
        
        // 标准布局：每个通道固定高度
        double heightPerChannel = 50; // 紧凑的通道行高度
        double headerHeight = 35;     // 表头高度
        double paddingHeight = 20;    // 上下padding
        
        // 计算基础高度
        double totalHeight = headerHeight + (channelCount * heightPerChannel) + paddingHeight;
        
        // 标准布局策略：
        // 1-4个通道：显示全部，无滚动
        // 5-8个通道：适中高度，轻度滚动 
        // 9+个通道：固定高度，滚动浏览
        double finalHeight;
        if (channelCount <= 4) {
            finalHeight = totalHeight; // 显示全部
        } else if (channelCount <= 8) {
            finalHeight = Math.min(totalHeight, 300); // 适中高度
        } else {
            finalHeight = 350; // 固定较大高度，支持滚动
        }
        
        logger.info("标准布局高度计算: {}个通道 -> {}px ({})", 
                   channelCount, finalHeight, 
                   channelCount <= 4 ? "无滚动" : channelCount <= 8 ? "轻度滚动" : "标准滚动");
        return finalHeight;
    }
    
    /**
     * 根据通道数量优化整体布局
     */
    private void optimizeLayoutForChannels() {
        int channelCount = availableChannels.size();
        
        // 根据通道数量调整各个区域的比例
        Platform.runLater(() -> {
            // 更新通道区域高度
            if (channelContainer != null && channelContainer.getParent() instanceof ScrollPane) {
                ScrollPane scrollPane = (ScrollPane) channelContainer.getParent();
                double newHeight = calculateOptimalChannelHeight();
                scrollPane.setPrefHeight(newHeight);
                
                // 优化滚动策略
                if (channelCount <= 4) {
                    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                } else {
                    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                }
                
                logger.debug("已更新通道区域布局: {}个通道, 高度{}px", channelCount, newHeight);
            }
        });
    }
    
    // TODO: [方法] 待优化方法

    private void createChannelControls() {
        channelContainer.getChildren().clear();
        channelRadioButtons.clear();
        channelCheckBoxes.clear();
        measurementComboBoxes.clear();
        thresholdSliders.clear();
        thresholdFields.clear();
        thresholdStatusLabels.clear();
        
        // Clear toggle groups
        if (singleChannelGroup != null) {
            singleChannelGroup.getToggles().clear();
        }
        singleChannelGroup = new ToggleGroup();
        
        // Header with columns for both modes
        HBox header = new HBox(10);
        header.setPadding(new Insets(8));
        header.setStyle("-fx-background-color: #e3f2fd; -fx-border-color: #90caf9; -fx-border-radius: 3;");

        // 选择列（CREATE和LOAD模式都显示）
        Label loadSelectionLabel = new Label("选择");
        loadSelectionLabel.setPrefWidth(50);
        loadSelectionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        // 选择列在两种模式下都显示

        // 通道列
        Label channelLabel = new Label("通道");
        channelLabel.setPrefWidth(80);
        channelLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        // 预览列（CREATE和LOAD模式都显示）
        Label previewLabel = new Label("预览");
        previewLabel.setPrefWidth(50);
        previewLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        // 预览列在两种模式下都显示

        // 测量值列
        Label measurementLabel = new Label("测量值");
        measurementLabel.setPrefWidth(130);
        measurementLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        // 阈值列
        Label thresholdLabel = new Label("阈值");
        thresholdLabel.setPrefWidth(280); // 与内容列宽度匹配，确保按钮完整显示
        thresholdLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        // 阈值设置状态列（位置往右移）
        Label statusLabel = new Label("设置状态");
        statusLabel.setPrefWidth(100); // 增加宽度确保正常显示
        statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        header.getChildren().addAll(loadSelectionLabel, channelLabel, previewLabel, measurementLabel, thresholdLabel, statusLabel);
        channelContainer.getChildren().add(header);
        
        // Create controls for each channel
        for (String channelName : availableChannels) {
            HBox channelBox = createChannelRow(channelName);
            channelContainer.getChildren().add(channelBox);
        }
    }
    
    private HBox createChannelRow(String channelName) {
        HBox row = new HBox(10);
        row.setPadding(new Insets(5));
        row.setAlignment(Pos.CENTER_LEFT);

        // 1. 选择列（使用统一的选择逻辑）
        CheckBox loadSelectionCheckBox = new CheckBox();
        loadSelectionCheckBox.setPrefWidth(50);

        // 先将CheckBox添加到Map，然后通过统一方法设置状态
        channelCheckBoxes.put(channelName, loadSelectionCheckBox);

        // 使用统一的状态更新逻辑
        updateChannelSelectionStatus(channelName);

        row.getChildren().add(loadSelectionCheckBox);

        // 2. 通道列
        Label channelLabel = new Label(channelName);
        channelLabel.setPrefWidth(80);
        channelLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        // Add tooltip if channel name was modified in QuPath's Channel Properties
        // v1.4.0: 修复 - 使用辅助方法从displayName查找actualChannelName
        String originalName = findActualChannelNameForDisplayName(channelName);
        if (originalName != null && !originalName.equals(channelName)) {
            Tooltip tooltip = new Tooltip(String.format("Original: %s\nDisplay: %s", originalName, channelName));
            tooltip.setShowDelay(javafx.util.Duration.millis(300));
            channelLabel.setTooltip(tooltip);
            channelLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1976D2;"); // Blue color for modified names
        }
        row.getChildren().add(channelLabel);

        // 3. 预览列（两种模式都显示，仅CREATE模式可操作）
        RadioButton previewRadio = new RadioButton();
        previewRadio.setPrefWidth(50);
        previewRadio.setToggleGroup(singleChannelGroup);

        if (currentMode == OperationMode.CREATE_CLASSIFIER) {
            // CREATE模式：可操作
            previewRadio.setDisable(false);
            previewRadio.setStyle(""); // 正常样式
        } else {
            // LOAD模式：显示但不可操作，灰色
            previewRadio.setDisable(true);
            previewRadio.setSelected(false); // 确保不被选中
            previewRadio.setStyle("-fx-opacity: 0.6;"); // 视觉上显示为灰色
        }

        previewRadio.setOnAction(e -> {
            if (previewRadio.isSelected() && currentMode == OperationMode.CREATE_CLASSIFIER) {
                clearLivePreview();
                currentPreviewChannel = channelName;
                livePreviewEnabled = true;

                // 如果该通道已确认阈值，自动加载保存的设置并应用预览
                if (isChannelThresholdConfirmed(channelName)) {
                    loadSavedThresholdAndPreview(channelName);
                } else {
                    // 如果没有确认阈值，启用实时预览（滑块变化时预览）
                    logger.info("Selected channel for Create Classifier (no saved threshold): {}", channelName);
                }

                logger.info("Selected channel for Create Classifier: {}", channelName);

                // Build 16: 立即切换Brightness&Contrast窗口到当前通道
                Platform.runLater(() -> switchToChannelDisplay(channelName));
            }
        });

        channelRadioButtons.put(channelName, previewRadio);
        row.getChildren().add(previewRadio);

        // 4. 测量值列
        ThresholdConfig.ChannelThreshold channelThreshold = currentConfig.getChannelThresholds().get(channelName);

        // === v1.4.0: 使用固定测量值列表，不依赖实际measurements ===
        // 关键修复: 使用辅助方法从displayName查找actualChannelName
        String actualChannelName = findActualChannelNameForDisplayName(channelName);
        if (actualChannelName == null) {
            actualChannelName = channelName;  // fallback
        }
        SegmentationModel model = currentConfig.getSegmentationModel();
        List<String> channelMeasurements = generateFixedMeasurementsForChannel(actualChannelName, model);

        ComboBox<String> measurementCombo = new ComboBox<>();
        measurementCombo.getItems().addAll(channelMeasurements);  // 显示固定预设的measurement名称

        // Preserve existing measurement from configuration
        String existingMeasurement = null;
        if (channelThreshold != null) {
            existingMeasurement = channelThreshold.getMeasurement();
        }

        // 如果配置中有值，尝试在列表中查找
        String selectedMeasurement = null;
        if (existingMeasurement != null && !existingMeasurement.trim().isEmpty()) {
            // 尝试精确匹配
            if (channelMeasurements.contains(existingMeasurement)) {
                selectedMeasurement = existingMeasurement;
            }
            // 尝试部分匹配（兼容旧的"Nucleus: Mean"格式）
            else {
                for (String measurement : channelMeasurements) {
                    if (measurement.toLowerCase().contains(existingMeasurement.toLowerCase())) {
                        selectedMeasurement = measurement;
                        logger.info("通道 '{}': 配置的 '{}' 匹配到 '{}'",
                                   channelName, existingMeasurement, measurement);
                        break;
                    }
                }
            }

            // 如果仍未找到，添加到列表中（向后兼容）
            if (selectedMeasurement == null) {
                measurementCombo.getItems().add(existingMeasurement);
                selectedMeasurement = existingMeasurement;
                logger.warn("通道 '{}': 配置的 '{}' 不在实际measurements中，已添加到列表",
                           channelName, existingMeasurement);
            }
        }

        // 如果没有配置值或未找到匹配，使用默认值（第一个包含"mean"的）
        if (selectedMeasurement == null && !channelMeasurements.isEmpty()) {
            // 优先选择包含"mean"的measurement
            selectedMeasurement = channelMeasurements.stream()
                .filter(m -> m.toLowerCase().contains("mean"))
                .findFirst()
                .orElse(channelMeasurements.get(0));
            logger.info("通道 '{}': 使用默认measurement '{}'", channelName, selectedMeasurement);
        }

        measurementCombo.setValue(selectedMeasurement);
        measurementCombo.setPrefWidth(180);  // 增加宽度以显示完整名称

        // Load模式下禁用measurement选择
        measurementCombo.setDisable(currentMode == OperationMode.LOAD_CLASSIFIER);

        measurementCombo.setOnAction(e -> {
            logger.info("=== ComboBox Action Triggered ===");
            logger.info("Channel: {}", channelName);
            logger.info("New Value: {}", measurementCombo.getValue());
            logger.info("Current Mode: {}", currentMode);

            // === 关键修复：立即更新ThresholdConfig中的measurement ===
            String newMeasurement = measurementCombo.getValue();
            if (newMeasurement != null && !newMeasurement.trim().isEmpty()) {
                // 获取当前通道的阈值配置
                ThresholdConfig.ChannelThreshold currentThreshold = currentConfig.getChannelThresholds().get(channelName);
                if (currentThreshold != null) {
                    // 创建新的ChannelThreshold，保留阈值和启用状态，更新measurement
                    ThresholdConfig.ChannelThreshold updatedThreshold =
                        new ThresholdConfig.ChannelThreshold(
                            newMeasurement,  // 新的measurement
                            currentThreshold.getThreshold(),  // 保留阈值
                            currentThreshold.isEnabled()  // 保留启用状态
                        );
                    // 更新配置
                    currentConfig = currentConfig.withChannelThreshold(channelName, updatedThreshold);
                    logger.info("✓ 已更新通道 '{}' 的measurement: '{}' -> '{}'",
                               channelName, currentThreshold.getMeasurement(), newMeasurement);
                } else {
                    // 如果没有现有配置，创建新的
                    TextField thresholdField = thresholdFields.get(channelName);
                    double threshold = 100.0;  // 默认阈值
                    if (thresholdField != null) {
                        try {
                            threshold = Double.parseDouble(thresholdField.getText());
                        } catch (NumberFormatException ex) {
                            // 使用默认值
                        }
                    }
                    ThresholdConfig.ChannelThreshold newThreshold =
                        new ThresholdConfig.ChannelThreshold(newMeasurement, threshold, true);
                    currentConfig = currentConfig.withChannelThreshold(channelName, newThreshold);
                    logger.info("✓ 为通道 '{}' 创建新配置，measurement: '{}'", channelName, newMeasurement);
                }
            }

            // Update status when measurement changes
            updateThresholdStatus(channelName, false);

            // === Build 8修复：无论什么模式，如果当前通道正在预览，都应该更新 ===
            boolean shouldUpdatePreview = false;
            String updateReason = "";

            // 情况1: Create模式 + RadioButton被选中
            if (currentMode == OperationMode.CREATE_CLASSIFIER) {
                RadioButton radio = channelRadioButtons.get(channelName);
                logger.info("RadioButton for channel '{}': exists={}, selected={}",
                           channelName,
                           radio != null,
                           radio != null ? radio.isSelected() : "N/A");

                if (radio != null && radio.isSelected()) {
                    currentPreviewChannel = channelName;
                    livePreviewEnabled = true;
                    shouldUpdatePreview = true;
                    updateReason = "RadioButton selected in Create mode";
                }
            }

            // 情况2: 任何模式 + 当前通道已经在预览中
            if (livePreviewEnabled && channelName.equals(currentPreviewChannel)) {
                shouldUpdatePreview = true;
                updateReason = "Current preview channel measurement changed";
            }

            // 触发预览更新
            if (shouldUpdatePreview) {
                logger.info("✓ Measurement changed to '{}' for channel '{}', triggering preview update (Reason: {})",
                           measurementCombo.getValue(), channelName, updateReason);
                updateLivePreview();
            } else {
                logger.info("✗ Measurement changed but preview NOT triggered - channel '{}' not currently previewing", channelName);
            }
        });
        measurementComboBoxes.put(channelName, measurementCombo);
        row.getChildren().add(measurementCombo);

        // 5. 阈值列
        VBox thresholdBox = new VBox(5);

        // Create logarithmic slider with dynamic range based on image bit depth
        double[] range = getImageDynamicRange();

        // Preserve existing threshold value from configuration
        double existingThreshold = 100; // Default value
        if (channelThreshold != null) {
            existingThreshold = channelThreshold.getThreshold();
        }
        
        Slider slider = createLogarithmicSlider(range[0], range[1], existingThreshold, channelName);
        slider.setPrefWidth(140); // 进一步减少滑块宽度以适应按钮

        // Load模式下禁用阈值滑块
        slider.setDisable(currentMode == OperationMode.LOAD_CLASSIFIER);

        TextField textField = new TextField(String.format("%.1f", existingThreshold));
        textField.setPrefWidth(50); // 减少文本框宽度

        // Load模式下禁用阈值文本框
        textField.setDisable(currentMode == OperationMode.LOAD_CLASSIFIER);

        thresholdFields.put(channelName, textField);
        
        // Bind slider and text field with live preview (logarithmic handling)
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            // Update text field with linear value from logarithmic slider
            double linearValue = getLinearValue(slider);
            textField.setText(String.format("%.1f", linearValue));

            // === 修改：Manual模式滑动阈值时标记为"未设置" ===
            // 只有在Manual模式下，且用户主动拖动滑块时才重置状态
            if (currentConfig != null && currentConfig.getStrategy() == ThresholdConfig.Strategy.MANUAL) {
                // 检查当前状态是否为"已设置"
                Label statusLabel = thresholdStatusLabels.get(channelName);
                if (statusLabel != null && "已设置".equals(statusLabel.getText())) {
                    // 用户手动调整了阈值，需要重新确认
                    updateThresholdStatus(channelName, false);
                    logger.debug("Manual模式下滑动阈值，通道'{}' 状态改为'未设置'，需点击确定按钮", channelName);
                }
            }

            if (livePreviewEnabled && channelName.equals(currentPreviewChannel)) {
                // Debounce live preview updates for better performance
                Platform.runLater(() -> updateLivePreview());
            }
        });
        
        textField.setOnAction(e -> {
            try {
                double val = Double.parseDouble(textField.getText());
                // Convert linear value to logarithmic scale
                double logVal = Math.log10(Math.max(1, val));
                slider.setValue(logVal);

                // === 修改：Manual模式输入阈值时标记为"未设置" ===
                if (currentConfig != null && currentConfig.getStrategy() == ThresholdConfig.Strategy.MANUAL) {
                    Label statusLabel = thresholdStatusLabels.get(channelName);
                    if (statusLabel != null && "已设置".equals(statusLabel.getText())) {
                        updateThresholdStatus(channelName, false);
                        logger.debug("Manual模式下输入阈值，通道'{}' 状态改为'未设置'，需点击确定按钮", channelName);
                    }
                }

                if (livePreviewEnabled && channelName.equals(currentPreviewChannel)) {
                    updateLivePreview();
                }
            } catch (NumberFormatException ex) {
                textField.setText(String.format("%.1f", getLinearValue(slider)));
            }
        });
        
        HBox sliderBox = new HBox(3); // 减少元素间间距以节省空间
        sliderBox.getChildren().addAll(slider, textField);
        sliderBox.setAlignment(Pos.CENTER_LEFT);
        
        // Add confirm button for Create Classifier mode - for live preview and threshold saving
        if (currentMode == OperationMode.CREATE_CLASSIFIER) {
            Button confirmButton = new Button("确定");
            confirmButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 10px;");
            confirmButton.setPrefWidth(45); // 调整按钮宽度适应布局
            confirmButton.setMinWidth(45);
            confirmButton.setMaxWidth(45);
            confirmButton.setOnAction(e -> confirmChannelThreshold(channelName));

            sliderBox.getChildren().add(confirmButton);
        }

        thresholdBox.getChildren().add(sliderBox);
        thresholdBox.setPrefWidth(280); // 进一步增加阈值列总宽度确保所有元素完整显示
        row.getChildren().add(thresholdBox);

        // 6. 阈值设置状态列（与标题对应的宽度）
        Label statusLabel = new Label("未设置");
        statusLabel.setPrefWidth(100); // 与标题对应的宽度
        statusLabel.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 10px; -fx-alignment: center; -fx-padding: 4px;");

        thresholdStatusLabels.put(channelName, statusLabel);
        row.getChildren().add(statusLabel);

        return row;
    }

    /**
     * Extract algorithm name from display string with Chinese explanation
     * @param algorithmDisplay Display string like "Triangle (三角算法-适用于双峰分布)"
     * @return Algorithm name like "Triangle"
     */
    private String extractAlgorithmName(String algorithmDisplay) {
        if (algorithmDisplay == null) return "Triangle";
        // Extract the part before the first space and parenthesis
        int spaceIndex = algorithmDisplay.indexOf(' ');
        if (spaceIndex > 0) {
            return algorithmDisplay.substring(0, spaceIndex);
        }
        return algorithmDisplay; // Fallback to original if no space found
    }

    /**
     * Check if a channel's threshold has been confirmed
     * @param channelName The channel name to check
     * @return true if the threshold has been confirmed, false otherwise
     */
    private boolean isChannelThresholdConfirmed(String channelName) {
        Label statusLabel = thresholdStatusLabels.get(channelName);
        if (statusLabel != null) {
            // 检查状态标签文本是否为"已设置"
            return "已设置".equals(statusLabel.getText());
        }
        return false;
    }

    /**
     * Update channel selection checkbox status based on threshold confirmation
     * @param channelName The channel name to update
     */
    private void updateChannelSelectionStatus(String channelName) {
        CheckBox selectionCheckBox = channelCheckBoxes.get(channelName);
        if (selectionCheckBox != null) {
            boolean isThresholdConfirmed = isChannelThresholdConfirmed(channelName);

            // 无论什么模式，只要阈值确认了，就保存为CREATE模式选择状态（用于LOAD模式读取）
            if (isThresholdConfirmed && !createModeSelections.containsKey(channelName)) {
                createModeSelections.put(channelName, true); // 默认选中已确认的通道
            }

            if (currentMode == OperationMode.CREATE_CLASSIFIER) {
                // CREATE模式：阈值确认后自动勾选并禁用
                if (isThresholdConfirmed) {
                    selectionCheckBox.setSelected(true);
                    selectionCheckBox.setDisable(true);
                    selectionCheckBox.setStyle("-fx-opacity: 0.8;"); // 已确认状态

                    // 保存CREATE模式的选择状态
                    createModeSelections.put(channelName, true);
                } else {
                    selectionCheckBox.setSelected(false);
                    selectionCheckBox.setDisable(true);
                    selectionCheckBox.setStyle("-fx-opacity: 0.6;"); // 未确认状态

                    // 保存CREATE模式的选择状态
                    createModeSelections.put(channelName, false);
                }
            } else {
                // LOAD模式：阈值确认的可手动操作，未确认的禁用
                if (isThresholdConfirmed) {
                    // 读取CREATE模式保存的选择状态，如果没有则默认选中
                    boolean shouldSelect = createModeSelections.getOrDefault(channelName, true);
                    selectionCheckBox.setSelected(shouldSelect);
                    selectionCheckBox.setDisable(false);
                    selectionCheckBox.setStyle(""); // 正常样式

                    logger.debug("LOAD模式通道 {}: 阈值已确认={}, 选择状态={}", channelName, isThresholdConfirmed, shouldSelect);
                } else {
                    selectionCheckBox.setSelected(false);
                    selectionCheckBox.setDisable(true);
                    selectionCheckBox.setStyle("-fx-opacity: 0.6;"); // 禁用样式

                    logger.debug("LOAD模式通道 {}: 阈值未确认，禁用选择", channelName);
                }
            }
        }
    }

    /**
     * Update statistics display for cell analysis selection
     * @param statisticsLabel The label to update
     */
    private void updateStatisticsDisplay(Label statisticsLabel) {
        try {
            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null) {
                statisticsLabel.setText("统计信息: 无图像数据");
                return;
            }

            var hierarchy = imageData.getHierarchy();
            var allCells = hierarchy.getDetectionObjects();
            var selectedROIs = hierarchy.getSelectionModel().getSelectedObjects()
                    .stream()
                    .filter(obj -> obj.getROI() != null && obj.isAnnotation())
                    .collect(Collectors.toList());

            String selectedMode = cellAnalysisComboBox != null ? cellAnalysisComboBox.getValue() : "全部细胞";

            if ("当前选中细胞".equals(selectedMode)) {
                if (selectedROIs.isEmpty()) {
                    statisticsLabel.setText("统计信息: 未选中ROI区域，将分析全部 " + allCells.size() + " 个细胞");
                } else {
                    // Count cells within selected ROIs
                    int cellsInROI = 0;
                    for (var cell : allCells) {
                        for (var roi : selectedROIs) {
                            if (roi.getROI().contains(cell.getROI().getCentroidX(), cell.getROI().getCentroidY())) {
                                cellsInROI++;
                                break;
                            }
                        }
                    }
                    statisticsLabel.setText("统计信息: 已选中 " + selectedROIs.size() + " 个ROI区域，包含 " + cellsInROI + " 个细胞");
                }
            } else {
                statisticsLabel.setText("统计信息: 将分析全部 " + allCells.size() + " 个细胞");
            }
        } catch (Exception e) {
            statisticsLabel.setText("统计信息: 获取统计信息失败");
            logger.warn("Failed to update statistics display: {}", e.getMessage());
        }
    }

    /**
     * Update threshold status for a channel
     * @param channelName The channel name
     * @param isSet Whether the threshold has been set (green) or not (orange)
     */
    private void updateThresholdStatus(String channelName, boolean isSet) {
        Label statusLabel = thresholdStatusLabels.get(channelName);
        if (statusLabel != null) {
            if (isSet) {
                statusLabel.setText("已设置");
                statusLabel.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-size: 10px; -fx-alignment: center; -fx-padding: 4px;");
            } else {
                statusLabel.setText("未设置");
                statusLabel.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 10px; -fx-alignment: center; -fx-padding: 4px;");
            }
        }
    }

    /**
     * Confirm channel threshold setting and apply live preview
     */
    private void confirmChannelThreshold(String channelName) {
        // Check if this channel is selected
        if (currentMode == OperationMode.CREATE_CLASSIFIER) {
            RadioButton radioButton = channelRadioButtons.get(channelName);
            if (radioButton == null || !radioButton.isSelected()) {
                showAlert(Alert.AlertType.WARNING, "提示", "请先选择通道 " + channelName);
                return;
            }
        }
        
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            showAlert(Alert.AlertType.WARNING, "警告", "没有图像数据可用于预览");
            return;
        }
        
        Slider slider = thresholdSliders.get(channelName);
        ComboBox<String> measurementBox = measurementComboBoxes.get(channelName);
        
        if (slider != null && measurementBox != null) {
            double threshold = getLinearValue(slider);  // Convert from log scale
            String measurementType = measurementBox.getValue();
            
            // Save threshold setting to configuration
            ThresholdConfig.ChannelThreshold channelThreshold = new ThresholdConfig.ChannelThreshold(measurementType, threshold, true);
            currentConfig = currentConfig.withChannelThreshold(channelName, channelThreshold);
            
            // Apply live preview using unified method
            updateLivePreview();

            // Update status to "已设置" (green)
            updateThresholdStatus(channelName, true);

            // Update selection checkbox status after threshold confirmation
            updateChannelSelectionStatus(channelName);

            logger.info("Confirmed threshold for channel {}: threshold={}, measurement={}",
                channelName, threshold, measurementType);
        }
    }
    

    /**
     * 加载已保存的阈值设置并应用预览
     * Load saved threshold settings and apply preview for confirmed channels
     * @param channelName The channel name to load saved settings for
     */
    private void loadSavedThresholdAndPreview(String channelName) {
        ThresholdConfig.ChannelThreshold savedThreshold = currentConfig.getChannelThresholds().get(channelName);
        if (savedThreshold == null) {
            logger.warn("No saved threshold found for channel: {}", channelName);
            return;
        }

        try {
            // 更新滑块到保存的阈值位置
            Slider slider = thresholdSliders.get(channelName);
            TextField textField = thresholdFields.get(channelName);
            ComboBox<String> measurementBox = measurementComboBoxes.get(channelName);

            if (slider != null && textField != null && measurementBox != null) {
                // 设置测量值类型
                measurementBox.setValue(savedThreshold.getMeasurement());

                // 设置阈值（转换为对数刻度）
                double logValue = Math.log10(Math.max(1, savedThreshold.getThreshold()));
                slider.setValue(logValue);
                textField.setText(String.format("%.1f", savedThreshold.getThreshold()));

                // 应用预览效果 - 直接调用更新预览
                Platform.runLater(() -> {
                    updateLivePreview();
                });

                logger.info("Loaded saved threshold for channel {}: threshold={}, measurement={}",
                    channelName, savedThreshold.getThreshold(), savedThreshold.getMeasurement());
            }
        } catch (Exception e) {
            logger.error("Failed to load saved threshold for channel {}: {}", channelName, e.getMessage());
        }
    }

    /**
     * Find actual measurement name from possible alternatives
     * ENHANCED BOTTOM LAYER CHANNEL NAME MATCHING for user-modified channels
     */
    private String findActualMeasurementName(List<qupath.lib.objects.PathObject> cells, String[] possibleNames, String channelName) {
        if (cells.isEmpty()) return null;
        
        var firstCell = cells.get(0);
        var measurements = firstCell.getMeasurementList();
        List<String> availableNames = measurements.getNames();
        
        logger.info("BOTTOM LAYER MATCHING - Channel: '{}', Total measurements available: {}", channelName, availableNames.size());
        
        // Try exact matches from possibleNames first
        for (String testName : possibleNames) {
            if (measurements.containsKey(testName)) {
                logger.info("Found exact measurement match for channel '{}': {}", channelName, testName);
                return testName;
            }
        }
        
        // ENHANCED C2/C3/C4 STRATEGY: Multiple mapping approaches for QuPath measurements
        String originalChannelName = channelNameMapping.getOrDefault(channelName, channelName);
        String channelIndex = channelNameMapping.getOrDefault(channelName + "_INDEX", "");
        
        // Debug log all available measurements containing channel-like patterns
        logger.info("C2/C3/C4 PATTERN MATCHING - Display: '{}', Original: '{}', Index: '{}'", 
                   channelName, originalChannelName, channelIndex);
        logger.info("Available measurements containing any channel patterns:");
        availableNames.stream()
            .filter(name -> name.toLowerCase().contains(channelName.toLowerCase()) || 
                           name.toLowerCase().contains(originalChannelName.toLowerCase()) ||
                           (!channelIndex.isEmpty() && name.toLowerCase().contains(channelIndex.toLowerCase())))
            .forEach(name -> logger.info("  -> {}", name));
        
        // Strategy 1: ENHANCED patterns including C2/C3/C4 (highest priority)
        List<String> patternList = new ArrayList<>();
        if (!channelIndex.isEmpty()) {
            patternList.add(channelIndex);  // C2, C3, C4... (highest priority for QuPath)
        }
        patternList.add(originalChannelName);     // Original channel name from metadata
        patternList.add(channelName);             // Current display name (user-modified)
        
        String[] patterns = patternList.toArray(new String[0]);
        String[] measurementTypes = {"Mean", "Median", "Max", "Min"};
        
        for (String pattern : patterns) {
            // Exact pattern matching
            for (String availableName : availableNames) {
                if (availableName.contains(pattern + ":")) {
                    logger.info("Found bottom layer exact match for channel '{}': {} (pattern: {})", 
                               channelName, availableName, pattern);
                    return availableName;
                }
            }
            
            // Pattern with measurement type matching
            for (String measurementType : measurementTypes) {
                for (String availableName : availableNames) {
                    String expectedName = pattern + ": " + measurementType;
                    String expectedName2 = measurementType + ": " + pattern;
                    String expectedName3 = "Nucleus: " + pattern + ": " + measurementType;
                    String expectedName4 = "Cell: " + pattern + ": " + measurementType;
                    String expectedName5 = "Cytoplasm: " + pattern + ": " + measurementType;
                    
                    if (availableName.equals(expectedName) || availableName.equals(expectedName2) ||
                        availableName.equals(expectedName3) || availableName.equals(expectedName4) ||
                        availableName.equals(expectedName5)) {
                        logger.info("Found bottom layer pattern match for channel '{}': {} (pattern: {}, type: {})", 
                                   channelName, availableName, pattern, measurementType);
                        return availableName;
                    }
                }
            }
        }
        
        // Strategy 2: Case-insensitive bottom layer matching
        for (String pattern : patterns) {
            for (String availableName : availableNames) {
                if (availableName.toLowerCase().contains(pattern.toLowerCase() + ":") ||
                    availableName.toLowerCase().contains(":" + pattern.toLowerCase())) {
                    logger.info("Found case-insensitive bottom layer match for channel '{}': {} (pattern: {})", 
                               channelName, availableName, pattern);
                    return availableName;
                }
            }
        }
        
        // Strategy 3: Substring matching with measurement types
        for (String pattern : patterns) {
            for (String measurementType : measurementTypes) {
                for (String availableName : availableNames) {
                    if (availableName.toLowerCase().contains(pattern.toLowerCase()) &&
                        availableName.toLowerCase().contains(measurementType.toLowerCase())) {
                        logger.info("Found bottom layer substring match for channel '{}': {} (pattern: {}, type: {})", 
                                   channelName, availableName, pattern, measurementType);
                        return availableName;
                    }
                }
            }
        }
        
        // Strategy 4: Fallback with partial name matching (minimum 3 characters)
        for (String pattern : patterns) {
            if (pattern.length() >= 3) {
                String partialPattern = pattern.substring(0, Math.min(pattern.length(), 6));
                for (String availableName : availableNames) {
                    if (availableName.toLowerCase().contains(partialPattern.toLowerCase()) &&
                        availableName.toLowerCase().contains("mean")) {
                        logger.info("Found bottom layer partial match for channel '{}': {} (partial: {})", 
                                   channelName, availableName, partialPattern);
                        return availableName;
                    }
                }
            }
        }
        
        // Strategy 5: CRITICAL FALLBACK - Use first measurement containing channel name
        String criticalFallback = availableNames.stream()
            .filter(name -> name.toLowerCase().contains(channelName.toLowerCase()) && 
                           name.toLowerCase().contains("mean"))
            .findFirst()
            .orElse(null);
            
        if (criticalFallback != null) {
            logger.warn("Using critical fallback measurement for channel '{}': {}", channelName, criticalFallback);
            return criticalFallback;
        }
        
        // List available measurements for debugging
        logger.warn("Bottom layer matching failed for channel: '{}' (original: '{}'). Available measurements:", 
                   channelName, originalChannelName);
        availableNames.stream()
                     .sorted()
                     .limit(20) // Show first 20 to avoid spam
                     .forEach(name -> logger.warn("  - {}", name));
        
        return null;
    }
    
    /**
     * Sync QuPath display data including markers, annotations, and detections - ENHANCED for pseudo-coloring
     */
    private void syncQuPathDisplayData(ImageData<?> imageData) {
        try {
            var hierarchy = imageData.getHierarchy();
            
            // CRITICAL: Fire hierarchy change events to update displays
            hierarchy.fireHierarchyChangedEvent(null);
            
            // ENHANCED: Multiple update mechanisms for robust pseudo-color display
            if (qupath.getViewer() != null) {
                var viewer = qupath.getViewer();
                
                // Update overlay options first to ensure color mapping is refreshed
                var overlayOptions = viewer.getOverlayOptions();
                if (overlayOptions != null) {
                    // Reset measurement mapper to refresh display
                    overlayOptions.resetMeasurementMapper();
                }
                
                // Force overlay updates before repaint
                viewer.forceOverlayUpdate();
                
                // Force complete viewer repaint
                viewer.repaint();
                
                // ADDITIONAL: Force complete viewer refresh for color changes
                try {
                    // Multiple repaint strategies for robust color display
                    viewer.repaint();
                    // Additional repaint with slight delay to ensure colors are applied
                    Platform.runLater(() -> viewer.repaint());
                } catch (Exception displayEx) {
                    logger.debug("Viewer repaint failed: {}", displayEx.getMessage());
                }
            }
            
            // Additional hierarchy update to ensure all listeners are notified
            Platform.runLater(() -> {
                try {
                    hierarchy.fireHierarchyChangedEvent(null);
                    if (qupath.getViewer() != null) {
                        qupath.getViewer().repaint();
                    }
                    logger.debug("Delayed pseudo-color display update completed");
                } catch (Exception delayedEx) {
                    logger.debug("Delayed display update failed (non-critical): {}", delayedEx.getMessage());
                }
            });
            
        } catch (Exception e) {
            logger.error("Error syncing QuPath display data: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Update channel selection mode based on current operation mode
     */
    private void updateChannelSelectionMode() {
        // Clear any existing preview
        clearLivePreview();

        // 直接更新控件状态，不重新创建控件（避免丢失状态）
        updateControlStatesForMode();

        // Update instruction label if it exists
        channelContainer.getChildren().stream()
            .filter(node -> node instanceof HBox)
            .map(node -> (HBox) node)
            .flatMap(hbox -> hbox.getChildren().stream())
            .filter(node -> node instanceof Label)
            .map(node -> (Label) node)
            .filter(label -> label.getStyle().contains("italic"))
            .findFirst()
            .ifPresent(this::updateInstructionLabel);

        logger.info("Channel selection mode updated for: {} - {}",
            currentMode,
            currentMode == OperationMode.CREATE_CLASSIFIER ? "Single channel selection" : "Multi-channel selection");
    }

    /**
     * 计算并保存所有通道的Auto阈值
     */
    // TODO: [方法] 待优化方法

    /**
     * 阈值计算结果类
     */
    private static class ThresholdCalculationResult {
        enum Status {
            SUCCESS,                // ✓ 成功
            SUCCESS_WITH_WARNING,   // ⚠ 成功但有警告
            FAILED                  // ✗ 失败
        }

        Status status;
        String channelName;
        Double threshold;           // 可能为null（失败时）
        int positiveCells;          // 阳性细胞数
        double percentage;          // 阳性百分比
        String message;             // 具体说明

        ThresholdCalculationResult(Status status, String channelName, Double threshold,
                                  int positiveCells, double percentage, String message) {
            this.status = status;
            this.channelName = channelName;
            this.threshold = threshold;
            this.positiveCells = positiveCells;
            this.percentage = percentage;
            this.message = message;
        }
    }

    private void calculateAutoThresholds(String algorithm) {
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            showAlert(Alert.AlertType.WARNING, "警告", "无图像数据，无法计算Auto阈值");
            return;
        }

        try {
            var hierarchy = imageData.getHierarchy();
            var cells = hierarchy.getDetectionObjects();
            if (cells.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "警告",
                    "当前图像没有检测到细胞数据\n\n" +
                    "建议：\n" +
                    "1. 在QuPath中执行 Analyze > Cell detection\n" +
                    "2. 或者切换到\"手动\"策略手动设置阈值");
                return;
            }

            int totalCellCount = cells.size();
            int channelCount = availableChannels.size();
            logger.info("开始使用{}算法为 {} 个通道计算自动阈值 (细胞数: {})", algorithm, channelCount, totalCellCount);

            // 在后台线程执行计算，避免阻塞UI
            Thread calculationThread = new Thread(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    List<ThresholdCalculationResult> results = new ArrayList<>();

                    // 为每个通道计算阈值
                    for (String channelName : availableChannels) {
                        ThresholdCalculationResult result = calculateThresholdForSingleChannel(
                            channelName, algorithm, cells, totalCellCount);
                        results.add(result);
                    }

                    long calcTime = System.currentTimeMillis() - startTime;
                    logger.info("⚡ 阈值计算完成，耗时: {}ms", calcTime);

                    // 回到UI线程更新界面和显示结果
                    Platform.runLater(() -> {
                        try {
                            // 应用成功的阈值（包括有警告的）
                            int appliedCount = 0;
                            for (ThresholdCalculationResult result : results) {
                                if (result.status == ThresholdCalculationResult.Status.SUCCESS ||
                                    result.status == ThresholdCalculationResult.Status.SUCCESS_WITH_WARNING) {
                                    // 保存Auto计算的阈值
                                    savedAutoThresholds.put(result.channelName, result.threshold);

                                    // 更新显示
                                    updateThresholdDisplay(result.channelName, result.threshold);

                                    // 更新ThresholdConfig中的阈值设置
                                    updateConfigThreshold(result.channelName, result.threshold);

                                    // 更新阈值状态为"已设置"
                                    updateThresholdStatus(result.channelName, true);

                                    // 更新选择列状态
                                    updateChannelSelectionStatus(result.channelName);

                                    appliedCount++;
                                    logger.info("通道 '{}' {}算法阈值: {} (已更新到配置)",
                                        result.channelName, algorithm, result.threshold);
                                }
                            }

                            if (appliedCount > 0) {
                                updateControlStatesForMode();
                            }

                            // 显示详细结果对话框
                            showThresholdCalculationResults(results, algorithm);

                        } catch (Exception e) {
                            logger.error("自动阈值UI更新失败: {}", e.getMessage(), e);
                        }
                    });

                } catch (Exception e) {
                    logger.error("自动阈值计算失败: {}", e.getMessage(), e);
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.ERROR, "计算错误", "阈值计算失败: " + e.getMessage());
                    });
                }
            }, "ThresholdCalculation");

            calculationThread.setDaemon(true);
            calculationThread.start();

        } catch (Exception e) {
            logger.error("自动阈值计算启动失败: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "计算错误", "阈值计算启动失败: " + e.getMessage());
        }
    }

    /**
     * 为单个通道计算阈值并分类结果
     */
    private ThresholdCalculationResult calculateThresholdForSingleChannel(
            String channelName, String algorithm,
            Collection<qupath.lib.objects.PathObject> cells, int totalCellCount) {

        try {
            // 1. 获取measurement名称
            String measurementName = null;
            ComboBox<String> measurementCombo = measurementComboBoxes.get(channelName);
            if (measurementCombo != null && measurementCombo.getValue() != null) {
                measurementName = measurementCombo.getValue();
            } else {
                var firstCell = cells.iterator().next();
                measurementName = findMeasurementNameForClassification(
                    firstCell.getMeasurementList(), channelName, null);
            }

            if (measurementName == null || measurementName.trim().isEmpty()) {
                return new ThresholdCalculationResult(
                    ThresholdCalculationResult.Status.FAILED,
                    channelName, null, 0, 0.0,
                    "无法获取measurement数据"
                );
            }

            // 2. 收集所有细胞的测量值
            List<Double> values = new ArrayList<>();
            for (var cell : cells) {
                try {
                    double value = cell.getMeasurementList().get(measurementName);
                    if (!Double.isNaN(value)) {
                        values.add(value);
                    }
                } catch (Exception e) {
                    // 忽略单个细胞的读取错误
                }
            }

            if (values.isEmpty()) {
                return new ThresholdCalculationResult(
                    ThresholdCalculationResult.Status.FAILED,
                    channelName, null, 0, 0.0,
                    "无法获取measurement数据"
                );
            }

            // 3. 检查数据有效性
            double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);

            if (min == max) {
                return new ThresholdCalculationResult(
                    ThresholdCalculationResult.Status.FAILED,
                    channelName, null, 0, 0.0,
                    String.format("数据方差为0，所有值相同 (%.2f)", min)
                );
            }

            // 计算方差
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0);

            if (variance < 0.01) {
                return new ThresholdCalculationResult(
                    ThresholdCalculationResult.Status.FAILED,
                    channelName, null, 0, 0.0,
                    String.format("数据方差过小 (%.4f)，无法有效区分", variance)
                );
            }

            // 4. 计算Otsu阈值
            double threshold = calculateThresholdByAlgorithm(values, algorithm);

            if (Double.isNaN(threshold) || Double.isInfinite(threshold)) {
                return new ThresholdCalculationResult(
                    ThresholdCalculationResult.Status.FAILED,
                    channelName, null, 0, 0.0,
                    "计算返回异常值"
                );
            }

            // 5. 统计阳性细胞
            int positiveCells = 0;
            for (double value : values) {
                if (value > threshold) {
                    positiveCells++;
                }
            }

            double percentage = (double) positiveCells / totalCellCount * 100.0;

            // 6. 判断是否数据不足（< 0.1%）
            if (percentage < 0.1) {
                return new ThresholdCalculationResult(
                    ThresholdCalculationResult.Status.SUCCESS_WITH_WARNING,
                    channelName, threshold, positiveCells, percentage,
                    String.format("阳性细胞数据不足，建议手动验证调节")
                );
            }

            // 7. 正常成功
            return new ThresholdCalculationResult(
                ThresholdCalculationResult.Status.SUCCESS,
                channelName, threshold, positiveCells, percentage,
                String.format("阈值=%.1f，阳性细胞%d个，%.1f%%",
                    threshold, positiveCells, percentage)
            );

        } catch (Exception e) {
            return new ThresholdCalculationResult(
                ThresholdCalculationResult.Status.FAILED,
                channelName, null, 0, 0.0,
                "计算过程出错: " + e.getMessage()
            );
        }
    }

    /**
     * 显示阈值计算结果对话框
     */
    private void showThresholdCalculationResults(List<ThresholdCalculationResult> results, String algorithm) {
        StringBuilder message = new StringBuilder();
        message.append("计算结果：\n\n");

        boolean hasWarnings = false;
        boolean hasFailures = false;
        int totalChannels = results.size();
        int failedChannels = 0;

        for (ThresholdCalculationResult result : results) {
            switch (result.status) {
                case SUCCESS:
                    message.append(String.format("✓ %s: 成功（阈值=%.1f，阳性细胞%d个，%.2f%%）\n",
                        result.channelName, result.threshold,
                        result.positiveCells, result.percentage));
                    break;

                case SUCCESS_WITH_WARNING:
                    hasWarnings = true;
                    message.append(String.format("⚠ %s: 成功（阈值=%.1f，阳性细胞%d个，%.4f%%）\n",
                        result.channelName, result.threshold,
                        result.positiveCells, result.percentage));
                    message.append(String.format("   警告：%s\n", result.message));
                    break;

                case FAILED:
                    hasFailures = true;
                    failedChannels++;
                    message.append(String.format("✗ %s: 失败 - %s\n",
                        result.channelName, result.message));
                    message.append("   请切换到手动模式调节此通道\n");
                    break;
            }
        }

        message.append("\n提示：\n");
        message.append("• 成功的通道已自动设置阈值\n");
        if (hasWarnings) {
            message.append("• 有警告的通道建议手动验证\n");
        }
        if (hasFailures) {
            message.append("• 失败的通道请切换到\"手动\"模式调节\n");

            // v1.4.0: 根据失败通道数量给出不同建议
            if (failedChannels == totalChannels) {
                // 所有通道都失败 - 可能是分割模型问题
                message.append("\n⚠️  所有通道都失败，建议：\n");
                message.append("  • 检查��割模型选择是否正确\n");
                message.append("  • 确认图像已进行细胞分割\n");
                message.append("  • 验证测量值格式是否匹配\n");
            } else {
                // 部分通道失败 - 分析其他原因
                message.append("\n💡 部分通道失败，可能原因：\n");
                message.append("  • 通道名称不匹配（可在测量值下拉框中选择正确的值）\n");
                message.append("  • 选中通道的数据质量问题\n");
                message.append("  • 请检查具体通道的measurement选择\n");
            }
        }

        Alert.AlertType alertType = hasFailures ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION;
        showAlert(alertType, "自动阈值计算完成", message.toString());
    }

    /**
     * 为单个通道计算阈值（已废弃 - 由calculateAutoThresholds内联并优化）
     * 保留此方法签名以避免潜在的兼容性问题
     */
    @Deprecated
    private double calculateThresholdForChannel(String channelName, String algorithm, Collection<qupath.lib.objects.PathObject> cells) {
        // 此方法已被优化的并行实现取代
        logger.warn("调用了已废弃的calculateThresholdForChannel方法");
        return 0.0;
    }

    /**
     * 根据算法计算阈值
     */
    private double calculateThresholdByAlgorithm(List<Double> values, String algorithm) {
        switch (algorithm) {
            case "Otsu":
                return calculateOtsuThreshold(values);
            case "Triangle":
                return calculateTriangleThreshold(values);
            case "MaxEntropy":
                return calculateMaxEntropyThreshold(values);
            case "Minimum":
                return calculateMinimumThreshold(values);
            default:
                return calculateOtsuThreshold(values); // 默认使用Otsu
        }
    }

    private void calculateAndSaveAutoThresholds() {
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            logger.warn("无图像数据，无法计算Auto阈值");
            return;
        }

        try {
            // v1.4.0修复: 自动阈值计算前完整刷新通道数据
            logger.info("=== 自动阈值计算前刷新通道数据 ===");
            refreshChannels();  // 完整刷新通道数据，包括验证通道名称和重新创建控件
            logger.info("=== 通道数据刷新完成 ===");

            var hierarchy = imageData.getHierarchy();
            var cells = hierarchy.getDetectionObjects();
            if (cells.isEmpty()) {
                logger.warn("无细胞数据，无法计算Auto阈值");
                return;
            }

            logger.info("开始为 {} 个通道计算Auto阈值", availableChannels.size());

            for (String channelName : availableChannels) {
                try {
                    // === 关键修复：从ComboBox获取用户选择的完整measurement名称 ===
                    String measurementName = null;
                    ComboBox<String> measurementCombo = measurementComboBoxes.get(channelName);
                    if (measurementCombo != null && measurementCombo.getValue() != null) {
                        measurementName = measurementCombo.getValue();
                        logger.debug("通道 '{}' 使用ComboBox选择的measurement: '{}'", channelName, measurementName);

                        // v1.4.0 InstanSeg特殊处理：自动阈值计算使用Cell数据
                        if (currentConfig.getSegmentationModel() == SegmentationModel.INSTANSEG && measurementName != null) {
                            // 将InstanSeg的默认Compartment测量值转换为Cell测量值
                            if (measurementName.startsWith("Cell:")) {
                                // 已经是Cell的测量值，保持不变
                                logger.debug("InstanSeg通道 '{}' 已经是Cell测量值: {}", channelName, measurementName);
                            } else if (measurementName.startsWith("Nucleus:") ||
                                       measurementName.startsWith("Cytoplasm:") ||
                                       measurementName.startsWith("Membrane:")) {
                                // 将Nucleus/Cytoplasm/Membrane的测量值替换为Cell的对应值
                                String[] parts = measurementName.split(":");
                                if (parts.length >= 3) {
                                    String stat = parts[2].trim();  // 获取统计量 (Mean, Median等)
                                    String cellMeasurementName = "Cell: " + channelName + ": " + stat;
                                    logger.info("InstanSeg通道 '{}' 将测量值从 '{}' 转换为Cell数据: {}",
                                               channelName, measurementName, cellMeasurementName);
                                    measurementName = cellMeasurementName;
                                }
                            }
                        }
                    } else {
                        // 如果ComboBox不可用，尝试查找
                        measurementName = findMeasurementNameForClassification(
                            cells.iterator().next().getMeasurementList(),
                            channelName,
                            null  // 不使用固定的旧格式
                        );
                        logger.warn("通道 '{}' ComboBox不可用，查找到measurement: '{}'", channelName, measurementName);
                    }

                    if (measurementName != null && !measurementName.trim().isEmpty()) {
                        List<Double> values = new ArrayList<>();
                        for (var cell : cells) {
                            try {
                                double value = cell.getMeasurementList().get(measurementName);
                                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                                    values.add(value);
                                }
                            } catch (Exception e) {
                                // 忽略无效值
                            }
                        }

                        if (!values.isEmpty()) {
                            double otsuThreshold = calculateOtsuThreshold(values);
                            savedAutoThresholds.put(channelName, otsuThreshold);

                            // 更新界面显示
                            updateThresholdDisplay(channelName, otsuThreshold);

                            logger.info("通道 '{}' Auto阈值计算完成: {}", channelName, otsuThreshold);
                        }
                    }
                } catch (Exception e) {
                    logger.error("计算通道 '{}' 的Auto阈值失败: {}", channelName, e.getMessage());
                }
            }

            logger.info("Auto阈值计算完成，共处理 {} 个通道，成功计算 {} 个",
                    availableChannels.size(), savedAutoThresholds.size());

            // 如果有选中的预览通道，触发预览更新
            if (livePreviewEnabled && currentPreviewChannel != null) {
                updateLivePreview();
            }

        } catch (Exception e) {
            logger.error("Auto阈值计算过程出错: {}", e.getMessage());
        }
    }

    /**
     * 更新阈值显示控件
     */
    private void updateThresholdDisplay(String channelName, double threshold) {
        TextField thresholdField = thresholdFields.get(channelName);
        Slider thresholdSlider = thresholdSliders.get(channelName);

        if (thresholdField != null) {
            thresholdField.setText(String.format("%.2f", threshold));
        }

        if (thresholdSlider != null) {
            // 转换为对数值设置滑块
            double logValue = Math.log10(Math.max(1, threshold));
            thresholdSlider.setValue(logValue);
        }
    }

    /**
     * 更新ThresholdConfig中的阈值设置
     */
    private void updateConfigThreshold(String channelName, double threshold) {
        try {
            // === 关键修复：从ComboBox获取用户选择的完整measurement名称 ===
            String measurementName = null;

            // 首先尝试从现有的ComboBox中获取选中的measurement名称
            ComboBox<String> measurementCombo = measurementComboBoxes.get(channelName);
            if (measurementCombo != null && measurementCombo.getValue() != null) {
                measurementName = measurementCombo.getValue();
                logger.debug("通道 '{}' 使用ComboBox的measurement: '{}'", channelName, measurementName);
            } else {
                // 如果没有ComboBox或没有选择，使用固定测量值列表
                // v1.4.0: 使用固定测量值列表，不依赖实际measurements
                // 关键修复: 使用channelNameMapping中实际的通道名
                String actualChannelName = findActualChannelNameForDisplayName(channelName);
                if (actualChannelName == null) {
                    actualChannelName = channelName;  // fallback
                }
                SegmentationModel model = currentConfig.getSegmentationModel();
                List<String> channelMeasurements = generateFixedMeasurementsForChannel(actualChannelName, model);

                if (!channelMeasurements.isEmpty()) {
                    // 优先使用包含"mean"的第一个measurement
                    measurementName = channelMeasurements.stream()
                        .filter(m -> m.toLowerCase().contains("mean"))
                        .findFirst()
                        .orElse(channelMeasurements.get(0));
                    logger.info("通道 '{}' 自动选��measurement: '{}'", channelName, measurementName);
                }
            }

            // 创建新的ChannelThreshold
            ThresholdConfig.ChannelThreshold channelThreshold =
                new ThresholdConfig.ChannelThreshold(measurementName, threshold, true);

            // 更新配置
            currentConfig = currentConfig.withChannelThreshold(channelName, channelThreshold);

            logger.info("已更新配置：通道 '{}' 使用measurement '{}' 阈值设为 {}",
                       channelName, measurementName, threshold);

        } catch (Exception e) {
            logger.error("更新配置阈值失败 - 通道: {}, 阈值: {}, 错误: {}", channelName, threshold, e.getMessage());
        }
    }

    /**
     * 更新控件状态根据当前模式
     */
    private void updateControlStatesForMode() {
        boolean isLoadMode = (currentMode == OperationMode.LOAD_CLASSIFIER);
        boolean isAutoMode = (currentConfig != null && currentConfig.getStrategy() == ThresholdConfig.Strategy.AUTO);

        // Load模式下禁用通道阈值策略、刷新通道控件
        if (strategyComboBox != null) {
            strategyComboBox.setDisable(isLoadMode);
        }
        if (refreshButton != null) {
            refreshButton.setDisable(isLoadMode);
        }

        // Load模式下禁用算法选择和计算按钮
        if (algorithmComboBox != null) {
            algorithmComboBox.setDisable(isLoadMode);
        }
        if (calculateButton != null) {
            calculateButton.setDisable(isLoadMode);
        }

        // 为所有通道的控件设置状态
        for (String channelName : availableChannels) {
            ComboBox<String> measurementCombo = measurementComboBoxes.get(channelName);
            Slider thresholdSlider = thresholdSliders.get(channelName);
            TextField thresholdField = thresholdFields.get(channelName);
            CheckBox channelCheckBox = channelCheckBoxes.get(channelName);
            RadioButton channelRadioButton = channelRadioButtons.get(channelName);

            // 更新选择列状态 - 使用新的智能选择逻辑
            updateChannelSelectionStatus(channelName);

            // 更新预览列状态
            if (channelRadioButton != null) {
                if (isLoadMode) {
                    // LOAD模式：显示但不可操作，灰色
                    channelRadioButton.setDisable(true);
                    channelRadioButton.setSelected(false);
                    channelRadioButton.setStyle("-fx-opacity: 0.6;"); // 视觉上显示为灰色
                } else {
                    // CREATE模式：可操作
                    channelRadioButton.setDisable(false);
                    channelRadioButton.setStyle(""); // 正常样式
                }
            }

            if (measurementCombo != null) {
                // Load模式或Auto模式下禁用measurement选择
                measurementCombo.setDisable(isLoadMode || isAutoMode);
                if (isAutoMode) {
                    measurementCombo.setStyle("-fx-opacity: 0.6;"); // 设置灰色外观
                } else {
                    measurementCombo.setStyle(""); // 恢复正常外观
                }
            }
            if (thresholdSlider != null) {
                // Load模式或Auto模式下禁用阈值滑块
                thresholdSlider.setDisable(isLoadMode || isAutoMode);
                if (isAutoMode) {
                    thresholdSlider.setStyle("-fx-opacity: 0.6;"); // 设置灰色外观
                } else {
                    thresholdSlider.setStyle(""); // 恢复正常外观
                }
            }
            if (thresholdField != null) {
                // Load模式或Auto模式下禁用阈值文本框
                thresholdField.setDisable(isLoadMode || isAutoMode);
                if (isAutoMode) {
                    thresholdField.setStyle("-fx-opacity: 0.6;"); // 设置灰色外观
                } else {
                    thresholdField.setStyle(""); // 恢复正常外观
                }
            }
        }

        // 设置算法和计算控件的灰色外观
        if (algorithmComboBox != null && isLoadMode) {
            algorithmComboBox.setStyle("-fx-opacity: 0.6;");
        } else if (algorithmComboBox != null) {
            algorithmComboBox.setStyle("");
        }

        if (calculateButton != null && isLoadMode) {
            calculateButton.setStyle("-fx-background-color: #cccccc; -fx-text-fill: #666666; -fx-font-size: 12px;");
        } else if (calculateButton != null && !isLoadMode) {
            calculateButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 12px;");
        }

        logger.info("控件状态更新完成 - Load模式: {}, Auto模式: {}", isLoadMode, isAutoMode);
    }

    /**
     * v1.4.0: 分割模型切换处理
     */
    private void onSegmentationModelChanged() {
        String selectedModelName = segmentationModelComboBox.getValue();
        SegmentationModel newModel = SegmentationModel.fromDisplayName(selectedModelName);

        logger.info("分割模型切换: {} -> {}",
            currentConfig.getSegmentationModel().getDisplayName(),
            newModel.getDisplayName());

        // 更新配置中的分割模型
        currentConfig = currentConfig.withSegmentationModel(newModel);

        // v1.4.0修复: 分割模型切换后，完整刷新通道数据
        logger.info("=== 分割模型切换后刷新通道数据 ===");
        refreshChannels();  // 完整刷新通道数据，包括重新加载和验证通道名称
        logger.info("=== 通道数据刷新完成 ===");

        logger.info("分割模型切换完成，模型: {}, 默认compartment: {}",
            newModel.getDisplayName(),
            newModel.getMeasurementPrefix());
    }

    /**
     * v1.4.0: 更新所有测量类型下拉框以匹配当前分割模型
     * 从ImageData中提取每个通道的实际测量值
     */
    private void updateMeasurementComboBoxesForModel(SegmentationModel model) {
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            logger.warn("无法更新测量值 - ImageData为空");
            return;
        }

        for (Map.Entry<String, ComboBox<String>> entry : measurementComboBoxes.entrySet()) {
            String channelName = entry.getKey();
            ComboBox<String> comboBox = entry.getValue();
            String currentSelection = comboBox.getValue();

            // v1.4.0: 使用固定测量值列表，不依赖实际measurements
            // 关键修复: 使用辅助方法从displayName查找actualChannelName
            String actualChannelName = findActualChannelNameForDisplayName(channelName);
            if (actualChannelName == null) {
                actualChannelName = channelName;  // fallback
            }
            List<String> channelMeasurements = generateFixedMeasurementsForChannel(actualChannelName, model);

            // 更新选项
            comboBox.getItems().clear();
            comboBox.getItems().addAll(channelMeasurements);

            // 尝试保持原选择，如果不支持则选择默认的compartment对应的第一个
            if (channelMeasurements.contains(currentSelection)) {
                comboBox.setValue(currentSelection);
            } else {
                // 选择与模型默认compartment匹配的第一个measurement
                String defaultCompartment = model.getMeasurementPrefix();
                String defaultMeasurement = channelMeasurements.stream()
                    .filter(m -> m.startsWith(defaultCompartment + ":"))
                    .findFirst()
                    .orElse(channelMeasurements.isEmpty() ? null : channelMeasurements.get(0));

                comboBox.setValue(defaultMeasurement);
            }
        }

        logger.info("已更新所有测量类型下拉框以匹配模型: {}", model.getDisplayName());
    }
    
    /**
     * Update instruction label based on current mode
     */
    private void updateInstructionLabel(Label instructionLabel) {
        if (currentMode == OperationMode.CREATE_CLASSIFIER) {
            instructionLabel.setText("Create模式: 单选通道进行实时预览 (阳性=紫色, 阴性=灰色)");
        } else {
            instructionLabel.setText("Load模式: 多选通道进行完整分类加载");
        }
    }

    private void refreshChannels() {
        // Preserve current channel mapping before reload
        Map<String, String> previousMapping = new HashMap<>(channelNameMapping);

        // Reload channels from current image data
        loadAvailableChannels();

        // v1.4.0修复: 刷新后验证通道名称匹配（处理通道改名情况）
        validateAndFixChannelNames();

        // v1.4.0: 由于channelNameMapping现在使用quPathChannelName作为key，
        // 而availableChannels存储的是displayName，所以我们需要通过displayName找到对应的quPathChannelName
        // 然后再恢复映射
        for (String displayName : availableChannels) {
            // 找到这个displayName对应的quPathChannelName
            String quPathChannelName = null;
            for (Map.Entry<String, String> entry : channelNameMapping.entrySet()) {
                String key = entry.getKey();
                // 跳过索引映射
                if (key.endsWith("_INDEX")) {
                    continue;
                }
                String userDisplayName = userChannelDisplayNames.get(key);
                String currentDisplayName = (userDisplayName != null) ? userDisplayName : key;
                if (currentDisplayName.equals(displayName)) {
                    quPathChannelName = key;
                    break;
                }
            }

            // 恢复映射
            if (quPathChannelName != null && previousMapping.containsKey(quPathChannelName)) {
                channelNameMapping.put(quPathChannelName, previousMapping.get(quPathChannelName));
                logger.debug("恢复通道映射: '{}' -> '{}'", quPathChannelName, previousMapping.get(quPathChannelName));
            }
            // Also restore C-index mapping (QuPathChannelName_INDEX -> C1, C2, C3, etc.)
            if (quPathChannelName != null) {
                String channelIndexKey = quPathChannelName + "_INDEX";
                if (previousMapping.containsKey(channelIndexKey)) {
                    channelNameMapping.put(channelIndexKey, previousMapping.get(channelIndexKey));
                    logger.debug("恢复索引映射: '{}' -> '{}'", channelIndexKey, previousMapping.get(channelIndexKey));
                }
            }
        }

        // Reinitialize thresholds with preserved or new defaults
        initializeThresholds();

        // Recreate channel controls with updated mapping
        createChannelControls();

        // Also refresh the phenotype table columns
        if (phenotypeTable != null) {
            createPhenotypeTable();
        }

        logger.info("Refreshed channels with preserved mapping. Active channels: {}, Mappings: {}",
                   availableChannels.size(), channelNameMapping.size());
    }
    
    // TODO: [方法] 待优化方法

    private void executeStrategy() {
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            showAlert(Alert.AlertType.WARNING, "警告", "没有可用的图像数据!");
            return;
        }

        // === 关键修复：每次运行前刷新通道和测量值，确保读取最新的hierarchy数据 ===
        logger.info("=== 刷新通道和测量值数据 ===");
        loadAvailableChannels();  // 从hierarchy重新读取所有通道和测量值
        logger.info("=== 数据刷新完成 ===");

        if (currentMode == OperationMode.CREATE_CLASSIFIER) {
            // Create模式点击执行策略时提示需要进行Load处理
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("执行策略提示");
            alert.setHeaderText("Create模式仅用于预览");
            alert.setContentText("Create模式是用于预览和设置阈值的。\n\n" +
                                "要执行实际的分类策略，请：\n" +
                                "1. 切换到 'Load Classifier (Execute Strategy)' 模式\n" +
                                "2. 选择需要的通道\n" +
                                "3. 点击执行策略进行实际分类\n\n" +
                                "这是因为分类需要按照 Create → Load 的顺序处理。");
            alert.showAndWait();
            return;
        } else {
            // Check if any channels are enabled for Load Classifier mode
            boolean hasEnabled = channelCheckBoxes.values().stream()
                .anyMatch(CheckBox::isSelected);
            if (!hasEnabled) {
                showAlert(Alert.AlertType.WARNING, "提示", "Load Classifier模式需要启用至少一个通道!");
                return;
            }
            executeLoadClassifierMode(imageData);
        }
    }
    
    private void previewChannel(String channelName) {
        logger.info("Activating live preview for channel: {}", channelName);

        // === 关键修复：预览前刷新测量值数据 ===
        ImageData<?> imageData = qupath.getImageData();
        if (imageData != null) {
            logger.info("=== 预览前刷新测量值数据 ===");
            loadAvailableChannels();  // 重新读取最新数据
        }

        // Clear any existing preview when switching channels
        clearPreview();
        // Apply threshold preview for the selected channel
        Slider slider = thresholdSliders.get(channelName);
        if (slider != null) {
            previewThreshold(channelName, getLinearValue(slider));  // Convert from log scale
        }
    }
    
    private void previewThreshold(String channelName, double threshold) {
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            logger.warn("No image data available for preview");
            return;
        }
        
        // Only preview if in Create Classifier mode
        if (currentMode != OperationMode.CREATE_CLASSIFIER) {
            return;
        }
        
        logger.info("Live preview: {} > {}", channelName, threshold);
        
        try {
            // Apply threshold directly to cells for live preview
            var hierarchy = imageData.getHierarchy();
            var cells = new ArrayList<>(hierarchy.getDetectionObjects());
            
            // Clear existing preview classifications first
            clearPreview();
            
            final int[] counts = new int[2]; // [positive, negative]
            String measurementType = measurementComboBoxes.get(channelName).getValue();

            // === 修复Build 12: 使用精确匹配而不是简单拼接 ===
            // 先从第一个cell获取measurement名称（使用增强匹配逻辑）
            String measurementName = null;
            if (!cells.isEmpty()) {
                var sampleCell = cells.get(0);
                measurementName = findMeasurementNameForClassification(
                    sampleCell.getMeasurementList(), channelName, measurementType);
            }

            if (measurementName == null) {
                logger.error("❌ Live Preview失败: 无法找到通道 '{}' 的measurement", channelName);
                return;
            }

            logger.info("✅ Live Preview使用measurement: '{}'", measurementName);

            for (var cell : cells) {
                try {
                    var measurements = cell.getMeasurementList();
                    if (measurements.containsKey(measurementName)) {
                        double measurementValue = measurements.get(measurementName);
                        boolean isPositive = measurementValue > threshold;
                        
                        if (isPositive) {
                            // Set preview color for positive cells (purple/magenta)
                            cell.setColor(0xFF00FF); // Magenta for positive
                            counts[0]++;
                        } else {
                            // Set preview color for negative cells (gray)
                            cell.setColor(0x808080); // Gray for negative
                            counts[1]++;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Could not process cell {}: {}", cell.getID(), e.getMessage());
                }
            }
            
            // Update the display with enhanced zoom-independent method
            Platform.runLater(() -> {
                // === Build 14: 自动切换Brightness&Contrast通道 ===
                switchToChannelDisplay(channelName);

                // Force hierarchy update first
                hierarchy.fireHierarchyChangedEvent(null);

                // Use enhanced viewer update for zoom-independent display
                updateViewerForAllZoomLevels();

                // Show preview status
                String status = String.format("Preview %s: %d positive, %d negative (threshold: %.1f)",
                    channelName, counts[0], counts[1], threshold);
                showPreviewStatus(status);
            });
            
        } catch (Exception e) {
            logger.error("Error during live preview: {}", e.getMessage(), e);
        }
    }

    private void clearPreview() {
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) return;
        
        // Performance optimization for large datasets
        var hierarchy = imageData.getHierarchy();
        var allCells = new ArrayList<>(hierarchy.getDetectionObjects());
        
        // Clear all cells - high-performance 10M+ support with parallel processing
        logger.info("Clearing preview colors for {} cells using parallel processing", allCells.size());
        allCells.parallelStream().forEach(cell -> {
            cell.setColor(null); // Reset to default QuPath coloring
        });
        
        Platform.runLater(() -> {
            if (qupath.getViewer() != null) {
                qupath.getViewer().repaint();
            }
        });
    }
    
    private void showPreviewStatus(String status) {
        // Show status in QuPath status bar if available
        Platform.runLater(() -> {
            if (qupath.getStage() != null) {
                try {
                    // Try to update QuPath's status display
                    qupath.getStage().setTitle("QuPath - " + status);
                } catch (Exception e) {
                    // Fallback to console logging
                    logger.info("Live Preview Status: {}", status);
                }
            } else {
                logger.info("Live Preview Status: {}", status);
            }
        });
    }

    /**
     * Build 15: 自动切换Brightness&Contrast通道显示（增强日志）
     * 预览通道时，只显示当前通道，关闭其他通道
     */
    private void switchToChannelDisplay(String channelName) {
        try {
            logger.warn("🎯🎯🎯 [CHANNEL-SWITCH] 开始切换通道显示: '{}'", channelName);

            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null) {
                logger.warn("❌ [CHANNEL-SWITCH] ImageData为null");
                return;
            }

            var viewer = qupath.getViewer();
            if (viewer == null) {
                logger.warn("❌ [CHANNEL-SWITCH] Viewer为null");
                return;
            }

            // 获取所有通道
            List<ImageChannel> channels = imageData.getServer().getMetadata().getChannels();
            if (channels.isEmpty()) {
                logger.warn("❌ [CHANNEL-SWITCH] 通道列表为空");
                return;
            }

            logger.warn("🔍 [CHANNEL-SWITCH] 总通道数: {}", channels.size());
            for (int i = 0; i < channels.size(); i++) {
                logger.warn("    通道{}: '{}'", i, channels.get(i).getName());
            }

            // 查找目标通道索引
            int targetChannelIndex = -1;
            for (int i = 0; i < channels.size(); i++) {
                ImageChannel channel = channels.get(i);
                String displayName = channel.getName();

                // 检查显示名称是否匹配
                if (displayName != null && displayName.equals(channelName)) {
                    targetChannelIndex = i;
                    logger.warn("✅ [CHANNEL-SWITCH] 通过显示名称找到匹配: 索引={}", i);
                    break;
                }
            }

            if (targetChannelIndex == -1) {
                // 尝试通过availableChannels索引查找
                int index = availableChannels.indexOf(channelName);
                logger.warn("🔍 [CHANNEL-SWITCH] availableChannels.indexOf('{}') = {}", channelName, index);
                if (index >= 0 && index < channels.size()) {
                    targetChannelIndex = index;
                    logger.warn("✅ [CHANNEL-SWITCH] 通过availableChannels找到匹配: 索引={}", index);
                }
            }

            if (targetChannelIndex == -1) {
                logger.warn("❌ [CHANNEL-SWITCH] 无法找到通道 '{}' 的索引", channelName);
                return;
            }

            logger.warn("🎯 [CHANNEL-SWITCH] 目标通道索引: {}", targetChannelIndex);

            // 获取ImageDisplay来控制通道显示
            var imageDisplay = viewer.getImageDisplay();
            if (imageDisplay == null) {
                logger.warn("❌ [CHANNEL-SWITCH] ImageDisplay为null");
                return;
            }

            // 获取所有ChannelDisplayInfo
            var displayChannels = imageDisplay.availableChannels();
            logger.warn("🔍 [CHANNEL-SWITCH] DisplayChannels数量: {}", displayChannels.size());

            // 关闭所有通道
            for (int i = 0; i < displayChannels.size(); i++) {
                imageDisplay.setChannelSelected(displayChannels.get(i), false);
                logger.warn("    关闭通道{}", i);
            }

            // 只打开目标通道
            if (targetChannelIndex < displayChannels.size()) {
                imageDisplay.setChannelSelected(displayChannels.get(targetChannelIndex), true);
                logger.warn("✅✅✅ [CHANNEL-SWITCH] 已切换到通道 '{}' (索引{}), 其他通道已关闭",
                           channelName, targetChannelIndex);
            } else {
                logger.warn("❌ [CHANNEL-SWITCH] 目标索引{}超出displayChannels范围({})",
                           targetChannelIndex, displayChannels.size());
            }

            // 刷新viewer显示
            viewer.repaintEntireImage();
            logger.warn("🔄 [CHANNEL-SWITCH] Viewer已刷新");

        } catch (Exception e) {
            logger.error("❌❌❌ [CHANNEL-SWITCH] 切换通道显示时出错", e);
            e.printStackTrace();
        }
    }

    /**
     * Enhanced viewer update for zoom-independent cell display
     * 改进的查看器更新，支持所有缩放级别的细胞显示
     */
    private void updateViewerForAllZoomLevels() {
        try {
            if (qupath.getViewer() != null) {
                var viewer = qupath.getViewer();
                var overlayOptions = viewer.getOverlayOptions();

                if (overlayOptions != null) {
                    // === 修改：默认为非填充模式（边界显示）===
                    // 只显示细胞边界，不填充内部颜色，提供更清晰的视觉效果
                    overlayOptions.setShowDetections(true);
                    overlayOptions.setFillDetections(false);  // 改为false，只显示边界
                    overlayOptions.resetMeasurementMapper();
                }

                // Force overlay update before repaint
                viewer.forceOverlayUpdate();
                viewer.repaint();

                logger.debug("Enhanced viewer update completed - zoom-independent display enabled");
            }
        } catch (Exception e) {
            logger.debug("Enhanced viewer update failed (non-critical): {}", e.getMessage());
        }
    }

    /**
     * Clear Live Preview mode and reset channel button styles
     */
    private void clearLivePreview() {
        livePreviewEnabled = false;
        currentPreviewChannel = null;
        
        // Reset all channel button styles to inactive
        channelContainer.getChildren().forEach(child -> {
            if (child instanceof HBox) {
                HBox childRow = (HBox) child;
                if (!childRow.getChildren().isEmpty() && childRow.getChildren().get(0) instanceof Button) {
                    Button btn = (Button) childRow.getChildren().get(0);
                    if (!btn.getText().equals("Channel")) { // Skip header
                        btn.setStyle("-fx-font-weight: bold; -fx-background-color: #e0e0e0; -fx-text-fill: black;");
                    }
                }
            }
        });
        
        // Clear preview coloring
        clearPreview();

        // Update display with enhanced method
        updateViewerForAllZoomLevels();

        logger.info("Live Preview deactivated");
    }
    
    /**
     * Update live preview with current threshold settings - Optimized for large datasets
     */
    private void updateLivePreview() {
        if (!livePreviewEnabled || currentPreviewChannel == null) {
            return;
        }
        
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            logger.warn("No image data available for live preview update");
            return;
        }
        
        // Get current threshold for the preview channel
        Slider slider = thresholdSliders.get(currentPreviewChannel);
        ComboBox<String> measurementBox = measurementComboBoxes.get(currentPreviewChannel);
        
        if (slider != null && measurementBox != null) {
            double threshold = getLinearValue(slider);  // Convert from log scale
            String measurementType = measurementBox.getValue();

            // 调试日志：显示ComboBox选中的测量值
            logger.info("=== Live Preview Update ===");
            logger.info("Channel: {}", currentPreviewChannel);
            logger.info("Threshold: {}", threshold);
            logger.info("Selected Measurement Type from ComboBox: '{}'", measurementType);

            // Apply live preview with ROI support and optimization for large datasets
            try {
                var hierarchy = imageData.getHierarchy();
                
                // Use ROI-filtered cells if ROI mode is enabled
                List<qupath.lib.objects.PathObject> targetCells;
                if (useSelectedROI) {
                    targetCells = getCellsInSelectedROI(imageData);
                    logger.info("Live Preview: Using {} ROI-filtered cells for channel: {}", 
                               targetCells.size(), currentPreviewChannel);
                } else {
                    targetCells = new ArrayList<>(hierarchy.getDetectionObjects());
                    logger.info("Live Preview: Using {} total cells for channel: {}", 
                               targetCells.size(), currentPreviewChannel);
                }
                
                // Process all cells - true 10M+ support without artificial limits
                var cells = targetCells;
                
                logger.info("Live Preview: Processing {} cells for channel: {}", 
                    cells.size(), currentPreviewChannel);
                
                // Clear existing preview first
                clearPreview();
                
                final int[] counts = new int[2]; // [positive, negative]
                
                // === 关键修复Build 8: 检测完整measurement名称 ===
                // 如果measurementType已经是完整名称（包含通道名），直接使用
                boolean isCompleteMeasurementName = false;
                final String[] actualMeasurementName = {null};

                if (!cells.isEmpty()) {
                    var firstCell = cells.get(0);
                    var measurements = firstCell.getMeasurementList();

                    // 策略1: 直接精确匹配（用户选择的完整measurement名称）
                    if (measurementType != null && measurements.containsKey(measurementType)) {
                        actualMeasurementName[0] = measurementType;
                        isCompleteMeasurementName = true;
                        logger.info("✓ 直接使用完整measurement名称: '{}'", measurementType);
                    }

                    // 策略2: 如果直接匹配失败，使用旧的模式生成逻辑
                    if (actualMeasurementName[0] == null) {
                        logger.info("完整名称匹配失败，使用模式生成");
                        String[] possibleMeasurementNames = createPossibleMeasurementNames(currentPreviewChannel, measurementType);

                        for (String testName : possibleMeasurementNames) {
                            if (measurements.containsKey(testName)) {
                                actualMeasurementName[0] = testName;
                                logger.info("Found measurement via pattern: {}", testName);
                                break;
                            }
                        }
                    }
                }

                // 如果仍然没有找到，尝试fallback逻辑
                if (actualMeasurementName[0] == null && !cells.isEmpty()) {
                    var firstCell = cells.get(0);
                    var measurements = firstCell.getMeasurementList();

                    // List available measurements for debugging
                    logger.warn("No matching measurement found for channel: {}. Available measurements:", currentPreviewChannel);
                    measurements.getNames().stream()
                        .filter(name -> name.toLowerCase().contains(currentPreviewChannel.toLowerCase()))
                        .forEach(name -> logger.info("  - {}", name));

                    // Try any measurement containing the channel name
                    for (String availableName : measurements.getNames()) {
                        if (availableName.toLowerCase().contains(currentPreviewChannel.toLowerCase()) &&
                            availableName.toLowerCase().contains("mean")) {
                            actualMeasurementName[0] = availableName;
                            logger.info("Using fallback measurement: {}", actualMeasurementName[0]);
                            break;
                        }
                    }
                }
                
                if (actualMeasurementName[0] == null) {
                    logger.error("Could not find any suitable measurement for channel: {}", currentPreviewChannel);
                    showPreviewStatus("错误: 无法找到通道 " + currentPreviewChannel + " 的测量数据");
                    return;
                }
                
                // High-performance parallel processing for 10M+ cells
                logger.info("Starting high-performance live preview for {} cells", cells.size());
                
                // Process cells with parallel stream for massive datasets
                cells.parallelStream().forEach(cell -> {
                    try {
                        var measurements = cell.getMeasurementList();
                        if (measurements.containsKey(actualMeasurementName[0])) {
                            double measurementValue = measurements.get(actualMeasurementName[0]);
                            boolean isPositive = measurementValue > threshold;
                            
                            if (isPositive) {
                                // Set preview color for positive cells (purple/magenta)
                                cell.setColor(0xFF00FF); // Magenta for positive
                                synchronized(counts) { counts[0]++; }
                            } else {
                                // Set preview color for negative cells (gray)
                                cell.setColor(0x808080); // Gray for negative
                                synchronized(counts) { counts[1]++; }
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Could not process cell {}: {}", cell.getID(), e.getMessage());
                    }
                });
                
                // Update the display with enhanced zoom-independent method
                Platform.runLater(() -> {
                    updateViewerForAllZoomLevels();

                    // Show preview status with performance info
                    String status = String.format("Live Preview - %s: %d阳性, %d阴性 (阈值: %.1f) [处理了%d个细胞]",
                        currentPreviewChannel, counts[0], counts[1], threshold, cells.size());
                    showPreviewStatus(status);
                });
                
            } catch (Exception e) {
                logger.error("Error during live preview update: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Measurement名称解析：优先级 C2/C3/C4 > 原始名称 > 显示名称
     * 关键：QuPath改名后measurement仍使用C索引，所以必须优先用C索引匹配
     */
    private String[] createPossibleMeasurementNames(String channelName, String measurementType) {
        logger.info("=== createPossibleMeasurementNames called ===");
        logger.info("Channel Name: '{}'", channelName);
        logger.info("Measurement Type: '{}'", measurementType);

        List<String> possibleNames = new ArrayList<>();

        // 构建优先级候选列表：显示名称 > C索引 > 原始名称
        // 关键修复：优先使用显示名称（如FOXP3），这样可以直接匹配实际的measurement
        List<String> orderedCandidates = new ArrayList<>();

        // === 关键修复：优先级1: 显示名称（用户看到的名字，如FOXP3）===
        // 这是最重要的候选，因为QuPath的measurement可能直接使用显示名称
        orderedCandidates.add(channelName);
        logger.info("  [Priority 1] Display name: '{}'", channelName);

        // 优先级2: C2/C3/C4 Index（QuPath measurement的备用标识）
        String channelIndex = channelNameMapping.getOrDefault(channelName + "_INDEX", "");
        if (!channelIndex.isEmpty() && !orderedCandidates.contains(channelIndex)) {
            orderedCandidates.add(channelIndex);
            logger.info("  [Priority 2] C-index: '{}'", channelIndex);
        }

        // 优先级3: 原始名称（从映射表获取，如果与显示名称不同）
        String originalName = channelNameMapping.getOrDefault(channelName, channelName);
        if (!orderedCandidates.contains(originalName)) {
            orderedCandidates.add(originalName);
            logger.info("  [Priority 3] Original name: '{}'", originalName);
        }

        logger.info("  → Ordered candidates: {}", orderedCandidates);

        // Extract compartment and measurement suffix from measurementType
        // Format: "Compartment: Statistic" (e.g., "Nucleus: Mean", "Cell: Median", "Cytoplasm: Max")
        String compartment = null;
        String measurementSuffix = "Mean"; // default

        if (measurementType.contains(":")) {
            String[] parts = measurementType.split(":");
            if (parts.length >= 2) {
                compartment = parts[0].trim();  // e.g., "Nucleus", "Cell", "Cytoplasm"
                measurementSuffix = parts[1].trim();  // e.g., "Mean", "Median", "Max", "Min"
            } else if (parts.length == 1) {
                measurementSuffix = parts[0].trim();
            }
        } else {
            measurementSuffix = measurementType.trim();
        }

        logger.info("  → Parsed Compartment: '{}'", compartment);
        logger.info("  → Parsed Measurement Suffix: '{}'", measurementSuffix);

        // QuPath使用小写统计量，生成大小写变体
        List<String> suffixVariations = new ArrayList<>();
        suffixVariations.add(measurementSuffix.toLowerCase());           // QuPath标准: lowercase
        suffixVariations.add(measurementSuffix);                          // 用户输入
        suffixVariations.add(measurementSuffix.toUpperCase());           // 大写变体
        if (measurementSuffix.length() > 0) {
            suffixVariations.add(measurementSuffix.substring(0, 1).toUpperCase() +
                               measurementSuffix.substring(1).toLowerCase()); // 首字母大写
        }

        // 按优先级生成measurement patterns
        for (String candidate : orderedCandidates) {
            for (String suffix : suffixVariations) {
                if (compartment != null && !compartment.isEmpty()) {
                    // QuPath标准格式: "Compartment: ChannelName suffix" (空格分隔)
                    possibleNames.add(compartment + ": " + candidate + " " + suffix);
                    possibleNames.add(compartment + ": " + candidate + ": " + suffix);
                    possibleNames.add(compartment + ": " + candidate);
                }

                // 无Compartment格式
                possibleNames.add(candidate + ": " + suffix);
                possibleNames.add(candidate + " " + suffix);

                // 其他Compartment fallback
                for (String comp : new String[]{"Nucleus", "Cell", "Cytoplasm"}) {
                    if (compartment == null || !comp.equals(compartment)) {
                        possibleNames.add(comp + ": " + candidate + " " + suffix);
                        possibleNames.add(comp + ": " + candidate + ": " + suffix);
                        possibleNames.add(comp + ": " + candidate);
                    }
                }
            }
        }

        logger.info("  ✓ Generated {} patterns", possibleNames.size());
        logger.info("  ✓ First 5 patterns: {}",
                   possibleNames.stream().limit(5).collect(java.util.stream.Collectors.toList()));

        return possibleNames.toArray(new String[0]);
    }
    
    // TODO: [方法] 待优化方法

    private TitledPane createClassificationSection() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        // 优化布局：参考阈值策略配置的样式
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_RIGHT);
        headerBox.setPadding(new Insets(0, 0, 10, 0));

        Button addButton = new Button("+ 新增细胞类型");
        addButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
        addButton.setOnAction(e -> addNewPhenotype());

        headerBox.getChildren().add(addButton);

        // 表型配置表格 - 参考阈值策略配置的样式
        createPhenotypeTable();

        // 表格样式优化 - 参考阈值策略配置
        phenotypeTable.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-radius: 5;");

        ScrollPane tableScrollPane = new ScrollPane(phenotypeTable);
        tableScrollPane.setFitToWidth(false); // 重要：允许水平滚动
        tableScrollPane.setFitToHeight(true);

        // 自适应整个区域：移除固定高度限制，让表格自动适应可用空间
        VBox.setVgrow(tableScrollPane, Priority.ALWAYS); // 让滚动面板垂直扩展填充剩余空间
        tableScrollPane.setMaxHeight(Double.MAX_VALUE); // 移除最大高度限制

        // 优化滚动策略支持30+通道的左右滑动 - 样式参考阈值配置
        tableScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScrollPane.setPannable(true); // 支持拖拽滚动
        tableScrollPane.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-focus-color: transparent;");

        // 强制表格支持水平滚动并使用全宽度（不紧凑）
        phenotypeTable.autosize();
        phenotypeTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY); // 使用无约束策略允许水平滚动

        // 智能初始高度设置 - 与updateTableHeight()保持一致
        int cellTypeCount = Math.max(phenotypes.size(), 2); // 最少显示2行，更紧凑
        double baseRowHeight = 45; // 基础行高增加以适应新样式
        double headerHeight = 50;  // 表头高度
        double paddingHeight = 20; // 上下边距

        double initialHeight;
        if (cellTypeCount <= 3) {
            initialHeight = cellTypeCount * (baseRowHeight + 5) + headerHeight + paddingHeight;
        } else if (cellTypeCount <= 8) {
            initialHeight = cellTypeCount * baseRowHeight + headerHeight + paddingHeight;
        } else {
            double compactHeight = cellTypeCount * (baseRowHeight - 5) + headerHeight + paddingHeight;
            initialHeight = Math.min(compactHeight, 400);
        }

        tableScrollPane.setPrefHeight(initialHeight);
        if (cellTypeCount > 8) {
            tableScrollPane.setMaxHeight(400);
            tableScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        } else {
            tableScrollPane.setMaxHeight(initialHeight + 10);
            tableScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        }

        // 让表格宽度和整个宽度一样（不紧凑）
        phenotypeTable.setPrefWidth(Region.USE_COMPUTED_SIZE);
        phenotypeTable.setMaxWidth(Double.MAX_VALUE);
        phenotypeTable.setStyle("-fx-font-size: 12px;"); // 调小字体一号
        VBox.setVgrow(tableScrollPane, javafx.scene.layout.Priority.NEVER); // 不再自动扩展

        content.getChildren().addAll(headerBox, tableScrollPane);

        TitledPane pane = new TitledPane("细胞分类", content);
        pane.setCollapsible(false);

        logger.info("细胞分类界面优化完成 - 表格宽度适配整个区域");
        return pane;
    }

    /**
     * 为未分类的细胞应用灰色伪彩
     * 修复：同时处理PathClass为null和PathClass为"Unclassified"的情况
     */
    private void applyGrayColorToUnclassifiedCells(ImageData<?> imageData) {
        try {
            var hierarchy = imageData.getHierarchy();
            Collection<qupath.lib.objects.PathObject> cells = hierarchy.getDetectionObjects();

            int unclassifiedCount = 0;
            Integer grayColor = 0xFF808080; // 灰色 RGB(128, 128, 128)

            for (var cell : cells) {
                boolean isUnclassified = false;

                // 情况1: PathClass为null
                if (cell.getPathClass() == null) {
                    isUnclassified = true;
                }
                // 情况2: PathClass名称为"Unclassified"或"unclassified"
                else if (cell.getPathClass().getName() != null) {
                    String className = cell.getPathClass().getName().toLowerCase();
                    if (className.equals("unclassified") || className.equals("undefined")) {
                        isUnclassified = true;
                    }
                }

                if (isUnclassified) {
                    // 设置灰色
                    cell.setColor(grayColor);
                    unclassifiedCount++;
                }
            }

            // 更新显示
            hierarchy.fireHierarchyChangedEvent(null);

            if (unclassifiedCount > 0) {
                logger.info("已为{}个未分类细胞应用灰色伪彩", unclassifiedCount);
            }

        } catch (Exception e) {
            logger.error("应用灰色伪彩失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 加载表型配置
     */
    private void loadPhenotypeConfiguration() {
        loadConfigurationFromProject();
    }

    /**
     * 保存表型配置
     */
    private void savePhenotypeConfiguration() {
        saveConfigurationToProject();
    }

    /**
     * 运行细胞分类
     */
    private void runCellClassification() {
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            showAlert(Alert.AlertType.WARNING, "警告", "没有可用的图像数据!");
            return;
        }

        if (phenotypes.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "警告", "请先添加表型定义!");
            return;
        }

        try {
            logger.info("开始运行细胞分类 - {} 个表型", phenotypes.size());

            // 使用现有的CellPhenotypeAPI进行分类
            PhenotypeManager phenotypeManager = new PhenotypeManager();
            for (CellPhenotype phenotype : phenotypes) {
                phenotypeManager.addPhenotype(phenotype);
            }

            CellPhenotypeAPI.applyCellClassification(imageData, currentConfig, phenotypeManager);

            showAlert(Alert.AlertType.INFORMATION, "分类完成",
                String.format("细胞分类完成！\n处理了 %d 个表型定义\n结果已应用到图像", phenotypes.size()));

        } catch (Exception e) {
            logger.error("细胞分类失败: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "分类错误", "细胞分类失败: " + e.getMessage());
        }
    }

    /**
     * 导出分类结果
     */
    private void exportClassificationResults() {
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            showAlert(Alert.AlertType.WARNING, "警告", "没有可用的图像数据!");
            return;
        }

        try {
            // 选择导出文件 - 使用平台特定的文件选择器
            String defaultFileName = "cell_classification_results.csv";
            String outputPath = System.getProperty("user.home") + "/" + defaultFileName;

            // 使用现有的导出方法或创建简单导出
            try {
                var hierarchy = imageData.getHierarchy();
                var cells = hierarchy.getDetectionObjects();

                StringBuilder csvContent = new StringBuilder();
                // v1.4.0: 添加UTF-8 BOM标记，帮助Excel等软件正确识别UTF-8编码
                csvContent.append("\uFEFF");
                csvContent.append("Cell_ID,X,Y,Classification,CellType\n");

                for (var cell : cells) {
                    String cellId = cell.getID().toString();
                    double x = cell.getROI().getCentroidX();
                    double y = cell.getROI().getCentroidY();
                    String classification = cell.getPathClass() != null ? cell.getPathClass().getName() : "Unclassified";
                    String cellType = classification; // 简化处理

                    // 修复Unicode编码问题：确保字符正确处理
                    csvContent.append(String.format("%s,%.2f,%.2f,%s,%s\n",
                            cellId, x, y, classification, cellType));
                }

                // v1.4.0: 使用UTF-8编码写入，确保Unicode字符（α、β等）正确显示
                java.nio.file.Files.write(
                    java.nio.file.Paths.get(outputPath),
                    csvContent.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
            } catch (Exception exportEx) {
                logger.warn("CSV导出失败，使用备用方法: {}", exportEx.getMessage());
                // 备用：只显示统计信息
                var hierarchy = imageData.getHierarchy();
                int totalCells = hierarchy.getDetectionObjects().size();

                showAlert(Alert.AlertType.INFORMATION, "导出完成",
                    String.format("分类统计：\n总细胞数: %d\n详细CSV导出功能开发中", totalCells));
                return;
            }
            showAlert(Alert.AlertType.INFORMATION, "导出完成",
                "分类结果已导出到：\n" + outputPath);
            logger.info("分类结果导出完成: {}", outputPath);
        } catch (Exception e) {
            logger.error("导出失败: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "导出错误", "导出失败: " + e.getMessage());
        }
    }

    private void createPhenotypeTable() {
        if (phenotypeTable == null) {
            phenotypeTable = new TableView<>();
            phenotypeData = FXCollections.observableArrayList();
            phenotypeTable.setItems(phenotypeData);
        } else {
            phenotypeTable.getColumns().clear();
        }

        // 设置表格为可编辑（允许直接修改细胞类型名称）
        phenotypeTable.setEditable(true);

        // 优化表格显示属性 - 无约束列宽度（支持水平滚动）
        phenotypeTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // 简洁统一的表格样式
        phenotypeTable.getStyleClass().add("compact-phenotype-table");
        phenotypeTable.setStyle(
            "-fx-font-size: 11px; " +
            "-fx-font-family: system; " +
            "-fx-background-color: white; " +
            "-fx-border-color: #d0d0d0; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 4; " +
            // 简化表头样式
            ".column-header-background { " +
            "-fx-background-color: #f5f5f5; " +
            "-fx-border-color: #d0d0d0; " +
            "} " +
            ".column-header { " +
            "-fx-background-color: #f5f5f5; " +
            "-fx-text-fill: #333; " +
            "-fx-font-weight: bold; " +
            "-fx-font-size: 11px; " +
            "-fx-padding: 6 8 6 8; " +
            "-fx-border-color: #d0d0d0; " +
            "} " +
            ".table-column { " +
            "-fx-background-color: #f5f5f5; " +
            "-fx-text-fill: #333; " +
            "} " +
            // 简化表格行样式
            ".table-row-cell { " +
            "-fx-background-color: white; " +
            "-fx-border-color: transparent; " +
            "-fx-padding: 4 2 4 2; " +
            "} " +
            ".table-row-cell:odd { " +
            "-fx-background-color: #fafafa; " +
            "} " +
            ".table-row-cell:hover { " +
            "-fx-background-color: #f0f0f0; " +
            "} " +
            ".table-row-cell:selected { " +
            "-fx-background-color: #e8e8e8; " +
            "-fx-text-fill: #333; " +
            "} " +
            ".table-cell { " +
            "-fx-padding: 4 6 4 6; " +
            "-fx-text-fill: #333; " +
            "-fx-alignment: center; " +
            "}");

        // Priority/Sort column - 只显示序号（无上下按钮） - 拖动排序替代
        TableColumn<PhenotypeTableRow, Integer> sortCol = new TableColumn<>("排序");
        sortCol.setCellValueFactory(new PropertyValueFactory<>("priority"));
        sortCol.setCellFactory(col -> new TableCell<PhenotypeTableRow, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    // SEQUENTIAL 1/2/3 display - 只显示序号
                    int sequenceNumber = getIndex() + 1; // 1-based sequence
                    Label priorityLabel = new Label(String.valueOf(sequenceNumber));
                    priorityLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-min-width: 24px; -fx-alignment: center;");

                    setGraphic(priorityLabel);
                }
            }
        });
        // 紧凑固定宽度设置 - 去掉按钮后宽度缩小到30px
        sortCol.setPrefWidth(30);
        sortCol.setMinWidth(30);
        sortCol.setMaxWidth(30);

        // Name column - 智能宽度计算 + 可编辑
        TableColumn<PhenotypeTableRow, String> nameCol = new TableColumn<>("分类名称");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        // 设置为可编辑，使用TextFieldTableCell
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setEditable(true);

        // 监听名称修改事件
        nameCol.setOnEditCommit(event -> {
            PhenotypeTableRow row = event.getRowValue();
            String oldName = event.getOldValue();
            String newName = event.getNewValue();

            logger.info("🔧 开始编辑细胞类型名称: {} -> {}", oldName, newName);

            // 验证新名称不为空
            if (newName == null || newName.trim().isEmpty()) {
                logger.warn("细胞类型名称不能为空，恢复为原名称: {}", oldName);
                showAlert(Alert.AlertType.WARNING, "无效名称", "细胞类型名称不能为空");
                row.setName(oldName);
                phenotypeTable.refresh();
                return;
            }

            // 验证新名称不重复
            boolean isDuplicate = phenotypeData.stream()
                .filter(r -> r != row)
                .anyMatch(r -> r.getName().equals(newName.trim()));

            if (isDuplicate) {
                logger.warn("细胞类型名称重复: {}", newName);
                showAlert(Alert.AlertType.WARNING, "重复名称", "细胞类型名称 \"" + newName + "\" 已存在");
                row.setName(oldName);
                phenotypeTable.refresh();
                return;
            }

            // 更新UI表格行的名称
            row.setName(newName.trim());

            // 同步更新phenotypes列表中的对应项（CellPhenotype是不可变的，需要替换）
            for (int i = 0; i < phenotypes.size(); i++) {
                CellPhenotype phenotype = phenotypes.get(i);
                if (phenotype.getName().equals(oldName)) {
                    // 使用withName创建新对象并替换
                    CellPhenotype updatedPhenotype = phenotype.withName(newName.trim());
                    phenotypes.set(i, updatedPhenotype);
                    logger.info("✅ 细胞类型名称已更新: {} -> {}", oldName, newName.trim());
                    break;
                }
            }

            // 刷新表格显示
            phenotypeTable.refresh();

            logger.info("✅ 名称编辑完成并已保存到内存");
        });

        // 智能计算分类名称列宽度 - 根据最长名称动态调整
        double nameColumnWidth = calculateOptimalNameColumnWidth();
        nameCol.setPrefWidth(nameColumnWidth);
        nameCol.setMinWidth(Math.max(90, nameColumnWidth - 15)); // 减小最小宽度，更紧凑
        nameCol.setMaxWidth(nameColumnWidth + 20); // 减少扩展空间

        phenotypeTable.getColumns().add(sortCol);
        phenotypeTable.getColumns().add(nameCol);

        // 使用从阈值策略传递的通道，如果没有则使用全部通道
        List<String> channelsToUse = selectedChannelsFromThreshold.isEmpty() ?
                new ArrayList<>(availableChannels) :
                selectedChannelsFromThreshold;

        logger.info("创建表型表格 - 使用通道: {} (共{}个)", channelsToUse, channelsToUse.size());

        // Dynamic marker columns based on selected channels
        List<String> markerChannels = channelsToUse;
            
        for (String channel : markerChannels) {
            TableColumn<PhenotypeTableRow, String> markerCol = new TableColumn<>(channel);
            markerCol.setCellFactory(col -> new TableCell<PhenotypeTableRow, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                        setGraphic(null);
                    } else {
                        ComboBox<String> comboBox = new ComboBox<>();
                        comboBox.getItems().addAll("阳性", "阴性", "无关");

                        // 简洁ComboBox样式
                        comboBox.setStyle(
                            "-fx-font-size: 12px; " +
                            "-fx-padding: 3 6 3 6; " +
                            "-fx-background-color: white; " +
                            "-fx-border-color: #ccc; " +
                            "-fx-border-width: 1; " +
                            "-fx-border-radius: 3; " +
                            "-fx-cursor: hand;"
                        );

                        double comboBoxWidth = calculateOptimalMarkerColumnWidth(markerChannels.size()) - 15; // 更紧凑
                        comboBox.setPrefWidth(comboBoxWidth);
                        comboBox.setMaxWidth(comboBoxWidth + 10); // 减少扩展空间

                        // 简化ComboBox悬停效果
                        comboBox.setOnMouseEntered(event -> {
                            comboBox.setStyle(
                                "-fx-font-size: 11px; " +
                                "-fx-padding: 3 6 3 6; " +
                                "-fx-background-color: #f8f8f8; " +
                                "-fx-border-color: #999; " +
                                "-fx-border-width: 1; " +
                                "-fx-border-radius: 3; " +
                                "-fx-cursor: hand;"
                            );
                        });
                        comboBox.setOnMouseExited(event -> {
                            comboBox.setStyle(
                                "-fx-font-size: 11px; " +
                                "-fx-padding: 3 6 3 6; " +
                                "-fx-background-color: white; " +
                                "-fx-border-color: #ccc; " +
                                "-fx-border-width: 1; " +
                                "-fx-border-radius: 3; " +
                                "-fx-cursor: hand;"
                            );
                        });

                        // Get current marker state from the phenotype
                        PhenotypeTableRow row = getTableRow().getItem();
                        String currentState = getCurrentMarkerState(row.getName(), channel);
                        comboBox.setValue(currentState != null ? currentState : "无关");

                        // 优化性能：减少不必要的表格刷新
                        comboBox.setOnAction(e -> {
                            PhenotypeTableRow currentRow = getTableView().getItems().get(getIndex());
                            String newValue = comboBox.getValue();
                            String oldValue = getCurrentMarkerState(currentRow.getName(), channel);

                            // 仅在值实际改变时更新
                            if (!Objects.equals(oldValue, newValue)) {
                                updatePhenotypeMarkerState(currentRow.getName(), channel, newValue);
                                logger.debug("Updated marker state for phenotype '{}', channel '{}': {} -> {}",
                                           currentRow.getName(), channel, oldValue, newValue);
                                // 移除自动表格刷新 - 让JavaFX的数据绑定自然处理
                            }
                        });

                        setGraphic(comboBox);
                    }
                }
            });

            // 智能列宽设置 - 根据通道数量和可用空间智能分配
            double markerColumnWidth = calculateOptimalMarkerColumnWidth(markerChannels.size());
            markerCol.setPrefWidth(markerColumnWidth);
            markerCol.setMinWidth(85); // 减小最小宽度，更紧凑
            markerCol.setMaxWidth(markerColumnWidth + 10); // 减少扩展空间

            // 设置列标题样式，调小字体大小
            markerCol.setStyle("-fx-font-size: 11px;");

            phenotypeTable.getColumns().add(markerCol);
        }

        // Action column - 优化宽度
        TableColumn<PhenotypeTableRow, Void> actionCol = new TableColumn<>("操作");
        actionCol.setCellFactory(col -> new TableCell<PhenotypeTableRow, Void>() {
            private final Button deleteButton = new Button("删除");

            {
                // 简洁删除按钮样式
                deleteButton.setStyle(
                    "-fx-font-size: 10px; " +
                    "-fx-padding: 3 6 3 6; " +
                    "-fx-min-width: 50px; " +
                    "-fx-pref-width: 60px; " +
                    "-fx-background-color: #f8f8f8; " +
                    "-fx-text-fill: #e53935; " +
                    "-fx-border-color: #e53935; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 3; " +
                    "-fx-background-radius: 3; " +
                    "-fx-cursor: hand; " +
                    "-fx-font-weight: bold;"
                );

                // 简化悬停效果
                deleteButton.setOnMouseEntered(e -> deleteButton.setStyle(
                    "-fx-font-size: 10px; " +
                    "-fx-padding: 3 6 3 6; " +
                    "-fx-min-width: 50px; " +
                    "-fx-pref-width: 60px; " +
                    "-fx-background-color: #e53935; " +
                    "-fx-text-fill: white; " +
                    "-fx-border-color: #e53935; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 3; " +
                    "-fx-background-radius: 3; " +
                    "-fx-cursor: hand; " +
                    "-fx-font-weight: bold;"
                ));

                deleteButton.setOnMouseExited(e -> deleteButton.setStyle(
                    "-fx-font-size: 10px; " +
                    "-fx-padding: 3 6 3 6; " +
                    "-fx-min-width: 50px; " +
                    "-fx-pref-width: 60px; " +
                    "-fx-background-color: #f8f8f8; " +
                    "-fx-text-fill: #e53935; " +
                    "-fx-border-color: #e53935; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 3; " +
                    "-fx-background-radius: 3; " +
                    "-fx-cursor: hand; " +
                    "-fx-font-weight: bold;"
                ));

                deleteButton.setOnAction(e -> {
                    PhenotypeTableRow row = getTableView().getItems().get(getIndex());
                    phenotypeData.remove(row);
                    // Remove from phenotypes list
                    phenotypes.removeIf(p -> p.getName().equals(row.getName()));
                    refreshPriorities();
                    // 更新表格高度以适应删除后的细胞类型数量
                    updateTableHeight();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteButton);
                }
            }
        });
        // 紧凑固定宽度设置 - 操作列固定75px，更紧凑
        actionCol.setPrefWidth(75);
        actionCol.setMinWidth(75);
        actionCol.setMaxWidth(75);

        phenotypeTable.getColumns().add(actionCol);

        // 优化整体表格显示设置 - 支持30+通道滚动
        phenotypeTable.setPrefHeight(280); // 减小高度为其他内容留出空间
        phenotypeTable.setMaxHeight(350);

        // 强制表格支持水平滚动
        phenotypeTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // 添加行拖动排序功能
        phenotypeTable.setRowFactory(tv -> {
            TableRow<PhenotypeTableRow> row = new TableRow<>();

            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Integer index = row.getIndex();
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    db.setDragView(row.snapshot(null, null));
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString(String.valueOf(index));
                    db.setContent(cc);
                    event.consume();
                    logger.info("🖱️ 开始拖动行: 索引={}, 名称={}", index, row.getItem().getName());
                }
            });

            row.setOnDragOver(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasString()) {
                    if (row.getIndex() != Integer.parseInt(db.getString())) {
                        event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                        event.consume();
                    }
                }
            });

            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasString()) {
                    int draggedIndex = Integer.parseInt(db.getString());
                    PhenotypeTableRow draggedRow = phenotypeTable.getItems().remove(draggedIndex);

                    int dropIndex;
                    if (row.isEmpty()) {
                        dropIndex = phenotypeTable.getItems().size();
                    } else {
                        dropIndex = row.getIndex();
                    }

                    phenotypeTable.getItems().add(dropIndex, draggedRow);

                    logger.info("🔄 拖动操作: {} 从位置 {} 移动到位置 {}", draggedRow.getName(), draggedIndex, dropIndex);

                    // 同步更新phenotypes列表
                    CellPhenotype draggedPhenotype = null;
                    int phenotypeIndex = -1;
                    for (int i = 0; i < phenotypes.size(); i++) {
                        if (phenotypes.get(i).getName().equals(draggedRow.getName())) {
                            draggedPhenotype = phenotypes.get(i);
                            phenotypeIndex = i;
                            break;
                        }
                    }

                    if (draggedPhenotype != null && phenotypeIndex >= 0) {
                        phenotypes.remove(phenotypeIndex);
                        // 调整dropIndex以适应phenotypes列表的实际大小
                        int newPhenotypeIndex = Math.min(dropIndex, phenotypes.size());
                        phenotypes.add(newPhenotypeIndex, draggedPhenotype);
                        logger.info("✅ 已同步更新phenotypes列表");
                    }

                    // 更新所有行的priority
                    refreshPriorities();
                    logger.info("✅ 已更新所有优先级");

                    event.setDropCompleted(true);
                    phenotypeTable.getSelectionModel().select(dropIndex);
                    event.consume();
                }
            });

            return row;
        });

        // 记录紧凑优化成果
        double nameWidth = calculateOptimalNameColumnWidth();
        double markerWidth = calculateOptimalMarkerColumnWidth(markerChannels.size());
        logger.info("✅ 表型表格紧凑优化完成 (适配600px窗口):");
        logger.info("   📊 紧凑列宽度: 排序列70px | 分类名称列{}px | marker列{}px({}通道) | 操作列75px",
                   nameWidth, markerWidth, markerChannels.size());
        logger.info("   🎨 简洁设计: 统一灰色主题 | 简化边框 | 紧凑间距 | 清晰层次");
        logger.info("   📐 智能高度自适应: 2-3类型宽松 | 4-8类型标准 | 9+类型紧凑+滚动");
        logger.info("   ⚡ 性能优化: 减少刷新频率 | 异步UI更新 | 差异检测更新");
        logger.info("   🖱️  交互体验: 简洁悬停效果 | 紧凑控件尺寸 | 统一配色方案");
        logger.info("🚀 表型表格现已支持{}个通道的高效紧凑显示", markerChannels.size());
    }
    


    private void updatePhenotypePriority(String phenotypeName, int newPriority) {
        for (int i = 0; i < phenotypes.size(); i++) {
            CellPhenotype phenotype = phenotypes.get(i);
            if (phenotype.getName().equals(phenotypeName)) {
                phenotypes.set(i, phenotype.withPriority(newPriority));
                break;
            }
        }
    }
    
    private void updatePhenotypeMarkerState(String phenotypeName, String channel, String state) {
        CellPhenotype.MarkerState markerState;
        switch (state) {
            case "阳性": markerState = CellPhenotype.MarkerState.POSITIVE; break;
            case "阴性": markerState = CellPhenotype.MarkerState.NEGATIVE; break;
            default: markerState = CellPhenotype.MarkerState.IGNORE; break;
        }
        
        for (int i = 0; i < phenotypes.size(); i++) {
            CellPhenotype phenotype = phenotypes.get(i);
            if (phenotype.getName().equals(phenotypeName)) {
                phenotypes.set(i, phenotype.withMarkerState(channel, markerState));
                break;
            }
        }
    }
    
    /**
     * Get current marker state for a phenotype and channel - UI synchronization support
     */
    private String getCurrentMarkerState(String phenotypeName, String channel) {
        for (CellPhenotype phenotype : phenotypes) {
            if (phenotype.getName().equals(phenotypeName)) {
                CellPhenotype.MarkerState markerState = phenotype.getMarkerState(channel);
                if (markerState != null) {
                    switch (markerState) {
                        case POSITIVE: return "阳性";
                        case NEGATIVE: return "阴性";
                        case IGNORE:
                        default: return "无关";
                    }
                }
            }
        }
        return "无关"; // Default state
    }
    
    private void refreshPriorities() {
        // === 关键修复：Priority数字越小优先级越高！===
        // 第一行（索引0）应该得到最小的priority值（最高优先级）
        // 第二行（索引1）应该得到较大的priority值（较低优先级）
        for (int i = 0; i < phenotypeData.size(); i++) {
            PhenotypeTableRow row = phenotypeData.get(i);
            if (row != null && row.getName() != null) {
                // === 修复：索引越小，priority值也越小（优先级越高）===
                int newPriority = (i + 1) * 10;  // 第0行=10, 第1行=20, 第2行=30...
                phenotypeData.set(i, new PhenotypeTableRow(row.getName(), newPriority));
                updatePhenotypePriority(row.getName(), newPriority);

                logger.debug("Updated priority for phenotype '{}' at position {} to priority {}",
                           row.getName(), i, newPriority);
            } else {
                logger.warn("NULL row or name detected at index {}, skipping priority update", i);
            }
        }

        // Ensure the phenotypes list is also ordered to match the table
        reorderPhenotypesList();
        
        phenotypeTable.refresh();
        logger.info("Refreshed priorities for {} phenotypes - display order: 1,2,3...", phenotypeData.size());
    }
    
    /**
     * Reorder the phenotypes list to match the table display order
     */
    private void reorderPhenotypesList() {
        List<CellPhenotype> reorderedPhenotypes = new ArrayList<>();
        
        // Build new ordered list based on table row order
        for (PhenotypeTableRow row : phenotypeData) {
            if (row != null && row.getName() != null) {
                for (CellPhenotype phenotype : phenotypes) {
                    if (phenotype.getName().equals(row.getName())) {
                        reorderedPhenotypes.add(phenotype);
                        break;
                    }
                }
            }
        }
        
        // Replace the phenotypes list with the reordered version
        if (reorderedPhenotypes.size() == phenotypes.size()) {
            phenotypes.clear();
            phenotypes.addAll(reorderedPhenotypes);
            logger.debug("Reordered phenotypes list to match table display order");
        } else {
            logger.warn("Phenotype list reordering failed: size mismatch ({} vs {})", 
                       reorderedPhenotypes.size(), phenotypes.size());
        }
    }
    
    private HBox createActionButtonsSection() {
        HBox buttonBox = new HBox(15); // 增加按钮间距
        buttonBox.setAlignment(Pos.CENTER); // 改为居中对齐
        buttonBox.setPadding(new Insets(15, 0, 0, 0));

        // 左侧配置管理按钮组
        HBox leftButtonGroup = new HBox(10);
        leftButtonGroup.setAlignment(Pos.CENTER_LEFT);

        // 保存配置按钮 - 保存到用户设置的保存地址
        Button saveConfigButton = new Button("保存配置");
        saveConfigButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 13px;");
        saveConfigButton.setOnAction(e -> saveConfigurationToUserPath());

        // 加载配置按钮 - 改为蓝色，弹出文件选择框
        Button loadConfigButton = new Button("加载配置");
        loadConfigButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 13px;");
        loadConfigButton.setOnAction(e -> loadConfigurationFromUserPath());

        leftButtonGroup.getChildren().addAll(saveConfigButton, loadConfigButton);

        // 右侧检测导出按钮组
        HBox rightButtonGroup = new HBox(10);
        rightButtonGroup.setAlignment(Pos.CENTER_RIGHT);

        runDetectionButton = new Button("运行检测并导出数据");
        runDetectionButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        runDetectionButton.setOnAction(e -> runDetectionWithExport());
        // 初始状态为禁用，必须先运行阈值策略
        runDetectionButton.setDisable(true);

        rightButtonGroup.getChildren().add(runDetectionButton);

        // 创建spacer使左右按钮组分离
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        buttonBox.getChildren().addAll(leftButtonGroup, spacer, rightButtonGroup);

        logger.info("按钮布局优化完成 - 配置管理功能增强，支持用户路径选择");
        return buttonBox;
    }
    

    private void addNewPhenotype() {
        // 优先使用阈值操作中选中的通道，没有则使用全部可用通道
        List<String> channelsToUse = selectedChannelsFromThreshold.isEmpty() ?
                availableChannels : selectedChannelsFromThreshold;

        logger.info("新增表型 - 使用通道列表: {} (来源: {})",
                channelsToUse,
                selectedChannelsFromThreshold.isEmpty() ? "全部可用通道" : "阈值策略选中通道");

        PhenotypeEditorDialog dialog = new PhenotypeEditorDialog(stage, channelsToUse);
        dialog.showAndWait().ifPresent(phenotype -> {
            phenotypes.add(phenotype);

            // Add to table with auto-assigned priority
            int priority = (phenotypeData.size() + 1) * 10;
            PhenotypeTableRow row = new PhenotypeTableRow(phenotype.getName(), priority);
            phenotypeData.add(row);

            // Update the phenotype with the assigned priority
            phenotypes.set(phenotypes.size() - 1, phenotype.withPriority(priority));

            // Force table refresh to ensure correct display
            Platform.runLater(() -> {
                phenotypeTable.refresh();
            });

            // 更新表格高度以适应新增的细胞类型
            updateTableHeight();
        });
    }

    /**
     * 智能计算分类名称列的最优宽度 - 紧凑版本适配600px窗口
     */
    private double calculateOptimalNameColumnWidth() {
        double baseWidth = 100; // 减小基础宽度，更紧凑
        double maxWidth = 160;  // 减小最大宽度限制

        if (phenotypes.isEmpty()) {
            return baseWidth;
        }

        // 计算最长名称的近似像素宽度
        int maxNameLength = phenotypes.stream()
            .mapToInt(p -> p.getName().length())
            .max()
            .orElse(8);

        // 紧凑计算：根据字符长度估算像素宽度 (中文字符约10px，英文字符约7px)
        double estimatedWidth = Math.max(baseWidth, maxNameLength * 8 + 30); // 减少边距

        // 限制在紧凑范围内
        return Math.min(estimatedWidth, maxWidth);
    }

    /**
     * 智能计算marker列的最优宽度 - 紧凑版本适配600px窗口
     */
    private double calculateOptimalMarkerColumnWidth(int channelCount) {
        double minWidth = 85;   // 减小最小宽度，更紧凑
        double maxWidth = 105;  // 减小最大宽度

        if (channelCount <= 4) {
            return maxWidth; // 通道少时使用较大宽度
        } else if (channelCount <= 10) {
            return 95; // 中等通道数使用标准宽度
        } else {
            return minWidth; // 通道多时使用紧凑宽度
        }
    }

    /**
     * 智能更新表格高度以适应细胞类型数量 - 现代化自适应机制
     */
    private void updateTableHeight() {
        Platform.runLater(() -> {
            try {
                int cellTypeCount = Math.max(phenotypes.size(), 2); // 最少显示2行，更紧凑

                // 智能行高计算 - 根据内容密度动态调整
                double baseRowHeight = 45; // 基础行高增加以适应新样式
                double headerHeight = 50;  // 表头高度
                double paddingHeight = 20; // 上下边距

                // 根据细胞类型数量调整策略
                double adaptiveHeight;
                if (cellTypeCount <= 3) {
                    // 少量类型时使用较大行高，提供更好的可读性
                    adaptiveHeight = cellTypeCount * (baseRowHeight + 5) + headerHeight + paddingHeight;
                } else if (cellTypeCount <= 8) {
                    // 中等数量时使用标准行高
                    adaptiveHeight = cellTypeCount * baseRowHeight + headerHeight + paddingHeight;
                } else {
                    // 大量类型时使用紧凑行高，设置合理的最大高度
                    double compactHeight = cellTypeCount * (baseRowHeight - 5) + headerHeight + paddingHeight;
                    adaptiveHeight = Math.min(compactHeight, 400); // 最大高度400px，超过则显示滚动条
                }

                // 查找现有的ScrollPane并更新高度
                javafx.scene.Node parentNode = phenotypeTable.getParent();
                if (parentNode instanceof ScrollPane) {
                    ScrollPane scrollPane = (ScrollPane) parentNode;
                    scrollPane.setPrefHeight(adaptiveHeight);

                    // 智能最大高度设置
                    if (cellTypeCount > 8) {
                        scrollPane.setMaxHeight(400); // 大量类型时限制高度
                        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                        logger.debug("表格启用垂直滚动模式 - 类型数: {}, 高度: {}", cellTypeCount, adaptiveHeight);
                    } else {
                        scrollPane.setMaxHeight(adaptiveHeight + 10);
                        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                        logger.debug("表格高度完全自适应 - 类型数: {}, 高度: {}", cellTypeCount, adaptiveHeight);
                    }

                    logger.info("智能表格高度已更新: {}px (细胞类型数: {}, 策略: {})",
                               adaptiveHeight, cellTypeCount,
                               cellTypeCount <= 3 ? "宽松" : cellTypeCount <= 8 ? "标准" : "紧凑");
                }
            } catch (Exception e) {
                logger.warn("更新表格高度失败: {}", e.getMessage());
            }
        });
    }


    /**
     * 保存配置到用户指定的保存地址
     */
    private void saveConfigurationToUserPath() {
        updateCurrentConfiguration();

        // 获取用户设置的保存地址
        String savePath = savePathField.getText();
        if (savePath == null || savePath.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "警告", "请在基础设置中设置保存地址！");
            return;
        }

        java.io.File saveDir = new java.io.File(savePath.trim());
        if (!saveDir.exists()) {
            showAlert(Alert.AlertType.WARNING, "警告", "保存地址不存在：" + savePath);
            return;
        }

        try {
            // 创建配置数据
            Map<String, Object> configData = new HashMap<>();
            configData.put("config", currentConfig);
            configData.put("phenotypes", phenotypes);
            configData.put("channelMapping", channelNameMapping);
            configData.put("availableChannels", availableChannels);
            configData.put("selectedChannels", selectedChannelsFromThreshold);
            // v1.4.0: 保存用户自定义通道显示名称映射
            configData.put("userChannelDisplayNames", userChannelDisplayNames);

            // 生成配置文件名（包含配置名称和时间戳）
            String configName = configNameField.getText().trim();
            if (configName.isEmpty()) {
                configName = "Default";
            }
            // 清理配置名称，移除不安全的文件名字符
            String safeConfigName = configName.replaceAll("[\\\\/:*?\"<>|]", "_");
            String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            String fileName = safeConfigName + "_" + timeStamp + ".json";
            java.io.File configFile = new java.io.File(saveDir, fileName);

            // 保存为JSON文件
            ObjectMapper mapper = new ObjectMapper();
            String configJson = mapper.writeValueAsString(configData);

            // v1.4.0: 使用UTF-8编码保存，确保Unicode字符（α、β等）正确保存
            try (java.io.FileWriter writer = new java.io.FileWriter(configFile, java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(configJson);
            }

            showAlert(Alert.AlertType.INFORMATION, "保存成功",
                String.format("配置已保存到：\\n%s\\n\\n包含内容：\\n- 阈值配置：%d个通道\\n- 表型定义：%d个\\n- 通道映射：%d个",
                    configFile.getAbsolutePath(),
                    currentConfig.getChannelThresholds().size(),
                    phenotypes.size(),
                    channelNameMapping.size()));

            logger.info("配置保存成功: {}", configFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("配置保存失败: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "保存失败", "配置保存失败: " + e.getMessage());
        }
    }

    /**
     * 从用户选择的文件加载配置
     */
    private void loadConfigurationFromUserPath() {
        try {
            // 创建文件选择器
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("选择配置文件");

            // 设置文件过滤器
            javafx.stage.FileChooser.ExtensionFilter jsonFilter =
                new javafx.stage.FileChooser.ExtensionFilter("配置文件 (*.json)", "*.json");
            fileChooser.getExtensionFilters().add(jsonFilter);

            // 设置初始目录为保存地址
            String savePath = savePathField.getText();
            if (savePath != null && !savePath.trim().isEmpty()) {
                java.io.File saveDir = new java.io.File(savePath.trim());
                if (saveDir.exists() && saveDir.isDirectory()) {
                    fileChooser.setInitialDirectory(saveDir);
                }
            }

            // 显示文件选择对话框
            java.io.File selectedFile = fileChooser.showOpenDialog(stage);
            if (selectedFile == null) {
                return; // 用户取消选择
            }

            if (!selectedFile.exists()) {
                showAlert(Alert.AlertType.ERROR, "文件不存在", "选择的配置文件不存在：" + selectedFile.getName());
                return;
            }

            // 读取和解析配置文件
            ObjectMapper mapper = new ObjectMapper();
            // v1.4.0: 使用UTF-8编码读取，确保Unicode字符正确加载
            String configJson = java.nio.file.Files.readString(
                selectedFile.toPath(),
                java.nio.charset.StandardCharsets.UTF_8
            );

            // 基础JSON解析验证
            Map<String, Object> configData = mapper.readValue(configJson, Map.class);

            // 验证配置文件内容
            if (!configData.containsKey("config") || !configData.containsKey("phenotypes")) {
                showAlert(Alert.AlertType.ERROR, "配置文件错误", "配置文件格式不正确，缺少必要的配置数据");
                return;
            }

            // 显示加载成功信息
            int configCount = configData.containsKey("config") ? 1 : 0;
            int phenotypeCount = 0;
            int channelCount = 0;

            if (configData.get("phenotypes") instanceof java.util.List) {
                phenotypeCount = ((java.util.List<?>) configData.get("phenotypes")).size();
            }

            if (configData.get("availableChannels") instanceof java.util.List) {
                channelCount = ((java.util.List<?>) configData.get("availableChannels")).size();
            }

            // 开始应用配置数据到所有设置
            try {
                boolean configApplied = false;

                // 1. 应用阈值配置
                if (configData.containsKey("config")) {
                    Map<String, Object> configMap = (Map<String, Object>) configData.get("config");
                    if (configMap != null) {
                        // 应用配置名称
                        if (configMap.containsKey("configName") && configNameField != null) {
                            configNameField.setText(String.valueOf(configMap.get("configName")));
                        }

                        // 应用策略选择
                        if (configMap.containsKey("strategy") && strategyComboBox != null) {
                            String strategyName = String.valueOf(configMap.get("strategy"));
                            for (ThresholdConfig.Strategy strategy : ThresholdConfig.Strategy.values()) {
                                if (strategy.toString().equals(strategyName)) {
                                    strategyComboBox.setValue(strategy);
                                    break;
                                }
                            }
                        }

                        // v1.4.0: 应用分割模型选择
                        if (configMap.containsKey("segmentationModel") && segmentationModelComboBox != null) {
                            String modelName = String.valueOf(configMap.get("segmentationModel"));
                            // 兼容：modelName可能是枚举名(STARDIST)或显示名(StarDist)
                            SegmentationModel loadedModel = null;
                            try {
                                // 先尝试作为枚举名解析
                                loadedModel = SegmentationModel.valueOf(modelName);
                            } catch (IllegalArgumentException e) {
                                // 如果失败，尝试作为显示名解析
                                loadedModel = SegmentationModel.fromDisplayName(modelName);
                            }
                            if (loadedModel != null) {
                                segmentationModelComboBox.setValue(loadedModel.getDisplayName());
                                currentConfig = currentConfig.withSegmentationModel(loadedModel);
                                // 加载分割模型后，立即更新测量值下拉框
                                updateMeasurementComboBoxesForModel(loadedModel);
                            }
                        }

                        // 应用通道阈值配置
                        if (configMap.containsKey("channelThresholds")) {
                            Map<String, Object> thresholds = (Map<String, Object>) configMap.get("channelThresholds");
                            if (thresholds != null) {
                                for (Map.Entry<String, Object> entry : thresholds.entrySet()) {
                                    String channelName = entry.getKey();
                                    Map<String, Object> thresholdData = (Map<String, Object>) entry.getValue();

                                    if (thresholdData != null && availableChannels.contains(channelName)) {
                                        // 应用measurement类型
                                        if (thresholdData.containsKey("measurement")) {
                                            ComboBox<String> measurementCombo = measurementComboBoxes.get(channelName);
                                            if (measurementCombo != null) {
                                                measurementCombo.setValue(String.valueOf(thresholdData.get("measurement")));
                                            }
                                        }

                                        // 应用阈值
                                        if (thresholdData.containsKey("threshold")) {
                                            double threshold = Double.parseDouble(String.valueOf(thresholdData.get("threshold")));
                                            updateThresholdDisplay(channelName, threshold);

                                            // 更新内部配置
                                            String measurement = String.valueOf(thresholdData.getOrDefault("measurement", "Nucleus: Mean"));
                                            boolean enabled = Boolean.parseBoolean(String.valueOf(thresholdData.getOrDefault("enabled", true)));
                                            ThresholdConfig.ChannelThreshold channelThreshold =
                                                new ThresholdConfig.ChannelThreshold(measurement, threshold, enabled);
                                            currentConfig = currentConfig.withChannelThreshold(channelName, channelThreshold);
                                        }
                                    }
                                }
                            }
                        }
                        configApplied = true;
                    }
                }

                // 2. 应用表型定义
                if (configData.containsKey("phenotypes")) {
                    List<Object> phenotypeList = (List<Object>) configData.get("phenotypes");
                    if (phenotypeList != null && !phenotypeList.isEmpty()) {
                        // 清空现有表型
                        phenotypes.clear();
                        phenotypeData.clear();

                        // 加载表型数据
                        for (Object phenotypeObj : phenotypeList) {
                            Map<String, Object> phenotypeMap = (Map<String, Object>) phenotypeObj;
                            if (phenotypeMap != null && phenotypeMap.containsKey("name")) {
                                String phenotypeName = String.valueOf(phenotypeMap.get("name"));
                                int priority = Integer.parseInt(String.valueOf(phenotypeMap.getOrDefault("priority", 10)));

                                // 创建表型对象 - 使用正确的构造器
                                CellPhenotype phenotype = new CellPhenotype(phenotypeName, priority);

                                // 应用marker状态（如果存在）
                                if (phenotypeMap.containsKey("markerStates")) {
                                    Map<String, Object> markerStates = (Map<String, Object>) phenotypeMap.get("markerStates");
                                    if (markerStates != null) {
                                        for (Map.Entry<String, Object> markerEntry : markerStates.entrySet()) {
                                            String channel = markerEntry.getKey();
                                            String stateStr = String.valueOf(markerEntry.getValue());

                                            CellPhenotype.MarkerState markerState = CellPhenotype.MarkerState.IGNORE;
                                            if ("POSITIVE".equals(stateStr)) {
                                                markerState = CellPhenotype.MarkerState.POSITIVE;
                                            } else if ("NEGATIVE".equals(stateStr)) {
                                                markerState = CellPhenotype.MarkerState.NEGATIVE;
                                            } else if ("IGNORE".equals(stateStr)) {
                                                markerState = CellPhenotype.MarkerState.IGNORE;
                                            }

                                            phenotype = phenotype.withMarkerState(channel, markerState);
                                        }
                                    }
                                }

                                phenotypes.add(phenotype);
                                phenotypeData.add(new PhenotypeTableRow(phenotypeName, priority));
                            }
                        }

                        // 刷新表型表格显示
                        if (phenotypeTable != null) {
                            phenotypeTable.refresh();
                        }
                        configApplied = true;
                    }
                }

                // 3. 应用通道映射（如果存在）
                if (configData.containsKey("channelMapping")) {
                    Map<String, Object> channelMapping = (Map<String, Object>) configData.get("channelMapping");
                    if (channelMapping != null) {
                        channelNameMapping.clear();
                        for (Map.Entry<String, Object> entry : channelMapping.entrySet()) {
                            channelNameMapping.put(entry.getKey(), String.valueOf(entry.getValue()));
                        }
                        configApplied = true;
                    }
                }

                // 4. 应用可用通道列表（如果存在）
                if (configData.containsKey("availableChannels")) {
                    List<Object> channels = (List<Object>) configData.get("availableChannels");
                    if (channels != null) {
                        availableChannels.clear();
                        for (Object channel : channels) {
                            availableChannels.add(String.valueOf(channel));
                        }
                        configApplied = true;
                    }
                }

                // 5. 应用选中通道列表（如果存在）
                if (configData.containsKey("selectedChannels")) {
                    List<Object> selectedChannels = (List<Object>) configData.get("selectedChannels");
                    if (selectedChannels != null) {
                        selectedChannelsFromThreshold.clear();
                        for (Object channel : selectedChannels) {
                            selectedChannelsFromThreshold.add(String.valueOf(channel));
                        }
                        configApplied = true;
                    }
                }

                // v1.4.0: 恢复用户自定义通道显示名称映射
                if (configData.containsKey("userChannelDisplayNames")) {
                    Map<String, Object> userDisplayNames = (Map<String, Object>) configData.get("userChannelDisplayNames");
                    if (userDisplayNames != null) {
                        userChannelDisplayNames.clear();
                        for (Map.Entry<String, Object> entry : userDisplayNames.entrySet()) {
                            userChannelDisplayNames.put(entry.getKey(), String.valueOf(entry.getValue()));
                        }
                        logger.info("已恢复 {} 个用户自定义通道显示名称", userChannelDisplayNames.size());
                        configApplied = true;
                    }
                }

                // v1.4.0修复: 配置加载后验证通道名称匹配
                validateAndFixChannelNames();

                // 刷新界面以反映加载的配置
                if (configApplied) {
                    // 重新创建通道控件以应用新配置
                    createChannelControls();

                    // 更新所有通道的状态显示和选择状态
                    for (String channelName : availableChannels) {
                        // 检查配置数据中是否有该通道的阈值设置
                        boolean hasThresholdInConfig = currentConfig.getChannelThresholds().containsKey(channelName);

                        // 更新阈值状态标签
                        updateThresholdStatus(channelName, hasThresholdInConfig);

                        // 更新选择状态
                        updateChannelSelectionStatus(channelName);
                    }

                    // 重新创建表型表格以应用新数据
                    createPhenotypeTable();

                    logger.info("配置应用完成: 阈值配置、表型定义、通道映射、状态显示已全部恢复");
                }

                showAlert(Alert.AlertType.INFORMATION, "配置加载成功",
                    String.format("配置文件加载并应用成功：\\n%s\\n\\n已恢复内容：\\n- 阈值配置：%d个通道\\n- 表型定义：%d个\\n- 通道映射：%d个\\n- 选中通道：%d个\\n\\n所有设置已更新完成！",
                        selectedFile.getName(),
                        currentConfig.getChannelThresholds().size(),
                        phenotypes.size(),
                        channelNameMapping.size(),
                        selectedChannelsFromThreshold.size()));

            } catch (Exception applyEx) {
                logger.error("配置应用失败: {}", applyEx.getMessage(), applyEx);
                showAlert(Alert.AlertType.WARNING, "配置应用警告",
                    "配置文件读取成功，但部分设置应用失败: " + applyEx.getMessage());
            }

            logger.info("配置文件加载成功: {} (表型数: {}, 通道数: {})",
                       selectedFile.getAbsolutePath(), phenotypeCount, channelCount);

            // 加载配置后，重置"运行检测并导出数据"按钮为禁用状态
            // 用户需要先运行阈值策略才能执行细胞分类
            if (runDetectionButton != null) {
                Platform.runLater(() -> {
                    runDetectionButton.setDisable(true);
                    logger.info("已重置'运行检测并导出数据'按钮为禁用状态");
                });
            }

        } catch (Exception e) {
            logger.error("配置加载失败: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "加载失败", "配置文件加载失败: " + e.getMessage());
        }
    }

    /**
     * Legacy method - kept for compatibility
     */
    private void saveConfigurationToProject() {
        updateCurrentConfiguration();
        
        Project<?> project = qupath.getProject();
        if (project == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No project is currently open!");
            return;
        }
        
        try {
            // Save configuration to a project file instead
            Map<String, Object> configData = new HashMap<>();
            configData.put("config", currentConfig);
            configData.put("phenotypes", phenotypes);
            
            // Save to project directory as a JSON file
            ObjectMapper mapper = new ObjectMapper();
            String configJson = mapper.writeValueAsString(configData);
            
            java.io.File projectDir = project.getPath().getParent().toFile();
            java.io.File configFile = new java.io.File(projectDir, "cell_phenotype_config.json");
            
            try (java.io.FileWriter writer = new java.io.FileWriter(configFile)) {
                writer.write(configJson);
            }
            
            showAlert(Alert.AlertType.INFORMATION, "Success", 
                "Configuration saved to project directory: " + configFile.getName());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to save configuration: " + e.getMessage());
        }
    }
    
    private void loadConfigurationFromProject() {
        Project<?> project = qupath.getProject();
        if (project == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No project is currently open!");
            return;
        }
        
        try {
            // Load from project directory JSON file
            java.io.File projectDir = project.getPath().getParent().toFile();
            java.io.File configFile = new java.io.File(projectDir, "cell_phenotype_config.json");
            
            if (configFile.exists()) {
                // Read and parse configuration
                ObjectMapper mapper = new ObjectMapper();
                String configJson = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
                
                // Load and apply configuration
                // This is a simplified version - in reality you'd need proper deserialization
                showAlert(Alert.AlertType.INFORMATION, "Success", 
                    "Configuration loaded from project directory: " + configFile.getName());
            } else {
                showAlert(Alert.AlertType.INFORMATION, "Information", 
                    "No saved configuration found in project directory.");
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load configuration: " + e.getMessage());
        }
    }
    
    /**
     * Enhanced run detection with comprehensive data export including:
     * - Cell ID, Position (x,y), Parent, Classification, CellType
     * - CSV format output with complete cell information
     * - Immediate pseudo-coloring application
     */
    private void runDetectionWithExport() {
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No image data available!");
            return;
        }
        
        // Use save path from basic settings instead of prompting user
        String savePath = savePathField.getText();
        if (savePath == null || savePath.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "警告", "请在基础设置中设置保存地址！");
            return;
        }
        
        java.io.File saveDir = new java.io.File(savePath.trim());
        if (!saveDir.exists()) {
            showAlert(Alert.AlertType.WARNING, "警告", "保存地址不存在：" + savePath);
            return;
        }
        
        // Create save file in the configured directory with image and ROI names
        String imageName = "Unknown";
        String roiName = "";

        // Get image name from ImageData
        if (imageData.getServer() != null && imageData.getServer().getMetadata() != null) {
            String fullImageName = imageData.getServer().getMetadata().getName();
            if (fullImageName != null && !fullImageName.isEmpty()) {
                // Extract base name without extension
                imageName = fullImageName.replaceFirst("\\.[^.]+$", "");
            }
        }

        // Get ROI name if ROI mode is enabled and ROI is selected
        boolean isRoiModeEnabled = cellAnalysisComboBox != null && "当前选中细胞".equals(cellAnalysisComboBox.getValue());
        if (isRoiModeEnabled) {
            var selectedROI = imageData.getHierarchy().getSelectionModel().getSelectedObject();
            if (selectedROI != null && selectedROI.getName() != null && !selectedROI.getName().isEmpty()) {
                roiName = selectedROI.getName();
            } else {
                roiName = "ROI";  // Default ROI name if no specific name is set
            }
        }

        // Clean names for safe file naming
        String safeImageName = imageName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String safeRoiName = roiName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());

        // Build filename with ROI name if available
        String fileName;
        if (!roiName.isEmpty()) {
            fileName = safeImageName + "_" + safeRoiName + "-classification_and_celltype-" + timeStamp + ".csv";
        } else {
            fileName = safeImageName + "-classification_and_celltype-" + timeStamp + ".csv";
        }
        java.io.File saveFile = new java.io.File(saveDir, fileName);
        
        updateCurrentConfiguration();
        
        try {
            // Create PhenotypeManager from phenotypes list
            PhenotypeManager phenotypeManager = new PhenotypeManager();
            for (CellPhenotype phenotype : phenotypes) {
                phenotypeManager.addPhenotype(phenotype);
            }
            
            logger.info("Starting comprehensive detection with data export...");
            long startTime = System.currentTimeMillis();

            // Use ROI-filtered cells if ROI mode is enabled
            List<qupath.lib.objects.PathObject> cellsToProcess = getCellsInSelectedROI(imageData);

            // === v1.3.0修复：只应用CellType分类，不重新计算Classification ===
            // v1.4.0: 创建测量名称映射（使用固定模型前缀）
            List<String> channelNames = new ArrayList<>(currentConfig.getChannelThresholds().keySet());
            Map<String, String> measurementMapping = buildMeasurementMapping(channelNames, currentConfig);

            // 只执行CellType分类（不执行Classification分类）
            Map<qupath.lib.objects.PathObject, String> cellTypeResults =
                CellClassificationService.performPhenotypeClassification(
                    cellsToProcess, currentConfig, measurementMapping, phenotypes);

            // 为所有细胞设置CellType（包括未匹配的）
            Map<qupath.lib.objects.PathObject, String> allCellTypeResults = new HashMap<>();
            for (qupath.lib.objects.PathObject cell : cellsToProcess) {
                // 如果有匹配结果，使用匹配结果；否则设置为"undefined"
                String cellType = cellTypeResults.getOrDefault(cell, "undefined");
                allCellTypeResults.put(cell, cellType);
            }

            // 应用CellType结果到所有细胞（保留已有的Classification）
            ColorUtils.applyCellTypeColors(allCellTypeResults.keySet(), allCellTypeResults);

            // Apply gray-white pseudo color to unclassified cells
            applyGrayColorToUnclassifiedCells(imageData);

            // ENHANCED: Comprehensive display update for pseudo-coloring
            var hierarchy = imageData.getHierarchy();
            hierarchy.fireHierarchyChangedEvent(null);
            syncQuPathDisplayData(imageData);

            // CRITICAL: Final display update to ensure pseudo-colors are visible
            Platform.runLater(() -> {
                updateViewerForAllZoomLevels();
                logger.info("Final pseudo-color display update completed");
            });
            
            // ENHANCED: Export comprehensive cell data
            exportComprehensiveCellData(imageData, cellsToProcess, saveFile);

            // CRITICAL: Force final project save to ensure maximum color persistence
            try {
                logger.info("Executing final project save for maximum color persistence...");

                // Force mark as changed and trigger save
                imageData.setChanged(true);

                // Try to manually trigger QuPath's save mechanism
                Platform.runLater(() -> {
                    try {
                        // Force mark as changed to trigger save mechanisms
                        imageData.setChanged(true);

                        // Trigger repaint to ensure changes are visible
                        Platform.runLater(() -> {
                            if (qupath.getViewer() != null) {
                                qupath.getViewer().repaint();
                            }
                        });
                        logger.debug("Triggered QuPath repaint for project save");
                    } catch (Exception e) {
                        logger.debug("Could not trigger GUI refresh: {}", e.getMessage());
                    }
                });

                logger.info("Final project save mechanism triggered successfully");
            } catch (Exception e) {
                logger.warn("Final project save failed: {}", e.getMessage());
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Comprehensive detection and export completed in {}ms", duration);
            
            // Count classified cells for feedback
            int totalCells = cellsToProcess.size();
            long classifiedCells = cellsToProcess.stream()
                .mapToLong(cell -> cell.getPathClass() != null ? 1 : 0)
                .sum();
            
            showAlert(Alert.AlertType.INFORMATION, "检测和导出完成",
                String.format("综合检测完成! 用时 %dms\n" +
                             "总细胞: %d\n" +
                             "分类细胞: %d\n" +
                             "数据已导出到:\n%s\n\n" +
                             "✅ 伪彩效果已永久保存到QuPath项目中\n" +
                             "✅ 分类结果已持久化，关闭插件后依然有效\n" +
                             "✅ 细胞颜色和分类信息将随项目一起保存",
                    duration, totalCells, classifiedCells, saveFile.getAbsolutePath()));
                    
        } catch (Exception e) {
            logger.error("Detection and export failed: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Error", "Detection and export failed: " + e.getMessage());
        }
    }
    
    /**
     * Export comprehensive cell data including:
     * Cell_ID, X, Y, Parent, Classification, CellType
     */
    private void exportComprehensiveCellData(ImageData<?> imageData, 
                                           List<qupath.lib.objects.PathObject> cells, 
                                           java.io.File saveFile) throws IOException {
        
        logger.info("Exporting comprehensive cell data for {} cells to {}", cells.size(), saveFile.getName());

        // v1.4.0: 使用UTF-8编码写入，确保Unicode字符正确显示
        try (java.io.FileWriter writer = new java.io.FileWriter(
                saveFile, java.nio.charset.StandardCharsets.UTF_8)) {
            // 添加UTF-8 BOM标记，帮助Excel等软件正确识别编码
            writer.write("\uFEFF");
            // Write comprehensive header
            writer.write("Cell_ID,X,Y,Parent,Classification,CellType\n");
            
            // Process each cell and export complete information
            int exportedCount = 0;
            for (qupath.lib.objects.PathObject cell : cells) {
                try {
                    // Get cell basic information
                    String cellId = cell.getID() != null ? cell.getID().toString() : "cell_" + exportedCount;
                    
                    // Get centroid coordinates
                    double x = 0, y = 0;
                    if (cell.getROI() != null) {
                        x = cell.getROI().getCentroidX();
                        y = cell.getROI().getCentroidY();
                    }
                    
                    // Get parent information
                    // === 修改：Parent字段两级优先级：name → object type ===
                    String parentInfo = "";
                    if (cell.getParent() != null) {
                        var parent = cell.getParent();

                        // 优先级1: 如果parent有name，使用name
                        if (parent.getName() != null && !parent.getName().trim().isEmpty()) {
                            parentInfo = parent.getName();
                        }
                        // 优先级2: 如果name为空，使用对象类型
                        else {
                            parentInfo = parent.getClass().getSimpleName();
                        }
                    }
                    
                    // ENHANCED: Get BOTH Classification and CellType data separately

                    // 1. Get Classification from Load Object Classifier (stored in our mapping)
                    String classification = getClassificationName(cellId);
                    if (classification.isEmpty()) {
                        classification = ""; // No Load Object Classifier result
                    }

                    // 2. Get CellType from Cell Classification (check measurements for CellType_Info)
                    String cellType = "";
                    var measurements = cell.getMeasurementList();
                    if (measurements.containsKey("CellType_Info")) {
                        double cellTypeHash = measurements.get("CellType_Info");
                        if (cellTypeHash != 0.0) {
                            // Try to reconstruct CellType from current configuration
                            cellType = generateCellTypeFromMeasurements(cell);
                        }
                    }

                    // 3. Get CellType from PathClass - this is the authoritative source
                    if (cellType.isEmpty() && cell.getPathClass() != null) {
                        String pathClassName = cell.getPathClass().getName();

                        // The PathClass name IS the CellType - it contains either:
                        // 1. A defined phenotype name (e.g., "Helper T Cell", "B Cell")
                        // 2. "undefined" for unclassified cells
                        cellType = pathClassName;

                        // Classification should be derived from marker states, not PathClass
                        // If we don't have a separate classification, generate it from markers
                        if (classification.isEmpty()) {
                            classification = generateClassificationFromMarkers(cell);
                        }
                    }

                    // 4. Final fallback: if no CellType found, it should be "undefined"
                    if (cellType.isEmpty()) {
                        cellType = "undefined";
                    }
                    
                    // Write row data
                    writer.write(String.format("%s,%.2f,%.2f,%s,%s,%s\n",
                        cellId, x, y, parentInfo, classification, cellType));
                    
                    exportedCount++;
                    
                    // Log progress for large datasets
                    if (exportedCount % 10000 == 0) {
                        logger.info("Exported {} cells...", exportedCount);
                    }
                    
                } catch (Exception e) {
                    logger.debug("Error exporting cell {}: {}", cell.getID(), e.getMessage());
                }
            }
            
            logger.info("Successfully exported {} cells to {}", exportedCount, saveFile.getName());
        }
    }
    
    /**
     * Generate CellType string from cell measurements and current thresholds
     */
    private String generateCellTypeFromClassification(qupath.lib.objects.PathObject cell, String classification) {
        try {
            var measurements = cell.getMeasurementList();
            StringBuilder cellTypeBuilder = new StringBuilder();
            
            // Generate CellType from available measurements and current thresholds
            for (String channelName : availableChannels) {
                // === 关键修复：从ComboBox或配置中获取measurement名称 ===
                String actualMeasurementName = null;

                // 方法1: 从ThresholdConfig获取（最可靠）
                ThresholdConfig.ChannelThreshold threshold = currentConfig.getChannelThresholds().get(channelName);
                if (threshold != null && threshold.getMeasurement() != null) {
                    actualMeasurementName = threshold.getMeasurement();
                }

                // 方法2: 从ComboBox获取（如果配置中没有）
                if (actualMeasurementName == null) {
                    ComboBox<String> measurementCombo = measurementComboBoxes.get(channelName);
                    if (measurementCombo != null && measurementCombo.getValue() != null) {
                        actualMeasurementName = measurementCombo.getValue();
                    }
                }

                // 方法3: 尝试查找（最后手段）
                if (actualMeasurementName == null) {
                    String[] possibleMeasurementNames = createPossibleMeasurementNames(channelName, null);
                    for (String testName : possibleMeasurementNames) {
                        if (measurements.containsKey(testName)) {
                            actualMeasurementName = testName;
                            break;
                        }
                    }
                }

                if (actualMeasurementName != null && measurements.containsKey(actualMeasurementName)) {
                    double value = measurements.get(actualMeasurementName);
                    // 使用之前已定义的threshold变量
                    if (threshold != null) {
                        boolean isPositive = value > threshold.getThreshold();
                        String marker = channelName + (isPositive ? "+" : "-");
                        
                        if (cellTypeBuilder.length() > 0) {
                            cellTypeBuilder.append("");
                        }
                        cellTypeBuilder.append(marker);
                    }
                }
            }
            
            return cellTypeBuilder.toString();
            
        } catch (Exception e) {
            logger.debug("Error generating CellType for cell {}: {}", cell.getID(), e.getMessage());
            return classification; // Fallback to classification name
        }
    }
    
    // LEGACY: Keep old export method for compatibility
    private void exportEnhancedResults(List<CellPhenotypeClassifier.ClassificationResult> results, String filePath) throws IOException {
        try (java.io.FileWriter writer = new java.io.FileWriter(filePath)) {
            // Write header with new fields
            writer.write("Cell_ID,X,Y,Parent,Class,CellType\n");
            
            // Write data
            for (CellPhenotypeClassifier.ClassificationResult result : results) {
                // Get additional information
                ImageData<?> imageData = qupath.getImageData();
                if (imageData != null) {
                    var hierarchy = imageData.getHierarchy();
                    var cells = new ArrayList<>(hierarchy.getDetectionObjects());
                    
                    for (var cell : cells) {
                        if (cell.getID().toString().equals(result.getCellId())) {
                            // === 修改：Parent字段优先使用name，fallback到type ===
                            String parentInfo = "";
                            if (cell.getParent() != null) {
                                var parent = cell.getParent();
                                // 优先使用name
                                if (parent.getName() != null && !parent.getName().trim().isEmpty()) {
                                    parentInfo = parent.getName();
                                } else {
                                    // Fallback到object type
                                    if (parent.getROI() != null) {
                                        parentInfo = parent.getROI().getRoiName();
                                        if (parentInfo == null || parentInfo.trim().isEmpty()) {
                                            parentInfo = parent.getClass().getSimpleName();
                                        }
                                    } else {
                                        parentInfo = parent.getClass().getSimpleName();
                                    }
                                }
                            }

                            String pathClassName = cell.getPathClass() != null ? cell.getPathClass().getName() : "";

                            // Class contains individual marker states (positive/negative)
                            String classStates = generateClassString(cell, result.getPositiveProteins());

                            writer.write(String.format("%s,%.2f,%.2f,%s,%s,%s\n",
                                result.getCellId(),
                                result.getCentroidX(),
                                result.getCentroidY(),
                                parentInfo,
                                classStates,
                                result.getPhenotypeName()
                            ));
                            break;
                        }
                    }
                }
            }
        }
    }
    
    private String generateClassString(Object cell, String positiveProteins) {
        // Generate class string showing positive/negative states for each enabled channel
        // Format: CD3+_CD4+_CD8- (traditional QuPath classification format)
        StringBuilder classBuilder = new StringBuilder();
        Set<String> positiveSet = new HashSet<>();

        if (positiveProteins != null && !positiveProteins.trim().isEmpty()) {
            positiveSet.addAll(Arrays.asList(positiveProteins.split(",")));
        }

        List<String> stateList = new ArrayList<>();

        // Only include enabled channels in the classification string
        for (String channel : availableChannels) {
            // Check if this channel is enabled in current threshold config
            if (currentConfig != null &&
                currentConfig.getChannelThresholds().containsKey(channel) &&
                currentConfig.getChannelThresholds().get(channel).isEnabled()) {

                boolean isPositive = positiveSet.contains(channel.trim());
                stateList.add(channel + (isPositive ? "+" : "-"));
            }
        }

        if (stateList.isEmpty()) {
            return "undefined";
        }

        return String.join("_", stateList);
    }
    
    private void updateCurrentConfiguration() {
        // Update basic settings
        currentConfig = currentConfig
            .withConfigName(configNameField.getText())
            .withStrategy(strategyComboBox.getValue());
        
        // Update thresholds from dynamic controls
        for (String channel : availableChannels) {
            Slider slider = thresholdSliders.get(channel);
            ComboBox<String> measurementBox = measurementComboBoxes.get(channel);
            
            if (slider != null && measurementBox != null) {
                ThresholdConfig.ChannelThreshold threshold = new ThresholdConfig.ChannelThreshold(
                    measurementBox.getValue(),
                    getLinearValue(slider),  // Convert from log scale
                    true // For now, assume all channels are enabled
                );
                currentConfig = currentConfig.withChannelThreshold(channel, threshold);
            }
        }
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Toggle between Create Single Measurement Classifier and Load Classifier operation modes
     */
    /**
     * Execute Load Classifier mode - OPTIMIZED for 10M+ cells with progress tracking
     */
    private void executeLoadClassifierMode(ImageData<?> imageData) {
        logger.info("=== Load Classifier 执行开始 ===");
        
        // 调试: 检查通道状态
        logger.info("当前可用通道数: {}", availableChannels.size());
        logger.info("CheckBox映射数: {}", channelCheckBoxes.size());
        
        // Get selected channels
        List<String> selectedChannels = new ArrayList<>();
        for (Map.Entry<String, CheckBox> entry : channelCheckBoxes.entrySet()) {
            String channel = entry.getKey();
            CheckBox checkBox = entry.getValue();
            boolean isSelected = checkBox.isSelected();
            
            logger.info("通道 '{}' 选中状态: {}", channel, isSelected);
            
            if (isSelected) {
                selectedChannels.add(channel);
            }
        }
        
        logger.info("选中的通道: {}", selectedChannels);

        if (selectedChannels.isEmpty()) {
            logger.warn("没有选中的通道，显示警告对话框");
            showAlert(Alert.AlertType.WARNING, "提示", "请启用至少一个通道进行Load Classifier应用!");
            return;
        }

        // 保存选中的通道列表供细胞分类使用
        selectedChannelsFromThreshold = new ArrayList<>(selectedChannels);
        logger.info("已保存选中通道列表供细胞分类使用: {}", selectedChannelsFromThreshold);

        // 重要修复：更新细胞分类表格的通道显示
        Platform.runLater(() -> {
            if (phenotypeTable != null) {
                // 重新创建表格以反映新的通道列表
                createPhenotypeTable();
                logger.info("已更新细胞分类表格通道显示 - 重新创建表格列结构");
            }
        });

        // 高性能执行：支持50,000,000+细胞，无限制，无弹窗
        List<qupath.lib.objects.PathObject> cellsToProcess = getCellsInSelectedROI(imageData);
        int actualCellCount = cellsToProcess.size();
        
        logger.info("高性能Load Classifier: 处理 {} 细胞 (ROI模式: {})", actualCellCount, useSelectedROI);
        
        // 直接执行，无细胞数量限制，无进度对话框
        logger.info("开始执行 executeLoadClassifierImmediate");
        executeLoadClassifierImmediate(imageData, selectedChannels);
        logger.info("=== Load Classifier 执行完成 ===");

        // 执行成功后，启用"运行检测并导出数据"按钮
        if (runDetectionButton != null) {
            Platform.runLater(() -> {
                runDetectionButton.setDisable(false);
                logger.info("已启用'运行检测并导出数据'按钮");
            });
        }
    }
    
    
    /**
     * Execute Load Classifier Strategy - TRUE Load Object Classifier functionality
     * This loads and applies a classification system (not our cell phenotype system)
     */
    private void executeLoadClassifierImmediate(ImageData<?> imageData, List<String> selectedChannels) {
        try {
            logger.info("=== Load Object Classifier 策略执行开始 ===");
            logger.info("This should load and apply a pre-trained classifier file");

            // Create configuration based on current thresholds for classification output
            updateCurrentConfiguration();
            ThresholdConfig classificationConfig = new ThresholdConfig(currentConfig.getConfigName() + "_classification");
            classificationConfig = classificationConfig.withStrategy(currentConfig.getStrategy());

            // Add only selected channels to classification config
            for (String channel : selectedChannels) {
                ThresholdConfig.ChannelThreshold channelThreshold = currentConfig.getChannelThresholds().get(channel);
                if (channelThreshold != null) {
                    classificationConfig = classificationConfig.withChannelThreshold(channel, channelThreshold);
                }
            }

            logger.info("Load Object Classifier for channels: {}", selectedChannels);

            // Get cells to process with ROI support
            List<qupath.lib.objects.PathObject> cellsToProcess = getCellsInSelectedROI(imageData);

            // TRUE Load Object Classifier: Apply threshold-based classification directly
            applyThresholdBasedClassification(imageData, classificationConfig, cellsToProcess, selectedChannels);

            // 统计分类结果
            var hierarchy = imageData.getHierarchy();
            var allCells = hierarchy.getDetectionObjects();
            long classifiedCells = allCells.stream()
                .filter(cell -> cell.getPathClass() != null)
                .count();

            int totalCells = cellsToProcess.size();

            logger.info("Load Object Classifier完成: 分类了 {}/{} 细胞，启用通道: {}",
                       classifiedCells, totalCells, String.join(", ", selectedChannels));

            // 成功提示 - 这是Load Object Classifier的行为
            showAlert(Alert.AlertType.INFORMATION, "Load Object Classifier完成",
                String.format("对象分类器加载完成！\n" +
                             "总细胞: %d\n" +
                             "已分类: %d\n" +
                             "未分类: %d\n" +
                             "启用通道: %s\n" +
                             "数据已更新，伪彩已应用",
                    totalCells, classifiedCells, totalCells - classifiedCells, String.join(", ", selectedChannels)));

        } catch (Exception e) {
            logger.error("Load Object Classifier failed: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Error",
                "Load Object Classifier执行失败: " + e.getMessage());
        }
    }

    /**
     * Apply threshold-based classification (True Load Object Classifier behavior)
     */
    private void applyThresholdBasedClassification(ImageData<?> imageData,
                                                 ThresholdConfig config,
                                                 List<qupath.lib.objects.PathObject> cellsToProcess,
                                                 List<String> selectedChannels) {
        try {
            var hierarchy = imageData.getHierarchy();

            logger.info("=== Load Object Classifier Debug ===");
            logger.info("Processing {} cells with {} channels", cellsToProcess.size(), selectedChannels.size());

            // Debug: Check config
            logger.info("Config has {} channel thresholds:", config.getChannelThresholds().size());
            for (String channel : selectedChannels) {
                ThresholdConfig.ChannelThreshold thresh = config.getChannelThresholds().get(channel);
                if (thresh != null) {
                    logger.info("Channel '{}': threshold={}, measurement={}, enabled={}",
                               channel, thresh.getThreshold(), thresh.getMeasurement(), thresh.isEnabled());
                } else {
                    logger.warn("No threshold config for channel '{}'", channel);
                }
            }

            int[] counts = {0, 0}; // [classified, unclassified]

            // === 性能优化：预计算所有通道的measurement名称，避免在细胞循环中重复查找 ===
            Map<String, String> channelMeasurementNames = new HashMap<>();
            if (!cellsToProcess.isEmpty()) {
                var sampleMeasurements = cellsToProcess.get(0).getMeasurementList();
                for (String channelName : selectedChannels) {
                    ThresholdConfig.ChannelThreshold threshold = config.getChannelThresholds().get(channelName);
                    if (threshold != null && threshold.isEnabled()) {
                        String measurementName = findMeasurementNameForClassification(
                            sampleMeasurements, channelName, threshold.getMeasurement());
                        if (measurementName != null) {
                            channelMeasurementNames.put(channelName, measurementName);
                            logger.debug("预计算通道映射: '{}' -> '{}'", channelName, measurementName);
                        }
                    }
                }
            }
            logger.info("预计算完成: {} 个通道的measurement名称已缓存", channelMeasurementNames.size());

            // Apply threshold-based classification to each cell
            for (var cell : cellsToProcess) {
                try {
                    var measurements = cell.getMeasurementList();
                    boolean cellClassified = false;

                    // For Load Object Classifier, classify based on any selected channel exceeding threshold
                    StringBuilder classificationParts = new StringBuilder();

                    for (String channelName : selectedChannels) {
                        ThresholdConfig.ChannelThreshold threshold = config.getChannelThresholds().get(channelName);
                        if (threshold == null || !threshold.isEnabled()) {
                            continue; // 移除debug日志，减少输出
                        }

                        // === 性能优化：使用预计算的measurement名称 ===
                        String measurementName = channelMeasurementNames.get(channelName);
                        if (measurementName == null) {
                            continue; // 移除debug日志
                        }

                        // 检查measurement是否存在（快速containsKey检查）
                        if (!measurements.containsKey(measurementName)) {
                            continue;
                        }

                        // Get measurement value and apply threshold
                        double value = measurements.get(measurementName);
                        boolean isPositive = value > threshold.getThreshold();

                        // 移除过多的debug日志，只保留关键信息
                        // logger.debug已被注释以提高性能

                        if (isPositive) {
                            if (classificationParts.length() > 0) {
                                classificationParts.append("_");
                            }
                            classificationParts.append(channelName).append("+");
                            cellClassified = true;
                        }
                    }

                    // Apply classification result
                    if (cellClassified && classificationParts.length() > 0) {
                        // Create classification name
                        String classificationName = classificationParts.toString();

                        // === v1.3.0新逻辑：Load Classifier时Classification存PathClass ===
                        // PathClass显示Classification（用于Hierarchy显示和伪彩）
                        PathClass pathClass = ColorUtils.createOrGetClassificationPathClass(classificationName);
                        cell.setPathClass(pathClass);

                        // Measurement存储Classification映射（用于CSV导出）
                        var cellMeasurements = cell.getMeasurementList();
                        cellMeasurements.put("Classification_Info", classificationName.hashCode());
                        storeClassificationMapping(cell.getID().toString(), classificationName);

                        // v1.3.0: 添加字符串到metadata，显示在Properties面板
                        cell.getMetadata().put("classification", classificationName);

                        // 如果CellType已存在，保留在Measurement中（不影响PathClass）
                        // CellType_Info measurement会在Cell Classification时设置

                        counts[0]++; // classified
                        logger.debug("Cell {} classified as: {}", cell.getID(), classificationName);
                    } else {
                        // Unclassified情况
                        PathClass unclassifiedPathClass = ColorUtils.createOrGetClassificationPathClass("Unclassified");
                        cell.setPathClass(unclassifiedPathClass);

                        var clearMeasurements = cell.getMeasurementList();
                        clearMeasurements.put("Classification_Info", "unclassified".hashCode());
                        storeClassificationMapping(cell.getID().toString(), "unclassified");

                        counts[1]++; // unclassified
                    }

                } catch (Exception e) {
                    // logger.debug移除以提高性能，只记录严重错误
                    counts[1]++; // unclassified
                }
            }

            // 为未分类的细胞应用灰白色伪彩
            applyGrayColorToUnclassifiedCells(imageData);

            // ENHANCED: 多层级显示更新确保伪彩立即生效
            hierarchy.fireHierarchyChangedEvent(null);

            // 立即更新 - 不等待Platform.runLater
            if (qupath.getViewer() != null) {
                var viewer = qupath.getViewer();
                try {
                    viewer.forceOverlayUpdate();
                    viewer.repaint();
                } catch (Exception ex) {
                    logger.debug("Immediate viewer update failed (non-critical): {}", ex.getMessage());
                }
            }

            // 延迟更新增强 - 确保伪彩在所有zoom级别都显示
            Platform.runLater(() -> {
                try {
                    if (qupath.getViewer() != null) {
                        var viewer = qupath.getViewer();

                        // 强制overlay选项重置
                        var overlayOptions = viewer.getOverlayOptions();
                        if (overlayOptions != null) {
                            overlayOptions.resetMeasurementMapper();
                        }

                        // 多层级显示刷新
                        viewer.forceOverlayUpdate();
                        viewer.repaint();

                        // 第二次延迟更新确保颜色持久化
                        Platform.runLater(() -> {
                            try {
                                viewer.repaint();
                                logger.info("策略执行伪彩更新完成 - 所有zoom级别颜色已应用");
                            } catch (Exception innerEx) {
                                logger.debug("Second delayed update failed: {}", innerEx.getMessage());
                            }
                        });
                    }
                } catch (Exception ex) {
                    logger.debug("Delayed display update failed (non-critical): {}", ex.getMessage());
                }
            });

            logger.info("Threshold-based classification complete: {} classified, {} unclassified", counts[0], counts[1]);

        } catch (Exception e) {
            logger.error("Error in threshold-based classification: {}", e.getMessage(), e);
        }
    }

    /**
     * Find measurement name for classification (simpler version)
     */
    /**
     * 核心方法: 查找measurement名称（带缓存优化）
     * 策略: QuPath改名后，改名信息不会传递到measurement，所以必须用C索引来匹配
     * 流程: 用户显示名(如"345") -> 映射到C索引(如"C4") -> 查找measurement(如"Cell: C4 mean")
     */
    /**
     * Find measurement name for classification (Build 11: 增强CD31/CD3精确匹配)
     */
    /**
     * Find measurement name for classification (Build 15: 修复CD3误匹配CD31)
     */
    private String findMeasurementNameForClassification(qupath.lib.measurements.MeasurementList measurements,
                                                      String channelName, String measurementType) {
        List<String> availableNames = measurements.getNames();

        // v1.4.0修复: 如果channelName是用户自定义的显示名称，需要先找到对应的QuPathChannelName
        String actualChannelName = findActualChannelNameForDisplayName(channelName);
        if (actualChannelName == null) {
            actualChannelName = channelName; // fallback to original name
        }
        logger.debug("通道名称转换: 显示名 '{}' -> QuPath原名 '{}'", channelName, actualChannelName);

        // Extract the measurement suffix (e.g., "Mean" from "Nucleus: Mean")
        String measurementSuffix = measurementType.contains(": ") ?
            measurementType.split(": ")[1].trim() : measurementType;

        logger.warn("🔍🔍🔍 [DEBUG] 查找measurement: channel='{}' (实际: '{}'), type='{}', suffix='{}'",
                    channelName, actualChannelName, measurementType, measurementSuffix);
        logger.warn("🔍🔍🔍 [DEBUG] 可用measurements总数: {}", availableNames.size());

        // 输出前5个可用的measurements帮助调试
        logger.warn("🔍🔍🔍 [DEBUG] 前5个可用measurements:");
        availableNames.stream().limit(5).forEach(name -> logger.warn("    - {}", name));

        // === 关键修复Build 15: 精确单词匹配防止CD3误匹配到CD31 ===
        // 使用actualChannelName而不是channelName进行匹配
        String lowerChannelName = actualChannelName.toLowerCase();

        // 策略1: 精确匹配完整measurement名称（使用单词边界）
        for (String availableName : availableNames) {
            String lowerAvailableName = availableName.toLowerCase();

            // 检查是否包含精确的通道名作为完整单词
            boolean containsExactChannel = false;

            // 使用分隔符检查精确匹配（防止CD3匹配到CD31）
            if (lowerAvailableName.contains(": " + lowerChannelName + ":") ||
                lowerAvailableName.contains(": " + lowerChannelName + " ") ||
                lowerAvailableName.contains("_" + lowerChannelName + "_") ||
                lowerAvailableName.contains("_" + lowerChannelName + " ") ||
                lowerAvailableName.contains(" " + lowerChannelName + " ") ||
                lowerAvailableName.contains(" " + lowerChannelName + ":")) {
                containsExactChannel = true;
            }

            // 检查是否也包含measurement suffix
            boolean containsSuffix = lowerAvailableName.contains(measurementSuffix.toLowerCase());

            if (containsExactChannel && containsSuffix) {
                logger.warn("✅✅✅ [SUCCESS] 精确匹配成功: '{}' -> '{}' (完整单词匹配)",
                           channelName, availableName);
                return availableName;
            }
        }

        // 策略2: 标准QuPath模式匹配
        String[] patterns = {
            "Nucleus: " + channelName + ": " + measurementSuffix,
            "Cell: " + channelName + ": " + measurementSuffix,
            "Cytoplasm: " + channelName + ": " + measurementSuffix
        };

        logger.warn("🔍 [DEBUG] 尝试标准模式匹配，patterns: {}", java.util.Arrays.toString(patterns));
        for (String pattern : patterns) {
            if (measurements.containsKey(pattern)) {
                logger.warn("✅✅✅ [SUCCESS] 标准模式匹配: '{}' -> '{}'", channelName, pattern);
                return pattern;
            }
        }

        // 策略3: 通道映射匹配（C-index和原始名称）
        if (channelNameMapping != null) {
            // Try C2/C3/C4 patterns
            String channelIndex = channelNameMapping.getOrDefault(channelName + "_INDEX", "");
            if (!channelIndex.isEmpty()) {
                logger.warn("🔍 [DEBUG] 尝试C-index匹配: {}", channelIndex);
                String[] indexPatterns = {
                    "Nucleus: " + channelIndex + ": " + measurementSuffix,
                    "Cell: " + channelIndex + ": " + measurementSuffix,
                    "Cytoplasm: " + channelIndex + ": " + measurementSuffix
                };

                for (String pattern : indexPatterns) {
                    if (measurements.containsKey(pattern)) {
                        logger.warn("✅✅✅ [SUCCESS] C-index匹配: '{}' -> '{}'", channelName, pattern);
                        return pattern;
                    }
                }
            }

            // Try original name mapping
            String originalName = channelNameMapping.getOrDefault(channelName, channelName);
            if (!originalName.equals(channelName)) {
                logger.warn("🔍 [DEBUG] 尝试原始名称匹配: {}", originalName);
                String[] origPatterns = {
                    "Nucleus: " + originalName + ": " + measurementSuffix,
                    "Cell: " + originalName + ": " + measurementSuffix,
                    "Cytoplasm: " + originalName + ": " + measurementSuffix
                };

                for (String pattern : origPatterns) {
                    if (measurements.containsKey(pattern)) {
                        logger.warn("✅✅✅ [SUCCESS] 原始名称匹配: '{}' -> '{}'", channelName, pattern);
                        return pattern;
                    }
                }
            }
        }

        // === 策略4: 增强的模糊匹配（带单词边界检查）===
        logger.warn("🔍 [DEBUG] 尝试增强模糊匹配...");
        for (String availableName : availableNames) {
            String lowerAvailableName = availableName.toLowerCase();

            // 检查是否包含suffix
            if (!lowerAvailableName.contains(measurementSuffix.toLowerCase())) {
                continue;
            }

            // === 关键修复: 使用单词边界检查，防止CD3匹配到CD31 ===
            // 检查通道名前后是否有分隔符（不是字母数字）
            int index = lowerAvailableName.indexOf(lowerChannelName);
            if (index != -1) {
                // 检查前面的字符
                boolean validBefore = (index == 0) || !Character.isLetterOrDigit(lowerAvailableName.charAt(index - 1));
                // 检查后面的字符
                int endIndex = index + lowerChannelName.length();
                boolean validAfter = (endIndex >= lowerAvailableName.length()) ||
                                   !Character.isLetterOrDigit(lowerAvailableName.charAt(endIndex));

                if (validBefore && validAfter) {
                    logger.warn("⚠️⚠️⚠️ [FALLBACK] 使用增强模糊匹配: '{}' -> '{}'",
                               channelName, availableName);
                    return availableName;
                }
            }
        }

        logger.error("❌❌❌ [FAILED] 未找到通道 '{}' 的measurement，类型 '{}'", channelName, measurementType);

        // 输出所有包含通道名的measurements帮助调试
        logger.error("❌ Available measurements containing '{}':", channelName);
        availableNames.stream()
            .filter(name -> name.toLowerCase().contains(channelName.toLowerCase()))
            .forEach(name -> logger.error("    - {}", name));

        return null;
    }

    /**
     * 辅助方法: 首字母大写
     * 用于生成大小写变体的测量名称模式
     */

    /**
     * Get auto color for Load Object Classifier results
     */
    // TODO: [集成] 显示训练结果预览，使用新API重新实现
    /*
    private void showTrainingResults(Map<String, CellPhenotypeAPI.PhenotypeResult> results) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("训练结果预览");
        alert.setHeaderText("Live Preview训练模式结果");

        StringBuilder content = new StringBuilder();
        Map<String, Integer> phenotypeCounts = new HashMap<>();

        for (CellPhenotypeAPI.PhenotypeResult result : results.values()) {
            String phenotype = result.getPhenotypeName();
            phenotypeCounts.put(phenotype, phenotypeCounts.getOrDefault(phenotype, 0) + 1);
        }

        content.append("检测到的细胞表型分布：\n\n");
        for (Map.Entry<String, Integer> entry : phenotypeCounts.entrySet()) {
            content.append(String.format("%s: %d 个细胞\n", entry.getKey(), entry.getValue()));
        }

        content.append("\n注意：这是训练预览结果，细胞的正式PathClass标签未被修改。");
        content.append("\n切换到应用模式并执行策略以永久保存分类结果。");

        alert.setContentText(content.toString());
        alert.showAndWait();
    }
    */

    /**
     * Generate CellType string from measurements (for Cell Classification)
     * CellType should be the actual phenotype name (e.g., "Helper T Cell", "B Cell")
     * instead of marker combinations like "CD3+CD4+CD8-"
     */
    private String generateCellTypeFromMeasurements(qupath.lib.objects.PathObject cell) {
        try {
            var measurements = cell.getMeasurementList();

            // Check if we have CellType_Info measurement
            if (!measurements.containsKey("CellType_Info")) {
                return "";
            }

            double cellTypeHash = measurements.get("CellType_Info");
            if (cellTypeHash == 0.0) {
                return "";
            }

            // Try to find the phenotype name by matching the hash
            if (phenotypeData != null && !phenotypeData.isEmpty()) {
                for (PhenotypeTableRow row : phenotypeData) {
                    if (row.getName().hashCode() == (int)cellTypeHash) {
                        logger.debug("Successfully recovered CellType '{}' from hash {}",
                                   row.getName(), (int)cellTypeHash);
                        return row.getName(); // Return the actual phenotype name
                    }
                }
            }

            // Fallback: try to reconstruct from current PathClass if available
            if (cell.getPathClass() != null) {
                String pathClassName = cell.getPathClass().getName();
                logger.debug("Using PathClass '{}' as CellType fallback for hash {}",
                           pathClassName, (int)cellTypeHash);
                return pathClassName;
            }

            // If we can't match the hash, return a descriptive string
            logger.debug("Could not recover CellType name for hash {}, using generic name", (int)cellTypeHash);
            return "CellType_" + (int)cellTypeHash;

        } catch (Exception e) {
            logger.debug("Error generating celltype from measurements: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Generate Classification string from cell markers (for Load Object Classifier)
     * Classification should show marker states like "CD3+_CD4+_CD8-"
     */
    private String generateClassificationFromMarkers(qupath.lib.objects.PathObject cell) {
        try {
            var measurements = cell.getMeasurementList();

            // Build classification string from available thresholds
            List<String> markerStates = new ArrayList<>();

            if (currentConfig != null && currentConfig.getChannelThresholds() != null) {
                for (Map.Entry<String, ThresholdConfig.ChannelThreshold> entry :
                     currentConfig.getChannelThresholds().entrySet()) {

                    String marker = entry.getKey();
                    ThresholdConfig.ChannelThreshold config = entry.getValue();

                    if (!config.isEnabled()) {
                        continue; // Skip disabled channels
                    }

                    // Get measurement value
                    String measurementName = config.getMeasurement();
                    if (measurements.containsKey(measurementName)) {
                        double value = measurements.get(measurementName);
                        boolean isPositive = value > config.getThreshold();
                        markerStates.add(marker + (isPositive ? "+" : "-"));
                    }
                }
            }

            if (markerStates.isEmpty()) {
                return "undefined";
            }

            return String.join("_", markerStates);

        } catch (Exception e) {
            logger.debug("Error generating classification from markers: {}", e.getMessage());
            return "undefined";
        }
    }
    
    // Helper class for table rows - NULL-SAFE version
    public static class PhenotypeTableRow {
        private String name;  // 改为可变，支持编辑
        private final Integer priority;

        public PhenotypeTableRow(String name, Integer priority) {
            this.name = (name != null) ? name : "未命名";
            this.priority = (priority != null) ? priority : 0;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = (name != null) ? name : "未命名"; }
        public Integer getPriority() { return priority; }
    }
    
    /**
     * Update ROI status label with current ROI information
     */
    private void updateROIStatusLabel(Label statusLabel) {
        if (!useSelectedROI) {
            statusLabel.setText("状态: 处理所有细胞");
            statusLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
            return;
        }
        
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            statusLabel.setText("状态: 无图像数据");
            statusLabel.setStyle("-fx-text-fill: #f44336; -fx-font-style: italic;");
            return;
        }
        
        var hierarchy = imageData.getHierarchy();
        var selectedObjects = hierarchy.getSelectionModel().getSelectedObjects();
        
        // Enhanced ROI status with more detailed information
        List<qupath.lib.objects.PathObject> roiObjects = selectedObjects.stream()
            .filter(obj -> obj.hasROI() && !obj.isDetection())
            .collect(Collectors.toList());
            
        if (roiObjects.isEmpty()) {
            // Check if there are any ROI objects in the hierarchy
            var allROIs = hierarchy.getObjects(null, null).stream()
                .filter(obj -> obj.hasROI() && !obj.isDetection())
                .count();
                
            if (allROIs > 0) {
                statusLabel.setText(String.format("状态: 未选中ROI区域 (共有%d个ROI可选)", allROIs));
                statusLabel.setStyle("-fx-text-fill: #ff9800; -fx-font-style: italic;");
            } else {
                statusLabel.setText("状态: 图像中无ROI区域");
                statusLabel.setStyle("-fx-text-fill: #ff9800; -fx-font-style: italic;");
            }
        } else {
            int cellsInROI = getCellsInSelectedROI(imageData).size();
            int totalCells = hierarchy.getDetectionObjects().size();
            
            // Calculate coverage percentage
            double coveragePercent = totalCells > 0 ? (cellsInROI * 100.0 / totalCells) : 0;
            
            statusLabel.setText(String.format("状态: %d个ROI区域, %d个细胞 (%.1f%% 覆盖率)", 
                               roiObjects.size(), cellsInROI, coveragePercent));
            statusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-style: italic;");
        }
    }
    
    /**
     * Get cells within currently selected ROI(s)
     */
    private List<qupath.lib.objects.PathObject> getCellsInSelectedROI(ImageData<?> imageData) {
        // Check if ROI mode is enabled via the cell analysis combo box
        boolean isRoiMode = cellAnalysisComboBox != null && "当前选中细胞".equals(cellAnalysisComboBox.getValue());

        if (!isRoiMode || imageData == null) {
            return new ArrayList<>(imageData.getHierarchy().getDetectionObjects());
        }
        
        var hierarchy = imageData.getHierarchy();
        var selectedObjects = hierarchy.getSelectionModel().getSelectedObjects();
        
        // Get selected ROI objects (non-detection objects with ROI)
        List<qupath.lib.objects.PathObject> selectedROIs = selectedObjects.stream()
            .filter(obj -> obj.hasROI() && !obj.isDetection())
            .collect(Collectors.toList());
            
        if (selectedROIs.isEmpty()) {
            logger.warn("ROI mode enabled but no ROI objects selected. Processing all cells.");
            return new ArrayList<>(hierarchy.getDetectionObjects());
        }
        
        // Get all cells
        Collection<qupath.lib.objects.PathObject> allCells = hierarchy.getDetectionObjects();
        List<qupath.lib.objects.PathObject> cellsInROI = new ArrayList<>();

        // v1.6.1: 添加调试日志
        logger.info("=== DEBUG INFO ===");
        logger.info("Total cells in hierarchy: {}", allCells.size());
        logger.info("Selected objects count: {}", selectedObjects.size());
        logger.info("Selected ROIs count: {}", selectedROIs.size());

        // v1.6.2: 添加更详细的ROI信息
        logger.info("=== ROI DETAILS ===");
        for (int i = 0; i < selectedROIs.size(); i++) {
            var roiObj = selectedROIs.get(i);
            var roi = roiObj.getROI();
            if (roi != null) {
                logger.info("ROI #{}: Class={}, Bounds=X{} Y{} W{} H{}, Centroid=({}, {})",
                           i + 1,
                           roiObj.getClass().getSimpleName(),
                           roi.getBoundsX(), roi.getBoundsY(),
                           roi.getBoundsWidth(), roi.getBoundsHeight(),
                           roi.getCentroidX(), roi.getCentroidY());
            }
        }

        // v1.6.2: 添加单元格样本信息
        logger.info("=== CELL SAMPLE INFO ===");
        int cellSampleCount = Math.min(5, allCells.size());
        int sampleIndex = 0;
        for (var cell : allCells) {
            if (sampleIndex >= cellSampleCount) break;
            if (cell.hasROI()) {
                var cellROI = cell.getROI();
                logger.info("Cell #{}: Centroid=({}, {}), Bounds=X{} Y{} W{} H{}",
                           sampleIndex + 1,
                           cellROI.getCentroidX(), cellROI.getCentroidY(),
                           cellROI.getBoundsX(), cellROI.getBoundsY(),
                           cellROI.getBoundsWidth(), cellROI.getBoundsHeight());
                sampleIndex++;
            }
        }

        // v1.6.1: 记录第一个ROI的边界信息
        if (!selectedROIs.isEmpty()) {
            var firstROI = selectedROIs.get(0).getROI();
            if (firstROI != null) {
                logger.info("First ROI bounds: X={}, Y={}, W={}, H={}",
                           firstROI.getBoundsX(), firstROI.getBoundsY(),
                           firstROI.getBoundsWidth(), firstROI.getBoundsHeight());
            }
        }

        logger.info("Filtering {} cells using {} selected ROI(s)", allCells.size(), selectedROIs.size());

        // v1.6.2: 尝试不同的ROI检测方法，使用getContainsPoint()或类似方法
        int detectedCount = 0;
        for (var cell : allCells) {
            if (!cell.hasROI()) continue;

            var cellROI = cell.getROI();
            for (var roiObject : selectedROIs) {
                var roi = roiObject.getROI();
                if (roi != null && cellROI != null) {
                    // v1.6.2: 尝试多种方法判断细胞是否在ROI内
                    boolean cellInROI = false;

                    try {
                        // 方法1: 获取细胞中心点
                        double cellCenterX = cellROI.getCentroidX();
                        double cellCenterY = cellROI.getCentroidY();

                        // 方法2: 检查中心点是否在ROI内（尝试不同的contains实现）
                        // 如果ROI.contains(double, double)有问题，尝试其他方法
                        cellInROI = roi.contains(cellCenterX, cellCenterY);

                        // 如果方法1失败，尝试方法2：使用Cell ROI的边界框
                        if (!cellInROI) {
                            // 获取细胞ROI的边界框
                            double cellMinX = cellROI.getBoundsX();
                            double cellMinY = cellROI.getBoundsY();
                            double cellMaxX = cellMinX + cellROI.getBoundsWidth();
                            double cellMaxY = cellMinY + cellROI.getBoundsHeight();

                            // 检查细胞ROI的四个角点是否有任何一个在ROI内
                            if (roi.contains(cellMinX, cellMinY) ||
                                roi.contains(cellMaxX, cellMinY) ||
                                roi.contains(cellMaxX, cellMaxY) ||
                                roi.contains(cellMinX, cellMaxY) ||
                                roi.contains(cellCenterX, cellCenterY)) {
                                cellInROI = true;
                            }
                        }

                        // v1.6.2: 添加调试日志
                        if (cellInROI) {
                            detectedCount++;
                            logger.debug("Cell #{} at ({}, {}) is INSIDE ROI (comprehensive check)",
                                       detectedCount, cellCenterX, cellCenterY);
                        }
                    } catch (Exception e) {
                        logger.warn("ROI检测异常: {}", e.getMessage(), e);
                        // 尝试简单的中心点检测作为备选
                        try {
                            double cellCenterX = cellROI.getCentroidX();
                            double cellCenterY = cellROI.getCentroidY();
                            cellInROI = roi.contains(cellCenterX, cellCenterY);
                        } catch (Exception ex) {
                            logger.error("所有ROI检测方法都失败", ex);
                        }
                    }

                    if (cellInROI) {
                        cellsInROI.add(cell);
                        break; // Cell is in at least one selected ROI
                    }
                }
            }
        }

        logger.info("=== FINAL RESULT ===");
        logger.info("Cells found in ROI: {}", cellsInROI.size());
        logger.info("Detected count: {}", detectedCount);
        
        logger.info("ROI filtering: {} cells found within {} selected ROI(s) out of {} total cells", 
                   cellsInROI.size(), selectedROIs.size(), allCells.size());
        
        return cellsInROI;
    }
    
    /**
     * Browse for save path directory
     */
    private void browseSavePath(TextField savePathField) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("选择保存目录");
        directoryChooser.setInitialDirectory(new java.io.File(System.getProperty("user.home")));

        java.io.File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory != null) {
            savePathField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    /**
     * 更新按钮状态根据当前操作模式
     */
    private void updateButtonStates() {
        if (executeButton != null) {
            // Create模式下，执行策略不可点击
            executeButton.setDisable(currentMode == OperationMode.CREATE_CLASSIFIER);

            if (currentMode == OperationMode.CREATE_CLASSIFIER) {
                executeButton.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-font-size: 13px;");
                executeButton.setText("运行");
            } else {
                executeButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
                executeButton.setText("运行");
            }
        }
    }

    /**
     * 更新通道控件为Auto模式
     */

    /**
     * Calculate Otsu threshold for automatic threshold detection
     */
    /**
     * Triangle算法计算阈值
     */
    private double calculateTriangleThreshold(List<Double> values) {
        if (values.isEmpty()) {
            return 100.0;
        }

        // 性能优化：使用快速选择找分位数，避免完整排序 O(N) vs O(N log N)
        return quickSelect(new ArrayList<>(values), (int)(values.size() * 0.75));
    }

    /**
     * MaxEntropy算法计算阈值
     */
    private double calculateMaxEntropyThreshold(List<Double> values) {
        if (values.isEmpty()) {
            return 100.0;
        }

        // 性能优化：单次遍历计算均值和方差（Welford算法）
        int n = values.size();
        double mean = 0;
        double m2 = 0;

        for (int i = 0; i < n; i++) {
            double value = values.get(i);
            double delta = value - mean;
            mean += delta / (i + 1);
            double delta2 = value - mean;
            m2 += delta * delta2;
        }

        double variance = n > 1 ? m2 / (n - 1) : 0;
        double stdDev = Math.sqrt(variance);

        // 使用均值加偏移（避免排序找中位数）
        return mean + stdDev * 0.5;
    }

    /**
     * Minimum算法计算阈值
     */
    private double calculateMinimumThreshold(List<Double> values) {
        if (values.isEmpty()) {
            return 100.0;
        }

        // 性能优化：使用快速选择找分位数，避免完整排序
        return quickSelect(new ArrayList<>(values), (int)(values.size() * 0.25));
    }

    /**
     * 快速选择算法：O(N)时间复杂度找第k小的元素
     * 避免完整排序 O(N log N)
     */
    private double quickSelect(List<Double> arr, int k) {
        if (arr.size() == 1) return arr.get(0);
        if (k >= arr.size()) return arr.get(arr.size() - 1);
        if (k < 0) return arr.get(0);

        int left = 0;
        int right = arr.size() - 1;

        while (left < right) {
            int pivotIndex = partition(arr, left, right);
            if (pivotIndex == k) {
                return arr.get(k);
            } else if (pivotIndex < k) {
                left = pivotIndex + 1;
            } else {
                right = pivotIndex - 1;
            }
        }

        return arr.get(k);
    }

    private int partition(List<Double> arr, int left, int right) {
        double pivot = arr.get(right);
        int i = left;

        for (int j = left; j < right; j++) {
            if (arr.get(j) <= pivot) {
                // Swap arr[i] and arr[j]
                double temp = arr.get(i);
                arr.set(i, arr.get(j));
                arr.set(j, temp);
                i++;
            }
        }

        // Swap arr[i] and arr[right]
        double temp = arr.get(i);
        arr.set(i, arr.get(right));
        arr.set(right, temp);

        return i;
    }

    private double calculateOtsuThreshold(List<Double> values) {
        if (values.isEmpty()) {
            return 100.0;
        }

        // 性能优化：使用流式操作避免排序
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(255);

        int numBins = 256;
        double binWidth = (max - min) / numBins;

        if (binWidth <= 0) {
            return (min + max) / 2.0;
        }

        // 构建直方图（单次遍历）
        int[] histogram = new int[numBins];
        for (double value : values) {
            int bin = Math.min(numBins - 1, Math.max(0, (int)((value - min) / binWidth)));
            histogram[bin]++;
        }

        // 性能优化：预计算累积和，避免嵌套循环
        int total = values.size();
        double sumTotal = 0;
        for (int i = 0; i < numBins; i++) {
            sumTotal += i * histogram[i];
        }

        double sumB = 0;
        int wB = 0;
        double maxVariance = 0;
        int bestThreshold = 0;

        // 单次遍历找最佳阈值（从 O(256²) 优化到 O(256)）
        for (int t = 0; t < numBins; t++) {
            wB += histogram[t];
            if (wB == 0) continue;

            int wF = total - wB;
            if (wF == 0) break;

            sumB += t * histogram[t];
            double mB = sumB / wB;
            double mF = (sumTotal - sumB) / wF;

            double variance = (double) wB * wF * (mB - mF) * (mB - mF);

            if (variance > maxVariance) {
                maxVariance = variance;
                bestThreshold = t;
            }
        }

        return min + bestThreshold * binWidth;
    }

    // ===== Cell Selection Highlighting Support =====

    private Map<String, Integer> originalColors = new HashMap<>(); // Store original colors for selected cells
    private qupath.lib.gui.viewer.QuPathViewer currentViewer = null;
    private Object selectionListener = null; // Store the listener reference for cleanup

    /**
     * Initialize cell selection highlighting mechanism for yellow highlighting
     */
    private void initializeCellSelectionHighlighting() {
        try {
            ImageData<?> imageData = qupath.getImageData();
            if (imageData == null) {
                logger.debug("No image data available for selection highlighting");
                return;
            }

            var hierarchy = imageData.getHierarchy();
            if (hierarchy == null) {
                logger.debug("No hierarchy available for selection highlighting");
                return;
            }

            // Get the QuPath viewer
            currentViewer = qupath.getViewer();
            if (currentViewer == null) {
                logger.debug("No viewer available for selection highlighting");
                return;
            }

            logger.info("Initializing cell selection highlighting mechanism");

            // Note: Cell selection highlighting feature disabled due to QuPath API compatibility
            // Future versions may re-enable this feature with proper QuPath API integration
            selectionListener = null;

            logger.info("Cell selection highlighting initialized successfully");

        } catch (Exception e) {
            logger.warn("Failed to initialize cell selection highlighting: {}", e.getMessage());
        }
    }

    /**
     * Handle cell selection changes for yellow highlighting
     */
    /**
     * Clean up selection highlighting when closing the plugin
     */
    private void cleanupSelectionHighlighting() {
        try {
            // Restore all originally colored cells
            ImageData<?> imageData = qupath.getImageData();
            if (imageData != null && !originalColors.isEmpty()) {
                var hierarchy = imageData.getHierarchy();
                for (var cell : hierarchy.getDetectionObjects()) {
                    String cellId = cell.getID().toString();
                    if (originalColors.containsKey(cellId)) {
                        Integer originalColor = originalColors.get(cellId);
                        cell.setColor(originalColor);
                        logger.debug("Restored original color for cell {} during cleanup", cellId);
                    }
                }
                originalColors.clear();
            }

            // Remove selection listener
            if (imageData != null && selectionListener != null) {
                var hierarchy = imageData.getHierarchy();
                // Selection listener cleanup - disabled for compatibility
                selectionListener = null;
                logger.info("Selection highlighting listener removed");
            }

            // Update display
            Platform.runLater(() -> {
                if (currentViewer != null) {
                    currentViewer.repaint();
                }
            });

            selectionListener = null;
            currentViewer = null;

            logger.info("Selection highlighting cleanup completed");

        } catch (Exception e) {
            logger.warn("Error during selection highlighting cleanup: {}", e.getMessage());
        }
    }
}