package com.perfkit;

import org.knowm.xchart.*;
import org.knowm.xchart.style.markers.SeriesMarkers;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.opencsv.CSVReader;

public class PerfChartGenerator {

    private static final String CSV_PATH = "../results/dashboard_performance_history.csv";
    private static final String CURRENT_CHART = "../results/current_execution.png";
    private static final String WEEKLY_CHART = "../results/weekly_status.png";

    public static void main(String[] args) throws Exception {
        List<String[]> rows;
        try (CSVReader reader = new CSVReader(new FileReader(CSV_PATH))) {
            rows = reader.readAll();
        }

        if (rows.size() <= 1) {
            System.out.println("Not enough data in CSV.");
            return;
        }

        // Remove header
        rows.remove(0);

        List<String> envs = new ArrayList<>();
        List<Double> loadTimes = new ArrayList<>();
        List<Double> fcpTimes = new ArrayList<>();
        List<Double> lcpTimes = new ArrayList<>();

        for (String[] row : rows) {
            envs.add(row[0]); // Environment
            loadTimes.add(row[1].isEmpty() ? 0 : Double.parseDouble(row[1]));
            fcpTimes.add(row[2].isEmpty() ? 0 : Double.parseDouble(row[2]));
            lcpTimes.add(row[3].isEmpty() ? 0 : Double.parseDouble(row[3]));
            // row[4] = Notes → ignore (contains CSS selector)
        }
        

        // --- Current Execution (latest build only) ---
        String latestBuild = envs.get(envs.size() - 1);
        double latestLoad = loadTimes.get(loadTimes.size() - 1);
        double latestFCP = fcpTimes.get(fcpTimes.size() - 1);
        double latestLCP = lcpTimes.get(lcpTimes.size() - 1);

        CategoryChart currentChart = new CategoryChartBuilder()
                .width(800).height(600)
                .title("Current Execution - " + latestBuild)
                .xAxisTitle("Metric").yAxisTitle("Milliseconds")
                .build();

        currentChart.addSeries("Metrics",
                Arrays.asList("LoadTimeMs", "FCPms", "LCPms"),
                Arrays.asList(latestLoad, latestFCP, latestLCP));

        BitmapEncoder.saveBitmap(currentChart, CURRENT_CHART, BitmapEncoder.BitmapFormat.PNG);

        // --- Weekly Status (last 7 builds) ---
        int startIdx = Math.max(0, envs.size() - 7);
        List<String> lastBuilds = envs.subList(startIdx, envs.size());
        List<Double> lastLoads = loadTimes.subList(startIdx, loadTimes.size());
        List<Double> lastFCP = fcpTimes.subList(startIdx, fcpTimes.size());
        List<Double> lastLCP = lcpTimes.subList(startIdx, lcpTimes.size());

        // Create numeric X values (1..N) instead of using strings
        List<Integer> xIndexes = new ArrayList<>();
        for (int i = 0; i < lastBuilds.size(); i++) {
            xIndexes.add(i + 1);
        }

        XYChart weeklyChart = new XYChartBuilder()
        .width(900).height(600)
        .title("Weekly Performance Trend")
        .xAxisTitle("Build Index (see legend for labels)").yAxisTitle("Milliseconds")
        .build();

        weeklyChart.addSeries("LoadTimeMs", xIndexes, lastLoads).setMarker(SeriesMarkers.CIRCLE);
        weeklyChart.addSeries("FCPms", xIndexes, lastFCP).setMarker(SeriesMarkers.DIAMOND);
        weeklyChart.addSeries("LCPms", xIndexes, lastLCP).setMarker(SeriesMarkers.SQUARE);

        // Append build names in the title for quick reference
        weeklyChart.setTitle("Weekly Performance Trend (Builds: " + String.join(", ", lastBuilds) + ")");

        BitmapEncoder.saveBitmap(weeklyChart, WEEKLY_CHART, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("Charts generated: " + CURRENT_CHART + ", " + WEEKLY_CHART);
    }
}
