const anchors = document.querySelectorAll('a[href^="#"]');

anchors.forEach((anchor) => {
    anchor.addEventListener("click", (event) => {
        const href = anchor.getAttribute("href");
        if (!href || href === "#") return;

        const target = document.querySelector(href);
        if (!target) return;

        event.preventDefault();
        target.scrollIntoView({
            behavior: "smooth",
            block: "start",
        });
    });
});

const guideTabs = document.querySelectorAll("[data-guide-tab]");
const guidePanels = document.querySelectorAll("[data-guide-panel]");

guideTabs.forEach((tab) => {
    tab.addEventListener("click", () => {
        const target = tab.getAttribute("data-guide-tab");
        if (!target) return;

        guideTabs.forEach((item) => {
            item.classList.toggle("is-active", item === tab);
        });

        guidePanels.forEach((panel) => {
            const isActive = panel.getAttribute("data-guide-panel") === target;
            panel.classList.toggle("is-active", isActive);
        });
    });
});

const copyButtons = document.querySelectorAll("[data-copy-text]");

copyButtons.forEach((button) => {
    const defaultLabel = button.textContent;

    button.addEventListener("click", async () => {
        const text = button.getAttribute("data-copy-text");
        if (!text) return;

        try {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                await navigator.clipboard.writeText(text);
            } else {
                const textarea = document.createElement("textarea");
                textarea.value = text;
                textarea.style.position = "fixed";
                textarea.style.opacity = "0";
                document.body.appendChild(textarea);
                textarea.focus();
                textarea.select();
                document.execCommand("copy");
                textarea.remove();
            }

            button.classList.add("is-copied");
            button.textContent = "已复制";

            window.setTimeout(() => {
                button.classList.remove("is-copied");
                button.textContent = defaultLabel;
            }, 1600);
        } catch (_error) {
            button.textContent = "复制失败";
            window.setTimeout(() => {
                button.textContent = defaultLabel;
            }, 1600);
        }
    });
});
