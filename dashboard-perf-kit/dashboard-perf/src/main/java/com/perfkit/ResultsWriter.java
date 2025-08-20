package com.perfkit;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ResultsWriter {
    private final String csvPath;

    public ResultsWriter(String csvPath) {
        this.csvPath = csvPath;
    }

    public void append(String date,String buildId,String environment,
                       String loadTimeMs,
                       Long fcpMs,
                       Long lcpMs,
                       String notes) throws IOException {
        boolean newFile = !Files.exists(Paths.get(csvPath));
        try (PrintWriter out = new PrintWriter(new FileWriter(csvPath, true))) {
            if (newFile) {
                out.println("Date,BuildId,Environment,DashboardLoadTimeMs ,FCPms,LCPms,Iteration");
            }
            out.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        date,
                        buildId,
                        environment,
                        loadTimeMs,
                        fcpMs != null ? fcpMs.toString() : "",
                        lcpMs != null ? lcpMs.toString() : "",
                        notes != null ? notes.replace(",", ";") : "");

        }
    }
}
