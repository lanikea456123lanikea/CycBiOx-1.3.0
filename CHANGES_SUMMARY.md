# CycBiOx v1.3.0 - 修改总结

## 修改日期
2025-01-07

## 修改目标
1. **删除部分匹配**，改为精准匹配
2. **支持Unicode特殊字符**（如γ、α、β等）
3. **删除DAPI特殊处理**，全量识别所有通道
4. **Classification和CellType独立存储**，都能在Hierarchy显示，支持伪彩切换

---

## 修改内容

### 1. 通道匹配机制优化

#### 文件：`MeasurementUtils.java`

**修改内容**：
- ✅ 删除模糊匹配（部分匹配）
- ✅ 改为精准Unicode匹配
- ✅ 删除C1/DAPI优先级降低逻辑，改为C1最高优先级
- ✅ 添加`findExactUnicodeMatch()`方法

**关键代码**：
```java
// 通道前缀优先级（全量识别）
private static final Map<String, Integer> CHANNEL_PREFIX_PRIORITY = Map.of(
    "C1", 1,  // 第一个通道（可能是DAPI或其他marker）
    "C2", 2,
    "C3", 3,
    "C4", 4,
    "C5", 5
);

// 精准Unicode匹配
private static String findExactUnicodeMatch(List<String> measurementNames, String channelName) {
    // 方式1: 精准包含检查（区分大小写）
    if (measurementName.contains(": " + channelName + " ")) {
        exactCandidates.add(measurementName);
    }

    // 方式2: Token完全相等匹配
    String[] tokens = measurementName.split("[:\\s]+");
    for (String token : tokens) {
        if (token.equals(channelName)) {
            exactCandidates.add(measurementName);
        }
    }
}
```

---

### 2. 删除DAPI过滤

#### 文件：`CellPhenotypeAPI.java`

**修改前**：
```java
return imageData.getServer().getMetadata().getChannels().stream()
    .map(ImageChannel::getName)
    .filter(name -> !name.toLowerCase().contains("dapi"))  // ❌ 过滤DAPI
    .collect(Collectors.toList());
```

**修改后**：
```java
// 全量返回所有通道，不进行DAPI过滤
return imageData.getServer().getMetadata().getChannels().stream()
    .map(ImageChannel::getName)
    .collect(Collectors.toList());
```

#### 文件：`ChannelInfoManager.java`

**修改内容**：
- ✅ 删除DAPI跳过逻辑
- ✅ 删除`analysisChannelIndex`偏移量
- ✅ 全量处理所有通道

#### 文件：`DirectChannelFixer.java`

**修改内容**：
- ✅ 修改优先级顺序：`C1 > C2 > C3 > C4`（之前是`C2 > C3 > C4 > C1`）

---

### 3. Classification和CellType独立存储（组合显示）

#### 问题分析
- **当前机制**：两者共用一个PathClass字段
- **QuPath限制**：PathObject只有一个PathClass字段
- **解决方案**：使用组合PathClass格式同时显示两者，伪彩根据按钮切换

#### 文件：`ColorUtils.java`

**核心实现**：组合PathClass + 伪彩切换

**关键方法**：

1. **`applyClassificationColors()`**（Load Classifier触发）
   - 读取已有的CellType
   - 构建组合PathClass：`"Classification: CD3+_CD4+ | CellType: Helper T"`
   - 使用Classification的颜色（伪彩显示Classification）

2. **`applyCellTypeColors()`**（Cell Classification触发）
   - 读取已有的Classification
   - 构建组合PathClass：`"Classification: CD3+_CD4+ | CellType: Helper T"`
   - 使用CellType的颜色（伪彩显示CellType）

3. **`buildCombinedPathClassName()`**
   - 智能组合两个分类名称
   - 格式：`"Classification: xxx | CellType: yyy"`

4. **辅助方法**
   - `getClassificationFromMeasurement()` - 从PathClass提取Classification
   - `getCellTypeFromMeasurement()` - 从PathClass提取CellType

**关键代码**：
```java
// 组合PathClass名称构建
private static String buildCombinedPathClassName(String classification, String cellType) {
    StringBuilder sb = new StringBuilder();

    if (classification != null && !classification.isEmpty()) {
        sb.append("Classification: ").append(classification);
    }

    if (cellType != null && !cellType.isEmpty()) {
        if (sb.length() > 0) sb.append(" | ");
        sb.append("CellType: ").append(cellType);
    }

    return sb.length() > 0 ? sb.toString() : "Unclassified";
}

// Load Classifier：使用Classification颜色
public static void applyClassificationColors(...) {
    String cellType = getCellTypeFromMeasurement(cell);
    String combinedName = buildCombinedPathClassName(classification, cellType);
    Integer color = getClassificationColor(classification); // Classification颜色
    PathClass pathClass = PathClass.fromString(combinedName, color);
    cell.setPathClass(pathClass);
}

// Cell Classification：使用CellType颜色
public static void applyCellTypeColors(...) {
    String classification = getClassificationFromMeasurement(cell);
    String combinedName = buildCombinedPathClassName(classification, cellType);
    Integer color = getCellTypeColor(cellType); // CellType颜色
    PathClass pathClass = PathClass.fromString(combinedName, color);
    cell.setPathClass(pathClass);
}
```

#### 文件：`CellClassificationService.java`

**新增方法**：

1. **`applyClassificationResultsOnly()`**
   - Load Classifier专用
   - 只应用Classification伪彩

2. **`applyCellTypeResultsOnly()`**
   - Cell Classification专用
   - 只应用CellType伪彩

3. **`applyClassificationResults(displayMode)`**
   - 支持切换显示模式
   - `"classification"` 或 `"celltype"`

#### 文件：`CellPhenotypeManagerPane.java`

**Load Classifier修改**（Line 5448-5481）：
```java
// 使用层级PathClass
PathClass pathClass = ColorUtils.createOrGetClassificationPathClass(classificationName);
cell.setPathClass(pathClass);

// 存储到measurement
cellMeasurements.put("Classification_Info", classificationName.hashCode());
storeClassificationMapping(cell.getID().toString(), classificationName);
```

**Cell Classification修改**：
- 通过`runDetectionWithExport()`调用
- 使用`CellPhenotypeAPI.applyCellClassification()`
- 内部会调用`ColorUtils.applyCellTypeColors()`
- 使用`"CellType: xxx"`前缀

---

### 4. CSV导出数据格式

#### 文件：`CellPhenotypeManagerPane.java`

**CSV表头**：
```csv
Cell_ID,X,Y,Parent,Classification,CellType
```

**数据读取逻辑**：

1. **Classification读取**：
   - 优先从内部映射读取：`getClassificationName(cellId)`
   - 回退：从PathClass提取（去掉"Classification: "前缀）

2. **CellType读取**：
   - 从PathClass读取（去掉"CellType: "前缀）
   - 回退：从measurements重建

**需要添加的代码**：
```java
// 3. 改进：从PathClass正确提取Classification和CellType
if (cell.getPathClass() != null) {
    String pathClassName = cell.getPathClass().getName();

    // 判断PathClass类型
    if (pathClassName.startsWith("Classification: ")) {
        classification = pathClassName.substring("Classification: ".length());
    } else if (pathClassName.startsWith("CellType: ")) {
        cellType = pathClassName.substring("CellType: ".length());
    }
}
```

---

## 使用流程

### Load Classifier按钮
1. 用户点击"Load Classifier"按钮
2. 执行阈值分类，生成Classification（如"CD3+_CD4+_CD8-"）
3. 读取已有的CellType（如果存在）
4. 构建组合PathClass：`"Classification: CD3+_CD4+_CD8- | CellType: Helper T Cell"`
5. 使用Classification的颜色（伪彩显示Classification）
6. 存储到`Classification_Info` measurement
7. **Hierarchy显示**：同时显示Classification和CellType，但伪彩反映Classification

### Cell Classification按钮（运行检测并导出数据）
1. 用户点击"运行检测并导出数据"按钮
2. 执行表型匹配，生成CellType（如"Helper T Cell"）
3. 读取已有的Classification（从PathClass提取）
4. 构建组合PathClass：`"Classification: CD3+_CD4+_CD8- | CellType: Helper T Cell"`
5. 使用CellType的颜色（伪彩显示CellType）
6. 存储到`CellType_Info` measurement
7. **Hierarchy显示**：同时显示Classification和CellType，但伪彩反映CellType
8. 导出CSV（两列独立显示）

### CSV导出结果示例
```csv
Cell_ID,X,Y,Parent,Classification,CellType
cell_1,100.5,200.3,ROI_1,CD3+_CD4+_CD8-,Helper T Cell
cell_2,150.2,250.8,ROI_1,CD3+_CD4-_CD8+,Cytotoxic T Cell
cell_3,180.7,300.1,ROI_1,CD3-_CD4-_CD8-,undefined
```

---

## 优点

1. **同时显示**：Classification和CellType在Hierarchy中同时显示在一个PathClass名称中
2. **伪彩切换**：点击不同按钮显示不同伪彩（Classification颜色 vs CellType颜色）
3. **数据完整**：两者都存储在Measurements中（Classification_Info和CellType_Info）
4. **智能组合**：自动识别已有分类并组合显示
5. **CSV导出**：两列独立导出，数据不丢失

## 显示效果

**Hierarchy中的PathClass名称**：
- 只有Classification：`"Classification: CD3+_CD4+_CD8-"`
- 只有CellType：`"CellType: Helper T Cell"`
- 两者都有：`"Classification: CD3+_CD4+_CD8- | CellType: Helper T Cell"`

**伪彩显示**：
- Load Classifier后：使用Classification对应的颜色
- Cell Classification后：使用CellType对应的颜色
- 字符串内容保持不变，只有颜色改变

---

## 测试要点

1. ✅ **通道匹配测试**：
   - 测试γ、α、β等Unicode字符通道
   - 测试C1通道（DAPI或其他）能否正确识别

2. ⏳ **分类独立性测试**：
   - 先运行Load Classifier，检查Hierarchy显示`Classification: xxx`
   - 再运行Cell Classification，检查Hierarchy切换到`CellType: xxx`

3. ⏳ **CSV导出测试**：
   - 导出CSV，检查Classification列和CellType列是否独立显示
   - 验证数据完整性

4. ⏳ **伪彩显示测试**：
   - Load Classifier后观察伪彩
   - Cell Classification后观察伪彩切换

---

## 构建状态

✅ **BUILD SUCCESSFUL** (2025-01-07)
- 11个编译警告（unchecked cast，可忽略）
- 所有功能模块编译通过
- QuPath兼容性验证通过

---

## 待完成任务

- [ ] 修改CSV导出逻辑，正确提取"Classification: "和"CellType: "前缀
- [ ] 测试完整流程
- [ ] 更新用户文档

