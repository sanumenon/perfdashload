# Dashboard Performance Kit

Measure login → dashboard-ready time with Selenium+TestNG, store results, and analyze trends with Python.

## Project layout
```
dashboard-perf-kit/
├─ dashboard-perf/                # Java Maven project
│  ├─ pom.xml
│  ├─ testng.xml
│  ├─ src/
│  │  ├─ main/java/com/perfkit/BaseTest.java
│  │  ├─ main/java/com/perfkit/ResultsWriter.java
│  │  └─ test/java/com/perfkit/DashboardPerformanceTest.java
│  └─ reports/                    # ExtentReports output (HTML) at runtime
├─ results/                       # CSV history + charts + reports
├─ analyze_performance.py         # Python analyzer (with optional AI helper)
└─ Jenkinsfile
```

## Prereqs
- Java 17+, Maven 3.8+
- Python 3.9+ (`pip install pandas matplotlib`), optional: `pip install openai`
- Chrome installed on EC2 (or add `--headless=new` which is already set)

## Run the Java test
From `dashboard-perf/`:
```bash
mvn -DskipTests clean test   -Denvironment=QA   -DbaseUrl=https://qa.example.com   -Dusername="$USER_EMAIL"   -Dpassword="$USER_PASS"   -DtargetSelector="#dashboardReadyMarker"   -DperfThresholdMs=3000   -DbuildId=$(date +%Y%m%d%H%M%S)   -Dsurefire.suiteXmlFiles=testng.xml
```
This appends a row to `../results/dashboard_performance_history.csv`.

### Environment switching
Set `-Denvironment=QA|STAGE|PROD` and/or override `-DbaseUrl` explicitly.

## Analyze & generate suggestions
From repo root:
```bash
python3 analyze_performance.py --history_csv results/dashboard_performance_history.csv --environment QA
```
Outputs:
- `results/trend_QA.png`
- `results/report_QA.md` (with suggestions)

To enable AI helper (optional), export your key:
```bash
export OPENAI_API_KEY=sk-...    # Uses gpt-4o-mini by default
pip install openai
```

## CI/CD (Jenkins on EC2) example
- Install Java, Maven, Python3, Chrome, and chromedriver (or rely on WebDriverManager).
- Configure credentials as Jenkins secrets.

In Jenkins, add a Pipeline with this `Jenkinsfile`:
```groovy
pipeline {
  agent any
  environment {
    ENVIRONMENT = "QA"            // or STAGE/PROD
    BASE_URL    = "https://qa.example.com"
    USER_EMAIL  = credentials('perf_user_email')
    USER_PASS   = credentials('perf_user_password')
    BUILD_ID    = "${env.BUILD_NUMBER}-${new Date().format('yyyyMMddHHmmss')}"
    PYTHON      = "python3"
  }
  stages {
    stage('Checkout') {
      steps { checkout scm }
    }
    stage('Measure') {
      steps {
        dir('dashboard-perf') {
          sh '''
            mvn -DskipTests clean test               -Denvironment=$ENVIRONMENT               -DbaseUrl=$BASE_URL               -Dusername=$USER_EMAIL               -Dpassword=$USER_PASS               -DtargetSelector="#dashboardReadyMarker"               -DperfThresholdMs=3000               -DbuildId=$BUILD_ID               -Dsurefire.suiteXmlFiles=testng.xml
          '''
        }
      }
    }
    stage('Analyze') {
      steps {
        sh '''
          $PYTHON analyze_performance.py --history_csv results/dashboard_performance_history.csv --environment $ENVIRONMENT
        '''
      }
    }
  }
  post {
    always {
      archiveArtifacts artifacts: 'results/**, dashboard-perf/reports/**', fingerprint: true
    }
  }
}
```

## Customizing login
Update selectors in `DashboardPerformanceTest.java` to match your login form and the **first interactive element** that proves the dashboard is ready (e.g., a filter dropdown, search box, or main widget container).

## Notes
- The test measures click → interactive-ready (via explicit wait). Optional FCP is fetched via the Performance API.
- Gate the build by checking `perfThresholdMs` in the test or by adding a separate assertion.
- Results persist across runs in `results/dashboard_performance_history.csv` so you can trend over time.
