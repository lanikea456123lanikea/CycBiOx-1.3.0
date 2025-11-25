//TODO: [代码功能] 表型编辑对话框 (300+行)


package com.cellphenotype.qupath.ui;

// TODO: [导入] 表型编辑对话框依赖
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cellphenotype.qupath.model.CellPhenotype;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

//TODO: [方法] 简化方法
public class PhenotypeEditorDialog extends Dialog<CellPhenotype> {

    // TODO: [字段] 对话框数据字段
    private final List<String> availableChannels;
    private TextField nameField;
    private Map<String, ComboBox<String>> channelComboBoxes;
    
    //TODO: [构造函数] 对话框初始化
     
    public PhenotypeEditorDialog(Stage owner, List<String> availableChannels) {
        this.availableChannels = availableChannels;
        this.channelComboBoxes = new HashMap<>();

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Add New Cell Phenotype");
        setHeaderText("Define a new cell phenotype");

        // TODO: [创建] 自定义对话框窗格
        DialogPane dialogPane = getDialogPane();
        dialogPane.setContent(createContent());

        // TODO: [添加] 按钮
        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(okButtonType, cancelButtonType);

        // TODO: [验证] 根据输入验证启用/禁用“确定”按钮
        Button okButton = (Button) dialogPane.lookupButton(okButtonType);
        okButton.setDisable(true);

        nameField.textProperty().addListener((observable, oldValue, newValue) -> {
            okButton.setDisable(newValue.trim().isEmpty());
        });

        // TODO: [设置] 
        setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return createPhenotypeFromInput();
            }
            return null;
        });
    }
    
    //TODO: [方法] 创建对话框内容
    private VBox createContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // TODO: [分区] 基本信息部分
        GridPane basicInfo = new GridPane();
        basicInfo.setHgap(10);
        basicInfo.setVgap(10);

        // TODO: [字段] 名称字段
        Label nameLabel = new Label("Phenotype Name:");
        nameField = new TextField();
        nameField.setPromptText("Enter phenotype name (e.g., Helper T Cell)");
        nameField.setPrefWidth(250);
        basicInfo.add(nameLabel, 0, 0);
        basicInfo.add(nameField, 1, 0);

        // TODO: [优先级] 优先级自动分配，无需用户输入

        content.getChildren().add(basicInfo);

        // TODO: [分区] 标记状态部分
        Label markerLabel = new Label("Marker States:");
        markerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        content.getChildren().add(markerLabel);

        GridPane markerGrid = new GridPane();
        markerGrid.setHgap(10);
        markerGrid.setVgap(10);
        markerGrid.setPadding(new Insets(10));
        markerGrid.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5px;");

        // === 修改：支持DAPI通道全量展示 ===
        // 移除DAPI过滤，允许所有通道用于表型定义
        int row = 0;
        for (String channel : availableChannels) {
            // 不再过滤DAPI通道，全量展示
            ComboBox<String> comboBox = createMarkerComboBox();
            channelComboBoxes.put(channel, comboBox);

            markerGrid.add(new Label(channel + ":"), 0, row);
            markerGrid.add(comboBox, 1, row);
            row++;
        }

        content.getChildren().add(markerGrid);
        return content;
    }
    
    // TODO: [方法] 创建标记状态下拉框
    private ComboBox<String> createMarkerComboBox() {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().addAll("阳性", "阴性", "无关");
        comboBox.setValue("无关");
        comboBox.setPrefWidth(120);
        comboBox.setStyle("-fx-font-size: 12px;");
        return comboBox;
    }
    
    // TODO: [方法] 创建表型实例
    private CellPhenotype createPhenotypeFromInput() {
        String name = nameField.getText().trim();
        int priority = 10; // Auto-assign default priority

        CellPhenotype phenotype = new CellPhenotype(name, priority);

        // TODO: [设置] 所有通道的标记状态
        for (Map.Entry<String, ComboBox<String>> entry : channelComboBoxes.entrySet()) {
            String channel = entry.getKey();
            String stateValue = entry.getValue().getValue();

            CellPhenotype.MarkerState markerState;
            switch (stateValue) {
                case "阳性": markerState = CellPhenotype.MarkerState.POSITIVE; break;
                case "阴性": markerState = CellPhenotype.MarkerState.NEGATIVE; break;
                case "无关":
                default: markerState = CellPhenotype.MarkerState.IGNORE; break;
            }

            phenotype = phenotype.withMarkerState(channel, markerState);
        }

        return phenotype;
    }
}