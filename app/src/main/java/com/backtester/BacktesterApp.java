package com.backtester;

import com.backtester.ui.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main entry point for the Backtesting Research Terminal.
 */
public class BacktesterApp extends Application {

    private static final String APP_TITLE = "Backtesting Research Terminal";
    private static final int DEFAULT_WIDTH = 1400;
    private static final int DEFAULT_HEIGHT = 900;

    @Override
    public void start(Stage primaryStage) {
        // Create main window
        MainWindow mainWindow = new MainWindow();

        // Create scene
        Scene scene = new Scene(mainWindow.getRoot(), DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // Load CSS
        String css = getClass().getResource("/css/dark-theme.css") != null ?
                getClass().getResource("/css/dark-theme.css").toExternalForm() : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }

        // Configure stage
        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);

        // Show
        primaryStage.show();

        // Initialize after show
        mainWindow.initialize();
    }

    @Override
    public void stop() {
        // Cleanup on exit
        System.out.println("Application closing...");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
