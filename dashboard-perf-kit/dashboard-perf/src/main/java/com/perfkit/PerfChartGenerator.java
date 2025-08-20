package com.perfkit;

import org.knowm.xchart.*;
import org.knowm.xchart.style.markers.SeriesMarkers;
import com.opencsv.CSVReader;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

public class PerfChartGenerator {

    private static final String CSV_PATH = "../results/dashboard_performance_history.csv";
    private static final String CURRENT_CHART = "../results/current_execution.png";
    private static final String WEEKLY_CHART = "../results/weekly_status.png";

    public static void main(String[] args) throws Exception {

        if (!Files.exists(Paths.get(CSV_PATH))) {
            System.out.println("CSV file not found: " + CSV_PATH);
            return;
        }

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

        List<String> dates = new ArrayList<>();
        List<String> buildIds = new ArrayList<>();
        List<Double> loadTimes = new ArrayList<>();
        List<Double> fcpTimes = new ArrayList<>();
        List<Double> lcpTimes = new ArrayList<>();

        for (String[] row : rows) {
            dates.add(row[0]);      // Date
            buildIds.add(row[1]);   // BuildId
            loadTimes.add(row[3].isEmpty() ? 0 : Double.parseDouble(row[3]));
            fcpTimes.add(row[4].isEmpty() ? 0 : Double.parseDouble(row[4]));
            lcpTimes.add(row[5].isEmpty() ? 0 : Double.parseDouble(row[5]));
        }

        // --- Current Execution (latest build only) ---
        String latestBuild = buildIds.get(buildIds.size() - 1);
        double latestLoad = loadTimes.get(loadTimes.size() - 1);
        double latestFCP = fcpTimes.get(fcpTimes.size() - 1);
        double latestLCP = lcpTimes.get(lcpTimes.size() - 1);

        CategoryChart currentChart = new CategoryChartBuilder()
                .width(800).height(600)
                .title("Current Execution - " + latestBuild)
                .xAxisTitle("Metric").yAxisTitle("Milliseconds")
                .build();

        currentChart.addSeries("Metrics",
                Arrays.asList("DashboardLoadTimeMs", "FCPms", "LCPms"),
                Arrays.asList(latestLoad, latestFCP, latestLCP));

        BitmapEncoder.saveBitmap(currentChart, CURRENT_CHART, BitmapEncoder.BitmapFormat.PNG);

        // --- Weekly Aggregation ---
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        WeekFields weekFields = WeekFields.ISO;

        class Metrics {
            double loadSum = 0, fcpSum = 0, lcpSum = 0;
            int count = 0;
        }

        // Map<WeekKey, Metrics>
        Map<String, Metrics> weeklyMetrics = new LinkedHashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            LocalDateTime dateTime = LocalDateTime.parse(dates.get(i), formatter);
            LocalDate d = dateTime.toLocalDate();
            int weekNum = d.get(weekFields.weekOfWeekBasedYear());
            String weekKey = d.getYear() + "-W" + weekNum;

            weeklyMetrics.putIfAbsent(weekKey, new Metrics());
            Metrics m = weeklyMetrics.get(weekKey);
            m.loadSum += loadTimes.get(i);
            m.fcpSum += fcpTimes.get(i);
            m.lcpSum += lcpTimes.get(i);
            m.count++;
        }

        // Limit to last 7 weeks
        List<String> weekKeys = new ArrayList<>(weeklyMetrics.keySet());
        if (weekKeys.size() > 7) {
            weekKeys = weekKeys.subList(weekKeys.size() - 7, weekKeys.size());
        }

        List<Integer> xIndexes = new ArrayList<>();
        List<Double> weeklyLoadAvg = new ArrayList<>();
        List<Double> weeklyFCPAvg = new ArrayList<>();
        List<Double> weeklyLCPAvg = new ArrayList<>();

        int idx = 1;
        for (String wk : weekKeys) {
            Metrics m = weeklyMetrics.get(wk);
            xIndexes.add(idx++);
            weeklyLoadAvg.add(m.loadSum / m.count);
            weeklyFCPAvg.add(m.fcpSum / m.count);
            weeklyLCPAvg.add(m.lcpSum / m.count);
        }

        XYChart weeklyChart = new XYChartBuilder()
                .width(900).height(600)
                .title("Weekly Performance Trend")
                .xAxisTitle("Week Index").yAxisTitle("Milliseconds")
                .build();

        weeklyChart.addSeries("DashboardLoadTimeMs", xIndexes, weeklyLoadAvg).setMarker(SeriesMarkers.CIRCLE);
        weeklyChart.addSeries("FCPms", xIndexes, weeklyFCPAvg).setMarker(SeriesMarkers.DIAMOND);
        weeklyChart.addSeries("LCPms", xIndexes, weeklyLCPAvg).setMarker(SeriesMarkers.SQUARE);

        // Add week labels in the chart title for clarity
        weeklyChart.setTitle("Weekly Performance Trend (Weeks: " + String.join(", ", weekKeys) + ")");

        BitmapEncoder.saveBitmap(weeklyChart, WEEKLY_CHART, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("Charts generated: " + CURRENT_CHART + ", " + WEEKLY_CHART);
    }
}
