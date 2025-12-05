package com.backtester.ui.panels;

import com.backtester.core.engine.BacktestResult;
import com.backtester.core.portfolio.Portfolio;
import com.backtester.stats.BacktestStatistics;
import com.backtester.stats.MonteCarloSimulator;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * Panel 1: Benchmarks and Statistics display.
 * Shows equity curve, statistics table, and Monte Carlo results.
 */
public class BenchmarkPanel {

    private final VBox root;
    private final TabPane tabPane;
    private final LineChart<Number, Number> equityChart;
    private final TableView<StatRow> statsTable;
    private final TextArea monteCarloText;

    public BenchmarkPanel() {
        root = new VBox(10);
        root.setPadding(new Insets(10));

        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1: Equity Curve
        equityChart = createEquityChart();
        Tab chartTab = new Tab("Equity Curve", equityChart);

        // Tab 2: Statistics
        statsTable = createStatsTable();
        Tab statsTab = new Tab("Statistics", new ScrollPane(statsTable));

        // Tab 3: Monte Carlo
        monteCarloText = new TextArea();
        monteCarloText.setEditable(false);
        monteCarloText.setStyle("-fx-font-family: monospace;");
        Tab mcTab = new Tab("Monte Carlo", monteCarloText);

        tabPane.getTabs().addAll(chartTab, statsTab, mcTab);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        root.getChildren().add(tabPane);
    }

    private LineChart<Number, Number> createEquityChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Bar");
        xAxis.setAutoRanging(true);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Equity ($)");
        yAxis.setAutoRanging(true);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setLegendVisible(true);

        return chart;
    }

    private TableView<StatRow> createStatsTable() {
        TableView<StatRow> table = new TableView<>();

        TableColumn<StatRow, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(data -> data.getValue().categoryProperty());
        categoryCol.setPrefWidth(120);

        TableColumn<StatRow, String> metricCol = new TableColumn<>("Metric");
        metricCol.setCellValueFactory(data -> data.getValue().metricProperty());
        metricCol.setPrefWidth(180);

        TableColumn<StatRow, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(data -> data.getValue().valueProperty());
        valueCol.setPrefWidth(150);

        table.getColumns().addAll(categoryCol, metricCol, valueCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return table;
    }

    public Node getNode() {
        return root;
    }

    /**
     * Update panel with backtest results.
     */
    public void setResult(BacktestResult result, BacktestStatistics stats,
                          MonteCarloSimulator.MonteCarloResult mcResult) {
        updateEquityChart(result);
        updateStatsTable(stats, result);
        updateMonteCarloText(mcResult);
    }

    private void updateEquityChart(BacktestResult result) {
        equityChart.getData().clear();

        // Strategy equity curve
        XYChart.Series<Number, Number> strategySeries = new XYChart.Series<>();
        strategySeries.setName("Strategy");

        List<Portfolio.EquityPoint> history = result.getEquityHistory();
        int step = Math.max(1, history.size() / 500); // Limit points for performance

        for (int i = 0; i < history.size(); i += step) {
            strategySeries.getData().add(new XYChart.Data<>(i, history.get(i).equity()));
        }

        // Buy & Hold curve
        XYChart.Series<Number, Number> buyHoldSeries = new XYChart.Series<>();
        buyHoldSeries.setName("Buy & Hold");

        double initial = result.getInitialEquity();
        double bhReturn = result.getBuyAndHoldReturn() / 100.0;

        for (int i = 0; i < history.size(); i += step) {
            double progress = (double) i / history.size();
            double bhEquity = initial * (1 + bhReturn * progress);
            buyHoldSeries.getData().add(new XYChart.Data<>(i, bhEquity));
        }

        equityChart.getData().addAll(strategySeries, buyHoldSeries);
    }

    private void updateStatsTable(BacktestStatistics stats, BacktestResult result) {
        statsTable.getItems().clear();

        // P&L Metrics
        addStat("P&L", "Net Profit", formatCurrency(stats.netProfit()));
        addStat("P&L", "Net Return", formatPercent(stats.netReturnPercent()));
        addStat("P&L", "Gross Profit", formatCurrency(stats.grossProfit()));
        addStat("P&L", "Gross Loss", formatCurrency(stats.grossLoss()));
        addStat("P&L", "Profit Factor", formatNumber(stats.profitFactor()));
        addStat("P&L", "Max Equity Run-up", formatPercent(stats.maxEquityRunUpPercent()));

        // Risk Metrics
        addStat("Risk", "Max Drawdown", formatCurrency(stats.maxDrawdown()));
        addStat("Risk", "Max Drawdown %", formatPercent(stats.maxDrawdownPercent()));
        addStat("Risk", "Buy & Hold Return", formatPercent(stats.buyAndHoldReturn()));
        addStat("Risk", "Alpha", formatPercent(stats.alpha()));
        addStat("Risk", "Sharpe Ratio", formatNumber(stats.sharpeRatio()));
        addStat("Risk", "Sortino Ratio", formatNumber(stats.sortinoRatio()));
        addStat("Risk", "Calmar Ratio", formatNumber(stats.calmarRatio()));
        addStat("Risk", "Recovery Factor", formatNumber(stats.recoveryFactor()));

        // Trade Metrics
        addStat("Trades", "Total Trades", String.valueOf(stats.totalTrades()));
        addStat("Trades", "Winning Trades", String.valueOf(stats.winningTrades()));
        addStat("Trades", "Losing Trades", String.valueOf(stats.losingTrades()));
        addStat("Trades", "Win Rate", formatPercent(stats.winRate()));
        addStat("Trades", "Avg Trade", formatCurrency(stats.avgTrade()));
        addStat("Trades", "Avg Win", formatCurrency(stats.avgWin()));
        addStat("Trades", "Avg Loss", formatCurrency(stats.avgLoss()));
        addStat("Trades", "Payoff Ratio", formatNumber(stats.payoffRatio()));
        addStat("Trades", "Expectancy", formatCurrency(stats.expectancy()));
        addStat("Trades", "Largest Win", formatCurrency(stats.largestWin()));
        addStat("Trades", "Largest Loss", formatCurrency(stats.largestLoss()));
        addStat("Trades", "Avg Bars in Trade", formatNumber(stats.avgBarsInTrade()));
        addStat("Trades", "Max Consecutive Wins", String.valueOf(stats.maxConsecutiveWins()));
        addStat("Trades", "Max Consecutive Losses", String.valueOf(stats.maxConsecutiveLosses()));

        // Volatility
        addStat("Volatility", "Return Volatility", formatPercent(stats.returnVolatility()));
        addStat("Volatility", "Downside Deviation", formatPercent(stats.downsideDeviation()));
        addStat("Volatility", "Time in Market", formatPercent(stats.timeInMarket()));

        // Costs
        addStat("Costs", "Total Commissions", formatCurrency(stats.totalCommissions()));
        addStat("Costs", "Total Slippage", formatCurrency(stats.totalSlippage()));
        addStat("Costs", "Total Costs", formatCurrency(stats.totalCosts()));
        addStat("Costs", "Cost Impact", formatPercent(stats.costImpactPercent()));
    }

    private void addStat(String category, String metric, String value) {
        statsTable.getItems().add(new StatRow(category, metric, value));
    }

    private void updateMonteCarloText(MonteCarloSimulator.MonteCarloResult mc) {
        StringBuilder sb = new StringBuilder();
        sb.append("MONTE CARLO ANALYSIS\n");
        sb.append("====================\n\n");
        sb.append(String.format("Simulations: %,d\n\n", mc.numSimulations()));

        sb.append("FINAL EQUITY DISTRIBUTION\n");
        sb.append("-------------------------\n");
        sb.append(String.format("  5th Percentile:  $%,.2f\n", mc.equity5thPercentile()));
        sb.append(String.format(" 25th Percentile:  $%,.2f\n", mc.equity25thPercentile()));
        sb.append(String.format(" 50th Percentile:  $%,.2f (Median)\n", mc.equity50thPercentile()));
        sb.append(String.format(" 75th Percentile:  $%,.2f\n", mc.equity75thPercentile()));
        sb.append(String.format(" 95th Percentile:  $%,.2f\n", mc.equity95thPercentile()));
        sb.append(String.format("            Mean:  $%,.2f\n", mc.equityMean()));
        sb.append(String.format("         Std Dev:  $%,.2f\n\n", mc.equityStdDev()));

        sb.append("MAX DRAWDOWN DISTRIBUTION\n");
        sb.append("-------------------------\n");
        sb.append(String.format("  5th Percentile:  %.2f%%\n", mc.maxDD5thPercentile()));
        sb.append(String.format(" 50th Percentile:  %.2f%% (Median)\n", mc.maxDD50thPercentile()));
        sb.append(String.format(" 95th Percentile:  %.2f%%\n", mc.maxDD95thPercentile()));
        sb.append(String.format("            Mean:  %.2f%%\n\n", mc.maxDDMean()));

        sb.append("RISK METRICS\n");
        sb.append("------------\n");
        sb.append(String.format("Ruin Probability (50%% loss): %.2f%%\n", mc.ruinProbability()));
        sb.append(String.format("Expected Return Range: %s\n", mc.getReturnRange()));
        sb.append(String.format("Expected Drawdown Range: %s\n", mc.getDrawdownRange()));

        monteCarloText.setText(sb.toString());
    }

    private String formatCurrency(double value) {
        return String.format("$%,.2f", value);
    }

    private String formatPercent(double value) {
        return String.format("%.2f%%", value);
    }

    private String formatNumber(double value) {
        if (Double.isInfinite(value)) return "∞";
        if (Double.isNaN(value)) return "N/A";
        return String.format("%.2f", value);
    }

    /**
     * Table row for statistics.
     */
    public static class StatRow {
        private final javafx.beans.property.SimpleStringProperty category;
        private final javafx.beans.property.SimpleStringProperty metric;
        private final javafx.beans.property.SimpleStringProperty value;

        public StatRow(String category, String metric, String value) {
            this.category = new javafx.beans.property.SimpleStringProperty(category);
            this.metric = new javafx.beans.property.SimpleStringProperty(metric);
            this.value = new javafx.beans.property.SimpleStringProperty(value);
        }

        public javafx.beans.property.StringProperty categoryProperty() { return category; }
        public javafx.beans.property.StringProperty metricProperty() { return metric; }
        public javafx.beans.property.StringProperty valueProperty() { return value; }
    }
}
