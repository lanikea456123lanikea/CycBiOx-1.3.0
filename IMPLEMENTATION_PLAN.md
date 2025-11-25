# Classification 和 CellType 独立存储实现方案

## 问题分析

- 当前：Classification和CellType共用一个PathClass字段
- 需求：两者独立存储，都能在Hierarchy显示，根据按钮切换伪彩

## QuPath限制

PathObject只有一个PathClass字段，不能同时存储两个分类。

## 解决方案

### 方案A：使用PathClass层级系统（推荐）

QuPath支持层级PathClass：`parent:child`格式

1. Classification使用前缀：`Classification: CD3+_CD4+`
2. CellType使用前缀：`CellType: Helper T Cell`
3. 点击不同按钮切换PathClass

**优点**：
- 利用QuPath原生机制
- Hierarchy能正确分组显示
- 伪彩自然切换

**缺点**：
- 需要频繁切换PathClass
- 可能有性能开销

### 方案B：使用Custom Properties

将Classification存储在PathObject的properties中

**优点**：
- 不占用PathClass
- 读取快速

**缺点**：
- Hierarchy不显示properties
- 需要额外代码管理

### 方案C：使用Measurements + UI切换

两者都存储在measurements，通过UI控制显示哪个

**优点**：
- 数据持久化
- 不修改PathClass

**缺点**：
- Hierarchy不能同时显示两者
- 伪彩切换需要遍历所有细胞

## 最终选择：方案A（层级PathClass） + 智能切换

### 实现步骤

1. **修改Load Classifier**：
   - 生成Classification结果（如"CD3+_CD4+_CD8-"）
   - 存储到PathClass：`PathClass.fromString("Classification: CD3+_CD4+")`
   - 生成Classification专用颜色
   - 同时存储到measurement：`Classification_Info`

2. **修改Cell Classification**：
   - 生成CellType结果（如"Helper T Cell"）
   - 存储到PathClass：`PathClass.fromString("CellType: Helper T Cell")`
   - 生成CellType专用颜色
   - 同时存储到measurement：`CellType_Info`

3. **按钮行为**：
   - **点击Load Classifier**：
     - 执行分类
     - 设置PathClass为`Classification: xxx`
     - 应用Classification伪彩
     - 刷新Hierarchy

   - **点击Cell Classification（运行检测并导出数据）**：
     - 执行分类
     - 设置PathClass为`CellType: xxx`
     - 应用CellType伪彩
     - 刷新Hierarchy
     - 导出CSV（包含两列：Classification和CellType）

4. **CSV导出**：
   - 从measurements读取Classification_Info
   - 从measurements读取CellType_Info
   - 两列独立显示

## 代码修改清单

### 1. ColorUtils.java
- 添加`getClassificationColor()`方法
- 添加`applyClas sificationColors()`方法
- 修改颜色生成策略，区分Classification和CellType

### 2. CellClassificationService.java
- 修改`performThresholdClassification()`：设置PathClass为"Classification: xxx"
- 修改`performPhenotypeClassification()`：设置PathClass为"CellType: xxx"

### 3. CellPhenotypeManagerPane.java
- 修改`executeLoadClassifierMode()`：应用Classification伪彩
- 修改`runDetectionWithExport()`：应用CellType伪彩
- 修改`exportComprehensiveCellData()`：从measurements读取两者

### 4. CellPhenotypeAPI.java
- 添加辅助方法支持新的存储机制

