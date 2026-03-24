package com.measim.ui.fx;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Live console at the bottom of the visualizer.
 * Captures stdout and displays GM events, agent actions, world events.
 * Scrollable, color-coded, pausable.
 */
public class LiveConsolePanel extends VBox {

    private final ListView<String> consoleList = new ListView<>();
    private boolean autoScroll = true;
    private final PrintStream originalOut;

    public LiveConsolePanel() {
        originalOut = System.out;

        Label title = new Label("Live Console");
        title.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");

        Button pauseBtn = new Button("Pause Scroll");
        pauseBtn.setOnAction(e -> {
            autoScroll = !autoScroll;
            pauseBtn.setText(autoScroll ? "Pause Scroll" : "Resume Scroll");
        });

        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> consoleList.getItems().clear());

        HBox controls = new HBox(5, title, pauseBtn, clearBtn);
        controls.setStyle("-fx-padding: 3;");

        consoleList.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 11;");
        consoleList.setPrefHeight(180);
        VBox.setVgrow(consoleList, Priority.ALWAYS);

        getChildren().addAll(controls, consoleList);
        setPrefHeight(200);

        // Intercept stdout to capture simulation output
        interceptStdout();
    }

    private void interceptStdout() {
        PrintStream consoleStream = new PrintStream(new OutputStream() {
            private final StringBuilder lineBuffer = new StringBuilder();

            @Override
            public void write(int b) {
                if (b == '\n') {
                    String line = lineBuffer.toString();
                    lineBuffer.setLength(0);
                    originalOut.println(line);
                    Platform.runLater(() -> addLine(line));
                } else if (b != '\r') {
                    lineBuffer.append((char) b);
                }
            }
        }, true);

        System.setOut(consoleStream);
    }

    private void addLine(String line) {
        // Prefix with color coding via style (ListView items are strings,
        // color is applied via cell factory style)
        consoleList.getItems().add(line);

        // Keep buffer manageable
        if (consoleList.getItems().size() > 2000) {
            consoleList.getItems().remove(0, 500);
        }

        if (autoScroll) {
            consoleList.scrollTo(consoleList.getItems().size() - 1);
        }
    }

    /**
     * Restore original stdout when viewer closes.
     */
    public void restoreStdout() {
        System.setOut(originalOut);
    }
}
