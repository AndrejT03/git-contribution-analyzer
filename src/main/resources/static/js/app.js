const reducedMotionPreference = window.matchMedia?.("(prefers-reduced-motion: reduce)");
const motionIsReduced = reducedMotionPreference?.matches ?? false;
const PAGE_EXIT_DURATION_MS = 400;
let pageExitStarted = false;

document.documentElement.classList.add("page-transition-ready");

const runAfterPageExit = (action) => {
    if (motionIsReduced) {
        action();
        return;
    }

    if (pageExitStarted) {
        return;
    }

    pageExitStarted = true;
    document.body.classList.add("is-page-leaving");
    window.setTimeout(action, PAGE_EXIT_DURATION_MS);
};

const navigateWithTransition = (url, {replace = false} = {}) => {
    runAfterPageExit(() => {
        if (replace) {
            window.location.replace(url);
        } else {
            window.location.assign(url);
        }
    });
};

window.addEventListener("pageshow", () => {
    pageExitStarted = false;
    document.body.classList.remove("is-page-leaving");
});

document.addEventListener("click", (event) => {
    const anchor = event.target.closest?.("a[href]");
    if (!anchor
        || event.defaultPrevented
        || event.button !== 0
        || event.metaKey
        || event.ctrlKey
        || event.shiftKey
        || event.altKey
        || anchor.hasAttribute("download")
        || (anchor.target && anchor.target !== "_self")
        || anchor.dataset.noPageTransition === "true") {
        return;
    }

    const destination = new URL(anchor.href, window.location.href);
    const staysOnCurrentDocument = destination.origin === window.location.origin
        && destination.pathname === window.location.pathname
        && destination.search === window.location.search
        && Boolean(destination.hash);

    if (destination.origin !== window.location.origin
        || !["http:", "https:"].includes(destination.protocol)
        || staysOnCurrentDocument) {
        return;
    }

    event.preventDefault();
    navigateWithTransition(destination.href);
});

const initializeRevealAnimations = () => {
    const revealSelector = "[data-reveal], [data-reveal-on-scroll], [data-reveal-group]";
    const groupTargets = Array.from(document.querySelectorAll("[data-reveal-group]"));
    const loadTargets = Array.from(document.querySelectorAll(
        "[data-reveal], [data-reveal-group=\"load\"]"
    ));
    const scrollTargets = Array.from(document.querySelectorAll(
        "[data-reveal-on-scroll], [data-reveal-group]:not([data-reveal-group=\"load\"])"
    ));
    const allTargets = [...new Set([...loadTargets, ...scrollTargets])];

    groupTargets.forEach((group) => {
        Array.from(group.children).forEach((child, index) => {
            child.style.setProperty("--reveal-item-delay", `${Math.min(index, 10) * 48}ms`);
        });
    });

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
        const target = event.target.closest?.(revealSelector);
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
        const revealTarget = target?.closest?.(revealSelector)
            ?? target?.querySelector?.(revealSelector);
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

const aiKey = document.getElementById("aiKey");
const aiKeyHint = document.getElementById("aiKeyHint");
const aiKeyVisibility = document.getElementById("aiKeyVisibility");
const aiModel = document.getElementById("aiModel");
const selectedProvider = document.getElementById("selectedProvider");
const aiKeyVisibilityIcon = aiKeyVisibility?.querySelector("i");

const setAiKeyVisibility = (visible) => {
    if (!aiKey || !aiKeyVisibility) {
        return;
    }

    aiKey.type = visible ? "text" : "password";
    aiKeyVisibility.setAttribute("aria-pressed", String(visible));
    aiKeyVisibility.setAttribute("aria-label", visible ? "Hide API key" : "Show API key");
    aiKeyVisibilityIcon?.classList.toggle("bi-eye", !visible);
    aiKeyVisibilityIcon?.classList.toggle("bi-eye-slash", visible);
};

if (aiKey && aiKeyHint && aiModel && selectedProvider) {
    let activeProvider = "";

    const updateSelectedProvider = () => {
        const selectedOption = aiModel.selectedOptions[0];
        const provider = selectedOption?.dataset.provider ?? "";
        const providerChanged = Boolean(activeProvider && provider && activeProvider !== provider);
        const keyWasCleared = providerChanged && aiKey.value.length > 0;

        if (providerChanged) {
            aiKey.value = "";
            setAiKeyVisibility(false);
        }

        activeProvider = provider;
        selectedProvider.hidden = provider.length === 0;
        selectedProvider.textContent = provider;
        aiKey.placeholder = selectedOption?.dataset.keyPlaceholder ?? "Paste your provider API key";
        aiKeyHint.textContent = keyWasCleared
            ? `API key cleared because the provider changed. Use an API key from ${provider}.`
            : provider
                ? `Use an API key from ${provider}.`
                : "Choose a model to see which provider key is required.";
    };

    updateSelectedProvider();
    aiModel.addEventListener("change", updateSelectedProvider);
}

if (aiKey && aiKeyVisibility) {
    aiKeyVisibility.addEventListener("click", () => {
        const keyIsVisible = aiKey.type === "text";
        setAiKeyVisibility(!keyIsVisible);
        aiKey.focus({ preventScroll: true });
    });
}

if (form) {
    const controls = Array.from(form.querySelectorAll("input, textarea, select"));
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
        control.addEventListener("change", () => clearInvalidWhenValid(control));
        control.addEventListener("blur", () => {
            if (!control.validity.valid) {
                markInvalid(control);
            }
        });
    });

    form.addEventListener("submit", (event) => {
        if (!form.checkValidity()) {
            return;
        }

        event.preventDefault();
        const button = document.getElementById("submitButton");
        const overlay = document.getElementById("loadingOverlay");
        button.disabled = true;
        button.textContent = "Starting analysis…";
        overlay.hidden = false;
        runAfterPageExit(() => window.HTMLFormElement.prototype.submit.call(form));
    });
}

const progressRoot = document.querySelector("[data-analysis-progress]");

if (progressRoot) {
    const MIN_PROGRESS_TWEEN_MS = 900;
    const MAX_PROGRESS_TWEEN_MS = 2600;
    const PROGRESS_TWEEN_MS_PER_PERCENT = 48;
    const STAGE_HOLD_MS = 480;
    const STAGE_COPY_ANIMATION_MS = 620;
    const INITIAL_STAGE_HOLD_MS = motionIsReduced ? 0 : 700;
    const COMPLETION_HOLD_MS = motionIsReduced ? 1200 : 1750;
    const statusUrl = progressRoot.dataset.statusUrl;
    const initialStatus = progressRoot.dataset.initialStatus;
    const replayFromStart = progressRoot.dataset.replayFromStart === "true";
    const initialReportUrl = progressRoot.dataset.reportUrl;
    const progressRing = document.getElementById("progressRing");
    const pipelineTrackFill = document.getElementById("pipelineTrackFill");
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
    let statusUpdatesStopped = false;
    let completionScheduled = false;
    let displayedProgress = Number(progressRing.getAttribute("aria-valuenow")) || 0;
    let displayedStageIndex = Math.max(0, stageItems.findIndex((item) => item.classList.contains("is-current")));
    let progressAnimationFrame = null;
    let finishProgressAnimation = null;
    let stageChangeAnimationFrame = null;
    let stageChangeTimeout = null;
    let statusRenderVersion = 0;
    let displayedStageKey = `${stages[displayedStageIndex]?.name}:${initialStatus}`;

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
        pipelineTrackFill.style.width = `${activeIndex / (stageItems.length - 1) * 100}%`;
    };

    const applyProgress = (progress) => {
        const boundedProgress = Math.max(0, Math.min(100, Number(progress) || 0));
        const roundedProgress = Math.round(boundedProgress);
        displayedProgress = boundedProgress;
        progressRing.style.setProperty("--progress-offset", String(100 - boundedProgress));
        if (progressRing.getAttribute("aria-valuenow") !== String(roundedProgress)) {
            progressRing.setAttribute("aria-valuenow", String(roundedProgress));
        }
        updateText(progressPercent, String(roundedProgress));
    };

    const stopProgressAnimation = () => {
        if (progressAnimationFrame !== null) {
            window.cancelAnimationFrame(progressAnimationFrame);
            progressAnimationFrame = null;
        }
        finishProgressAnimation?.();
        finishProgressAnimation = null;
        progressRoot.classList.remove("is-advancing");
        progressRing.classList.remove("is-advancing");
    };

    const stopStageAnimation = () => {
        if (stageChangeAnimationFrame !== null) {
            window.cancelAnimationFrame(stageChangeAnimationFrame);
            stageChangeAnimationFrame = null;
        }
        window.clearTimeout(stageChangeTimeout);
        stageChangeTimeout = null;
        progressRoot.classList.remove("is-stage-changing");
    };

    const animateStageChange = (job) => {
        const stageKey = `${job.stage}:${job.status}`;
        if (displayedStageKey === stageKey) {
            return;
        }

        displayedStageKey = stageKey;
        stopStageAnimation();
        if (motionIsReduced) {
            return;
        }

        stageChangeAnimationFrame = window.requestAnimationFrame(() => {
            stageChangeAnimationFrame = null;
            progressRoot.classList.add("is-stage-changing");
            stageChangeTimeout = window.setTimeout(() => {
                progressRoot.classList.remove("is-stage-changing");
                stageChangeTimeout = null;
            }, STAGE_COPY_ANIMATION_MS);
        });
    };

    applyProgress(displayedProgress);

    const animateProgressTo = (targetProgress) => {
        stopProgressAnimation();

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
                const eased = elapsed * elapsed * elapsed * (elapsed * (elapsed * 6 - 15) + 10);
                applyProgress(startProgress + distance * eased);

                if (elapsed < 1) {
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
        if (statusUpdatesStopped) {
            return;
        }
        progressRoot.classList.add("is-reconnecting");
        updateText(connectionStatus, "Reconnecting…");
        updateText(
            progressMessage,
            "The connection dropped briefly. We saved your place and are retrying automatically — no need to refresh."
        );
    };

    const renderStatus = (job) => {
        if (statusUpdatesStopped) {
            return Promise.resolve();
        }

        const renderVersion = ++statusRenderVersion;
        const progress = Math.max(0, Math.min(100, Number(job.progress) || 0));
        const completed = job.status === "COMPLETED";
        const failed = job.status === "FAILED";
        let progressAnimation;
        if (failed) {
            stopProgressAnimation();
            applyProgress(progress);
            progressAnimation = Promise.resolve();
        } else {
            progressAnimation = animateProgressTo(progress);
        }
        animateStageChange(job);
        updateText(progressStage, job.stageLabel);
        updateText(progressMessage, job.message);
        updateText(repositoryLabel, job.repositoryLabel);
        setStageState(completed && displayedProgress < 100 ? {
            ...job,
            stageStates: {...job.stageStates, [job.stage]: "ACTIVE"}
        } : job);

        progressRoot.classList.toggle("is-failed", failed);
        progressRoot.classList.toggle("is-complete", completed && displayedProgress >= 100);
        progressFailure.hidden = !failed;
        if (failed) {
            pollingStopped = true;
            statusUpdatesStopped = true;
            stopStageAnimation();
            updateText(connectionStatus, "Analysis stopped");
        } else if (completed) {
            updateText(connectionStatus, displayedProgress >= 100 ? "Analysis complete" : "Finishing analysis");
        } else {
            updateText(connectionStatus, "Analyzing repository");
        }
        return progressAnimation.then(() => {
            if (statusUpdatesStopped || renderVersion !== statusRenderVersion) {
                return;
            }
            if (completed && displayedProgress >= 100) {
                progressRoot.classList.add("is-complete");
                setStageState(job);
                updateText(connectionStatus, "Analysis complete");
            }
        });
    };

    const stageIndexFor = (stageName) => stages.findIndex((stage) => stage.name === stageName);

    const replayStageStates = (job, activeIndex, completed) => {
        const reachedStages = new Set(job.stageHistory ?? []);

        return Object.fromEntries(stages.map((stage) => {
            let state = "PENDING";
            if (stage.index === activeIndex) {
                state = completed ? "COMPLETE" : "ACTIVE";
            } else if (stage.index < activeIndex && job.stageStates?.[stage.name] === "SKIPPED") {
                state = "SKIPPED";
            } else if (stage.index < activeIndex && reachedStages.has(stage.name)) {
                state = "COMPLETE";
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
            stageStates: {
                ...replayStageStates(job, stage.index, completed),
                ...(isTargetStage ? job.stageStates : {})
            }
        };
    };

    const renderStatusSequence = async (job) => {
        if (statusUpdatesStopped) {
            return;
        }
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
            if (statusUpdatesStopped) {
                return;
            }
            const isTargetStage = stage.index === targetStageIndex;
            await renderStatus(replayJobFor(job, stage, isTargetStage));
            if (statusUpdatesStopped) {
                return;
            }
            displayedStageIndex = stage.index;

            if (index < stagesToReplay.length - 1) {
                await wait(STAGE_HOLD_MS);
            }
        }
    };

    const scheduleReportRedirect = (reportUrl) => {
        if (completionScheduled || statusUpdatesStopped) {
            return;
        }

        if (!reportUrl) {
            showFailure("The report was completed but is not available. Start a new analysis and try again.");
            return;
        }

        completionScheduled = true;
        window.requestAnimationFrame(() => {
            window.setTimeout(() => {
                if (!statusUpdatesStopped) {
                    navigateWithTransition(reportUrl, {replace: true});
                }
            }, COMPLETION_HOLD_MS);
        });
    };

    const showFailure = (message) => {
        pollingStopped = true;
        statusUpdatesStopped = true;
        statusRenderVersion += 1;
        stopProgressAnimation();
        stopStageAnimation();
        setConnected();
        progressRoot.classList.remove("is-complete");
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
            if (statusUpdatesStopped) {
                return;
            }
            setConnected();
            await renderStatusSequence(job);
            if (statusUpdatesStopped) {
                return;
            }
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
            if (statusUpdatesStopped) {
                return;
            }
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