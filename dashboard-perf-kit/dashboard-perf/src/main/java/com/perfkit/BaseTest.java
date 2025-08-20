package com.perfkit;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;

import java.nio.file.Files;
import java.nio.file.Path; 
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class BaseTest {
    protected WebDriver driver;
    protected WebDriverWait wait;
    protected ExtentReports extent;
    protected ExtentTest test;

    protected String environment;
    protected String baseUrl;
    protected String username;
    protected String password;
    protected String targetSelector;
    protected long perfThresholdMs;
    protected String buildId;
    ExtentSparkReporter spark;

    protected Map<String, String> envUrlMap = new HashMap<>();

    @BeforeClass
    public void init() throws Exception{
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path reportsDir = Paths.get("reports");
        Files.createDirectories(reportsDir);
        String reportPath = reportsDir.resolve("perf_report_" + timestamp + ".html").toString();
        spark = new ExtentSparkReporter(reportPath);
        extent = new ExtentReports();
    }
    @BeforeMethod //BeforeClass
    public void setUp() throws Exception {
        envUrlMap.put("QA", "https://my.qa.charitableimpact.com/users/login");
        envUrlMap.put("STAGE", "https://my1.stg.charitableimpact.com/users/login");
        envUrlMap.put("PROD", "https://my.charitableimpact.com/users/login");

        environment = System.getProperty("environment", "STAGE").toUpperCase();
        baseUrl = System.getProperty("baseUrl", envUrlMap.getOrDefault(environment, "https://my1.stg.charitableimpact.com/users/login"));
        username = System.getProperty("username", "YOUR_USERNAME");
        password = System.getProperty("password", "YOUR_PASSWORD");
        // username = System.getProperty("username", "testchimppro@charitableimpact.com");
        // password = System.getProperty("password", "EWQdsa1983#a");
        targetSelector = System.getProperty("targetSelector", "#cimpactAccount-Owner > div.fresnel-container.fresnel-greaterThanOrEqual-xl > div.homePageWrapper > div > div > div > div > div > div.userHomeRight > div:nth-child(5) > div > div > div:nth-child(1) > div > div.impactAccountsFooter > div.link > i.sc-jXbUNg.jwKnWE.icon.icon-arrow-right.undefined");
        //targetSelector = System.getProperty("targetSelector", "//*[@id='cimpactAccount-Owner']/div[2]/div[3]/div/div/div/div/div/div[2]/div[4]/div/div/div[1]/div/div[4]");
        String perfThresholdStr = System.getProperty("perfThresholdMs");
        if (perfThresholdStr != null && !perfThresholdStr.isEmpty()) {
            perfThresholdMs = Long.parseLong(perfThresholdStr);
        } else {
            perfThresholdMs = 10000; // Default threshold of 10 seconds
        }
        buildId = System.getProperty("buildId", "local-" + System.currentTimeMillis());

        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        //options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        wait = new WebDriverWait(driver, Duration.ofSeconds(120));
  
        extent.attachReporter(spark);

        test = extent.createTest("Dashboard Performance - " + environment);
        test.info("Base URL: " + baseUrl);
        test.info("Build ID: " + buildId);
    }

    @AfterMethod(alwaysRun = true) //AfterClass(alwaysRun = true)
    public void tearDown() throws Exception{
        if (driver != null) {
            Thread.sleep(9000);
            driver.quit();
        }
    }
    @AfterClass(alwaysRun = true)
    public void afterClass() {
        if (extent != null) {
            extent.flush(); 
        }
    }
    @AfterSuite
    public void generateCharts() throws Exception {
        // Generate charts
        PerfChartGenerator.main(null);
        ExtentTest suiteTest = extent.createTest("Performance Charts - Dashboard");
        Path resultsDir = Paths.get(System.getProperty("user.dir")).getParent().resolve("results");
        System.out.println("Results directory: " + resultsDir.toAbsolutePath().normalize());
        suiteTest.addScreenCaptureFromPath(resultsDir.resolve("current_execution.png").toString(),
                "Current Execution Metrics");
        suiteTest.addScreenCaptureFromPath(resultsDir.resolve("weekly_status.png").toString(),
                "Weekly Performance Trend");
        
    
        extent.flush();
    }
    
}
