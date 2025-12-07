package com.backtester.ui.toolbar;

import com.backtester.core.engine.BacktestConfig;
import com.backtester.core.market.CommissionModel;
import com.backtester.core.market.SlippageModel;
import com.backtester.core.market.SpreadModel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Toolbar for configuring backtest parameters.
 */
public class ParametersToolbar {

    private final HBox root;
    private final Spinner<Double> commissionSpinner;
    private final Spinner<Double> spreadSpinner;
    private final Spinner<Double> slippageSpinner;
    private final CheckBox integerQtyCheckbox;
    private final Button runButton;
    private final Button loadDataButton;
    private final ProgressIndicator progressIndicator;

    private final Runnable onRun;
    private final Runnable onLoadData;

    public ParametersToolbar(Runnable onRun, Runnable onLoadData) {
        this.onRun = onRun;
        this.onLoadData = onLoadData;

        root = new HBox(10);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(10));
        root.getStyleClass().add("toolbar");

        // Load Data Button
        loadDataButton = new Button("Load CSV");
        loadDataButton.getStyleClass().add("primary-button");
        loadDataButton.setOnAction(e -> onLoadData.run());

        // Separator
        Separator sep1 = new Separator();
        sep1.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // Starting Equity (fixed)
        Label equityLabel = new Label("Equity: $100,000");
        equityLabel.getStyleClass().add("toolbar-label");

        // Commission
        Label commLabel = new Label("Commission:");
        commissionSpinner = new Spinner<>(0.0, 1.0, 0.1, 0.01);
        commissionSpinner.setEditable(true);
        commissionSpinner.setPrefWidth(80);
        Label commUnit = new Label("%");

        // Spread
        Label spreadLabel = new Label("Spread:");
        spreadSpinner = new Spinner<>(0.0, 1.0, 0.01, 0.005);
        spreadSpinner.setEditable(true);
        spreadSpinner.setPrefWidth(80);
        Label spreadUnit = new Label("%");

        // Slippage
        Label slipLabel = new Label("Slippage:");
        slippageSpinner = new Spinner<>(0.0, 1.0, 0.05, 0.01);
        slippageSpinner.setEditable(true);
        slippageSpinner.setPrefWidth(80);
        Label slipUnit = new Label("%");

        // Integer Quantities Only (for futures)
        integerQtyCheckbox = new CheckBox("Futures Mode");
        integerQtyCheckbox.setTooltip(new Tooltip("Integer quantities only (for futures contracts)"));

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Progress indicator
        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(24, 24);
        progressIndicator.setVisible(false);

        // Run Button
        runButton = new Button("Run Backtest");
        runButton.getStyleClass().add("run-button");
        runButton.setOnAction(e -> onRun.run());

        // Assemble toolbar
        root.getChildren().addAll(
                loadDataButton,
                sep1,
                equityLabel,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                commLabel, commissionSpinner, commUnit,
                spreadLabel, spreadSpinner, spreadUnit,
                slipLabel, slippageSpinner, slipUnit,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                integerQtyCheckbox,
                spacer,
                progressIndicator,
                runButton
        );
    }

    public Node getNode() {
        return root;
    }

    /**
     * Get the current configuration from toolbar inputs.
     */
    public BacktestConfig getConfig() {
        return BacktestConfig.builder()
                .initialCapital(100_000.0)
                .commissionPercent(commissionSpinner.getValue())
                .spreadPercent(spreadSpinner.getValue())
                .slippagePercent(slippageSpinner.getValue())
                .allowShorts(true)
                .integerQuantityOnly(integerQtyCheckbox.isSelected())
                .build();
    }

    /**
     * Set running state (shows progress indicator).
     */
    public void setRunning(boolean running) {
        progressIndicator.setVisible(running);
        runButton.setDisable(running);
        loadDataButton.setDisable(running);
    }

    /**
     * Get commission value.
     */
    public double getCommission() {
        return commissionSpinner.getValue();
    }

    /**
     * Get spread value.
     */
    public double getSpread() {
        return spreadSpinner.getValue();
    }

    /**
     * Get slippage value.
     */
    public double getSlippage() {
        return slippageSpinner.getValue();
    }
}
