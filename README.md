# Git Contribution AI

Git Contribution AI is a Spring Boot web application that analyzes a public GitHub or GitLab repository, reads its Git history, and generates a clear contribution report for each author or team member.

The goal is not just to show who wrote more code, but to provide a more understandable picture of:
- who contributed the most,
- what type of work each contributor performed,
- which commits are most relevant to the project goal,
- how contribution can be compared across the team.

This project is a practical Git activity analysis tool built around commit metadata, diff inspection, and AI-assisted classification.

## Why this project exists

This app is useful for:
- contribution review in software teams,
- academic and diploma projects,
- project evaluation based on repository activity,
- understanding who worked on what,
- generating a shareable or printable contribution report.

Important: this tool supports contribution analysis, but it should not be treated as the only measure of a person's value or the quality of code.

## Key features

- Analysis of public GitHub and GitLab repositories
- Reading of Git history, authors, commit messages, and diffs
- Contribution percentage calculation per contributor
- Commit classification by work type
- Optional AI analysis with Gemini
- Automatic fallback to a deterministic local analyzer
- Live progress page during analysis
- Browser-based report generation
- PDF export for printing and sharing
- Optional email delivery of the report
- Configurable limits for commit count, diff size, and execution time

## Tech stack

- Java 21
- Spring Boot 4.1
- Maven
- Thymeleaf
- Spring Validation
- Hibernate Validator
- OpenHTMLtoPDF
- Git CLI
- Google Gemini API (optional)

## How it works

The application has three main stages:

1. User input
    - repository URL
    - short project description / goal
    - email address

2. Analysis pipeline
    - clones the repository into a temporary directory,
    - reads Git history,
    - collects author and diff metadata,
    - attempts AI classification with Gemini,
    - falls back to a local analyzer if Gemini is unavailable or rejected.

3. Results
    - creates a contribution report,
    - presents it in the browser,
    - allows PDF export,
    - optionally sends the report by email.

## Architecture overview

```text
┌──────────────┐      ┌──────────────────────┐      ┌──────────────────────┐
│ Web UI       │ ──▶ │ Spring Controllers   │ ──▶ │ Service Layer        │
│ (Thymeleaf)  │      │ /, /analyze,         │      │ Git repo read        │
│              │      │ /analyses, /reports  │      │ Gemini/local         │
└──────────────┘      └──────────────────────┘      │ report generation    │
                                                       └─────────┬────────────┘
                                                                 │
                                                                 ▼
                                                     ┌──────────────────────┐
                                                     │ Domain/Data Layer    │
                                                     │ jobs, reports,       │
                                                     │ commits, DTOs        │
                                                     └──────────────────────┘
```

## Project structure

```text
.
├── .env.example                  # sample environment config
├── .env                          # local runtime config (not committed)
├── pom.xml                      # Maven configuration
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── mk/ukim/finki/gitcontributor/
│   │   │       ├── config/       # AppSettings and env binding
│   │   │       ├── dto/          # request/response DTOs
│   │   │       ├── enums/        # enum values
│   │   │       ├── exception/    # custom exceptions
│   │   │       ├── model/        # domain models for jobs and reports
│   │   │       ├── repository/   # in-memory repositories
│   │   │       ├── service/      # service interfaces
│   │   │       ├── service/impl/ # implementations
│   │   │       └── web/          # controllers and routes
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── static/           # CSS/JS assets
│   │       └── templates/        # Thymeleaf templates
│   └── test/
│       └── java/                 # unit/integration tests
├── docs/
├── README.md                    # this project documentation
├── .gitignore
```

## Requirements

Make sure you have:

- Java 21+
- Maven 3.9+
- Git CLI installed and available on PATH
- A public HTTPS GitHub or GitLab repository URL
- A Gemini API key for AI analysis

## Configuration

The app loads local configuration from `.env` using Spring Boot:

```properties
spring.config.import=optional:file:.env[.properties]
```

Create the local config file:

```bash
cp .env.example .env
```

Example `.env` values:

```properties
MAX_COMMITS=80
MAX_DIFF_CHARS=6000
GIT_TIMEOUT_SECONDS=120

GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.7-flash
GEMINI_TIMEOUT_SECONDS=180

MAIL_ENABLED=false
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=
```

### Configuration notes

- `MAX_COMMITS` limits how many commits are read from the repository
- `MAX_DIFF_CHARS` limits the diff size per commit
- `GIT_TIMEOUT_SECONDS` sets the timeout for Git operations
- `GEMINI_API_KEY` can be empty; in that case the app uses the local fallback analyzer
- `MAIL_ENABLED=false` disables email sending by default
- For Gmail, use an app password instead of the regular account password

## Running the application

### 1. Build the project

```bash
mvn clean install
```

### 2. Start the app

```bash
mvn spring-boot:run
```

After startup, the app is available at:

```text
http://localhost:8080
```

## Main routes

```text
GET  /                    -> landing page with the analysis form
POST /analyze             -> start a new analysis
GET  /analyses/{id}       -> progress page for a running analysis
GET  /api/analyses/{id}   -> JSON status endpoint
GET  /reports/{id}        -> browser report page
GET  /reports/{id}/pdf    -> PDF report (inline or download)
```

## Analysis workflow

### User flow

1. User submits:
    - repository URL
    - short project description or goal
    - email address
2. The backend validates the input.
3. The app clones the repository into a temporary directory.
4. It reads commit history, authors, messages, and diffs.
5. It attempts AI classification via Gemini.
6. If Gemini fails, it falls back to the local analyzer.
7. A report is generated and shown in the browser.
8. If enabled, a copy may also be sent by email.

### Progress stages

The job moves through these stages:

- QUEUED
- STARTING
- READING REPOSITORY
- ANALYZING WITH GEMINI
- LOCAL FALLBACK
- PREPARING REPORT
- SAVING REPORT
- DELIVERING EMAIL
- COMPLETED

The progress page polls the backend and updates the phase in real time.

## What the report contains

The generated report includes:
- repository name and default branch
- project description
- contribution percentage per user
- category/type of work per contributor
- commit-level evidence and classification
- team indicators or imbalance notices
- information about which analysis source was used (Gemini or local fallback)

The browser report is the primary output. PDF export is available for sharing and printing.

## Gemini vs local fallback

### Gemini analysis

When a valid API key is configured and the provider is reachable, the app performs a structured AI-based analysis of the repository history and maps commits to the project goal.

### Local fallback

If Gemini fails due to:
- missing API key,
- rate limiting or quota exhaustion,
- rejected request,
- timeout,
- network issue,
- invalid response,

then the app uses a built-in local analyzer that:
- evaluates commit messages and author metadata,
- measures importance based on changed lines and change type,
- classifies work according to project and file path patterns,
- computes deterministic contribution percentages.

The local fallback is useful and stable, but it does not replace semantic AI interpretation.

## Email behavior

Email delivery is optional, and can be turned off in the `.env` file by setting `MAIL_ENABLED=false`.

When enabled:
- the report remains available in the browser,
- a secondary copy may be sent to the provided email address,
- if sending fails, the report is still preserved and the issue is logged.

## Testing

Run the full test suite with:

```bash
mvn test
```

The project includes tests for:
- web controllers,
- service logic,
- analysis workflow,
- fallback behavior,
- email and report handling.

## Troubleshooting

### The app does not start

Check:
- Java 21 is installed,
- Maven can resolve dependencies,
- `.env` is valid,
- Git is installed and available on PATH.

### Gemini is unavailable

This is expected if:
- the API key is empty,
- the provider rejects the request,
- quota is exhausted,
- there is a timeout or network issue.

In that case, the app automatically switches to the local fallback analyzer.

### The repository cannot be analyzed

Check whether:
- the URL is correct and public,
- the host is GitHub or GitLab,
- the repository is not private or unavailable,
- the URL does not include unsupported query/fragment/userinfo parts.

## Quick start

```bash
git clone <repo-url>
cd git-contribution-ai
cp .env.example .env
# edit .env as needed
mvn spring-boot:run
```

Then open:

```text
http://localhost:8080
```

## License

This is an academic/demo project and should be checked before using it in any personal context.

## Short summary

Git Contribution AI is a Spring Boot application for repository contribution analysis. It reads Git history from a public project, analyzes author contributions, and turns the result into a readable report with optional AI support, local fallback logic, PDF export, and email delivery.
