# CycBiOx TODO Tree 注释规范指南

## 📋 注释风格说明

本文档展示了CycBiOx项目中使用的TODO Tree风格注释规范，用于提供清晰的代码功能描述和调用关系说明。

## 🎯 注释模板结构

### 📍 基础TODO注释模板

```java
/**
 * TODO: [功能简述] - [详细描述]
 *
 * 功能说明:
 * ├── [主要功能点1]
 * ├── [主要功能点2]
 * ├── [主要功能点3]
 * └── [主要功能点4]
 *
 * [具体配置/策略/特性说明]
 *
 * 调用关系:
 * - 被 [调用者] 调用
 * - 调用 [被调用者] 方法
 * - 影响 [相关组件]
 *
 * @param [参数说明]
 * @return [返回值说明]
 */
```

## 🏗️ 注释分类体系

### 1. 类级别注释 - 总体架构描述

```java
/**
 * TODO: 主界面控制器类 - CycBiOx插件的核心UI组件
 *
 * 功能概述:
 * ├── 界面管理: 三区域布局(基础设置/阈值配置/细胞分类)
 * ├── 模式控制: CREATE_CLASSIFIER(预览模式) vs LOAD_CLASSIFIER(执行模式)
 * ├── 通道处理: 智能识别QuPath图像通道，支持C2/C3/C4标准命名
 * ├── 阈值管理: 手动滑块调节 + 自动算法计算(Otsu/Triangle等)
 * ├── 表型定义: 用户自定义细胞类型和标记状态规则
 * ├── 数据导出: CSV格式输出分类结果和统计信息
 * └── ROI支持: 可选择性处理特定区域的细胞
 *
 * 调用关系:
 * - 由 CellPhenotypeExtension.showCellPhenotypeManager() 创建和显示
 * - 调用 CellPhenotypeAPI 执行分类算法
 * - 调用 CellClassificationService 处理批量细胞分类
 * - 与 QuPathGUI 深度集成，读取图像数据和细胞检测结果
 */
```

### 2. 变量级别注释 - 数据结构说明

```java
// TODO: 通道数据管理 - 处理QuPath图像通道信息
// availableChannels: 从QuPath图像中提取的可用通道列表(排除DAPI)
// channelNameMapping: 处理用户重命名通道的映射关系
//   - Key: 显示名称, Value: 原始metadata名称
//   - 支持 C2/C3/C4 标准QuPath命名格式
// 调用: loadAvailableChannels() -> 初始化, findMeasurementName() -> 查找使用
private List<String> availableChannels = new ArrayList<>();
private Map<String, String> channelNameMapping = new HashMap<>();
```

### 3. 方法级别注释 - 功能和流程描述

```java
/**
 * TODO: 加载可用通道 - 从QuPath图像数据中提取和处理通道信息
 *
 * 核心功能:
 * ├── 图像通道检测: 从ImageData获取所有通道的元数据信息
 * ├── DAPI过滤: 智能识别和跳过核染色通道(DAPI/Hoechst/Nucleus)
 * ├── 通道映射构建: 创建显示名称到原始名称的映射关系
 * ├── C索引生成: 支持QuPath标准的C1/C2/C3/C4命名格式
 * ├── 连续索引: 生成跳过DAPI后的连续编号索引
 * └── 调试信息: 详细记录通道分析过程和映射结果
 *
 * 映射策略:
 * - 基础映射: displayName -> originalName
 * - C索引映射: displayName_INDEX -> C(i+1) (与QuPath measurement名称一致)
 * - 连续索引: displayName_CONSECUTIVE -> 跳过DAPI后的连续编号
 *
 * 调用时机:
 * - 构造函数初始化时
 * - 图像切换时重新加载
 * - 手动刷新通道信息时
 *
 * 被调用方法:
 * - debugAvailableMeasurements() -> 调试输出实际measurement名称
 *
 * 影响组件:
 * - availableChannels列表更新
 * - channelNameMapping映射表更新
 * - 阈值界面动态重建
 */
```

### 4. 枚举/常量注释 - 选项和配置说明

```java
// TODO: 操作模式枚举 - 定义插件的两种主要工作模式
/**
 * CREATE_CLASSIFIER: 创建分类器模式
 * - 功能: 单通道实时预览，使用RadioButton选择
 * - 目的: 调试阈值设置，实时查看分类效果
 * - 特点: 非破坏性操作，仅显示紫色/灰色预览
 *
 * LOAD_CLASSIFIER: 加载分类器模式
 * - 功能: 多通道批量处理，使用CheckBox选择
 * - 目的: 正式执行分类，永久应用结果
 * - 特点: 修改PathClass和颜色，触发QuPath显示更新
 *
 * 调用: onModeChanged() -> 根据模式切换UI控件状态
 */
private enum OperationMode {
    CREATE_CLASSIFIER("Create Single Measurement Classifier"),
    LOAD_CLASSIFIER("Load Classifier (Execute Strategy)");
}
```

## 🔧 核心功能区域注释示例

### 界面创建方法注释

```java
/**
 * TODO: 创建优化阈值策略区域 - 插件的核心控制界面
 *
 * 区域架构:
 * ├── 标题头部: 标题 + 模式选择 + 策略控制
 * │   ├── 操作模式下拉框: CREATE_CLASSIFIER ↔ LOAD_CLASSIFIER
 * │   ├── Auto阈值复选框: Manual ↔ Auto模式切换
 * │   ├── 算法选择器: Otsu/Triangle/MaxEntropy/Minimum
 * │   └── 刷新和执行按钮组
 * ├── 动态通道区域: 滚动容器适配30+通道
 * │   ├── 智能高度计算: 4通道紧凑/15通道标准/30+通道大型
 * │   ├── 通道控件生成: createChannelControls()
 * │   └── 垂直滚动支持: 拖拽和按需滚动条
 * └── 执行按钮区域: 右对齐执行策略按钮
 *
 * 交互逻辑:
 * - 模式切换: updateChannelSelectionMode() + updateButtonStates()
 * - Auto模式: 显示算法选择和计算按钮
 * - Manual模式: 隐藏算法控件,启用滑块调节
 * - 控件状态: updateControlStatesForMode() 统一管理
 *
 * 性能优化:
 * - 延迟加载: 仅在需要时创建通道控件
 * - 虚拟滚动: 大量通道时的渲染优化
 * - 状态缓存: 避免重复的控件状态计算
 *
 * 调用关系:
 * - 被 createMainLayout() 调用作为第二区域
 * - 调用 createChannelControls() 生成动态通道列表
 * - 调用 calculateAutoThresholds() 执行自动阈值算法
 *
 * @return 完整配置的阈值策略VBox容器
 */
private VBox createOptimizedThresholdSection() {
    // 实现代码...
}
```

### 算法处理方法注释

```java
/**
 * TODO: 获取图像动态范围 - 智能检测图像数据的数值范围
 *
 * 检测策略:
 * 1. 优先从实际measurement数据中统计最小/最大值
 * 2. 采样前1000个细胞避免性能问题
 * 3. 回退到图像位深度判断(8位/16位/32位浮点)
 * 4. 最终回退到16位范围(0-65535)
 *
 * 用途:
 * - 为对数滑块设置合理的范围
 * - 确保阈值控件适配不同类型的图像数据
 * - 支持不同位深度和数值范围的图像
 *
 * 调用位置: createLogarithmicSlider() 创建滑块时
 *
 * @return double[]{minValue, maxValue} 数值范围数组
 */
private double[] getImageDynamicRange() {
    // 实现代码...
}
```

## 📊 数据流程和状态管理注释

### 状态变量组注释

```java
// TODO: 核心状态变量群 - 控制插件的运行状态和模式
private OperationMode currentMode = OperationMode.CREATE_CLASSIFIER;  // 当前操作模式
private ComboBox<OperationMode> modeComboBox;                         // 模式选择控件
private CheckBox autoThresholdCheckBox;                               // 自动阈值开关
private Button executeButton;                                         // 执行按钮
private Button refreshButton;                                         // 刷新按钮
private ComboBox<String> algorithmComboBox;                          // 算法选择
private Button calculateButton;                                       // 计算按钮
private boolean livePreviewEnabled = false;                          // 实时预览状态
private String currentPreviewChannel = null;                         // 当前预览通道
private List<String> selectedChannelsFromThreshold = new ArrayList<>(); // 从阈值操作传递的通道列表
```

### 数据映射注释

```java
// TODO: 阈值界面组件映射 - 动态生成的通道控制组件
// 设计模式: 每个通道对应一套完整的控制组件
// channelRadioButtons: CREATE模式下的单选按钮(单通道预览)
// channelCheckBoxes: LOAD模式下的复选框(多通道处理)
// measurementComboBoxes: 测量类型选择下拉框
// thresholdSliders: 对数刻度阈值滑块
// thresholdFields: 阈值数值输入框
// 调用: createChannelControls() -> 创建, updateControlStates() -> 状态管理
private final Map<String, RadioButton> channelRadioButtons = new HashMap<>();
private final Map<String, CheckBox> channelCheckBoxes = new HashMap<>();
private final Map<String, ComboBox<String>> measurementComboBoxes = new HashMap<>();
private final Map<String, Slider> thresholdSliders = new HashMap<>();
private final Map<String, TextField> thresholdFields = new HashMap<>();
```

## 🎨 界面布局注释规范

### 布局架构描述

```java
/**
 * TODO: 创建主布局 - 构建插件的完整三区域界面结构
 *
 * 布局架构:
 * ├── 基础设置区域: createBasicSettingsSection()
 * │   ├── 配置名称设置
 * │   ├── 保存路径选择
 * │   └── ROI处理选项
 * ├── 阈值策略区域: createThresholdSection()
 * │   ├── 操作模式切换(CREATE/LOAD)
 * │   ├── Auto/Manual阈值策略
 * │   ├── 动态通道控制列表
 * │   └── 算法选择和执行按钮
 * ├── 细胞分类区域: createClassificationSection()
 * │   ├── 表型管理表格
 * │   ├── 优先级调整控件
 * │   └── 批量操作按钮
 * └── 操作按钮区域: createActionButtonsSection()
 *     ├── 运行检测按钮
 *     └── 数据导出按钮
 *
 * 滚动设置:
 * - 水平: 适应宽度,无水平滚动条
 * - 垂直: 按需显示滚动条
 * - 内容: 10px间距和边距
 *
 * @return 配置好的ScrollPane主容器
 */
```

## 🔄 调用关系注释模式

### 上下文调用链

```java
/**
 * TODO: 显示主界面 - 创建并显示插件的主窗口
 *
 * 功能流程:
 * ├── 检查窗口是否已存在(避免重复创建)
 * ├── 刷新通道信息和阈值配置
 * ├── 创建JavaFX Stage和Scene
 * ├── 设置窗口属性(标题/模态/大小等)
 * ├── 初始化细胞选择高亮机制
 * └── 注册窗口关闭事件处理
 *
 * 窗口设置:
 * - 尺寸: 670x750像素
 * - 模态: 非模态窗口
 * - 父窗口: QuPath主窗口
 * - 可滚动: 垂直滚动支持
 *
 * 调用关系:
 * - 被 CellPhenotypeExtension.showCellPhenotypeManager() 调用
 * - 调用 createMainLayout() 构建界面布局
 * - 调用 initializeCellSelectionHighlighting() 初始化选择机制
 */
```

## 🏷️ 特殊注释标记

### 性能相关注释

```java
// TODO: 性能优化 - 大规模数据处理优化策略
// 采样策略: 处理前1000个细胞避免性能问题
// 并行处理: 使用parallelStream提升处理速度
// 内存管理: 流式处理避免OOM错误
// 缓存机制: 避免重复计算和数据查询
```

### 兼容性相关注释

```java
// TODO: QuPath集成 - 深度集成QuPath原生API
// ImageData访问: 通过qupath.getImageData()获取图像数据
// PathClass管理: 使用PathClass.fromString()创建分类标签
// Hierarchy更新: 调用fireHierarchyChangedEvent()触发显示更新
// 版本兼容: 支持QuPath 0.6.0+版本
```

### 调试支持注释

```java
// TODO: 调试支持 - 详细的日志和状态监控
// 日志级别: DEBUG级别输出详细的处理信息
// 状态追踪: 记录关键变量和状态变化
// 错误处理: 完善的异常捕获和降级处理
// 性能监控: 处理时间和内存使用统计
```

## 📝 注释维护建议

### 1. 保持同步
- 代码变更时及时更新注释
- 确保功能描述与实际实现一致
- 调用关系变化时更新相关注释

### 2. 详细程度
- 核心方法: 详细的功能和调用关系描述
- 辅助方法: 简洁的功能说明
- 复杂逻辑: 算法步骤和决策依据

### 3. 可读性
- 使用树形结构清晰展示层次关系
- 重要信息使用emoji和标记突出
- 避免过度冗长的描述

### 4. 实用性
- 包含调试和修改的实用信息
- 提供相关文件和方法的定位指导
- 记录设计决策和实现考虑

---

**这套TODO Tree注释规范确保了CycBiOx代码的可维护性和可理解性，帮助开发者快速理解代码结构和修改要点。**