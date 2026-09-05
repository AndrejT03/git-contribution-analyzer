const previewQuery = new URLSearchParams(window.location.search);

const selectPreviewButton = (buttons, state) => {
    buttons.forEach((button) => {
        const selected = button.dataset.progressPreview === state;
        button.classList.toggle("is-selected", selected);
        button.setAttribute("aria-pressed", String(selected));
    });
};

const initializeProgressPreview = (progress) => {
    const progressRoot = document.querySelector("[data-analysis-progress]");
    if (!progressRoot) {
        return;
    }

    const repositoryLabel = document.getElementById("repositoryLabel");
    const previewBadge = document.getElementById("previewBadge");
    const previewButtons = Array.from(progressRoot.querySelectorAll("[data-progress-preview]"));

    previewButtons.forEach((button) => {
        button.addEventListener("click", () => {
            const state = button.dataset.progressPreview;
            selectPreviewButton(previewButtons, state);

            if (state === "failure") {
                progress.showFailure("The repository could not be read. Check that it is public and available, then try again.");
            } else if (state === "missing") {
                progress.showFailure("This analysis is no longer available. The application may have restarted.");
            } else if (state === "restart") {
                window.location.assign(window.location.pathname);
            }
        });
    });

    if (previewQuery.get("preview") === "sequence") {
        previewBadge.hidden = previewQuery.get("chrome") !== "1";
        window.setTimeout(() => {
            void progress.renderStatusSequence({
                progress: 100,
                stage: "COMPLETED",
                stageLabel: "Completed",
                message: "Your contribution report is ready.",
                repositoryLabel: repositoryLabel.textContent,
                status: "COMPLETED",
                stageHistory: [
                    "QUEUED",
                    "STARTING",
                    "READING_REPOSITORY",
                    "ANALYZING_WITH_AI",
                    "PREPARING_REPORT",
                    "SAVING_REPORT",
                    "DELIVERING_EMAIL",
                    "COMPLETED"
                ],
                stageStates: {LOCAL_FALLBACK: "SKIPPED"}
            });
        }, progress.initialStageHoldMs);
    } else if (previewQuery.get("preview") === "reconnecting") {
        void progress.renderStatus({
            progress: 55,
            stage: "ANALYZING_WITH_AI",
            stageLabel: "Analyzing with AI",
            message: "Classifying commits and assessing their alignment with the project goal.",
            repositoryLabel: repositoryLabel.textContent,
            status: "RUNNING",
            stageStates: {
                QUEUED: "COMPLETE",
                STARTING: "COMPLETE",
                READING_REPOSITORY: "COMPLETE",
                ANALYZING_WITH_AI: "ACTIVE",
                LOCAL_FALLBACK: "PENDING",
                PREPARING_REPORT: "PENDING",
                SAVING_REPORT: "PENDING",
                DELIVERING_EMAIL: "PENDING",
                COMPLETED: "PENDING"
            }
        });
        progress.setReconnecting();
        previewBadge.hidden = previewQuery.get("chrome") !== "1";
    }
};

const initializeErrorPreview = () => {
    const errorCard = document.querySelector("[data-error-card]");
    if (!errorCard) {
        return;
    }

    const errorStatus = errorCard.querySelector("[data-error-status]");
    const errorHeading = errorCard.querySelector("[data-error-heading]");
    const errorDescription = errorCard.querySelector("[data-error-description]");
    const errorPreviewButtons = Array.from(document.querySelectorAll("[data-error-preview]"));
    const states = {
        "400": {
            heading: "We couldn't process that request",
            description: "The requested address contains an invalid value. Check the link and try again."
        },
        "404": {
            heading: "We couldn't find that analysis",
            description: "The analysis or report you're looking for doesn't exist or is no longer available. Reports are kept only for the current session and are not stored."
        },
        "500": {
            heading: "Something went wrong",
            description: "The application ran into an unexpected problem. Try again in a moment."
        }
    };

    errorPreviewButtons.forEach((button) => {
        button.addEventListener("click", () => {
            const status = button.dataset.errorPreview;
            const state = states[status];
            errorStatus.textContent = status;
            errorHeading.textContent = state.heading;
            errorDescription.textContent = state.description;
            document.title = `${status} · Git Contribution AI`;

            errorPreviewButtons.forEach((candidate) => {
                const selected = candidate === button;
                candidate.classList.toggle("is-selected", selected);
                candidate.setAttribute("aria-pressed", String(selected));
            });

            if (!(window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false)) {
                errorCard.classList.remove("is-state-refreshing");
                window.requestAnimationFrame(() => errorCard.classList.add("is-state-refreshing"));
            }
        });
    });
};

window.gitContributionDesignPreview = {
    initializeProgress: initializeProgressPreview
};

initializeErrorPreview();