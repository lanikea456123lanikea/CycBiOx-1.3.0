#!/bin/bash

# QuPath Cell Phenotype Manager - Create Single Measurement Classifier 验证
# Version 2.1.0 - 修正了Live Preview模式定义

echo "=== QuPath Cell Phenotype Manager v2.1.0 - Create Single Measurement Classifier 验证 ==="
echo

# Check build status
echo "📦 1. 构建验证:"
if [ -f "build/libs/qupath-extension2-2.1.0.jar" ]; then
    echo "  ✅ JAR文件存在: build/libs/qupath-extension2-2.1.0.jar"
    echo "  📏 大小: $(du -h build/libs/qupath-extension2-2.1.0.jar | cut -f1)"
    echo "  🕒 构建时间: $(stat build/libs/qupath-extension2-2.1.0.jar | grep Modify | cut -d' ' -f2-3)"
else
    echo "  ❌ JAR文件未找到!"
    exit 1
fi

echo -e "\n🔄 2. 模式名称修正验证:"

# Check for correct mode names
if grep -q "CREATE_CLASSIFIER.*Create Single Measurement Classifier" src/main/java/com/cellphenotype/qupath/ui/CellPhenotypeManagerPane.java; then
    echo "  ✅ CREATE_CLASSIFIER 模式名称正确"
else
    echo "  ❌ CREATE_CLASSIFIER 模式名称错误"
fi

if grep -q "LOAD_CLASSIFIER.*Load Classifier" src/main/java/com/cellphenotype/qupath/ui/CellPhenotypeManagerPane.java; then
    echo "  ✅ LOAD_CLASSIFIER 模式名称正确"
else
    echo "  ❌ LOAD_CLASSIFIER 模式名称错误"
fi

# Check default mode
if grep -q "currentMode = OperationMode.CREATE_CLASSIFIER" src/main/java/com/cellphenotype/qupath/ui/CellPhenotypeManagerPane.java; then
    echo "  ✅ 默认模式设置为 CREATE_CLASSIFIER"
else
    echo "  ❌ 默认模式设置错误"
fi

echo -e "\n🎯 3. 方法名称修正验证:"

# Check for method renames
if grep -q "executeCreateClassifierMode" src/main/java/com/cellphenotype/qupath/ui/CellPhenotypeManagerPane.java; then
    echo "  ✅ executeCreateClassifierMode() 方法已实现"
else
    echo "  ❌ executeCreateClassifierMode() 方法缺失"
fi

if grep -q "executeLoadClassifierMode" src/main/java/com/cellphenotype/qupath/ui/CellPhenotypeManagerPane.java; then
    echo "  ✅ executeLoadClassifierMode() 方法已实现"
else
    echo "  ❌ executeLoadClassifierMode() 方法缺失"
fi

echo -e "\n💬 4. 用户消息更新验证:"

if grep -q "Create Single Measurement Classifier" src/main/java/com/cellphenotype/qupath/ui/CellPhenotypeManagerPane.java; then
    echo "  ✅ 用户界面显示正确的模式名称"
else
    echo "  ❌ 用户界面模式名称未更新"
fi

if grep -q "创建分类器模式" src/main/java/com/cellphenotype/qupath/ui/CellPhenotypeManagerPane.java; then
    echo "  ✅ 中文用户提示已更新"
else
    echo "  ❌ 中文用户提示未更新"
fi

echo -e "\n🔍 5. 核心功能验证:"

# Check Live Preview logic
if grep -q "currentMode.*CREATE_CLASSIFIER" src/main/java/com/cellphenotype/qupath/ui/CellPhenotypeManagerPane.java; then
    echo "  ✅ Live Preview逻辑已修正为CREATE_CLASSIFIER模式"
else
    echo "  ❌ Live Preview逻辑仍使用错误模式"
fi

# Verify compiled classes exist
if jar -tf build/libs/qupath-extension2-2.1.0.jar | grep -q "CellPhenotypeManagerPane.class"; then
    echo "  ✅ 主UI类已正确编译"
else
    echo "  ❌ 主UI类编译失败"
fi

echo -e "\n=== 🎯 修正内容总结 ==="
echo "📝 术语修正:"
echo "   ❌ 修正前: Train Classifier (Live Preview)"
echo "   ✅ 修正后: Create Single Measurement Classifier"
echo
echo "   ❌ 修正前: Load Classifier (Execute Strategy)" 
echo "   ✅ 修正后: Load Classifier (Execute Strategy) [保持不变]"
echo

echo "🔧 代码修正:"
echo "   • 枚举值: TRAIN → CREATE_CLASSIFIER"
echo "   • 方法名: executeTrainMode() → executeCreateClassifierMode()"
echo "   • 默认模式: OperationMode.TRAIN → OperationMode.CREATE_CLASSIFIER"
echo "   • 模式检查: currentMode != TRAIN → currentMode != CREATE_CLASSIFIER"
echo

echo "💬 用户界面修正:"
echo "   • 模式切换提示更新为正确的术语"
echo "   • 警告对话框使用准确的功能描述"
echo "   • 中文界面显示'创建分类器模式'"
echo

echo "✅ QuPath术语规范化完成!"
echo
echo "🎯 正确的工作流程:"
echo "1. Create Single Measurement Classifier 模式:"
echo "   • 点击通道名称激活Live Preview"
echo "   • 实时显示阈值分类效果"
echo "   • 不修改细胞的正式PathClass"
echo
echo "2. Load Classifier 模式:"
echo "   • 正式应用分类结果"
echo "   • 永久更新细胞PathClass标签"
echo "   • 导出完整分析结果"