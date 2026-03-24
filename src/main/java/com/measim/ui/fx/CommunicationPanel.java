package com.measim.ui.fx;

import com.measim.dao.CommunicationDao;
import com.measim.model.communication.Message;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

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

        messageList.setPrefHeight(250);
        messageList.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");

        getChildren().addAll(title, channelFilter, searchField, messageList);
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
}
