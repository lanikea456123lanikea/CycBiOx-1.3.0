// TODO: [代码功能] 插件主入口 (200+行) ⭐ 核心文件

package com.cellphenotype.qupath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellphenotype.qupath.ui.CellPhenotypeManagerPane;

import javafx.scene.control.MenuItem;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

// TODO: [类] CycBiOx QuPath扩展入口类

public class CellPhenotypeExtension implements QuPathExtension {

    // TODO: [字段] 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(CellPhenotypeExtension.class);

    // TODO: [字段] 插件基本信息
    private static final String EXTENSION_NAME = "CycBiOx";
    private static final String EXTENSION_DESCRIPTION =
        "CycBiOx - Advanced Immunofluorescence Cell Classification and Phenotype Analysis for QuPath. " +
        "HIGH-PERFORMANCE extension for automated cell type identification and protein expression analysis supporting 10M+ cells with " +
        "optimized classification algorithms, real-time phenotype assignment, and seamless QuPath integration.";
    private static final String VERSION = "1.0.0";

    // TODO: [方法] 插件安装入口
    @Override
    public void installExtension(QuPathGUI qupath) {
        logger.info("Installing CycBiOx Extension v{}", VERSION);

        // TODO: [UI组件] 创建菜单项
        MenuItem menuItem = new MenuItem("CycBiOx");
        menuItem.setOnAction(e -> {
            logger.debug("Opening CycBiOx interface");
            showCellPhenotypeManager(qupath);
        });

        // TODO: [集成] 菜单注册
        MenuTools.addMenuItems(
            qupath.getMenu("Extensions", true),
            menuItem
        );

        logger.info("CycBiOx Extension v{} installed successfully", VERSION);
    }

    // TODO: [方法] 显示主界面
    private void showCellPhenotypeManager(QuPathGUI qupath) {
        try {
            CellPhenotypeManagerPane pane = new CellPhenotypeManagerPane(qupath);
            pane.show();
        } catch (Exception e) {
            logger.error("Failed to open CycBiOx", e);
            // TODO: [方法] 异常重新抛出
            throw new RuntimeException("Failed to open CycBiOx: " + e.getMessage(), e);
        }
    }
    // TODO: [集成] QuPath扩展接口实现

    // TODO: [方法] 获取插件名称
    @Override
    public String getName() {
        return EXTENSION_NAME;
    }

    // TODO: [方法] 获取插件描述
    @Override
    public String getDescription() {
        return EXTENSION_DESCRIPTION;
    }

    // TODO: [方法] 获取插件版本
    @Override
    public Version getVersion() {
        return Version.parse(VERSION);
    }

    // TODO: [方法] 插件信息字符串
    @Override
    public String toString() {
        return String.format("%s v%s (Compatible with QuPath 0.6.0+)",
                           EXTENSION_NAME, VERSION);
    }
}