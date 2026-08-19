const reducedMotionPreference = window.matchMedia?.("(prefers-reduced-motion: reduce)");
const motionIsReduced = reducedMotionPreference?.matches ?? false;

const initializeRevealAnimations = () => {
    const loadTargets = Array.from(document.querySelectorAll("[data-reveal]"));
    const scrollTargets = Array.from(document.querySelectorAll("[data-reveal-on-scroll]"));
    const allTargets = [...new Set([...loadTargets, ...scrollTargets])];

    if (allTargets.length === 0 || motionIsReduced || typeof window.IntersectionObserver !== "function") {
        allTargets.forEach((target) => target.classList.add("is-revealed"));
        return;
    }

    document.documentElement.classList.add("reveal-ready");
    let observer;
    const reveal = (target) => {
        target.classList.add("is-revealed");
        observer?.unobserve(target);
    };

    observer = new window.IntersectionObserver((entries) => {
        entries.forEach((entry) => {
            if (entry.isIntersecting) {
                reveal(entry.target);
            }
        });
    }, {
        threshold: 0.12,
        rootMargin: "0px 0px -8% 0px"
    });

    loadTargets.forEach((target) => {
        window.requestAnimationFrame(() => reveal(target));
    });
    scrollTargets.forEach((target) => observer.observe(target));

    document.addEventListener("focusin", (event) => {
        const target = event.target.closest?.("[data-reveal], [data-reveal-on-scroll]");
        if (target) {
            reveal(target);
        }
    });

    const revealHashTarget = () => {
        if (!window.location.hash) {
            return;
        }

        let targetId = window.location.hash.slice(1);
        try {
            targetId = decodeURIComponent(targetId);
        } catch {
            // Keep the raw fragment when it is not valid percent-encoded text.
        }
        const target = document.getElementById(targetId);
        const revealTarget = target?.closest?.("[data-reveal], [data-reveal-on-scroll]")
            ?? target?.querySelector?.("[data-reveal], [data-reveal-on-scroll]");
        if (revealTarget) {
            reveal(revealTarget);
        }
    };

    window.addEventListener("hashchange", revealHashTarget);
    revealHashTarget();
};

initializeRevealAnimations();

const form = document.getElementById("analysisForm");
const description = document.getElementById("projectDescription");
const descriptionCounter = document.getElementById("descriptionCounter");

if (description && descriptionCounter) {
    const updateDescriptionCounter = () => {
        descriptionCounter.textContent = `${description.value.length}/2000`;
    };

    updateDescriptionCounter();
    description.addEventListener("input", updateDescriptionCounter);
}

if (form) {
    const controls = Array.from(form.querySelectorAll("input, textarea"));
    const markInvalid = (control) => {
        control.closest(".form-field")?.classList.add("form-field--invalid");
        control.setAttribute("aria-invalid", "true");
    };
    const clearInvalidWhenValid = (control) => {
        if (!control.validity.valid) {
            return;
        }

        control.closest(".form-field")?.classList.remove("form-field--invalid");
        control.setAttribute("aria-invalid", "false");
    };

    controls.forEach((control) => {
        control.addEventListener("invalid", () => markInvalid(control));
        control.addEventListener("input", () => clearInvalidWhenValid(control));
        control.addEventListener("blur", () => {
            if (!control.validity.valid) {
                markInvalid(control);
            }
        });
    });

    form.addEventListener("submit", () => {
        if (!form.checkValidity()) {
            return;
        }

        const button = document.getElementById("submitButton");
        const overlay = document.getElementById("loadingOverlay");
        button.disabled = true;
        button.textContent = "Starting analysis…";
        overlay.hidden = false;
    });
}

const progressRoot = document.querySelector("[data-analysis-progress]");

if (progressRoot) {
    const prefersReducedMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    const MIN_PROGRESS_TWEEN_MS = 800;
    const MAX_PROGRESS_TWEEN_MS = 2400;
    const PROGRESS_TWEEN_MS_PER_PERCENT = 45;
    const MAX_PROGRESS_DELTA_PER_FRAME = 0.72;
    const STAGE_HOLD_MS = 450;
    const INITIAL_STAGE_HOLD_MS = prefersReducedMotion ? 0 : 650;
    const COMPLETION_HOLD_MS = prefersReducedMotion ? 1200 : 1600;
    const statusUrl = progressRoot.dataset.statusUrl;
    const initialStatus = progressRoot.dataset.initialStatus;
    const replayFromStart = progressRoot.dataset.replayFromStart === "true";
    const initialReportUrl = progressRoot.dataset.reportUrl;
    const progressRing = document.getElementById("progressRing");
    const progressPercent = document.getElementById("progressPercent");
    const pipelineStageNumber = document.getElementById("pipelineStageNumber");
    const progressStage = document.getElementById("progressStage");
    const progressMessage = document.getElementById("progressMessage");
    const progressFailure = document.getElementById("progressFailure");
    const connectionStatus = document.getElementById("connectionStatus");
    const repositoryLabel = document.getElementById("repositoryLabel");
    const previewBadge = document.getElementById("previewBadge");
    const stageItems = Array.from(progressRoot.querySelectorAll("[data-stage-name]"));
    const stages = stageItems.map((item, index) => ({
        index,
        name: item.dataset.stageName,
        progress: Number(item.dataset.stageProgress) || 0,
        label: item.dataset.stageLabel,
        message: item.dataset.stageMessage
    }));
    const previewButtons = Array.from(progressRoot.querySelectorAll("[data-progress-preview]"));
    let retryDelay = 1000;
    let manualPreview = false;
    let completionScheduled = false;
    let displayedProgress = Number(progressRing.getAttribute("aria-valuenow")) || 0;
    let displayedStageIndex = Math.max(0, stageItems.findIndex((item) => item.classList.contains("is-current")));
    let progressAnimationFrame = null;
    let finishProgressAnimation = null;

    const wait = (duration) => new Promise((resolve) => window.setTimeout(resolve, duration));

    const updateText = (element, text) => {
        if (element && element.textContent !== text) {
            element.textContent = text;
        }
    };

    const fallbackStageState = (job, itemIndex, activeIndex) => {
        const itemName = stageItems[itemIndex].dataset.stageName;
        const skippedStage = job.analysisSource === "LOCAL_FALLBACK"
            ? "ANALYZING_WITH_GEMINI"
            : (job.analysisSource === "GEMINI" ? "LOCAL_FALLBACK" : null);

        if (itemName === skippedStage) {
            return "SKIPPED";
        }
        if (job.status === "COMPLETED" && itemIndex === activeIndex) {
            return "COMPLETE";
        }
        if (itemIndex === activeIndex) {
            return "ACTIVE";
        }
        return itemIndex < activeIndex ? "COMPLETE" : "PENDING";
    };

    const setStageState = (job) => {
        const activeIndex = stageItems.findIndex((item) => item.dataset.stageName === job.stage);
        if (activeIndex < 0) {
            return;
        }

        stageItems.forEach((item, index) => {
            const state = job.stageStates?.[item.dataset.stageName]
                ?? fallbackStageState(job, index, activeIndex);
            const itemIsComplete = state === "COMPLETE";
            const itemIsCurrent = state === "ACTIVE";
            const itemIsSkipped = state === "SKIPPED";
            item.classList.toggle("is-complete", itemIsComplete);
            item.classList.toggle("is-current", itemIsCurrent);
            item.classList.toggle("is-skipped", itemIsSkipped);
            item.dataset.stageState = state;
            if (itemIsCurrent) {
                item.setAttribute("aria-current", "step");
            } else {
                item.removeAttribute("aria-current");
            }

            const stateLabel = item.querySelector(".stage-state-label");
            updateText(stateLabel, state.charAt(0) + state.slice(1).toLowerCase());
        });

        updateText(pipelineStageNumber, String(activeIndex + 1));
    };

    const applyProgress = (progress) => {
        const roundedProgress = Math.round(progress);
        displayedProgress = progress;
        progressRing.style.setProperty("--progress-stop", `${progress}%`);
        if (progressRing.getAttribute("aria-valuenow") !== String(roundedProgress)) {
            progressRing.setAttribute("aria-valuenow", String(roundedProgress));
        }
        updateText(progressPercent, String(roundedProgress));
    };

    const animateProgressTo = (targetProgress) => {
        if (progressAnimationFrame !== null) {
            window.cancelAnimationFrame(progressAnimationFrame);
            progressAnimationFrame = null;
            finishProgressAnimation?.();
            finishProgressAnimation = null;
            progressRoot.classList.remove("is-advancing");
            progressRing.classList.remove("is-advancing");
        }

        const startProgress = displayedProgress;
        const distance = targetProgress - startProgress;
        if (Math.abs(distance) < 0.01) {
            applyProgress(targetProgress);
            return Promise.resolve();
        }

        if (prefersReducedMotion) {
            applyProgress(targetProgress);
            return Promise.resolve();
        }

        const duration = Math.min(
            MAX_PROGRESS_TWEEN_MS,
            Math.max(MIN_PROGRESS_TWEEN_MS, Math.abs(distance) * PROGRESS_TWEEN_MS_PER_PERCENT)
        );
        progressRoot.classList.add("is-advancing");
        progressRing.classList.add("is-advancing");

        return new Promise((resolve) => {
            let startedAt = null;
            finishProgressAnimation = resolve;

            const animate = (timestamp) => {
                startedAt ??= timestamp;
                const elapsed = Math.min(1, (timestamp - startedAt) / duration);
                const eased = (1 - Math.cos(Math.PI * elapsed)) / 2;
                const desiredProgress = startProgress + distance * eased;
                const distanceToDesired = desiredProgress - displayedProgress;
                const frameDelta = Math.sign(distanceToDesired) * Math.min(
                    Math.abs(distanceToDesired),
                    MAX_PROGRESS_DELTA_PER_FRAME
                );
                applyProgress(displayedProgress + frameDelta);

                if (elapsed < 1 || Math.abs(targetProgress - displayedProgress) >= 0.01) {
                    progressAnimationFrame = window.requestAnimationFrame(animate);
                    return;
                }

                applyProgress(targetProgress);
                progressAnimationFrame = null;
                finishProgressAnimation = null;
                progressRoot.classList.remove("is-advancing");
                progressRing.classList.remove("is-advancing");
                resolve();
            };

            progressAnimationFrame = window.requestAnimationFrame(animate);
        });
    };

    const setConnected = () => {
        progressRoot.classList.remove("is-reconnecting");
        updateText(connectionStatus, "Analyzing repository");
    };

    const setReconnecting = () => {
        progressRoot.classList.add("is-reconnecting");
        updateText(connectionStatus, "Reconnecting…");
        updateText(
            progressMessage,
            "The connection dropped briefly. We saved your place and are retrying automatically — no need to refresh."
        );
    };

    const renderStatus = (job) => {
        const progress = Math.max(0, Math.min(100, Number(job.progress) || 0));
        const completed = job.status === "COMPLETED";
        const progressAnimation = animateProgressTo(progress);
        updateText(progressStage, job.stageLabel);
        updateText(progressMessage, job.message);
        updateText(repositoryLabel, job.repositoryLabel);
        setStageState(job);

        const failed = job.status === "FAILED";
        progressRoot.classList.toggle("is-failed", failed);
        progressRoot.classList.toggle("is-complete", completed);
        progressFailure.hidden = !failed;
        if (failed) {
            updateText(connectionStatus, "Analysis stopped");
        } else if (completed) {
            updateText(connectionStatus, "Analysis complete");
        } else if (job.stage === "QUEUED") {
            updateText(connectionStatus, "Queued for analysis");
        } else if (job.stage === "STARTING") {
            updateText(connectionStatus, "Starting analysis");
        } else {
            updateText(connectionStatus, "Analyzing repository");
        }
        return progressAnimation;
    };

    const stageIndexFor = (stageName) => stages.findIndex((stage) => stage.name === stageName);

    const replayStageStates = (job, activeIndex, completed) => {
        const reachedStages = new Set(job.stageHistory ?? []);

        return Object.fromEntries(stages.map((stage) => {
            let state = "PENDING";
            if (stage.index === activeIndex) {
                state = completed ? "COMPLETE" : "ACTIVE";
            } else if (stage.index < activeIndex && reachedStages.has(stage.name)) {
                state = "COMPLETE";
            } else if (stage.index < activeIndex && job.stageStates?.[stage.name] === "SKIPPED") {
                state = "SKIPPED";
            }
            return [stage.name, state];
        }));
    };

    const replayJobFor = (job, stage, isTargetStage) => {
        const completed = isTargetStage && job.status === "COMPLETED";
        const failed = isTargetStage && job.status === "FAILED";

        return {
            ...job,
            status: completed ? "COMPLETED" : (failed ? "FAILED" : "RUNNING"),
            stage: stage.name,
            stageLabel: stage.label,
            progress: stage.progress,
            message: failed ? job.message : stage.message,
            stageStates: replayStageStates(job, stage.index, completed)
        };
    };

    const renderStatusSequence = async (job) => {
        const targetStageIndex = stageIndexFor(job.stage);
        if (targetStageIndex < 0) {
            await renderStatus(job);
            return;
        }

        if (prefersReducedMotion) {
            await renderStatus(job);
            displayedStageIndex = targetStageIndex;
            return;
        }

        const reachedStages = new Set(job.stageHistory ?? [job.stage]);
        reachedStages.add(job.stage);
        const stagesToReplay = stages.filter((stage) => (
            stage.index > displayedStageIndex
            && stage.index <= targetStageIndex
            && reachedStages.has(stage.name)
        ));

        if (stagesToReplay.length === 0) {
            await renderStatus(job);
            displayedStageIndex = targetStageIndex;
            return;
        }

        for (const [index, stage] of stagesToReplay.entries()) {
            const isTargetStage = stage.index === targetStageIndex;
            await renderStatus(replayJobFor(job, stage, isTargetStage));
            displayedStageIndex = stage.index;

            if (index < stagesToReplay.length - 1) {
                await wait(STAGE_HOLD_MS);
            }
        }
    };

    const scheduleReportRedirect = (reportUrl) => {
        if (completionScheduled) {
            return;
        }

        if (!reportUrl) {
            showFailurePreview("The report was completed but is not available. Start a new analysis and try again.");
            return;
        }

        completionScheduled = true;
        window.requestAnimationFrame(() => {
            window.setTimeout(() => window.location.replace(reportUrl), COMPLETION_HOLD_MS);
        });
    };

    const selectPreviewButton = (state) => {
        previewButtons.forEach((button) => {
            const selected = button.dataset.progressPreview === state;
            button.classList.toggle("is-selected", selected);
            button.setAttribute("aria-pressed", String(selected));
        });
    };

    const showFailurePreview = (message) => {
        manualPreview = true;
        setConnected();
        progressRoot.classList.add("is-failed");
        updateText(connectionStatus, "Analysis stopped");
        updateText(progressMessage, message);
        progressFailure.hidden = false;
    };

    previewButtons.forEach((button) => {
        button.addEventListener("click", () => {
            const state = button.dataset.progressPreview;
            selectPreviewButton(state);

            if (state === "failure") {
                showFailurePreview("The repository could not be read. Check that it is public and available, then try again.");
            } else if (state === "missing") {
                showFailurePreview("This analysis is no longer available. The application may have restarted.");
            } else if (state === "restart") {
                window.location.assign(window.location.pathname);
            }
        });
    });

    async function pollStatus() {
        if (manualPreview) {
            return;
        }

        try {
            const response = await window.fetch(statusUrl, {
                cache: "no-store",
                headers: {"Accept": "application/json"}
            });

            if (response.status === 404) {
                showFailurePreview("This analysis is no longer available. The application may have restarted.");
                selectPreviewButton("missing");
                return;
            }

            if (!response.ok) {
                throw new Error(`Status request failed with ${response.status}`);
            }

            const job = await response.json();
            setConnected();
            await renderStatusSequence(job);
            retryDelay = 1000;

            if (job.status === "COMPLETED") {
                scheduleReportRedirect(job.reportUrl);
                return;
            }

            if (job.status === "FAILED") {
                return;
            }

            window.setTimeout(pollStatus, retryDelay);
        } catch {
            setReconnecting();
            retryDelay = Math.min(retryDelay * 2, 8000);
            window.setTimeout(pollStatus, retryDelay);
        }
    }

    const previewQuery = new URLSearchParams(window.location.search);
    const isPreviewRoute = window.location.pathname.startsWith("/__preview/");
    if (isPreviewRoute && previewQuery.get("preview") === "sequence") {
        manualPreview = true;
        previewBadge.hidden = previewQuery.get("chrome") !== "1";
        window.setTimeout(() => {
            void renderStatusSequence({
                progress: 100,
                stage: "COMPLETED",
                stageLabel: "Completed",
                message: "Your contribution report is ready.",
                repositoryLabel: repositoryLabel.textContent,
                status: "COMPLETED",
                analysisSource: "GEMINI",
                stageHistory: [
                    "QUEUED",
                    "STARTING",
                    "READING_REPOSITORY",
                    "ANALYZING_WITH_GEMINI",
                    "PREPARING_REPORT",
                    "SAVING_REPORT",
                    "DELIVERING_EMAIL",
                    "COMPLETED"
                ],
                stageStates: {LOCAL_FALLBACK: "SKIPPED"}
            });
        }, INITIAL_STAGE_HOLD_MS);
    } else if (isPreviewRoute && previewQuery.get("preview") === "reconnecting") {
        manualPreview = true;
        renderStatus({
            progress: 55,
            stage: "ANALYZING_WITH_GEMINI",
            stageLabel: "Analyzing with Gemini",
            message: "Classifying commits and assessing their alignment with the project goal.",
            repositoryLabel: repositoryLabel.textContent,
            status: "RUNNING"
        });
        setReconnecting();
        previewBadge.hidden = previewQuery.get("chrome") !== "1";
    } else if (isPreviewRoute) {
        manualPreview = true;
    } else if (replayFromStart) {
        window.setTimeout(pollStatus, INITIAL_STAGE_HOLD_MS);
    } else if (initialStatus === "COMPLETED") {
        updateText(connectionStatus, "Analysis complete");
        scheduleReportRedirect(initialReportUrl);
    } else {
        window.setTimeout(pollStatus, 350);
    }
}

const errorCard = document.querySelector("[data-error-card]");

if (errorCard) {
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
            updateErrorPreview(status, state);

            errorPreviewButtons.forEach((candidate) => {
                const selected = candidate === button;
                candidate.classList.toggle("is-selected", selected);
                candidate.setAttribute("aria-pressed", String(selected));
            });
        });
    });

    function updateErrorPreview(status, state) {
        errorStatus.textContent = status;
        errorHeading.textContent = state.heading;
        errorDescription.textContent = state.description;
        document.title = `${status} · Git Contribution AI`;
        if (!motionIsReduced) {
            errorCard.classList.remove("is-state-refreshing");
            window.requestAnimationFrame(() => errorCard.classList.add("is-state-refreshing"));
        }
    }
}