package com.measim.service.llm;

import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pauses the simulation when the Anthropic API rejects requests due to
 * exhausted account credits. Shows a popup asking the user to reload
 * credits on their Anthropic account. The exact same request is retried
 * after the user clicks Continue.
 */
@Singleton
public class BudgetPauseHandler {

    private final AtomicBoolean pauseTriggered = new AtomicBoolean(false);

    /**
     * Called when the API returns a billing/credit error.
     * Blocks the calling thread, shows a popup on the FX thread.
     * Returns true if user clicked Continue (retry), false if they clicked Skip (deterministic).
     */
    public boolean pauseForBudget(double totalSpent, double unused) {
        // Only one thread triggers the popup — others wait
        if (!pauseTriggered.compareAndSet(false, true)) {
            waitForResume();
            return true; // assume user clicked Continue
        }

        System.out.printf("    [API] Credits exhausted after $%.2f spent. Simulation paused.%n", totalSpent);
        System.out.flush();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean retry = new AtomicBoolean(false);

        try {
            Platform.runLater(() -> {
                try {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("API Credits Exhausted");
                    alert.setHeaderText("Anthropic API credits have run out");
                    alert.setContentText(String.format(
                            "Total spent this session: $%.2f\n\n" +
                            "To continue with LLM:\n" +
                            "1. Add credits at console.anthropic.com\n" +
                            "2. Click 'Continue' to retry\n\n" +
                            "Or click 'Skip' to continue in deterministic mode.",
                            totalSpent));

                    ButtonType continueBtn = new ButtonType("Continue");
                    ButtonType skipBtn = new ButtonType("Skip (Deterministic)");
                    alert.getButtonTypes().setAll(continueBtn, skipBtn);

                    var result = alert.showAndWait();
                    retry.set(result.isPresent() && result.get() == continueBtn);
                } finally {
                    latch.countDown();
                }
            });

            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pauseTriggered.set(false);
        }

        if (retry.get()) {
            System.out.println("    [API] User clicked Continue. Retrying...");
        } else {
            System.out.println("    [API] User clicked Skip. Continuing in deterministic mode.");
        }
        System.out.flush();

        return retry.get();
    }

    private void waitForResume() {
        while (pauseTriggered.get()) {
            try { Thread.sleep(100); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
