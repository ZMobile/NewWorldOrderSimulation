package com.measim.ui.fx;

import com.measim.dao.MetricsDao;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.VBox;

import java.util.List;

public class DashboardPanel extends VBox {

    private final XYChart.Series<Number, Number> giniSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> satisfactionSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> envHealthSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> creditsSeries = new XYChart.Series<>();

    public DashboardPanel() {
        setSpacing(10);
        setPrefWidth(350);

        giniSeries.setName("Gini Coefficient");
        satisfactionSeries.setName("Satisfaction");
        envHealthSeries.setName("Env Health");
        creditsSeries.setName("Avg Credits");

        getChildren().addAll(
                createChart("Inequality (Gini)", giniSeries, 0, 1),
                createChart("Satisfaction & Env Health", satisfactionSeries, 0, 1),
                createChart("Average Credits", creditsSeries, 0, 5000)
        );
    }

    public void update(List<MetricsDao.TickMetrics> history) {
        giniSeries.getData().clear();
        satisfactionSeries.getData().clear();
        envHealthSeries.getData().clear();
        creditsSeries.getData().clear();

        for (MetricsDao.TickMetrics m : history) {
            giniSeries.getData().add(new XYChart.Data<>(m.tick(), m.giniCoefficient()));
            satisfactionSeries.getData().add(new XYChart.Data<>(m.tick(), m.satisfactionMean()));
            envHealthSeries.getData().add(new XYChart.Data<>(m.tick(), m.environmentalHealth()));
            creditsSeries.getData().add(new XYChart.Data<>(m.tick(), m.averageCredits()));
        }
    }

    private LineChart<Number, Number> createChart(String title, XYChart.Series<Number, Number> series,
                                                   double yMin, double yMax) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Tick");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(title);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.getData().add(series);
        chart.setCreateSymbols(false);
        chart.setPrefHeight(200);
        chart.setLegendVisible(false);
        return chart;
    }
}
