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

// Double-click the email field to insert the suggested address (you@example.com)
const emailInput = document.getElementById("email");
if (emailInput) {
    emailInput.addEventListener("dblclick", () => {
        // Insert suggested email on double click and notify any listeners
        emailInput.value = "you@example.com";
        emailInput.dispatchEvent(new Event("input", { bubbles: true }));
        emailInput.focus();
    });
}

const progressRoot = document.querySelector("[data-analysis-progress]");

if (progressRoot) {
    const MIN_PROGRESS_TWEEN_MS = 800;
    const MAX_PROGRESS_TWEEN_MS = 2400;
    const PROGRESS_TWEEN_MS_PER_PERCENT = 45;
    const MAX_PROGRESS_DELTA_PER_FRAME = 0.72;
    const STAGE_HOLD_MS = 450;
    const INITIAL_STAGE_HOLD_MS = motionIsReduced ? 0 : 650;
    const COMPLETION_HOLD_MS = motionIsReduced ? 1200 : 1600;
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
    const stageItems = Array.from(progressRoot.querySelectorAll("[data-stage-name]"));
    const stages = stageItems.map((item, index) => ({
        index,
        name: item.dataset.stageName,
        progress: Number(item.dataset.stageProgress) || 0,
        label: item.dataset.stageLabel,
        message: item.dataset.stageMessage
    }));
    let retryDelay = 1000;
    let pollingStopped = false;
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

    const setStageState = (job) => {
        const activeIndex = stageItems.findIndex((item) => item.dataset.stageName === job.stage);
        if (activeIndex < 0 || !job.stageStates) {
            return;
        }

        stageItems.forEach((item) => {
            const state = job.stageStates[item.dataset.stageName];
            if (!state) {
                return;
            }
            const itemIsComplete = state === "COMPLETE";
            const itemIsCurrent = state === "ACTIVE";
            const itemIsSkipped = state === "SKIPPED";
            item.classList.toggle("is-complete", itemIsComplete);
            item.classList.toggle("is-current", itemIsCurrent);
            item.classList.toggle("is-skipped", itemIsSkipped);
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

        if (motionIsReduced) {
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

        if (motionIsReduced) {
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
            showFailure("The report was completed but is not available. Start a new analysis and try again.");
            return;
        }

        completionScheduled = true;
        window.requestAnimationFrame(() => {
            window.setTimeout(() => window.location.replace(reportUrl), COMPLETION_HOLD_MS);
        });
    };

    const showFailure = (message) => {
        pollingStopped = true;
        setConnected();
        progressRoot.classList.add("is-failed");
        updateText(connectionStatus, "Analysis stopped");
        updateText(progressMessage, message);
        progressFailure.hidden = false;
    };

    async function pollStatus() {
        if (pollingStopped) {
            return;
        }

        try {
            const response = await window.fetch(statusUrl, {
                cache: "no-store",
                headers: {"Accept": "application/json"}
            });

            if (response.status === 404) {
                showFailure("This analysis is no longer available. The application may have restarted.");
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

    const previewInitializer = window.gitContributionDesignPreview?.initializeProgress;
    if (typeof previewInitializer === "function") {
        pollingStopped = true;
        previewInitializer({
            initialStageHoldMs: INITIAL_STAGE_HOLD_MS,
            renderStatus,
            renderStatusSequence,
            setReconnecting,
            showFailure
        });
    } else if (replayFromStart) {
        window.setTimeout(pollStatus, INITIAL_STAGE_HOLD_MS);
    } else if (initialStatus === "COMPLETED") {
        updateText(connectionStatus, "Analysis complete");
        scheduleReportRedirect(initialReportUrl);
    } else {
        window.setTimeout(pollStatus, 350);
    }
}