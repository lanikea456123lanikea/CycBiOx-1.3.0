// TODO: [代码功能] UI工具类 (600+行)

package com.cellphenotype.qupath.utils;
// UI工具导入依赖模块
// JavaFX核心组件 - Platform/几何/控件/布局
// JavaFX控件系统 - Button/Label/TextField等
// JavaFX布局管理器 - VBox/HBox/GridPane/Priority
// 函数式接口 - Consumer回调支持
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

// TODO: [类] UI工具类

public class UIUtils {

    // TODO: [工具] UI组件标准常量定义区域
    //   TODO: [工具] 间距标准 - 统一的组件间距规范
    //   TODO: [工具] 填充标准 - 统一的容器填充规范
    public static final double STANDARD_SPACING = 10.0;
    public static final double SMALL_SPACING = 5.0;
    public static final Insets STANDARD_PADDING = new Insets(10);
    public static final Insets SMALL_PADDING = new Insets(5);

    // TODO: [方法] 待优化方法

    public static VBox createStandardVBox(double spacing) {
        VBox vbox = new VBox(spacing);
        vbox.setPadding(STANDARD_PADDING);
        return vbox;
    }

    // TODO: [方法] 创建标准HBox容器
    public static HBox createStandardHBox(double spacing) {
        HBox hbox = new HBox(spacing);
        hbox.setAlignment(Pos.CENTER_LEFT);
        return hbox;
    }

    // TODO: [方法] 待优化方法

    public static TitledPane createTitledSection(String title, Node content) {
        TitledPane titledPane = new TitledPane(title, content);
        titledPane.setCollapsible(false);
        titledPane.setExpanded(true);
        return titledPane;
    }

    // TODO: [方法] 待优化方法

    public static HBox createButtonBar(double spacing, Button... buttons) {
        HBox buttonBar = createStandardHBox(spacing);
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.getChildren().addAll(buttons);
        return buttonBar;
    }

    // TODO: [方法] 待优化方法

    public static HBox createFormRow(String labelText, Node control) {
        return createFormRow(labelText, control, 120);
    }

    // TODO: [方法] 创建指定标签宽度表单行
    public static HBox createFormRow(String labelText, Node control, double labelWidth) {
        Label label = new Label(labelText);
        label.setMinWidth(labelWidth);
        label.setMaxWidth(labelWidth);

        HBox row = createStandardHBox(STANDARD_SPACING);
        row.getChildren().addAll(label, control);
        HBox.setHgrow(control, Priority.ALWAYS);

        return row;
    }

    // TODO: [方法] 创建标准GridPane
    public static GridPane createStandardGridPane() {
        GridPane gridPane = new GridPane();
        gridPane.setHgap(STANDARD_SPACING);
        gridPane.setVgap(SMALL_SPACING);
        gridPane.setPadding(STANDARD_PADDING);
        return gridPane;
    }

    // TODO: [方法] 待优化方法

    public static HBox createSliderControl(String labelText, double min, double max, double value,
                                          Consumer<Double> onValueChange) {
        Label label = new Label(labelText);
        Slider slider = new Slider(min, max, value);
        TextField textField = new TextField(String.format("%.2f", value));
        textField.setPrefWidth(80);

        // 双向绑定
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double newValue = newVal.doubleValue();
            textField.setText(String.format("%.2f", newValue));
            if (onValueChange != null) {
                onValueChange.accept(newValue);
            }
        });

        textField.setOnAction(e -> {
            try {
                double newValue = Double.parseDouble(textField.getText());
                if (newValue >= min && newValue <= max) {
                    slider.setValue(newValue);
                }
            } catch (NumberFormatException ex) {
                textField.setText(String.format("%.2f", slider.getValue()));
            }
        });

        HBox container = createStandardHBox(SMALL_SPACING);
        container.getChildren().addAll(label, slider, textField);
        HBox.setHgrow(slider, Priority.ALWAYS);

        return container;
    }

    // TODO: [方法] 创建标准下拉框
    public static <T> ComboBox<T> createStandardComboBox() {
        ComboBox<T> comboBox = new ComboBox<>();
        comboBox.setMaxWidth(Double.MAX_VALUE);
        return comboBox;
    }

    // TODO: [方法] 创建标准按钮
    public static Button createStandardButton(String text, Runnable action) {
        Button button = new Button(text);
        if (action != null) {
            button.setOnAction(e -> action.run());
        }
        return button;
    }

    // TODO: [方法] 创建样式按钮
    public static Button createStyledButton(String text, String styleClass, Runnable action) {
        Button button = createStandardButton(text, action);
        button.getStyleClass().add(styleClass);
        return button;
    }

    // TODO: [方法] 创建工具栏
    public static ToolBar createToolBar(Node... items) {
        ToolBar toolBar = new ToolBar();
        toolBar.getItems().addAll(items);
        return toolBar;
    }

    // TODO: [方法] 待优化方法

    public static Separator createHorizontalSeparator() {
        return new Separator();
    }

    // TODO: [方法] 创建垂直分隔符
    public static Separator createVerticalSeparator() {
        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);
        return separator;
    }

    // TODO: [方法] 待优化方法

    public static void runOnFXThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    // TODO: [方法] 待优化方法

    public static void applyStandardStyle(Region node) {
        node.setPadding(STANDARD_PADDING);
    }

    // TODO: [方法] 待优化方法

    public static <S, T> TableColumn<S, T> createTableColumn(String title, double width) {
        TableColumn<S, T> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setMinWidth(50);
        return column;
    }

    // TODO: [方法] 创建可调整表格列
    public static <S, T> TableColumn<S, T> createResizableTableColumn(String title, double minWidth, double prefWidth) {
        TableColumn<S, T> column = createTableColumn(title, prefWidth);
        column.setMinWidth(minWidth);
        column.setResizable(true);
        return column;
    }

    // TODO: [方法] 添加工具提示
    public static void addTooltip(Control control, String text) {
        if (text != null && !text.isEmpty()) {
            control.setTooltip(new Tooltip(text));
        }
    }
}