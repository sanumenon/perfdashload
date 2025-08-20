package com.perfkit;

import com.aventstack.extentreports.Status;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardPerformanceTest extends BaseTest {
    int counter = 1;

    @Test(invocationCount = 20)
    public void measureDashboardReady() throws Exception {
        driver.get(baseUrl);
        test.info("Navigated to: " + baseUrl);
        Thread.sleep(5000); // Wait for page to load
        // Define a list of WebElements to store the found elements
        List<WebElement> pendoModal = driver.findElements(By.id("pendo-guide-container"));
        if (pendoModal.size() > 0) {
            // The modal is present, now find and click the close button
            try {
                WebElement closeButton = new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.elementToBeClickable(By.id("pendo-close-guide-eb913c7a")));
                closeButton.click();
                System.out.println("Pendo modal closed successfully.");
            } catch (Exception e) {
                System.out.println("Pendo modal was present but could not be closed.");
                // Log the exception for debugging purposes if needed
            }
        } 
        driver.findElement(By.id("1-email")).sendKeys(username);
        driver.findElement(By.id("1-password")).sendKeys(password);

        WebElement loginBtn = driver.findElement(By.id("1-submit"));

        // ✅ Inject LCP observer BEFORE login
        ((JavascriptExecutor) driver).executeScript(
            "if (!window._lcp) {" +
            "  window._lcp = 0;" +
            "  new PerformanceObserver((entryList) => {" +
            "    for (const entry of entryList.getEntries()) {" +
            "      window._lcp = Math.round(entry.startTime);" +
            "    }" +
            "  }).observe({type: 'largest-contentful-paint', buffered: true});" +
            "}"
        );

        long start = System.nanoTime();
        loginBtn.click();

        By ready = By.cssSelector(targetSelector);
        wait.until(ExpectedConditions.and(
                ExpectedConditions.visibilityOfElementLocated(ready),
                drv -> drv.findElement(ready).isEnabled()
        ));

        long end = System.nanoTime();
        long loadTimeMs = Duration.ofNanos(end - start).toMillis();
        test.log(Status.INFO, "Dashboard load time (click->ready): " + loadTimeMs + " ms");

        // ✅ Capture timestamp in ISO format with milliseconds
        LocalDateTime now = LocalDateTime.now();
        String loadTimestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
        test.log(Status.INFO, "Load event timestamp: " + loadTimestamp);

        // ✅ Capture FCP
        Long fcpMs = null;
        try {
            Object perf = ((JavascriptExecutor) driver).executeScript(
                    "const e = performance.getEntriesByType('paint');" +
                    "const fcp = e.find(x=>x.name==='first-contentful-paint');" +
                    "return fcp ? Math.round(fcp.startTime) : null;");
            if (perf != null) {
                fcpMs = Long.parseLong(perf.toString());
                test.log(Status.INFO, "First Contentful Paint (ms): " + fcpMs);
            }
        } catch (Exception ex) {
            test.log(Status.WARNING, "Unable to capture FCP: " + ex.getMessage());
        }

        // ✅ Threshold check
        if (loadTimeMs > perfThresholdMs) {
            test.log(Status.WARNING, "Load time exceeded threshold: " + perfThresholdMs + " ms");
        } else {
            test.log(Status.PASS, "Load time within threshold: " + perfThresholdMs + " ms");
        }

        // ✅ Capture LCP (after dashboard ready, small wait to stabilize)
        Long lcpMs = null;
        try {
                        // After waiting for dashboard ready:
            ((JavascriptExecutor) driver).executeScript(
                "window._lcp = 0;" +
                "new PerformanceObserver((entryList) => {" +
                "  for (const entry of entryList.getEntries()) {" +
                "    window._lcp = Math.round(entry.startTime);" +
                "  }" +
                "}).observe({type: 'largest-contentful-paint', buffered: true});"
            );

            // Give a few seconds for observer to catch last paints
            Thread.sleep(5000);

            Object lcpPerf = ((JavascriptExecutor) driver).executeScript(
                "return window._lcp || null;"
            );
            if (lcpPerf != null) {
                lcpMs = Long.parseLong(lcpPerf.toString());
                test.log(Status.INFO, "Largest Contentful Paint (ms): " + lcpMs);
            }
        }catch (Exception ex) {
            test.log(Status.WARNING, "Unable to capture LCP: " + ex.getMessage());
        }

        // ✅ Results directory
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path newPathdir = currentDir.getParent().resolve("results");
        Files.createDirectories(newPathdir);
        String csvPath = newPathdir.resolve("dashboard_performance_history.csv").toString();
        System.out.println("The new path is: " + newPathdir.toAbsolutePath().normalize());

        // ✅ Write results to CSV (now includes LCP)
        ResultsWriter writer = new ResultsWriter(csvPath);
        LocalDateTime timenow = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        writer.append(timenow.format(formatter),buildId,environment,String.valueOf(loadTimeMs),fcpMs, lcpMs,"targetSelector=" + targetSelector);

        Assert.assertTrue(loadTimeMs >= 0, "Measured load time should be non-negative");
        test.log(Status.INFO, "Test completed successfully. Refer csv file for detailed results: ");
        test.log(Status.INFO, "Iteration Completed: " + counter);
        counter++;

    }
}


//To run the program with PROD environment, you can use the following command:
/*How to run the program : (Desktop/jpphelper/dashboard-perf-kit/dashboard-perf$ )Current folder from Terminal $> mvn clean test -Denvironment=PROD -DbaseUrl="https://my.charitableimpact.com" -Dusername="testchimppro@charitableimpact.com" -Dpassword="EWQdsa1983#a" -DtargetSelector="#cimpactAccount-Owner > div.fresnel-container.fresnel-greaterThanOrEqual-xl > div.homePageWrapper > div > div > div > div > div > div.userHomeRight > div:nth-child(5) > div > div > div:nth-child(1) > div > div.impactAccountsFooter > div.link > i.sc-jXbUNg.jwKnWE.icon.icon-arrow-right.undefined" -DperfThresholdMs=10000 -DbuildId=$(date +%Y%m%d%H%M%S) -Dsurefire.suiteXmlFiles=testng.xml */

//To run the program with STAGE (my1) environment, you can use the following command:
//mvn clean test -Denvironment=STAGE -DbaseUrl="https://my1.stg.charitableimpact.com" -Dusername="bal.ganesh001@gmail.com" -Dpassword="Test123#" -DtargetSelector="#cimpactAccount-Owner > div.fresnel-container.fresnel-greaterThanOrEqual-xl > div.homePageWrapper > div > div > div > div > div > div.userHomeRight > div:nth-child(4) > div > div > div:nth-child(1) > div > div.impactAccountsFooter > div.link > i" -DperfThresholdMs=10000 -DbuildId=$(date +%Y%m%d%H%M%S) -Dsurefire.suiteXmlFiles=testng.xml 