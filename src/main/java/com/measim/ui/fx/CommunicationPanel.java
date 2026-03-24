package com.measim.ui.fx;

import com.measim.dao.CommunicationDao;
import com.measim.model.communication.Message;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Scrollable communication log panel for the visualizer.
 * Shows all messages: agent thoughts, GM reasoning, agent-to-agent, agent-to-GM.
 * Color-coded by channel.
 */
public class CommunicationPanel extends VBox {

    private final ListView<String> messageList = new ListView<>();
    private final ComboBox<String> channelFilter = new ComboBox<>();
    private final TextField searchField = new TextField();

    public CommunicationPanel() {
        setSpacing(5);
        setPrefWidth(400);
        setPrefHeight(300);

        Label title = new Label("Communication Log");
        title.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        channelFilter.getItems().addAll("ALL", "AGENT_TO_AGENT", "AGENT_TO_GM", "GM_TO_AGENT",
                "GM_INTERNAL", "AGENT_INTERNAL", "BROADCAST", "GM_WORLD_NARRATION");
        channelFilter.setValue("ALL");

        searchField.setPromptText("Search messages...");

        Button copySelectedBtn = new Button("Copy Selected");
        copySelectedBtn.setOnAction(e -> {
            String selected = messageList.getSelectionModel().getSelectedItem();
            if (selected != null) copyToClipboard(selected);
        });

        Button copyAllBtn = new Button("Copy All");
        copyAllBtn.setOnAction(e -> {
            String all = String.join("\n", messageList.getItems());
            copyToClipboard(all);
        });

        HBox buttons = new HBox(5, copySelectedBtn, copyAllBtn);

        messageList.setPrefHeight(250);
        messageList.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");
        messageList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        getChildren().addAll(title, channelFilter, searchField, buttons, messageList);
    }

    public void update(List<Message> messages) {
        String filter = channelFilter.getValue();
        String search = searchField.getText().toLowerCase().trim();

        messageList.getItems().clear();
        for (Message msg : messages) {
            if (!"ALL".equals(filter) && !msg.channel().name().equals(filter)) continue;
            if (!search.isEmpty() && !msg.content().toLowerCase().contains(search)
                    && !msg.senderId().toLowerCase().contains(search)) continue;

            String prefix = switch (msg.channel()) {
                case AGENT_INTERNAL -> "[THOUGHT]";
                case GM_INTERNAL -> "[GM THINK]";
                case AGENT_TO_GM -> "[→ GM]";
                case GM_TO_AGENT -> "[GM →]";
                case AGENT_TO_AGENT -> "[AGENT]";
                case BROADCAST -> "[BROADCAST]";
                case GM_WORLD_NARRATION -> "[WORLD]";
            };

            String line = String.format("T%d %s %s: %s",
                    msg.tick(), prefix, msg.senderId(),
                    msg.content().length() > 120 ? msg.content().substring(0, 120) + "..." : msg.content());
            messageList.getItems().add(line);
        }

        // Auto-scroll to bottom
        if (!messageList.getItems().isEmpty()) {
            messageList.scrollTo(messageList.getItems().size() - 1);
        }
    }

    public ComboBox<String> getChannelFilter() { return channelFilter; }
    public TextField getSearchField() { return searchField; }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
