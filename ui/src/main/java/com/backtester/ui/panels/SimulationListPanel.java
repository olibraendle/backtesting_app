package com.backtester.ui.panels;

import com.backtester.core.engine.BacktestResult;
import com.backtester.stats.BacktestStatistics;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Panel 2: Simulation history and summary.
 * Lists all backtest runs with quick stats.
 */
public class SimulationListPanel {

    private final VBox root;
    private final TableView<SimulationRow> table;
    private final Consumer<BacktestResult> onSelect;

    public SimulationListPanel(Consumer<BacktestResult> onSelect) {
        this.onSelect = onSelect;

        root = new VBox(10);
        root.setPadding(new Insets(10));

        Label titleLabel = new Label("Click a row to view details");
        titleLabel.getStyleClass().add("panel-hint");

        table = createTable();
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        // Selection listener
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onSelect != null) {
                onSelect.accept(newVal.result);
            }
        });

        root.getChildren().addAll(titleLabel, table);
    }

    private TableView<SimulationRow> createTable() {
        TableView<SimulationRow> t = new TableView<>();

        TableColumn<SimulationRow, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(data -> data.getValue().timeProperty());
        timeCol.setPrefWidth(80);

        TableColumn<SimulationRow, String> strategyCol = new TableColumn<>("Strategy");
        strategyCol.setCellValueFactory(data -> data.getValue().strategyProperty());
        strategyCol.setPrefWidth(120);

        TableColumn<SimulationRow, String> returnCol = new TableColumn<>("Return");
        returnCol.setCellValueFactory(data -> data.getValue().returnProperty());
        returnCol.setPrefWidth(80);

        TableColumn<SimulationRow, String> alphaCol = new TableColumn<>("Alpha");
        alphaCol.setCellValueFactory(data -> data.getValue().alphaProperty());
        alphaCol.setPrefWidth(70);

        TableColumn<SimulationRow, String> sharpeCol = new TableColumn<>("Sharpe");
        sharpeCol.setCellValueFactory(data -> data.getValue().sharpeProperty());
        sharpeCol.setPrefWidth(60);

        TableColumn<SimulationRow, String> tradesCol = new TableColumn<>("Trades");
        tradesCol.setCellValueFactory(data -> data.getValue().tradesProperty());
        tradesCol.setPrefWidth(60);

        TableColumn<SimulationRow, String> winRateCol = new TableColumn<>("Win%");
        winRateCol.setCellValueFactory(data -> data.getValue().winRateProperty());
        winRateCol.setPrefWidth(60);

        t.getColumns().addAll(timeCol, strategyCol, returnCol, alphaCol, sharpeCol, tradesCol, winRateCol);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return t;
    }

    public Node getNode() {
        return root;
    }

    /**
     * Add a new simulation result to the list.
     */
    public void addResult(BacktestResult result, BacktestStatistics stats) {
        SimulationRow row = new SimulationRow(result, stats);
        table.getItems().add(0, row); // Add to top
        table.getSelectionModel().selectFirst();
    }

    /**
     * Clear all results.
     */
    public void clear() {
        table.getItems().clear();
    }

    /**
     * Table row for simulation results.
     */
    public static class SimulationRow {
        private final BacktestResult result;
        private final BacktestStatistics stats;
        private final SimpleStringProperty time;
        private final SimpleStringProperty strategy;
        private final SimpleStringProperty returnPct;
        private final SimpleStringProperty alpha;
        private final SimpleStringProperty sharpe;
        private final SimpleStringProperty trades;
        private final SimpleStringProperty winRate;

        public SimulationRow(BacktestResult result, BacktestStatistics stats) {
            this.result = result;
            this.stats = stats;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            this.time = new SimpleStringProperty(result.getRunTime().format(formatter));
            this.strategy = new SimpleStringProperty(result.getStrategyName());
            this.returnPct = new SimpleStringProperty(String.format("%.1f%%", result.getNetReturnPercent()));
            this.alpha = new SimpleStringProperty(String.format("%.1f%%", result.getAlpha()));
            this.sharpe = new SimpleStringProperty(String.format("%.2f", stats.sharpeRatio()));
            this.trades = new SimpleStringProperty(String.valueOf(stats.totalTrades()));
            this.winRate = new SimpleStringProperty(String.format("%.0f%%", stats.winRate()));
        }

        public SimpleStringProperty timeProperty() { return time; }
        public SimpleStringProperty strategyProperty() { return strategy; }
        public SimpleStringProperty returnProperty() { return returnPct; }
        public SimpleStringProperty alphaProperty() { return alpha; }
        public SimpleStringProperty sharpeProperty() { return sharpe; }
        public SimpleStringProperty tradesProperty() { return trades; }
        public SimpleStringProperty winRateProperty() { return winRate; }
    }
}
