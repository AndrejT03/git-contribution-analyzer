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