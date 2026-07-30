# System Architecture

## Main idea

The application separates objective Git facts from the contribution assessment. The Git layer collects repository data, and Gemini AI interprets that data in relation to the project goal.

```mermaid
flowchart LR
    U["User"] --> W["Spring MVC form"]
    W --> R["Report service"]
    R --> G["Git repository reader"]
    G --> D["Commit and diff data"]
    D --> P["Gemini prompt builder"]
    P --> A["Gemini API"]
    A --> V["JSON validation"]
    V --> S["In-memory report store"]
    S --> H["Web report"]
    S --> E["Email report"]
```

## Responsibilities

- The web layer validates input and renders the form and reports.
- The Git layer accepts public GitHub/GitLab HTTPS URLs, clones them temporarily, and extracts commits, files, and diffs.
- The Gemini layer builds a controlled prompt, calls the API, parses JSON, and validates completeness.
- The report layer stores results in memory and optionally sends them through SMTP.

## Data and privacy

Repository content exists only in a temporary directory and is removed after extraction. Reports are kept in memory and disappear when the application restarts. Secrets are read from the local `.env` file and are not committed to Git.