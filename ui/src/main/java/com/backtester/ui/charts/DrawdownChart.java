package com.backtester.ui.charts;

import com.backtester.core.portfolio.Portfolio;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Underwater/Drawdown chart visualization.
 * Shows drawdown from peak over time.
 */
public class DrawdownChart {

    private final BorderPane root;
    private final Canvas canvas;
    private final Label titleLabel;
    private final Label statsLabel;

    private List<Portfolio.EquityPoint> equityHistory;
    private double maxDrawdown;
    private double maxDrawdownPercent;

    private static final int MARGIN_LEFT = 60;
    private static final int MARGIN_RIGHT = 20;
    private static final int MARGIN_TOP = 30;
    private static final int MARGIN_BOTTOM = 40;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public DrawdownChart() {
        root = new BorderPane();
        root.setPadding(new Insets(10));

        titleLabel = new Label("Drawdown Chart");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        statsLabel = new Label();
        statsLabel.setStyle("-fx-font-size: 11px;");

        canvas = new Canvas(400, 200);

        VBox topBox = new VBox(5, titleLabel, statsLabel);
        root.setTop(topBox);
        root.setCenter(canvas);
    }

    public Node getNode() {
        return root;
    }

    /**
     * Set equity history and render drawdown.
     */
    public void setData(List<Portfolio.EquityPoint> history, double maxDD, double maxDDPercent) {
        this.equityHistory = history;
        this.maxDrawdown = maxDD;
        this.maxDrawdownPercent = maxDDPercent;
        render();
    }

    public void clear() {
        this.equityHistory = null;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        titleLabel.setText("Drawdown Chart");
        statsLabel.setText("");
    }

    private void render() {
        if (equityHistory == null || equityHistory.isEmpty()) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();

        // Clear
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, width, height);

        double chartWidth = width - MARGIN_LEFT - MARGIN_RIGHT;
        double chartHeight = height - MARGIN_TOP - MARGIN_BOTTOM;

        // Calculate drawdown series
        double peak = equityHistory.get(0).equity();
        double[] drawdowns = new double[equityHistory.size()];
        double maxDDValue = 0;

        for (int i = 0; i < equityHistory.size(); i++) {
            double equity = equityHistory.get(i).equity();
            if (equity > peak) {
                peak = equity;
            }
            drawdowns[i] = (peak - equity) / peak * 100; // Percent drawdown
            maxDDValue = Math.max(maxDDValue, drawdowns[i]);
        }

        // Scale Y to max drawdown (or at least 10%)
        maxDDValue = Math.max(maxDDValue, 10);

        // Draw grid
        gc.setStroke(Color.web("#3c3c3c"));
        gc.setLineWidth(0.5);

        // Horizontal grid lines
        for (int i = 0; i <= 4; i++) {
            double y = MARGIN_TOP + chartHeight * i / 4;
            gc.strokeLine(MARGIN_LEFT, y, width - MARGIN_RIGHT, y);
        }

        // Draw underwater area
        gc.setFill(Color.web("#8b0000", 0.6)); // Dark red
        gc.beginPath();
        gc.moveTo(MARGIN_LEFT, MARGIN_TOP);

        for (int i = 0; i < drawdowns.length; i++) {
            double x = MARGIN_LEFT + chartWidth * i / (drawdowns.length - 1);
            double y = MARGIN_TOP + chartHeight * drawdowns[i] / maxDDValue;
            gc.lineTo(x, y);
        }

        gc.lineTo(MARGIN_LEFT + chartWidth, MARGIN_TOP);
        gc.closePath();
        gc.fill();

        // Draw drawdown line
        gc.setStroke(Color.web("#ff4444"));
        gc.setLineWidth(1.5);
        gc.beginPath();

        for (int i = 0; i < drawdowns.length; i++) {
            double x = MARGIN_LEFT + chartWidth * i / (drawdowns.length - 1);
            double y = MARGIN_TOP + chartHeight * drawdowns[i] / maxDDValue;
            if (i == 0) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
        }
        gc.stroke();

        // Draw axes
        gc.setStroke(Color.web("#555555"));
        gc.setLineWidth(1);
        gc.strokeLine(MARGIN_LEFT, MARGIN_TOP, MARGIN_LEFT, height - MARGIN_BOTTOM);
        gc.strokeLine(MARGIN_LEFT, MARGIN_TOP, width - MARGIN_RIGHT, MARGIN_TOP);

        // Y-axis labels (drawdown %)
        gc.setFill(Color.web("#bbbbbb"));
        gc.setFont(Font.font(10));
        gc.setTextAlign(TextAlignment.RIGHT);

        for (int i = 0; i <= 4; i++) {
            double val = maxDDValue * i / 4;
            double y = MARGIN_TOP + chartHeight * i / 4;
            gc.fillText(String.format("-%.1f%%", val), MARGIN_LEFT - 5, y + 4);
        }

        // X-axis labels (dates)
        gc.setTextAlign(TextAlignment.CENTER);
        int labelCount = 5;
        for (int i = 0; i <= labelCount; i++) {
            int idx = i * (equityHistory.size() - 1) / labelCount;
            Portfolio.EquityPoint point = equityHistory.get(idx);
            LocalDateTime date = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(point.timestamp()),
                    ZoneId.systemDefault()
            );
            double x = MARGIN_LEFT + chartWidth * i / labelCount;
            gc.fillText(date.format(DATE_FORMAT), x, height - MARGIN_BOTTOM + 15);
        }

        // Find and mark max drawdown point
        int maxDDIdx = 0;
        for (int i = 0; i < drawdowns.length; i++) {
            if (drawdowns[i] > drawdowns[maxDDIdx]) {
                maxDDIdx = i;
            }
        }

        double maxX = MARGIN_LEFT + chartWidth * maxDDIdx / (drawdowns.length - 1);
        double maxY = MARGIN_TOP + chartHeight * drawdowns[maxDDIdx] / maxDDValue;

        gc.setFill(Color.WHITE);
        gc.fillOval(maxX - 4, maxY - 4, 8, 8);
        gc.setFill(Color.web("#ff4444"));
        gc.fillOval(maxX - 2, maxY - 2, 4, 4);

        // Update labels
        titleLabel.setText("Underwater Equity Chart (Drawdown)");
        statsLabel.setText(String.format("Maximum Drawdown: $%.2f (%.2f%%)",
                maxDrawdown, maxDrawdownPercent));
    }

    public void resize(double width, double height) {
        canvas.setWidth(width - 20);
        canvas.setHeight(height - 60);
        render();
    }
}
