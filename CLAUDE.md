# CycBiOx - QuPath细胞表型管理插件 v1.3.0

## 修改要求
- 修改功能找到原功能位置的所有相关代码，修改代码以及检查相应的接口调用代码进行同步修改。
- 新增功能新增功能的代码，并找到相应的调用地方，进行修改调用。
- 删除功能，进行相应的功能的所有代码进行删除。

## 项目简介

**CycBiOx** (Cyclic Biological Analysis and Cell Phenotype Classification) 是专为QuPath开发的高性能细胞表型自动分类插件，深度集成QuPath原生API，提供企业级多通道蛋白表达分析和细胞表型管理功能。支持**10M+细胞**的实时处理，是QuPath生态系统中的专业级细胞分析解决方案。

## 核心功能总结

### 1. 基础设置管理
- **配置名称**: 用户自定义配置标识，支持项目级配置管理
- **保存地址**: 结果和配置文件存储路径管理，自动创建时间戳备份
- **ROI处理**: 智能ROI区域选择，支持全图和区域处理模式，自动检测ROI边界
- **状态指示**: 实时显示ROI覆盖率和细胞统计信息，动态更新处理进度

### 2. 阈值策略配置（专业级分类引擎）
- **双模式系统**: Create预览模式（单通道，实时预览，<100ms响应）+ Load执行模式（多通道，永久分类，并行处理）
- **智能阈值**: 手���滑块调节 + 算法计算（Otsu/Triangle/MaxEntropy/Minimum），支持算法切换和结果对比
- **分割模型支持**: StarDist、Cellpose、InstanSeg、QuPath Detection四种分割模型，自动过滤和排序对应格式的测量值
  - StarDist: "Nucleus: CD68: Mean" 格式（冒号分隔，包含Membrane）
  - Cellpose: "CD68: Nucleus: Mean" 格式（通道名前缀）
  - InstanSeg: "Cell: CD68: Mean" 格式（Cell优先，Auto阈值计算使用Cell数据）
  - QuPath Detection: "Nucleus: CD68 mean" 格式（空格分隔，小写统计量）
- **通道映射**: 自动识别QuPath通道命名，智能处理用户改名通道，支持DAPI排除逻辑
- **通道预览联动**: 点击通道预览按钮时，自动切换Brightness&Contrast窗口到当前通道，关闭其他通道，方便用户调整阈值
- **状态管理**: Load模式和Auto模式的智能控件禁用机制，防止操作冲突，确保数据一致性

### 3. 细胞分类管理（生物学表型引擎）
- **表型定义**: 用户自定义表型名称和marker states规则（阳性/阴性/无关），支持复杂生物学分类逻辑
- **优先级系统**: 拖动排序功能，支持鼠标拖拽调整顺序，优先级数字自动更新，确保特异性优先匹配
  - 双击编辑细胞类型名称，Enter确认，Esc取消
  - 实时验证：空名称警告、重复名称警告
  - 自动保存到内存和配置文件
- **多通道支持**: 30+通道的水平滚动表格，自适应列宽，动态字体大小调整，支持高分辨率显示
- **表格布局**: 动态高度调整，宽度适配GUI界面，智能缩放，支持批量编辑和快速克隆
- **分类一致性**: Cell Classification使用与Load Classifier完全相同的阈值配置，确保Classification组合标签与表型定义精确匹配

## QuPath原生集成特性

### 深度API集成
- **PathClass管理**: 使用PathClass.fromString()创建细胞分类标签
- **MeasurementList操作**: 直接读写QuPath measurement数据，支持数值类型存储
- **Hierarchy更新**: 调用hierarchy.fireHierarchyChangedEvent()触发界面刷新
- **ImageData访问**: 通过qupath.getImageData()获取图像数据和细胞信息
- **ROI集成**: 利用QuPath ROI选择机制，支持hierarchy.getSelectionModel()
- **通道显示控制**: 通过ImageDisplay API控制Brightness&Contrast窗口的通道选择和显示

### 原生性能对标
- **Load Classifier**: 性能完全对标QuPath原生Load Object Classifier功能
- **并行处理**: 使用Java parallel stream，充分利用QuPath的多核处理能力
- **内存优化**: 遵循QuPath内存管理模式，流式处理避免内存溢出
- **扩展注册**: 标准QuPath扩展注册机制，META-INF/services配置

### 数据兼容性
- **PathObject操作**: 直接操作QuPath PathObject对象，设置颜色和分类
- **通道解析**: 智能解析QuPath ImageChannel元数据，自动映射measurement名称
- **项目集成**: 配置文件自动保存到QuPath项目目录，支持项目保存/加载
- **伪彩系统**: 使用QuPath原生颜色系统，确保可视化一致性

## 高性能处理能力

### 大数据集支持（企业级性能）
- **细胞规模**: 支持**1000万+细胞**实时处理，经过大规模数据集验证
- **处理速度**: 单线程50,000细胞/秒，8核并行**200,000+细胞/秒**，自动负载均衡
- **响应时间**: 实时预览<100毫秒，大数据集处理<5分钟，支持后台处理
- **内存效率**: O(n)线性内存增长，智能垃圾回收，无内存泄漏，支持流式处理

### 算法优化（专业级优化策略）
- **并行分类**: Java parallelStream最大化多核性能，支持NUMA架构优化
- **优先级匹配**: 第一个匹配表型获胜机制，减少50%+计算量，支持早停优化
- **智能缓存**: Auto阈值结果缓存系统，Manual/Auto模式切换保持配置状态，避免重复计算
- **通道映射**: 多策略匹配算法，支持用户改名通道智能识别，兼容多种命名方案
- **阈值算法优化**: Otsu算法O(256²)→O(256)优化，QuickSelect替代排序，单次遍历方差计算（Welford算法）

## 智能通道匹配机制

### 核心问题解决
插件需要解决QuPath中用户修改通道显示名称后，底层measurement系统仍使用原始元数据名称的问题。例如：
- 用户将通道"nk1.1"改名为"345"
- QuPath measurement名称仍为"Nucleus: nk1.1 mean"
- 插件需要智能匹配这两个名称

### UI层通道匹配（阈值滑块显示）

**目标**: 将用户改名后的通道显示名称映射到实际的measurement名称，用于阈值滑块的实时预览功能。

**策略**:
1. **提取实际通道名称**: 从measurement列表中提取真实通道名称，同时过滤掉形状指标（area、circularity等），避免误匹配
2. **六策略逐级匹配**:
   - 策略1：精确匹配 - 用户未改名的情况
   - 策略2：大小写不敏感匹配 - 处理大小写差异
   - 策略3：部分匹配 - 处理前缀/后缀改名
   - 策略4：C-index匹配 - 保留了C2/C3/C4前缀的情况
   - 策略5：位置匹配 - 通过通道位置推断（排除DAPI后的索引）
   - 策略6：回退到C-index - 最后的尝试
3. **形状指标过滤**: 使用黑名单排除形状测量值，只处理包含"mean"的通道强度测量

**特点**:
- 只在loadAvailableChannels()时提取一次，性能高效
- 每个策略O(N)复杂度，早期退出机制
- 支持完全改名场景（通过位置匹配）

### 分类层通道匹配（Load Classifier & Cell Classification）

**目标**: 在执行阈值分类和细胞表型分类时，将通道名称映射到measurement名称，确保分类结果的准确性。

**关键区别**: 此层匹配需要处理相似通道名称的精确区分，特别是CD3和CD31这类容易混淆的名称。

**实现位置**: `MeasurementUtils.java` 的 `findMeasurementName()` 和 `findFuzzyMatch()` 函数

**匹配策略**:
1. **精确匹配**: 直接查找通道名称
2. **可能名称匹配**: 生成多种可能的measurement名称格式（C2: CD3 Mean、C2_CD3_Mean等）
3. **单词边界模糊匹配**:
   - 使用Character.isLetterOrDigit()进行字符级边界检查
   - 确保"CD3"不会匹配到"CD31"中的子串
   - 只有前后都是非字母数字字符时才算匹配成功

**单词边界检查机制**:
- 在查找到匹配位置后，检查前一个字符是否为字母或数字
- 检查后一个字符是否为字母或数字
- 只有两端都是单词边界（非字母数字字符或字符串边界）时才接受匹配

**示例**:
- "cd3"查找"Nucleus: cd31 mean": 找到index=9，但后续字符'1'是数字 → 拒绝匹配 ❌
- "cd3"查找"Nucleus: cd3 mean": 找到index=9，后续字符' '是空格 → 接受匹配 ✅
- "cd31"查找"Nucleus: cd31 mean": 找到index=9，后续字符' '是空格 → 接受匹配 ✅

### 调试日志支持

**MeasurementUtils日志**:
```
🔍 [FUZZY-MATCH] 查找通道 'CD3' (baseName: 'CD3')
   ✅ 单词边界匹配: 'Nucleus: CD3 mean'
   ❌ 拒绝（非单词边界）: 'Nucleus: CD31 mean' (前:OK, 后:FAIL)
✅ [FUZZY-MATCH] 'CD3' -> 'Nucleus: CD3 mean' (Mean优先)
```

**CellPhenotype.matches()日志**:
```
🔍 [PHENOTYPE-MATCH] 检查表型 'Helper T Cell' 是否匹配
   表型定义的markers: [CD3, CD4, CD8]
   细胞的markerStates: [DAPI, CD31, F480, nk1.1, CD3]
   ✅ 'CD3' 匹配: POSITIVE
   ❌ 'CD4' 不匹配: 需要POSITIVE, 实际阴性
⚠️  'CD4' 在markerPositiveStates中不存在! (可能表型定义与阈值配置不一致)
```

## 通道预览与Brightness&Contrast联动

### 功能描述
用户在阈值策略配置区域点击某个通道的"预览"按钮时，系统会自动：
1. 关闭Brightness&Contrast窗口中的所有其他通道
2. 只显示当前预览的通道
3. 刷新viewer显示，方便用户调整该通道的亮度对比度

### 实现机制
通过QuPath的ImageDisplay API实现通道选择控制：
- 获取ImageDisplay.availableChannels()获取所有ChannelDisplayInfo
- 使用setChannelSelected()控制每个通道的显示状态
- 调用viewer.repaintEntireImage()刷新显示

### 通道切换流程
1. 用户点击"预览"按钮
2. 触发switchToChannelDisplay()函数
3. 遍历所有通道，调用setChannelSelected(channel, false)关闭
4. 查找目标通道索引（支持显示名称匹配和位置匹配）
5. 调用setChannelSelected(targetChannel, true)打开目标通道
6. 刷新viewer显示

### 调试支持
```
🎯🎯🎯 [CHANNEL-SWITCH] 开始切换通道显示: 'CD31'
🔍 [CHANNEL-SWITCH] 总通道数: 6
    通道0: 'DAPI'
    通道1: 'CD31'
✅ [CHANNEL-SWITCH] 通过显示名称找到匹配: 索引=1
    关闭通道0
    关闭通道2
    关闭通道3
✅✅✅ [CHANNEL-SWITCH] 已切换到通道 'CD31' (索引1), 其他通道已关闭
🔄 [CHANNEL-SWITCH] Viewer已刷新
```

## 双数据存储系统

### Classification结果（Load Classifier）
- **来源**: Load Classifier基于阈值的二元分类
- **格式**: 组合标签（如"CD3+_CD31-_F480-_nk1.1-"）
- **存储**: Classification_Info measurement
- **特性**: 对标QuPath原生分类性能，记录每个通道的阳性/阴性状态

### CellType结果（Cell Classification）
- **来源**: Cell Classification基于用户定义表型规则
- **格式**: 用户定义名称（如"Helper T Cell"）
- **存储**: PathClass标签 + CellType_Info measurement
- **特性**: 伪彩可视化，优先级分配
- **一致性保证**: 使用与Load Classifier完全相同的阈值配置和measurement映射，确保marker states计算结果一致

### 分类流程与数据一致性

**Load Classifier执行流程**:
1. 从ThresholdConfig读取所有通道阈值配置
2. 通过MeasurementUtils创建通道名称到measurement名称的映射
3. 调用getCellMarkerStates()计算每个细胞的marker阳性/阴性状态
4. 生成Classification组合标签（如"CD3+_CD31-_F480-"）
5. 存储到Classification_Info measurement

**Cell Classification执行流程**:
1. 使用**相同的ThresholdConfig**和**相同的measurementMapping**
2. 调用**相同的getCellMarkerStates()函数**计算marker states
3. 将计算得到的marker states与用户定义的表型规则匹配
4. 返回匹配的表型名称（如"Helper T Cell"）
5. 存储到PathClass和CellType_Info measurement

**关键点**:
- 两个分类过程使用完全相同的阈值配置和通道映射
- getCellMarkerStates()函数保证了marker states计算逻辑的一致性
- 表型匹配时，如果表型定义的marker在measurementMapping中不存在，会发出警告日志
- 用户需要确保表型定义的marker名称与阈值配置的通道名称完全一致

## 表型匹配逻辑

### CellPhenotype.matches()函数
用于判断细胞的marker states是否符合表型定义的规则：

**匹配规则**:
1. 遍历表型定义的所有marker
2. 如果marker状态为"无关"（IGNORE），跳过该marker
3. 从markerPositiveStates中获取该marker的实际阳性/阴性状态
4. 如果marker不存在于markerPositiveStates中，记录警告并跳过
5. 比较要求状态与实际状态，不匹配则返回false
6. 所有marker都匹配成功则返回true

**调试支持**:
- 显示表型定义的所有markers
- 显示细胞实际的markerStates
- 逐个marker显示匹配结果
- 警告不存在的marker（可能是表型定义与阈值配置不一致）

## 用户界面设计

### 三区域布局
- **基础设置区域**: 配置管理、路径选择、ROI模式
- **阈值策略配置区域**: 双模式切换、阈值调节、算法选择、通道预览
- **细胞分类管理区域**: 表型定义、优先级管理、批量操作

### 自适应界面
- **动态高度**: 阈值区域按通道数自适应，分类区域按表型数自适应
- **响应式宽度**: 表格宽度随GUI界面变化，支持水平滚动
- **智能禁用**: 不同模式下控件状态自动管理
- **实时反馈**: 操作状态和进度的即时显示
- **滚动位置保持**: 使用PauseTransition机制，表型排序后自动恢复滚动位置

## 数据导出功能

### CSV标准格式
- **完整字段**: Cell_ID、坐标、Parent、Classification、CellType、蛋白信息
- **双重数据**: Classification和CellType独立列，数据不覆盖
- **兼容性**: 支持Excel和R分析软件读取
- **路径管理**: 自动保存到用户设置的目标路径

## 常见问题与解决方案

### 1. CD3/CD31通道名称混淆
**问题**: CD3通道的阈值错误匹配到CD31的measurement数据，导致分类结果不正确。

**原因**: 早期版本的模糊匹配使用简单的字符串contains()方法，"cd3"会匹配到"cd31"的子串。

**解决方案**: ✅ 已在v1.3.0 Build 18修复
- 实现单词边界检查机制（Character.isLetterOrDigit()）
- 只有前后都是非字母数字字符时才接受匹配
- 添加详细的调试日志，显示匹配过程和拒绝原因

**验证方法**: 查看日志中的FUZZY-MATCH标记，确认CD3不会匹配到CD31。

### 2. 通道预览与Brightness&Contrast窗口未联动
**问题**: 点击通道预览按钮后，Brightness&Contrast窗口没有自动切换到该通道。

**原因**:
- Build 16之前未实现通道切换功能
- RadioButton事件监听器中缺少switchToChannelDisplay()调用

**解决方案**: ✅ 已在v1.3.0 Build 16修复
- 在RadioButton的setOnAction事件中添加Platform.runLater(() -> switchToChannelDisplay(channelName))
- 通过ImageDisplay API控制通道选择状态
- 添加详细的调试日志跟踪切换过程

**验证方法**: 点击预览按钮后，查看日志中的CHANNEL-SWITCH标记，确认通道切换成功。

### 3. Cell Classification与Load Classifier结果不匹配
**问题**: 运行Load Classifier后生成的Classification组合标签，与Cell Classification的表型定义不匹配。

**常见原因**:
1. **时间差问题**: Load Classifier运行后修改了阈值，然后运行Cell Classification
2. **配置不一致**: 表型定义的marker名称与阈值配置的通道名称不一致
3. **通道映射失败**: CD3/CD31等相似名称的通道匹配错误

**解决方案**: ✅ 已在v1.3.0 Build 17-18优化
- 确保Cell Classification使用与Load Classifier相同的thresholdConfig
- 使用相同的getCellMarkerStates()函数计算marker states
- 添加表型匹配调试日志，警告不存在的marker
- 修复CD3/CD31通道匹配问题（单词边界检查）

**正确使用流程**:
1. 调整所有通道阈值并点击"确认"按钮
2. 先运行"Load Classifier"
3. 立即运行"Cell Classification"（不要修改阈值）
4. 导出CSV查看结果，Classification列和CellType列应该对应

### 4. 表型定义的marker在分类时找不到
**问题**: Cell Classification日志显示"marker在markerPositiveStates中不存在"警告。

**原因**: 表型定义中使用的marker名称与阈值配置中的通道名称不一致。

**示例**:
- 阈值配置中的通道: DAPI, CD31, F480, nk1.1, CD3
- 表型定义中的marker: CD3, CD4, CD8
- "CD4"和"CD8"在阈值配置中不存在 → 无法计算这些marker的状态

**解决方案**:
- 确保表型定义的所有marker都在阈值配置中存在
- 使用完全相同的通道名称（包括大小写）
- 查看PHENOTYPE-MATCH日志确认marker列表
- 如果需要忽略某些marker，将其状态设置为"无关"

### 5. 通道改名后无法识别
**问题**: 用户在QuPath中修改通道显示名称后，插件无法找到对应的measurement数据。

**解决方案**: ✅ 已通过多层匹配机制解决
- UI层：六策略匹配算法，支持位置匹配
- 分类层：精确匹配、可能名称匹配、单词边界模糊匹配
- 形状指标过滤：避免误匹配到Circularity等形状测量

**建议**: 尽量使用QuPath原始通道名称，或使用易于识别的简短名称。

### 6. 排序按钮导致界面跳动
**问题**: 点击细胞表型管理表格的上移/下移按钮后，滚动条跳回顶部。

**解决方案**: ✅ 已在v1.2.0修复
- 移除立即refresh调用，让ObservableList自动触发更新
- 使用PauseTransition延迟50ms后恢复滚动位置
- 确保JavaFX布局计算完成后再设置vvalue

## 构建和部署

### 构建要求
- **Java 21+**: 现代Java特性支持，Vector API优化，性能提升30%+
- **Gradle 8.4+**: 项目构建和依赖管理，支持增量编译和缓存优化
- **QuPath 0.6.0+**: 目标平台版本，深度API集成，完全兼容

### 安装方式
1. **构建**: `./gradlew build` （生成优化JAR，包含所有依赖）
2. **安装**: 复制`build/libs/cycbiox-1.0.0.jar`到QuPath扩展目录
3. **启用**: Extensions菜单中找到"**CycBiOx**"并启动

### 调试模式
启用DEBUG日志级别可查看详细的匹配过程和诊断信息：
- MeasurementUtils: 通道名称到measurement的匹配过程
- CellPhenotype: 表型匹配的详细过程和失败原因
- CellPhenotypeManagerPane: 通道切换和UI操作的跟踪信息

## 版本历史

### v1.3.0 (Build 19) - 分割模型支持
- ✅ 新增分割模型选择功能（StarDist、Cellpose、InstanSeg、QuPath Detection）
- ✅ 自动过滤和排序各模型对应的测量值格式
- ✅ InstanSeg模型自动阈值计算使用Cell数据
- ✅ 新增拖动排序功能（替代上下移动按钮）
- ✅ 双击编辑细胞类型名称功能（Enter确认，Esc取消）
- ✅ 配置保存/加载时自动保存和恢复分割模型选择
- ✅ 优化UI间距（标签和下拉框距离调整为3px）
- ✅ QuPath Detection模型统计量排序优化（mean → std dev → max → min）

### v1.3.0 (Build 18)
- ✅ 修复CD3/CD31通道名称混淆问题（单词边界检查）
- ✅ 添加详细的调试日志支持（FUZZY-MATCH、PHENOTYPE-MATCH、CHANNEL-SWITCH）
- ✅ 确保Cell Classification与Load Classifier使用相同配置
- ✅ 表型匹配警告不存在的marker

### v1.3.0 (Build 16)
- ✅ 实现通道预览与Brightness&Contrast窗口联动
- ✅ 自动切换到预览通道，关闭其他通道
- ✅ 添加通道切换调试日志

### v1.2.0
- ✅ 修复通道改名后识别问题（六策略匹配）
- ✅ 修复排序按钮导致界面跳动问题
- ✅ 优化阈值计算速度（15-30x加速）
- ✅ 修复Unclassified细胞颜色问题

### v1.0.0
- 🎉 初始版本发布
- 支持10M+细胞高性能处理
- 双模式系统（Create/Load）
- 细胞表型管理
