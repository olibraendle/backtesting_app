package com.backtester.ui;

import com.backtester.core.engine.BacktestConfig;
import com.backtester.core.engine.BacktestResult;
import com.backtester.core.engine.SimpleBacktestEngine;
import com.backtester.data.loader.CsvDataLoader;
import com.backtester.data.model.DataInfo;
import com.backtester.common.model.TimeSeries;
import com.backtester.stats.BacktestStatistics;
import com.backtester.stats.MonteCarloSimulator;
import com.backtester.stats.StatisticsCalculator;
import com.backtester.strategy.BaseStrategy;
import com.backtester.strategy.StrategyRegistry;
import com.backtester.strategy.loader.StrategyLoader;
import com.backtester.ui.panels.BenchmarkPanel;
import com.backtester.ui.panels.DataPanel;
import com.backtester.ui.panels.SimulationListPanel;
import com.backtester.ui.panels.StrategyPanel;
import com.backtester.ui.toolbar.ParametersToolbar;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Main window controller for the backtesting application.
 * Manages the 4-panel layout and coordinates between components.
 *
 * Supports drag-and-drop for:
 * - CSV data files
 * - Strategy files (.java, .class, .jar, .onnx)
 */
public class MainWindow {

    private final BorderPane root;
    private final ParametersToolbar toolbar;
    private final BenchmarkPanel benchmarkPanel;
    private final SimulationListPanel simulationListPanel;
    private final DataPanel dataPanel;
    private final StrategyPanel strategyPanel;
    private final StrategyLoader strategyLoader;

    // State
    private TimeSeries loadedData;
    private DataInfo loadedDataInfo;
    private BaseStrategy selectedStrategy;
    private final List<BacktestResult> simulationHistory = new ArrayList<>();
    private Label statusBar;

    public MainWindow() {
        root = new BorderPane();
        root.getStyleClass().add("main-window");
        strategyLoader = new StrategyLoader();

        // Create toolbar
        toolbar = new ParametersToolbar(this::onRunBacktest, this::onLoadData);

        // Create panels
        benchmarkPanel = new BenchmarkPanel();
        simulationListPanel = new SimulationListPanel(this::onSimulationSelected);
        dataPanel = new DataPanel();
        strategyPanel = new StrategyPanel(this::onStrategySelected);

        // Layout
        setupLayout();

        // Setup drag-and-drop
        setupDragAndDrop();
    }

    private void setupLayout() {
        // Top: Toolbar
        root.setTop(toolbar.getNode());

        // Center: 2x2 Grid of panels
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        // Column constraints (50/50)
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        col1.setHgrow(Priority.ALWAYS);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        // Row constraints (50/50)
        RowConstraints row1 = new RowConstraints();
        row1.setPercentHeight(50);
        row1.setVgrow(Priority.ALWAYS);
        RowConstraints row2 = new RowConstraints();
        row2.setPercentHeight(50);
        row2.setVgrow(Priority.ALWAYS);
        grid.getRowConstraints().addAll(row1, row2);

        // Add panels
        grid.add(wrapPanel("Benchmarks & Statistics", benchmarkPanel.getNode()), 0, 0);
        grid.add(wrapPanel("Simulation History", simulationListPanel.getNode()), 1, 0);
        grid.add(wrapPanel("Data Overview", dataPanel.getNode()), 0, 1);
        grid.add(wrapPanel("Strategy", strategyPanel.getNode()), 1, 1);

        root.setCenter(grid);

        // Bottom: Status bar
        statusBar = new Label("Ready - Drag & drop CSV data or strategy files, or use toolbar buttons");
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        root.setBottom(statusBar);
    }

    /**
     * Setup drag-and-drop handlers for the entire window.
     */
    private void setupDragAndDrop() {
        // Accept drag over
        root.setOnDragOver(event -> {
            if (event.getGestureSource() != root && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        // Visual feedback on drag enter
        root.setOnDragEntered(event -> {
            if (event.getDragboard().hasFiles()) {
                root.setStyle("-fx-border-color: #4a9df8; -fx-border-width: 3;");
                updateStatus("Drop files to load...");
            }
        });

        // Remove visual feedback on drag exit
        root.setOnDragExited(event -> {
            root.setStyle("");
            updateStatus("Ready");
        });

        // Handle drop
        root.setOnDragDropped(this::handleDragDrop);
    }

    /**
     * Handle dropped files.
     */
    private void handleDragDrop(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;

        if (db.hasFiles()) {
            for (File file : db.getFiles()) {
                String name = file.getName().toLowerCase();
                Path path = file.toPath();

                try {
                    if (name.endsWith(".csv")) {
                        // Load as data
                        loadDataFile(path);
                        success = true;
                    } else if (name.endsWith(".java") || name.endsWith(".class") ||
                               name.endsWith(".jar") || name.endsWith(".onnx")) {
                        // Load as strategy
                        loadStrategyFile(path);
                        success = true;
                    } else {
                        updateStatus("Unsupported file type: " + name);
                    }
                } catch (Exception e) {
                    showError("Load Error", "Failed to load " + name + ": " + e.getMessage());
                }
            }
        }

        root.setStyle("");
        event.setDropCompleted(success);
        event.consume();
    }

    private TitledPane wrapPanel(String title, Node content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setCollapsible(false);
        pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(pane, Priority.ALWAYS);
        HBox.setHgrow(pane, Priority.ALWAYS);
        return pane;
    }

    public void initialize() {
        // Initialize strategy panel with available built-in strategies
        List<String> strategies = new ArrayList<>(StrategyRegistry.getStrategyNames());
        strategyPanel.setStrategies(strategies);
    }

    public Parent getRoot() {
        return root;
    }

    // ===== Event Handlers =====

    private void onLoadData() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load CSV Data");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );

        File file = fileChooser.showOpenDialog(root.getScene().getWindow());
        if (file != null) {
            loadDataFile(file.toPath());
        }
    }

    private void loadDataFile(Path path) {
        // Show loading indicator
        dataPanel.setLoading(true);
        updateStatus("Loading data: " + path.getFileName());

        CompletableFuture.supplyAsync(() -> {
            try {
                CsvDataLoader loader = new CsvDataLoader();
                String symbol = path.getFileName().toString().replace(".csv", "").toUpperCase();
                return loader.loadWithInfo(path, symbol);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load data: " + e.getMessage(), e);
            }
        }).thenAccept(result -> Platform.runLater(() -> {
            loadedData = result.timeSeries();
            loadedDataInfo = result.dataInfo();
            dataPanel.setData(loadedDataInfo);
            dataPanel.setLoading(false);
            updateStatus("Loaded " + loadedData.size() + " bars of " + loadedData.getSymbol());
        })).exceptionally(e -> {
            Platform.runLater(() -> {
                dataPanel.setLoading(false);
                showError("Load Error", e.getMessage());
            });
            return null;
        });
    }

    /**
     * Load a strategy from a file (supports .java, .class, .jar, .onnx).
     */
    private void loadStrategyFile(Path path) {
        updateStatus("Loading strategy: " + path.getFileName());

        CompletableFuture.supplyAsync(() -> {
            try {
                return strategyLoader.loadStrategy(path);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).thenAccept(loaded -> Platform.runLater(() -> {
            // Add to strategy list
            String strategyName = loaded.getName();

            // Register the loaded strategy
            if (loaded.getInstance() instanceof BaseStrategy) {
                StrategyRegistry.registerStrategy(strategyName, () -> {
                    if (loaded.getStrategyClass() != null) {
                        try {
                            return (BaseStrategy) loaded.getStrategyClass().getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return (BaseStrategy) loaded.getInstance();
                });
            }

            // Update strategy panel
            List<String> strategies = new ArrayList<>(StrategyRegistry.getStrategyNames());
            strategyPanel.setStrategies(strategies);

            // Auto-select the loaded strategy
            selectedStrategy = (BaseStrategy) loaded.getInstance();
            strategyPanel.setStrategy(selectedStrategy);

            updateStatus("Loaded strategy: " + strategyName + " (" + loaded.getSourceType() + ")");
        })).exceptionally(e -> {
            Platform.runLater(() -> {
                showError("Strategy Load Error", e.getMessage());
                updateStatus("Failed to load strategy");
            });
            return null;
        });
    }

    private void onStrategySelected(String strategyName) {
        if (strategyName != null && !strategyName.isEmpty()) {
            selectedStrategy = StrategyRegistry.createStrategy(strategyName);
            strategyPanel.setStrategy(selectedStrategy);
            updateStatus("Selected strategy: " + strategyName);
        }
    }

    private void onRunBacktest() {
        if (loadedData == null) {
            showError("No Data", "Please load CSV data first (drag & drop or use Load button).");
            return;
        }
        if (selectedStrategy == null) {
            showError("No Strategy", "Please select a strategy first.");
            return;
        }

        // Get parameters from toolbar
        BacktestConfig config = toolbar.getConfig();

        // Update strategy parameters from panel
        selectedStrategy.setParameters(strategyPanel.getParameterValues());

        // Show running indicator
        toolbar.setRunning(true);
        updateStatus("Running backtest...");

        CompletableFuture.supplyAsync(() -> {
            // Use new SimpleBacktestEngine that gives strategies full control
            SimpleBacktestEngine engine = new SimpleBacktestEngine(config);
            return engine.run(selectedStrategy, loadedData);
        }).thenAccept(result -> Platform.runLater(() -> {
            // Calculate statistics
            StatisticsCalculator calculator = new StatisticsCalculator();
            BacktestStatistics stats = calculator.calculate(result);

            // Run Monte Carlo
            MonteCarloSimulator monteCarlo = new MonteCarloSimulator(result.getInitialEquity());
            MonteCarloSimulator.MonteCarloResult mcResult = monteCarlo.simulate(result.getTrades());

            // Update UI
            benchmarkPanel.setResult(result, stats, mcResult);
            simulationHistory.add(result);
            simulationListPanel.addResult(result, stats);

            toolbar.setRunning(false);
            updateStatus(String.format("Backtest complete: %.2f%% return, %.2f%% alpha",
                    result.getNetReturnPercent(), result.getAlpha()));
        })).exceptionally(e -> {
            Platform.runLater(() -> {
                toolbar.setRunning(false);
                showError("Backtest Error", e.getMessage());
                updateStatus("Backtest failed");
            });
            return null;
        });
    }

    private void onSimulationSelected(BacktestResult result) {
        if (result != null) {
            StatisticsCalculator calculator = new StatisticsCalculator();
            BacktestStatistics stats = calculator.calculate(result);
            MonteCarloSimulator monteCarlo = new MonteCarloSimulator(result.getInitialEquity());
            MonteCarloSimulator.MonteCarloResult mcResult = monteCarlo.simulate(result.getTrades());
            benchmarkPanel.setResult(result, stats, mcResult);
        }
    }

    private void updateStatus(String message) {
        if (statusBar != null) {
            statusBar.setText(message);
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
