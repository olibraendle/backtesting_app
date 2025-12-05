package com.backtester.ui.charts;

import com.backtester.stats.SensitivityAnalyzer.HeatmapResult;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Heatmap visualization for parameter sensitivity analysis.
 * Shows 2D grid of metric values with color-coded cells.
 */
public class HeatmapChart {

    private final BorderPane root;
    private final Canvas canvas;
    private final Label titleLabel;
    private final Label statsLabel;

    private HeatmapResult data;
    private double cellWidth;
    private double cellHeight;
    private static final int MARGIN = 60;
    private static final int COLOR_BAR_WIDTH = 30;

    public HeatmapChart() {
        root = new BorderPane();
        root.setPadding(new Insets(10));

        // Title
        titleLabel = new Label("Parameter Sensitivity Heatmap");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Canvas for drawing
        canvas = new Canvas(400, 300);

        // Stats label
        statsLabel = new Label();
        statsLabel.setStyle("-fx-font-size: 11px;");

        VBox topBox = new VBox(5, titleLabel, statsLabel);
        root.setTop(topBox);
        root.setCenter(canvas);

        // Tooltip for cell values
        Tooltip tooltip = new Tooltip();
        Tooltip.install(canvas, tooltip);

        canvas.setOnMouseMoved(event -> {
            if (data == null) return;

            double x = event.getX();
            double y = event.getY();

            // Calculate cell index
            int col = (int) ((x - MARGIN) / cellWidth);
            int row = (int) ((y - MARGIN) / cellHeight);

            if (col >= 0 && col < data.param2Values().length &&
                row >= 0 && row < data.param1Values().length) {

                double param1 = data.param1Values()[row];
                double param2 = data.param2Values()[col];
                double value = data.values()[row][col];

                tooltip.setText(String.format(
                        "%s: %.2f\n%s: %.2f\n%s: %.4f",
                        data.param1Name(), param1,
                        data.param2Name(), param2,
                        data.metric().getDisplayName(), value
                ));
                tooltip.show(canvas, event.getScreenX() + 10, event.getScreenY() + 10);
            } else {
                tooltip.hide();
            }
        });

        canvas.setOnMouseExited(event -> tooltip.hide());
    }

    public Node getNode() {
        return root;
    }

    /**
     * Set heatmap data and render.
     */
    public void setData(HeatmapResult result) {
        this.data = result;
        render();
    }

    /**
     * Clear the chart.
     */
    public void clear() {
        this.data = null;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        titleLabel.setText("Parameter Sensitivity Heatmap");
        statsLabel.setText("");
    }

    /**
     * Render the heatmap.
     */
    private void render() {
        if (data == null) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();

        // Clear
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, width, height);

        // Calculate dimensions
        int rows = data.param1Values().length;
        int cols = data.param2Values().length;

        double chartWidth = width - MARGIN * 2 - COLOR_BAR_WIDTH;
        double chartHeight = height - MARGIN * 2;

        cellWidth = chartWidth / cols;
        cellHeight = chartHeight / rows;

        // Find value range
        double minVal = Double.MAX_VALUE;
        double maxVal = Double.MIN_VALUE;
        for (double[] row : data.values()) {
            for (double val : row) {
                if (!Double.isNaN(val)) {
                    minVal = Math.min(minVal, val);
                    maxVal = Math.max(maxVal, val);
                }
            }
        }

        // Draw cells
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double val = data.values()[i][j];
                double x = MARGIN + j * cellWidth;
                double y = MARGIN + i * cellHeight;

                // Calculate color
                Color color = getColorForValue(val, minVal, maxVal);
                gc.setFill(color);
                gc.fillRect(x, y, cellWidth - 1, cellHeight - 1);

                // Highlight optimal cell
                if (i == data.optimalI() && j == data.optimalJ()) {
                    gc.setStroke(Color.WHITE);
                    gc.setLineWidth(2);
                    gc.strokeRect(x + 1, y + 1, cellWidth - 3, cellHeight - 3);
                }
            }
        }

        // Draw axes
        gc.setFill(Color.web("#bbbbbb"));
        gc.setFont(Font.font(10));

        // X-axis labels (param2)
        gc.setTextAlign(TextAlignment.CENTER);
        for (int j = 0; j < cols; j += Math.max(1, cols / 5)) {
            double x = MARGIN + j * cellWidth + cellWidth / 2;
            gc.fillText(String.format("%.1f", data.param2Values()[j]),
                    x, height - MARGIN + 15);
        }

        // X-axis title
        gc.setFont(Font.font(11));
        gc.fillText(data.param2Name(), width / 2, height - 10);

        // Y-axis labels (param1)
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFont(Font.font(10));
        for (int i = 0; i < rows; i += Math.max(1, rows / 5)) {
            double y = MARGIN + i * cellHeight + cellHeight / 2;
            gc.fillText(String.format("%.1f", data.param1Values()[i]),
                    MARGIN - 5, y + 4);
        }

        // Y-axis title
        gc.save();
        gc.translate(15, height / 2);
        gc.rotate(-90);
        gc.setFont(Font.font(11));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(data.param1Name(), 0, 0);
        gc.restore();

        // Draw color bar
        drawColorBar(gc, width - COLOR_BAR_WIDTH - 10, MARGIN, COLOR_BAR_WIDTH - 10,
                chartHeight, minVal, maxVal);

        // Update labels
        titleLabel.setText(String.format("Sensitivity: %s vs %s (%s)",
                data.param1Name(), data.param2Name(), data.metric().getDisplayName()));

        statsLabel.setText(String.format(
                "Optimal: %s=%.2f, %s=%.2f (%.4f)  |  Robustness: %s  |  Plateau: %.1f%%",
                data.param1Name(), data.getOptimalParam1(),
                data.param2Name(), data.getOptimalParam2(),
                data.optimalValue(),
                data.plateau().robustness(),
                data.plateau().plateauPercent()
        ));
    }

    /**
     * Get color for a value (green = good, red = bad).
     */
    private Color getColorForValue(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return Color.web("#333333");
        }

        // Normalize to 0-1
        double normalized = (value - min) / (max - min + 0.0001);

        // For drawdown, invert (lower is better)
        if (data.metric() == com.backtester.stats.SensitivityAnalyzer.Metric.MAX_DRAWDOWN) {
            normalized = 1 - normalized;
        }

        // Gradient from red (0) -> yellow (0.5) -> green (1)
        if (normalized < 0.5) {
            double t = normalized * 2;
            return Color.rgb(
                    200,
                    (int) (50 + t * 150),
                    50
            );
        } else {
            double t = (normalized - 0.5) * 2;
            return Color.rgb(
                    (int) (200 - t * 150),
                    200,
                    50
            );
        }
    }

    /**
     * Draw color bar legend.
     */
    private void drawColorBar(GraphicsContext gc, double x, double y,
                               double width, double height, double min, double max) {
        // Draw gradient
        int steps = 50;
        double stepHeight = height / steps;

        for (int i = 0; i < steps; i++) {
            double val = max - (max - min) * i / (steps - 1);
            Color color = getColorForValue(val, min, max);
            gc.setFill(color);
            gc.fillRect(x, y + i * stepHeight, width, stepHeight);
        }

        // Border
        gc.setStroke(Color.web("#555555"));
        gc.strokeRect(x, y, width, height);

        // Labels
        gc.setFill(Color.web("#bbbbbb"));
        gc.setFont(Font.font(9));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(String.format("%.2f", max), x + width + 3, y + 10);
        gc.fillText(String.format("%.2f", min), x + width + 3, y + height);
    }

    /**
     * Resize the canvas.
     */
    public void resize(double width, double height) {
        canvas.setWidth(width - 20);
        canvas.setHeight(height - 80);
        render();
    }
}
