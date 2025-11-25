# CycBiOx 代码结构与修改指南

## 📖 目录

1. [代码文件总览](#代码文件总览)
2. [核心文件详解](#核心文件详解)
3. [常见修改场景](#常见修改场景)
4. [代码定位指南](#代码定位指南)
5. [修改模板](#修改模板)

---

## 代码文件总览

### 📁 完整项目结构
```
src/main/java/com/cellphenotype/qupath/
├── CellPhenotypeExtension.java          # 🚪 插件入口 (108行)
├── CellPhenotypeAPI.java                # 🔧 API接口 (305行)
├── ui/
│   ├── CellPhenotypeManagerPane.java    # 🖥️ 主界面 (1800+行) ⭐ 核心文件
│   └── PhenotypeEditorDialog.java       # 📝 编辑对话框
├── model/
│   ├── CellPhenotype.java               # 📊 表型模型 (131行)
│   ├── ThresholdConfig.java             # ⚙️ 阈值配置 (134行)
│   ├── PhenotypeManager.java            # 📋 表型管理器
│   └── ProjectConfig.java               # 🗂️ 项目配置
├── service/
│   └── CellClassificationService.java   # 🎯 分类服务 (229行) ⭐ 核心算法
├── utils/
│   ├── ColorUtils.java                  # 🎨 颜色工具 (207行)
│   ├── MeasurementUtils.java            # 📏 测量工具 (193行)
│   └── UIUtils.java                     # 🖼️ UI工具
└── classifier/
    └── CellPhenotypeClassifier.java     # 🧠 分类器实现
```

### 🎯 重要性排序
1. **CellPhenotypeManagerPane.java** - 主界面，最常修改
2. **CellClassificationService.java** - 核心算法逻辑
3. **CellPhenotype.java** - 数据模型
4. **ColorUtils.java** - 颜色和显示
5. **MeasurementUtils.java** - 数据处理

---

## 核心文件详解

### 🚪 CellPhenotypeExtension.java (108行)
**作用**: QuPath插件的入口点，负责注册菜单和启动界面

#### 关键代码位置:
```java
📍 Line 43-48: 插件基本信息
private static final String EXTENSION_NAME = "CycBiOx";
private static final String VERSION = "1.0.0";

📍 Line 52-69: 插件安装逻辑
@Override
public void installExtension(QuPathGUI qupath) {
    MenuItem menuItem = new MenuItem("CycBiOx");
    menuItem.setOnAction(e -> showCellPhenotypeManager(qupath));
    MenuTools.addMenuItems(qupath.getMenu("Extensions", true), menuItem);
}

📍 Line 74-83: 显示主界面
private void showCellPhenotypeManager(QuPathGUI qupath) {
    CellPhenotypeManagerPane pane = new CellPhenotypeManagerPane(qupath);
    pane.show();
}
```

#### 常见修改:
- **修改插件名称**: 修改 Line 43 `EXTENSION_NAME`
- **修改版本号**: 修改 Line 49 `VERSION`
- **修改菜单项名称**: 修改 Line 56 `"CycBiOx"`

---

### 🖥️ CellPhenotypeManagerPane.java (1800+行) ⭐ 最重要
**作用**: 主界面控制器，包含所有UI逻辑和用户交互

#### 🗂️ 关键代码段分布:

##### 操作模式定义 (Line 47-61)
```java
📍 Line 47-61: 定义双操作模式
private enum OperationMode {
    CREATE_CLASSIFIER("Create Single Measurement Classifier"),
    LOAD_CLASSIFIER("Load Classifier (Execute Strategy)");
}
```
**修改场景**: 添加新的操作模式

##### 界面布局创建 (Line 200-800)
```java
📍 Line 250-350: 基础设置区域
private TitledPane createBasicSettingsSection() {
    // 配置名称、保存路径、ROI设置
}

📍 Line 400-600: 阈值配置区域
private TitledPane createThresholdSection() {
    // 模式切换、滑块、算法选择
}

📍 Line 700-800: 细胞分类区域
private TitledPane createClassificationSection() {
    // 表型管理表格、按钮
}
```

##### 通道映射逻辑 (Line 900-1100)
```java
📍 Line 950-1000: 通道加载
private void loadAvailableChannels() {
    ImageData<?> imageData = qupath.getImageData();
    List<ImageChannel> channels = imageData.getServer().getMetadata().getChannels();

    for (int i = 0; i < channels.size(); i++) {
        ImageChannel channel = channels.get(i);
        String displayName = channel.getName();

        // 跳过DAPI
        if (displayName.toLowerCase().contains("dapi")) continue;

        availableChannels.add(displayName);
        channelNameMapping.put(displayName, displayName);

        // C2/C3/C4映射
        String channelIndex = "C" + (i + 1);
        if (i > 0) {
            channelNameMapping.put(displayName + "_INDEX", channelIndex);
        }
    }
}
```

##### 阈值控制逻辑 (Line 1200-1500)
```java
📍 Line 1300-1400: 滑块创建
private Slider createThresholdSlider(String channelName) {
    // 对数滑块逻辑
    double[] range = detectImageBitRange(qupath.getImageData());
    double logMin = Math.log10(Math.max(range[0], 0.1));
    double logMax = Math.log10(range[1]);

    Slider slider = new Slider(logMin, logMax, logMin);
    return slider;
}

📍 Line 1450-1500: 自动阈值计算
private void calculateAutoThresholds(String algorithm) {
    // Otsu, Triangle, MaxEntropy, Minimum算法
}
```

##### 表型管理 (Line 1600-1800)
```java
📍 Line 1650-1750: 表型表格设置
private void setupPhenotypeTable() {
    // 动态列生成、ComboBox单元格
    for (String channel : markerChannels) {
        TableColumn<PhenotypeTableRow, String> markerCol = new TableColumn<>(channel);
        markerCol.setCellFactory(col -> new TableCell<PhenotypeTableRow, String>() {
            private final ComboBox<String> comboBox = new ComboBox<>();
            {
                comboBox.getItems().addAll("阳性", "阴性", "无关");
            }
        });
    }
}
```

#### 快速定位功能:
- **修改界面布局**: 搜索 `createBasicSettingsSection`, `createThresholdSection`, `createClassificationSection`
- **修改通道处理**: 搜索 `loadAvailableChannels`, `channelNameMapping`
- **修改阈值逻辑**: 搜索 `createThresholdSlider`, `calculateAutoThresholds`
- **修改表型管理**: 搜索 `setupPhenotypeTable`, `PhenotypeTableRow`

---

### 🎯 CellClassificationService.java (229行) ⭐ 核心算法
**作用**: 统一的细胞分类服务，包含所有分类算法

#### 关键方法位置:

##### 主分类入口 (Line 49-85)
```java
📍 Line 49-85: 完整分类流程
public static ClassificationResult classifyCells(ImageData<?> imageData,
                                               ThresholdConfig thresholdConfig,
                                               List<CellPhenotype> phenotypes) {
    // 1. 创建测量名称映射
    Map<String, String> measurementMapping = MeasurementUtils.createMeasurementMapping(imageData, channelNames);

    // 2. 执行阈值分类
    Map<PathObject, String> classificationResults = performThresholdClassification(detections, thresholdConfig, measurementMapping);

    // 3. 执行细胞表型分类
    Map<PathObject, String> cellTypeResults = performPhenotypeClassification(detections, thresholdConfig, measurementMapping, phenotypes);

    // 4. 应用分类结果
    applyClassificationResults(classificationResults, cellTypeResults);

    // 5. 同步显示
    ColorUtils.syncQuPathDisplay(imageData);
}
```

##### 阈值分类算法 (Line 90-108)
```java
📍 Line 90-108: 阈值分类实现
public static Map<PathObject, String> performThresholdClassification(
        Collection<PathObject> detections,
        ThresholdConfig thresholdConfig,
        Map<String, String> measurementMapping) {

    Map<PathObject, String> results = new ConcurrentHashMap<>();

    // 并行处理提高性能
    detections.parallelStream().forEach(detection -> {
        String classificationResult = classifySingleCell(detection, thresholdConfig, measurementMapping);
        if (classificationResult != null) {
            results.put(detection, classificationResult);
            detection.getMeasurementList().put("Classification_Info", (double)classificationResult.hashCode());
        }
    });

    return results;
}
```

##### 表型分类算法 (Line 113-136)
```java
📍 Line 113-136: 表型分类实现
public static Map<PathObject, String> performPhenotypeClassification(
        Collection<PathObject> detections,
        ThresholdConfig thresholdConfig,
        Map<String, String> measurementMapping,
        List<CellPhenotype> phenotypes) {

    Map<PathObject, String> results = new ConcurrentHashMap<>();

    // 按优先级排序表型
    List<CellPhenotype> sortedPhenotypes = phenotypes.stream()
            .sorted(Comparator.comparingInt(CellPhenotype::getPriority))
            .collect(Collectors.toList());

    detections.parallelStream().forEach(detection -> {
        String cellType = classifyPhenotype(detection, thresholdConfig, measurementMapping, sortedPhenotypes);
        if (cellType != null) {
            results.put(detection, cellType);
            detection.getMeasurementList().put("CellType_Info", (double)cellType.hashCode());
        }
    });

    return results;
}
```

##### 单细胞分类 (Line 186-200)
```java
📍 Line 186-200: 单细胞阈值分类
private static String classifySingleCell(PathObject detection,
                                       ThresholdConfig thresholdConfig,
                                       Map<String, String> measurementMapping) {
    Map<String, Boolean> markerStates = getCellMarkerStates(detection, thresholdConfig, measurementMapping);

    if (markerStates.isEmpty()) {
        return "Unclassified";
    }

    // 生成组合标签 (如 "CD3+_CD4+_CD8-")
    return markerStates.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + (entry.getValue() ? "+" : "-"))
            .collect(Collectors.joining("_"));
}
```

#### 修改指南:
- **修改分类算法**: 编辑 `classifySingleCell` 方法的返回格式
- **添加新算法**: 在 `performThresholdClassification` 中添加新的处理逻辑
- **修改表型匹配**: 编辑 `classifyPhenotype` 方法的匹配规则

---

### 📊 CellPhenotype.java (131行) - 数据模型
**作用**: 定义细胞表型的数据结构和匹配逻辑

#### 关键代码:

##### 标记状态枚举 (Line 11-38)
```java
📍 Line 11-38: 标记状态定义
public enum MarkerState {
    POSITIVE("阳性"),
    NEGATIVE("阴性"),
    IGNORE("无关");

    private final String displayName;

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
```

##### 表型匹配算法 (Line 86-106)
```java
📍 Line 86-106: 核心匹配逻辑
public boolean matches(Map<String, Boolean> markerPositiveStates) {
    for (Map.Entry<String, MarkerState> entry : markerStates.entrySet()) {
        String marker = entry.getKey();
        MarkerState requiredState = entry.getValue();

        if (requiredState == MarkerState.IGNORE) {
            continue; // 跳过无关标记
        }

        Boolean isPositive = markerPositiveStates.get(marker);
        if (isPositive == null) {
            continue; // 跳过缺失数据
        }

        boolean matches = (requiredState == MarkerState.POSITIVE) == isPositive;
        if (!matches) {
            return false; // 任一标记不匹配则失败
        }
    }
    return true; // 所有标记都匹配
}
```

#### 修改指南:
- **添加新标记状态**: 在 `MarkerState` 枚举中添加新值
- **修改匹配逻辑**: 编辑 `matches` 方法
- **修改显示名称**: 更改 `displayName` 字段

---

### 🎨 ColorUtils.java (207行) - 颜色管理
**作用**: 管理细胞类型的颜色分配和QuPath显示同步

#### 关键方法:

##### 颜色分配 (Line 31-52)
```java
📍 Line 31-52: 细胞类型颜色获取
public static Integer getCellTypeColor(String cellTypeName) {
    if (cellTypeName == null || cellTypeName.isEmpty()) {
        return PREDEFINED_COLORS.get("undefined");
    }

    // 检查预定义颜色
    if (PREDEFINED_COLORS.containsKey(cellTypeName)) {
        return PREDEFINED_COLORS.get(cellTypeName);
    }

    // 从缓存获取
    if (COLOR_CACHE.containsKey(cellTypeName)) {
        return COLOR_CACHE.get(cellTypeName);
    }

    // 生成新颜色
    Integer color = generateDistinctColor(cellTypeName);
    COLOR_CACHE.put(cellTypeName, color);
    return color;
}
```

##### 预定义颜色 (Line 24-28)
```java
📍 Line 24-28: 预定义颜色映射
private static final Map<String, Integer> PREDEFINED_COLORS = Map.of(
    "Unclassified", 0x808080, // 灰色
    "undefined", 0xE0E0E0,    // 灰白色 - 主要的未分类状态
    "Other", 0x606060         // 中灰色
);
```

##### QuPath同步 (Line 97-117)
```java
📍 Line 97-117: 显示同步
public static void syncQuPathDisplay(ImageData<?> imageData) {
    if (imageData == null) {
        return;
    }

    UIUtils.runOnFXThread(() -> {
        try {
            // 触发层次结构更新事件
            imageData.getHierarchy().fireHierarchyChangedEvent(null);

            // 刷新GUI显示
            QuPathGUI qupath = QuPathGUI.getInstance();
            if (qupath != null && qupath.getViewer() != null) {
                qupath.getViewer().repaint();
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to sync QuPath display: " + e.getMessage());
        }
    });
}
```

#### 修改指南:
- **添加预定义颜色**: 在 `PREDEFINED_COLORS` 中添加新条目
- **修改颜色生成算法**: 编辑 `generateDistinctColor` 方法
- **修改显示同步**: 编辑 `syncQuPathDisplay` 方法

---

### 📏 MeasurementUtils.java (193行) - 测量工具
**作用**: 处理QuPath测量数据，智能识别通道名称

#### 关键方法:

##### 智能通道查找 (Line 29-58)
```java
📍 Line 29-58: 统一测量名称查找
public static String findMeasurementName(ImageData<?> imageData, String channelName) {
    if (imageData == null || channelName == null || channelName.isEmpty()) {
        return null;
    }

    Collection<PathObject> detections = imageData.getHierarchy().getDetectionObjects();
    if (detections.isEmpty()) {
        return null;
    }

    PathObject firstObject = detections.iterator().next();
    List<String> measurementNames = firstObject.getMeasurementList().getMeasurementNames();

    // 1. 精确匹配
    String exactMatch = findExactMatch(measurementNames, channelName);
    if (exactMatch != null) {
        return exactMatch;
    }

    // 2. 可能的名称匹配
    List<String> possibleNames = createPossibleMeasurementNames(channelName);
    for (String possibleName : possibleNames) {
        if (measurementNames.contains(possibleName)) {
            return possibleName;
        }
    }

    // 3. 模糊匹配
    return findFuzzyMatch(measurementNames, channelName);
}
```

##### 通道优先级 (Line 17-23)
```java
📍 Line 17-23: 通道前缀优先级
private static final Map<String, Integer> CHANNEL_PREFIX_PRIORITY = Map.of(
    "C2", 1,  // 最高优先级
    "C3", 2,
    "C4", 3,
    "C1", 4,
    "DAPI", 5 // 最低优先级
);
```

##### 基础名称提取 (Line 106-124)
```java
📍 Line 106-124: 提取通道基础名称
public static String extractBaseName(String channelName) {
    if (channelName == null) {
        return null;
    }

    // 处理如 "C2: CD3" -> "CD3" 的情况
    if (channelName.contains(": ")) {
        return channelName.substring(channelName.indexOf(": ") + 2).trim();
    }

    // 处理如 "C2_CD3" -> "CD3" 的情况
    for (String prefix : CHANNEL_PREFIX_PRIORITY.keySet()) {
        if (channelName.startsWith(prefix + "_")) {
            return channelName.substring((prefix + "_").length());
        }
    }

    return channelName;
}
```

#### 修改指南:
- **修改通道优先级**: 编辑 `CHANNEL_PREFIX_PRIORITY` 映射
- **添加新的命名模式**: 在 `createPossibleMeasurementNames` 中添加模式
- **修改模糊匹配**: 编辑 `findFuzzyMatch` 方法

---

## 常见修改场景

### 🎯 场景1: 修改插件名称和版本

#### 文件: `CellPhenotypeExtension.java`
```java
// Line 43: 修改插件名称
private static final String EXTENSION_NAME = "您的插件名称";

// Line 49: 修改版本号
private static final String VERSION = "2.0.0";

// Line 56: 修改菜单显示名称
MenuItem menuItem = new MenuItem("您的菜单名称");
```

### 🎯 场景2: 添加新的标记状态

#### 文件: `CellPhenotype.java`
```java
// Line 11-14: 在枚举中添加新状态
public enum MarkerState {
    POSITIVE("阳性"),
    NEGATIVE("阴性"),
    IGNORE("无关"),
    WEAK_POSITIVE("弱阳性"), // 新增状态
    STRONG_POSITIVE("强阳性"); // 新增状态
}

// Line 26-37: 更新显示名称映射
public static MarkerState fromDisplayName(String displayName) {
    for (MarkerState state : values()) {
        if (state.displayName.equals(displayName)) {
            return state;
        }
    }
    // 添加兼容性处理
}
```

### 🎯 场景3: 修改预定义颜色

#### 文件: `ColorUtils.java`
```java
// Line 24-28: 修改预定义颜色
private static final Map<String, Integer> PREDEFINED_COLORS = Map.of(
    "Unclassified", 0x808080,
    "undefined", 0xE0E0E0,
    "Other", 0x606060,
    "T Cell", 0x00FF00,      // 新增: 绿色T细胞
    "B Cell", 0x0000FF,      // 新增: 蓝色B细胞
    "Tumor Cell", 0xFF0000   // 新增: 红色肿瘤细胞
);
```

### 🎯 场景4: 添加新的自动阈值算法

#### 文件: `CellPhenotypeManagerPane.java`
```java
// 搜索 "calculateAutoThresholds" 方法，约在 Line 1450
private void calculateAutoThresholds(String algorithm) {
    // 现有算法...

    // 添加新算法
    case "YourNewAlgorithm":
        threshold = calculateYourNewAlgorithm(values);
        break;
}

// 添加算法实现
private double calculateYourNewAlgorithm(List<Double> values) {
    // 实现您的算法逻辑
    return computedThreshold;
}

// 在算法选择框中添加选项 (搜索 "algorithmComboBox")
algorithmComboBox.getItems().addAll("Otsu", "Triangle", "MaxEntropy", "Minimum", "YourNewAlgorithm");
```

### 🎯 场景5: 修改分类结果格式

#### 文件: `CellClassificationService.java`
```java
// Line 186-200: 修改分类标签格式
private static String classifySingleCell(PathObject detection,
                                       ThresholdConfig thresholdConfig,
                                       Map<String, String> measurementMapping) {
    Map<String, Boolean> markerStates = getCellMarkerStates(detection, thresholdConfig, measurementMapping);

    if (markerStates.isEmpty()) {
        return "Unclassified";
    }

    // 原格式: "CD3+_CD4+_CD8-"
    // 修改为新格式: "CD3(+)|CD4(+)|CD8(-)"
    return markerStates.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "(" + (entry.getValue() ? "+" : "-") + ")")
            .collect(Collectors.joining("|"));
}
```

### 🎯 场景6: 添加新的界面控件

#### 文件: `CellPhenotypeManagerPane.java`
```java
// 在 createBasicSettingsSection() 方法中添加新控件 (约Line 250-350)
private TitledPane createBasicSettingsSection() {
    VBox content = new VBox(10);

    // 现有控件...

    // 添加新的控件
    HBox newControlBox = new HBox(10);
    Label newLabel = new Label("新功能");
    newLabel.setPrefWidth(80);
    TextField newField = new TextField();
    newField.setPrefWidth(200);
    newControlBox.getChildren().addAll(newLabel, newField);

    content.getChildren().addAll(nameBox, pathBox, roiBox, newControlBox); // 添加到布局

    TitledPane pane = new TitledPane("基本设置", content);
    return pane;
}
```

---

## 代码定位指南

### 🔍 快速搜索关键词

#### 界面相关修改
```
搜索关键词                    → 定位到功能
"createBasicSettingsSection"  → 基础设置界面
"createThresholdSection"      → 阈值配置界面
"createClassificationSection" → 细胞分类界面
"setupPhenotypeTable"         → 表型管理表格
"OperationMode"               → 操作模式定义
```

#### 算法相关修改
```
搜索关键词                    → 定位到功能
"classifySingleCell"          → 单细胞分类算法
"performThresholdClassification" → 阈值分类主逻辑
"performPhenotypeClassification" → 表型分类主逻辑
"calculateAutoThresholds"     → 自动阈值计算
"matches"                     → 表型匹配逻辑
```

#### 数据处理相关
```
搜索关键词                    → 定位到功能
"findMeasurementName"         → 通道名称查找
"channelNameMapping"          → 通道映射逻辑
"getCellTypeColor"            → 颜色分配
"syncQuPathDisplay"           → 显示同步
"exportResults"               → 数据导出
```

#### 配置相关
```
搜索关键词                    → 定位到功能
"ThresholdConfig"             → 阈值配置模型
"CellPhenotype"               → 表型数据模型
"ProjectConfig"               → 项目配置模型
"MarkerState"                 → 标记状态枚举
```

### 📋 文件优先级修改顺序

#### 🥇 最常修改 (90%的需求)
1. **CellPhenotypeManagerPane.java** - 界面调整、交互逻辑
2. **ColorUtils.java** - 颜色、显示效果
3. **CellPhenotype.java** - 数据模型、匹配规则

#### 🥈 中等频率 (75%的需求)
4. **CellClassificationService.java** - 算法调整
5. **MeasurementUtils.java** - 数据处理
6. **ThresholdConfig.java** - 配置结构

#### 🥉 较少修改 (25%的需求)
7. **CellPhenotypeExtension.java** - 插件信息
8. **CellPhenotypeAPI.java** - API接口
9. **其他工具类** - 辅助功能

---

## 修改模板

### 📝 模板1: 添加新功能的完整流程

#### 步骤1: 修改数据模型
```java
// 在 CellPhenotype.java 中添加新字段
public class CellPhenotype {
    private final String newFeature; // 添加新属性

    // 更新构造函数和getter方法
    // 更新 withXXX 方法
}
```

#### 步骤2: 修改界面
```java
// 在 CellPhenotypeManagerPane.java 中添加UI控件
private TextField newFeatureField; // 声明新控件

// 在相应的 createXXXSection 方法中添加控件布局
```

#### 步骤3: 修改算法
```java
// 在 CellClassificationService.java 中添加处理逻辑
// 更新分类方法以使用新属性
```

#### 步骤4: 测试验证
```java
// 编译检查
./gradlew build

// 功能测试
// 1. 界面显示正常
// 2. 数据保存/加载正常
// 3. 算法运行正常
```

### 📝 模板2: 修改现有算法

#### 定位算法代码
```java
// 1. 在 CellClassificationService.java 中找到相关方法
// 2. 理解现有逻辑
// 3. 备份原始代码（注释形式）
// 4. 实现新逻辑
// 5. 保留fallback机制
```

#### 修改示例
```java
// 原始代码 (保留注释)
/*
private static String classifySingleCell_Original(PathObject detection, ...) {
    // 原始实现
}
*/

// 新实现
private static String classifySingleCell(PathObject detection, ...) {
    try {
        // 新算法实现
        return newAlgorithmResult;
    } catch (Exception e) {
        logger.warn("New algorithm failed, falling back to original", e);
        // return classifySingleCell_Original(detection, ...); // fallback
        return "Unclassified";
    }
}
```

### 📝 模板3: 调试和日志

#### 添加调试日志
```java
// 在关键位置添加日志
private static final Logger logger = LoggerFactory.getLogger(YourClass.class);

// 调试信息
logger.debug("Processing cell: {}, markers: {}", cell.getID(), markerStates);

// 警告信息
logger.warn("Unexpected condition in cell classification: {}", condition);

// 错误信息
logger.error("Failed to process cell: {}", cell.getID(), exception);
```

#### 性能监控
```java
long startTime = System.currentTimeMillis();
// 执行操作
long duration = System.currentTimeMillis() - startTime;
logger.info("Operation completed in {}ms", duration);
```

---

## 🔧 开发环境配置

### 编译和测试
```bash
# 编译检查
./gradlew compileJava

# 完整构建
./gradlew build

# 清理重建
./gradlew clean build

# 只编译不运行测试
./gradlew compileJava compileTestJava
```

### 调试技巧
```bash
# 启用详细日志
export JAVA_OPTS="-Dlogging.level.com.cellphenotype=DEBUG"

# 增加内存用于大数据测试
export JAVA_OPTS="-Xmx8g -XX:+UseG1GC"
```

---

**这份代码指南帮助您快速定位和修改CycBiOx的任何功能。建议将此文档保存为书签，随时查阅！**

🎯 **最常用的修改**: 90%的需求都在 `CellPhenotypeManagerPane.java` 和 `ColorUtils.java` 中
🔍 **快速定位**: 使用Ctrl+F搜索关键词，几秒钟找到目标代码
📝 **安全修改**: 遵循模板步骤，先备份再修改，保留fallback机制