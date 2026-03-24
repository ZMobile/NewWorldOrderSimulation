package com.measim.service.llm;

import com.measim.model.config.SimulationConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pauses the simulation when LLM budget is exhausted and shows a popup
 * asking the user to reload credits. Blocks the calling thread until
 * the user clicks Continue, then the exact same request is retried.
 */
@Singleton
public class BudgetPauseHandler {

    private final SimulationConfig config;
    private final AtomicBoolean pauseTriggered = new AtomicBoolean(false);

    @Inject
    public BudgetPauseHandler(SimulationConfig config) {
        this.config = config;
    }

    /**
     * Called when budget is exhausted. Blocks the calling thread,
     * shows a popup on the FX thread, waits for user to continue.
     * Returns true if the user added budget and wants to retry,
     * false if they want to skip (continue in deterministic mode).
     */
    public boolean pauseForBudget(double spent, double budget) {
        // Only one thread triggers the popup — others wait
        if (!pauseTriggered.compareAndSet(false, true)) {
            // Another thread already triggered — wait for it to resolve
            waitForResume();
            return config.totalBudgetUsd() > spent;
        }

        System.out.printf("    [BUDGET] Exhausted: $%.2f spent of $%.2f budget. Pausing simulation...%n",
                spent, budget);
        System.out.flush();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean retry = new AtomicBoolean(false);

        try {
            Platform.runLater(() -> {
                try {
                    TextInputDialog dialog = new TextInputDialog(String.valueOf(budget + 50));
                    dialog.setTitle("LLM Budget Exhausted");
                    dialog.setHeaderText(String.format(
                            "Budget exhausted: $%.2f spent of $%.2f\n\n" +
                            "The simulation is paused. Enter a new total budget\n" +
                            "to continue with LLM, or Cancel to continue without LLM.",
                            spent, budget));
                    dialog.setContentText("New total budget ($):");

                    var result = dialog.showAndWait();
                    if (result.isPresent()) {
                        try {
                            double newBudget = Double.parseDouble(result.get().trim());
                            if (newBudget > spent) {
                                config.setTotalBudgetUsd(newBudget);
                                System.out.printf("    [BUDGET] New budget: $%.2f (%.2f remaining)%n",
                                        newBudget, newBudget - spent);
                                retry.set(true);
                            }
                        } catch (NumberFormatException e) {
                            // Invalid input — treat as cancel
                        }
                    }
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

        return retry.get();
    }

    private void waitForResume() {
        // Spin-wait for the popup to resolve (other threads hit budget at same time)
        while (pauseTriggered.get()) {
            try { Thread.sleep(100); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
