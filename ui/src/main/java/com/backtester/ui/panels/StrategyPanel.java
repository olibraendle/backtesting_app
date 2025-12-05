package com.backtester.ui.panels;

import com.backtester.strategy.BaseStrategy;
import com.backtester.strategy.StrategyParameter;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Panel 4: Strategy information and configuration.
 * Shows strategy details and allows parameter configuration.
 */
public class StrategyPanel {

    private final VBox root;
    private final ComboBox<String> strategySelector;
    private final Label descriptionLabel;
    private final GridPane parametersGrid;
    private final Map<String, Control> parameterControls = new HashMap<>();

    private final Consumer<String> onStrategySelected;
    private BaseStrategy currentStrategy;

    public StrategyPanel(Consumer<String> onStrategySelected) {
        this.onStrategySelected = onStrategySelected;

        root = new VBox(15);
        root.setPadding(new Insets(10));

        // Strategy selector
        Label selectLabel = new Label("Select Strategy:");
        selectLabel.setStyle("-fx-font-weight: bold;");

        strategySelector = new ComboBox<>();
        strategySelector.setPromptText("Choose a strategy...");
        strategySelector.setMaxWidth(Double.MAX_VALUE);
        strategySelector.setOnAction(e -> {
            String selected = strategySelector.getValue();
            if (selected != null && onStrategySelected != null) {
                onStrategySelected.accept(selected);
            }
        });

        // Description
        descriptionLabel = new Label();
        descriptionLabel.setWrapText(true);
        descriptionLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #666;");

        // Parameters section
        Label paramsLabel = new Label("Parameters:");
        paramsLabel.setStyle("-fx-font-weight: bold;");

        parametersGrid = new GridPane();
        parametersGrid.setHgap(10);
        parametersGrid.setVgap(10);

        // Assemble
        root.getChildren().addAll(
                selectLabel,
                strategySelector,
                new Separator(),
                descriptionLabel,
                new Separator(),
                paramsLabel,
                parametersGrid
        );

        VBox.setVgrow(parametersGrid, Priority.ALWAYS);
    }

    public Node getNode() {
        return root;
    }

    /**
     * Set available strategies.
     */
    public void setStrategies(List<String> strategyNames) {
        strategySelector.getItems().clear();
        strategySelector.getItems().addAll(strategyNames);
    }

    /**
     * Set the current strategy to display.
     */
    public void setStrategy(BaseStrategy strategy) {
        this.currentStrategy = strategy;

        if (strategy == null) {
            descriptionLabel.setText("");
            parametersGrid.getChildren().clear();
            parameterControls.clear();
            return;
        }

        // Update description
        descriptionLabel.setText(strategy.getDescription());

        // Build parameter controls
        parametersGrid.getChildren().clear();
        parameterControls.clear();

        List<StrategyParameter> params = strategy.getParameters();
        int row = 0;

        for (StrategyParameter param : params) {
            Label label = new Label(param.name() + ":");
            label.setTooltip(new Tooltip(param.description()));

            Control control = createParameterControl(param);
            parameterControls.put(param.name(), control);

            parametersGrid.add(label, 0, row);
            parametersGrid.add(control, 1, row);
            row++;
        }
    }

    private Control createParameterControl(StrategyParameter param) {
        return switch (param.type()) {
            case INTEGER -> {
                Spinner<Integer> spinner = new Spinner<>(
                        param.getMinInt(),
                        param.getMaxInt(),
                        param.getDefaultInt(),
                        param.getStepInt()
                );
                spinner.setEditable(true);
                spinner.setPrefWidth(100);
                yield spinner;
            }
            case DOUBLE -> {
                Spinner<Double> spinner = new Spinner<>(
                        param.getMinDouble(),
                        param.getMaxDouble(),
                        param.getDefaultDouble(),
                        param.getStepDouble()
                );
                spinner.setEditable(true);
                spinner.setPrefWidth(100);
                yield spinner;
            }
            case BOOLEAN -> {
                CheckBox checkBox = new CheckBox();
                checkBox.setSelected(param.getDefaultBoolean());
                yield checkBox;
            }
            case STRING -> {
                TextField textField = new TextField(param.getDefaultString());
                textField.setPrefWidth(150);
                yield textField;
            }
            case ENUM -> {
                ComboBox<String> comboBox = new ComboBox<>();
                // Would need enum values from parameter
                yield comboBox;
            }
        };
    }

    /**
     * Get current parameter values from UI controls.
     */
    public Map<String, Object> getParameterValues() {
        Map<String, Object> values = new HashMap<>();

        if (currentStrategy == null) {
            return values;
        }

        for (StrategyParameter param : currentStrategy.getParameters()) {
            Control control = parameterControls.get(param.name());
            if (control == null) continue;

            Object value = switch (param.type()) {
                case INTEGER -> ((Spinner<Integer>) control).getValue();
                case DOUBLE -> ((Spinner<Double>) control).getValue();
                case BOOLEAN -> ((CheckBox) control).isSelected();
                case STRING -> ((TextField) control).getText();
                case ENUM -> ((ComboBox<String>) control).getValue();
            };

            values.put(param.name(), value);
        }

        return values;
    }

    /**
     * Get the selected strategy name.
     */
    public String getSelectedStrategy() {
        return strategySelector.getValue();
    }
}
