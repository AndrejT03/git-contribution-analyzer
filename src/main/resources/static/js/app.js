const form = document.getElementById("analysisForm");

if (form) {
    form.addEventListener("submit", () => {
        if (!form.checkValidity()) {
            return;
        }

        const button = document.getElementById("submitButton");
        const overlay = document.getElementById("loadingOverlay");
        button.disabled = true;
        button.textContent = "Analysis in progress...";
        overlay.hidden = false;
    });
}

const progressRoot = document.querySelector("[data-analysis-progress]");

if (progressRoot) {
    const statusUrl = progressRoot.dataset.statusUrl;
    const progressBar = document.getElementById("analysisProgressBar");
    const progressFill = document.getElementById("progressFill");
    const progressPercent = document.getElementById("progressPercent");
    const progressStage = document.getElementById("progressStage");
    const progressMessage = document.getElementById("progressMessage");
    const progressFailure = document.getElementById("progressFailure");
    let retryDelay = 1000;
    let stopped = false;

    const scheduleNextPoll = (delay) => {
        if (!stopped) {
            window.setTimeout(pollStatus, delay);
        }
    };

    const renderStatus = (job) => {
        const progress = Math.max(0, Math.min(100, Number(job.progress) || 0));
        progressFill.style.width = `${progress}%`;
        if (progressBar.getAttribute("aria-valuenow") !== String(progress)) {
            progressBar.setAttribute("aria-valuenow", String(progress));
        }
        if (progressPercent.textContent !== `${progress}%`) {
            progressPercent.textContent = `${progress}%`;
        }
        if (progressStage.textContent !== job.stageLabel) {
            progressStage.textContent = job.stageLabel;
        }
        if (progressMessage.textContent !== job.message) {
            progressMessage.textContent = job.message;
        }

        if (job.status === "FAILED") {
            progressFailure.hidden = false;
        }
    };

    async function pollStatus() {
        try {
            const response = await window.fetch(statusUrl, {
                cache: "no-store",
                headers: {"Accept": "application/json"}
            });

            if (response.status === 404) {
                stopped = true;
                const missingMessage =
                    "This analysis is no longer available. The application may have restarted.";
                if (progressMessage.textContent !== missingMessage) {
                    progressMessage.textContent = missingMessage;
                }
                progressFailure.hidden = false;
                return;
            }

            if (!response.ok) {
                throw new Error(`Status request failed with ${response.status}`);
            }

            const job = await response.json();
            renderStatus(job);
            retryDelay = 1000;

            if (job.status === "COMPLETED" && job.reportUrl) {
                stopped = true;
                window.location.replace(job.reportUrl);
                return;
            }

            if (job.status === "FAILED") {
                stopped = true;
                return;
            }

            scheduleNextPoll(retryDelay);
        } catch (error) {
            const retryMessage =
                "The live connection was interrupted. Retrying automatically…";
            if (progressMessage.textContent !== retryMessage) {
                progressMessage.textContent = retryMessage;
            }
            retryDelay = Math.min(retryDelay * 2, 8000);
            scheduleNextPoll(retryDelay);
        }
    }

    scheduleNextPoll(350);
}