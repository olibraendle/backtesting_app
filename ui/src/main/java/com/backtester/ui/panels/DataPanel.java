package com.backtester.ui.panels;

import com.backtester.data.model.DataInfo;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;

/**
 * Panel 3: Data Overview.
 * Shows information about the loaded dataset.
 */
public class DataPanel {

    private final VBox root;
    private final GridPane infoGrid;
    private final ProgressIndicator loadingIndicator;
    private final Label noDataLabel;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public DataPanel() {
        root = new VBox(10);
        root.setPadding(new Insets(10));

        // Loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        loadingIndicator.setVisible(false);

        // No data label
        noDataLabel = new Label("No data loaded.\nClick 'Load CSV' to load market data.");
        noDataLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 14px;");
        noDataLabel.setWrapText(true);

        // Info grid
        infoGrid = new GridPane();
        infoGrid.setHgap(15);
        infoGrid.setVgap(8);
        infoGrid.setVisible(false);

        root.getChildren().addAll(noDataLabel, loadingIndicator, infoGrid);
    }

    public Node getNode() {
        return root;
    }

    /**
     * Set loading state.
     */
    public void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        noDataLabel.setVisible(!loading && !infoGrid.isVisible());
    }

    /**
     * Display data information.
     */
    public void setData(DataInfo info) {
        infoGrid.getChildren().clear();

        if (info == null) {
            infoGrid.setVisible(false);
            noDataLabel.setVisible(true);
            return;
        }

        noDataLabel.setVisible(false);
        infoGrid.setVisible(true);

        int row = 0;

        // Symbol
        addRow(row++, "Symbol:", info.symbol());

        // Timeframe
        addRow(row++, "Timeframe:", info.timeFrame().getDisplayName());

        // Date range
        String startDate = info.startDate() != null ? info.startDate().format(DATE_FORMAT) : "N/A";
        String endDate = info.endDate() != null ? info.endDate().format(DATE_FORMAT) : "N/A";
        addRow(row++, "Start Date:", startDate);
        addRow(row++, "End Date:", endDate);
        addRow(row++, "Duration:", info.durationDays() + " days");

        // Bar count
        addRow(row++, "Total Bars:", String.format("%,d", info.barCount()));

        // Price info
        addRow(row++, "First Price:", String.format("$%,.5f", info.firstPrice()));
        addRow(row++, "Last Price:", String.format("$%,.5f", info.lastPrice()));
        addRow(row++, "Highest:", String.format("$%,.5f", info.highestPrice()));
        addRow(row++, "Lowest:", String.format("$%,.5f", info.lowestPrice()));
        addRow(row++, "Price Change:", String.format("%.2f%%", info.priceChangePercent()));

        // Volume
        addRow(row++, "Total Volume:", String.format("%,.0f", info.totalVolume()));

        // Data quality
        var quality = info.dataQuality();
        String qualityStr = quality.isGood() ? "Good" : "Issues detected";
        addRow(row++, "Data Quality:", qualityStr);
        addRow(row++, "Coverage:", String.format("%.1f%%", quality.coveragePercent()));

        if (quality.gaps() > 0) {
            addRow(row++, "Gaps:", String.valueOf(quality.gaps()));
        }
        if (quality.invalidBars() > 0) {
            addRow(row++, "Invalid Bars:", String.valueOf(quality.invalidBars()));
        }
    }

    private void addRow(int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-weight: bold;");

        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-font-family: monospace;");

        infoGrid.add(labelNode, 0, row);
        infoGrid.add(valueNode, 1, row);
    }
}
