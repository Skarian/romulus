import mermaid from "mermaid";
import { diagramCatalog } from "./diagrams.js";

const linksEl = document.querySelector("#diagram-links");
const listEl = document.querySelector("#diagram-list");
const statusEl = document.querySelector("#status");
const MIN_SCALE = 0.3;
const MAX_SCALE = 4.0;
const ZOOM_STEP = 1.15;
const PAN_STEP_PX = 48;

function setStatus(message, isError = false) {
  statusEl.textContent = message;
  statusEl.classList.toggle("error", isError);
}

function renderLinkBar() {
  const fragment = document.createDocumentFragment();
  diagramCatalog.forEach((diagram) => {
    const link = document.createElement("a");
    link.href = `#${diagram.id}`;
    link.textContent = diagram.title;
    fragment.appendChild(link);
  });
  linksEl.replaceChildren(fragment);
}

function createDiagramCard(diagram, rawSource) {
  const section = document.createElement("section");
  section.id = diagram.id;
  section.className = "card";
  section.dataset.diagram = "true";

  const heading = document.createElement("h2");
  heading.textContent = diagram.title;

  const summary = document.createElement("p");
  summary.className = "summary";
  summary.textContent = diagram.description;

  const controls = document.createElement("div");
  controls.className = "diagram-controls";
  controls.innerHTML = `
    <button type="button" class="diagram-btn" data-action="fullscreen">Fullscreen</button>
    <button type="button" class="diagram-btn" data-action="zoom-in">+</button>
    <button type="button" class="diagram-btn" data-action="zoom-out">-</button>
    <button type="button" class="diagram-btn" data-action="reset">Reset</button>
    <span class="diagram-zoom" data-role="zoom-value">100%</span>
  `;

  const viewport = document.createElement("div");
  viewport.className = "diagram-viewport";
  viewport.tabIndex = 0;

  const mermaidBlock = document.createElement("div");
  mermaidBlock.className = "mermaid diagram-render";
  mermaidBlock.textContent = rawSource;
  viewport.appendChild(mermaidBlock);

  const details = document.createElement("details");
  const detailsSummary = document.createElement("summary");
  detailsSummary.textContent = "View source";
  const pre = document.createElement("pre");
  pre.textContent = rawSource;
  details.append(detailsSummary, pre);

  section.append(heading, summary, controls, viewport, details);
  return section;
}

function createErrorCard(diagram, reason) {
  const section = document.createElement("section");
  section.id = diagram.id;
  section.className = "card";

  const heading = document.createElement("h2");
  heading.textContent = diagram.title;

  const summary = document.createElement("p");
  summary.className = "summary";
  summary.textContent = diagram.description;

  const error = document.createElement("p");
  error.className = "error";
  error.textContent = `Unable to load ${diagram.path}: ${reason}`;

  section.append(heading, summary, error);
  return section;
}

async function loadDiagramSource(diagram) {
  const response = await fetch(diagram.path);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return response.text();
}

function clampScale(value) {
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, value));
}

function formatZoom(scale) {
  return `${Math.round(scale * 100)}%`;
}

function attachInteractionHandlers(card) {
  const viewport = card.querySelector(".diagram-viewport");
  const svg = viewport?.querySelector("svg");
  if (!viewport || !svg) return;

  const zoomValueEl = card.querySelector("[data-role='zoom-value']");
  const zoomInBtn = card.querySelector("[data-action='zoom-in']");
  const zoomOutBtn = card.querySelector("[data-action='zoom-out']");
  const resetBtn = card.querySelector("[data-action='reset']");
  const fullscreenBtn = card.querySelector("[data-action='fullscreen']");

  const state = {
    scale: 1,
    offsetX: 0,
    offsetY: 0,
    dragging: false,
    pointerX: 0,
    pointerY: 0
  };

  svg.style.transformOrigin = "0 0";
  svg.style.willChange = "transform";

  function syncUi() {
    svg.style.transform = `translate(${state.offsetX}px, ${state.offsetY}px) scale(${state.scale})`;
    if (zoomValueEl) {
      zoomValueEl.textContent = formatZoom(state.scale);
    }
  }

  function zoomAt(clientX, clientY, multiplier) {
    const nextScale = clampScale(state.scale * multiplier);
    if (nextScale === state.scale) return;

    const rect = viewport.getBoundingClientRect();
    const pointX = clientX - rect.left;
    const pointY = clientY - rect.top;
    const worldX = (pointX - state.offsetX) / state.scale;
    const worldY = (pointY - state.offsetY) / state.scale;

    state.scale = nextScale;
    state.offsetX = pointX - worldX * state.scale;
    state.offsetY = pointY - worldY * state.scale;
    syncUi();
  }

  function zoomCentered(multiplier) {
    const rect = viewport.getBoundingClientRect();
    zoomAt(rect.left + rect.width / 2, rect.top + rect.height / 2, multiplier);
  }

  function resetView() {
    state.scale = 1;
    state.offsetX = 0;
    state.offsetY = 0;
    syncUi();
  }

  function updateFullscreenButton() {
    if (!fullscreenBtn) return;
    fullscreenBtn.textContent =
      document.fullscreenElement === card ? "Exit Fullscreen" : "Fullscreen";
  }

  viewport.addEventListener(
    "wheel",
    (event) => {
      event.preventDefault();
      zoomAt(event.clientX, event.clientY, event.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP);
    },
    { passive: false }
  );

  viewport.addEventListener("pointerdown", (event) => {
    if (event.button !== 0) return;
    state.dragging = true;
    state.pointerX = event.clientX;
    state.pointerY = event.clientY;
    viewport.classList.add("dragging");
    if (viewport.setPointerCapture) {
      viewport.setPointerCapture(event.pointerId);
    }
  });

  viewport.addEventListener("pointermove", (event) => {
    if (!state.dragging) return;
    const dx = event.clientX - state.pointerX;
    const dy = event.clientY - state.pointerY;
    state.pointerX = event.clientX;
    state.pointerY = event.clientY;
    state.offsetX += dx;
    state.offsetY += dy;
    syncUi();
  });

  function stopDragging(event) {
    if (!state.dragging) return;
    state.dragging = false;
    viewport.classList.remove("dragging");
    if (event?.pointerId != null && viewport.releasePointerCapture) {
      try {
        viewport.releasePointerCapture(event.pointerId);
      } catch (_) {}
    }
  }

  viewport.addEventListener("pointerup", stopDragging);
  viewport.addEventListener("pointercancel", stopDragging);
  viewport.addEventListener("pointerleave", stopDragging);

  viewport.addEventListener("keydown", (event) => {
    if (event.key === "+" || event.key === "=") {
      event.preventDefault();
      zoomCentered(ZOOM_STEP);
      return;
    }
    if (event.key === "-") {
      event.preventDefault();
      zoomCentered(1 / ZOOM_STEP);
      return;
    }
    if (event.key === "0") {
      event.preventDefault();
      resetView();
      return;
    }
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      state.offsetX += PAN_STEP_PX;
      syncUi();
      return;
    }
    if (event.key === "ArrowRight") {
      event.preventDefault();
      state.offsetX -= PAN_STEP_PX;
      syncUi();
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      state.offsetY += PAN_STEP_PX;
      syncUi();
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      state.offsetY -= PAN_STEP_PX;
      syncUi();
    }
  });

  zoomInBtn?.addEventListener("click", () => zoomCentered(ZOOM_STEP));
  zoomOutBtn?.addEventListener("click", () => zoomCentered(1 / ZOOM_STEP));
  resetBtn?.addEventListener("click", () => resetView());

  fullscreenBtn?.addEventListener("click", async () => {
    if (document.fullscreenElement === card) {
      await document.exitFullscreen();
      return;
    }
    await card.requestFullscreen();
  });

  document.addEventListener("fullscreenchange", updateFullscreenButton);
  syncUi();
  updateFullscreenButton();
}

async function renderDiagrams() {
  renderLinkBar();

  const results = await Promise.allSettled(
    diagramCatalog.map((diagram) => loadDiagramSource(diagram))
  );

  const fragment = document.createDocumentFragment();
  let loadedCount = 0;

  results.forEach((result, index) => {
    const diagram = diagramCatalog[index];
    if (result.status === "fulfilled") {
      loadedCount += 1;
      fragment.appendChild(createDiagramCard(diagram, result.value));
      return;
    }
    fragment.appendChild(createErrorCard(diagram, result.reason?.message ?? "Unknown error"));
  });

  listEl.replaceChildren(fragment);

  mermaid.initialize({
    startOnLoad: false,
    theme: "neutral",
    securityLevel: "strict"
  });

  await mermaid.run({ querySelector: ".mermaid" });
  Array.from(listEl.querySelectorAll(".card[data-diagram='true']")).forEach((card) => {
    attachInteractionHandlers(card);
  });
  setStatus(`Loaded ${loadedCount}/${diagramCatalog.length} diagrams.`);
}

renderDiagrams().catch((error) => {
  setStatus(`Render failed: ${error.message}`, true);
});
