# v1.6.1 - 调试版查找根本问题

## 问题现状

用户反馈：**还是检测到861个细胞**，不是863个

即使我们使用了：
- v1.6.0: 最简单的中心点检测（与QuPath原生一致）
- 删除所有复杂的几何算法
- 只检查细胞ROI的中心点

**问题依然存在**，这说明问题不在检测算法本身！

---

## v1.6.1调试方案

### 添加详细日志记录
v1.6.1添加了详细的调试日志，将记录以下关键信息：

1. **总细胞数**: `hierarchy.getDetectionObjects().size()`
2. **选中对象数**: `selectedObjects.size()`
3. **选中ROI数**: `selectedROIs.size()`
4. **ROI边界信息**: X, Y, Width, Height
5. **每个被检测细胞的坐标**
6. **最终检测结果**

---

## 如何查看日志

### 方法1: 通过QuPath界面
1. 在QuPath中点击菜单 **View** → **Show Log**
2. 或者直接按键盘 **L** 键
3. 日志窗口会显示在底部

### 方法2: 通过日志级别
- 选择 **"Info"** 或 **"Debug"** 级别
- 查看以 `===` 开头的调试信息

---

## 预期日志输出示例

```
=== DEBUG INFO ===
Total cells in hierarchy: 863
Selected objects count: 1
Selected ROIs count: 1
First ROI bounds: X=100.5, Y=200.3, W=150.0, H=200.0
Filtering 863 cells using 1 selected ROI(s)

[Debug logs showing each detected cell...]

=== FINAL RESULT ===
Cells found in ROI: 861
Detected count: 861
ROI filtering: 861 cells found within 1 selected ROI(s) out of 863 total cells
```

---

## 可能发现的问题

### 情况1: 总细胞数就是861
如果日志显示：
```
Total cells in hierarchy: 861
```
**说明**: QuPath的hierarchy中只有861个细胞，不是863个
**原因**: 可能数据加载问题或过滤器问题

### 情况2: 总细胞数是863，但ROI检测结果是861
如果日志显示：
```
Total cells in hierarchy: 863
Cells found in ROI: 861
```
**说明**: 总细胞数正确，但ROI检测漏掉了2个细胞
**原因**: 可能是ROI边界问题或中心点计算问题

### 情况3: ROI边界信息异常
如果ROI bounds显示：
```
First ROI bounds: X=0, Y=0, W=0, H=0
```
**说明**: ROI对象获取错误
**原因**: 可能是ROI选择逻辑问题

---

## 根据日志判断下一步行动

### 如果总细胞数是861
- 问题不在检测算法
- 需要调查为什么hierarchy中只有861个细胞
- 可能需要检查数据加载或过滤逻辑

### 如果总���胞数是863，但ROI检测是861
- 说明检测算法还需要优化
- 可能需要调整中心点检测或边界处理
- 可能需要研究QuPath如何处理边界情况

### 如果ROI信息异常
- 需要检查ROI选择逻辑
- 可能是`getSelectionModel()`使用不当

---

## 用户需要做什么

1. **在QuPath中加载v1.6.1版本**
2. **创建圆形ROI**
3. **在插件中选择"当前选中细胞"**
4. **运行检测**
5. **打开日志窗口（按L键）**
6. **复制所有以 `===` 开头的调试信息**
7. **将日志信息反馈给我们**

---

## 调试日志关键信息

请特别关注这三行：
```
Total cells in hierarchy: XXX        <- 总细胞数
Cells found in ROI: XXX              <- ROI内细胞数
First ROI bounds: X=..., Y=..., W=..., H=...  <- ROI边界
```

这三行信息将告诉我们问题出在哪里！

---

**版本**: v1.6.1
**状态**: 调试版已编译
**需要**: 用户运行并查看日志
**目标**: 通过日志找到根本原因
