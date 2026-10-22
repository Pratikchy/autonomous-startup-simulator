// @vitest-environment happy-dom
// @ts-expect-error React 18 testing flag
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import GeneratedAppModal from "./GeneratedAppModal";
import { useSim } from "../../store/simulationStore";
import type { GeneratedApp, GeneratedFileInfo, GeneratedFileContent, Startup } from "../../types";

const mockStartup: Startup = {
  id: 2,
  name: "NPWR Automation",
  originalIdea: "Autonomous clean energy scheduler",
  currentPhase: "EXECUTION",
  problem: "High peak grid charges",
  solution: "AI battery optimization",
  targetAudience: "Industrial plants",
  valueProposition: "Cut power peak spend by 35%",
  businessModel: "SaaS + Savings share",
  constraints: "Strict ISO compliance",
  executiveSummary: "Scaling autonomous energy management",
  strategicObjectives: ["Deploy MVP grid agent"],
  assumptions: ["Real-time meters available"],
  mvpDirection: "Web dashboard + control API",
  ceoAnalysisProvider: "LLM",
  ceoAnalysisFailed: false,
  ceoAnalysisError: null,
  health: {
    overallProgress: 45.0,
    mvpProgress: 40.0,
    technicalFeasibility: 85,
    marketReadiness: 70,
    financialHealth: 88,
    budgetRemaining: 120000,
    runwayMonths: 14,
  },
  simulationStarted: true,
  simulationCompleted: true,
  createdAt: "2026-10-18T10:00:00Z",
  updatedAt: "2026-10-19T10:00:00Z",
};

const mockGeneratedApp: GeneratedApp = {
  id: 1,
  startupId: 2,
  appName: "NPWR Automation",
  template: "REACT_VITE_TS",
  status: "READY",
  buildStatus: "SUCCESS",
  buildOutput: "vite v5.4.10 building for production...\n✓ 42 modules transformed.\ndist/index.html 0.45 kB\ndist/assets/index.js 142 kB\n✓ built in 1.25s",
  previewPort: 5174,
  previewUrl: "/api/startups/2/app/preview/",
  createdAt: "2026-10-19T11:00:00Z",
  updatedAt: "2026-10-19T11:05:00Z",
};

const mockFiles: GeneratedFileInfo[] = [
  { path: "package.json", name: "package.json", sizeBytes: 520, isDirectory: false, fileType: "json" },
  { path: "vite.config.ts", name: "vite.config.ts", sizeBytes: 340, isDirectory: false, fileType: "typescript" },
  { path: "src/App.tsx", name: "App.tsx", sizeBytes: 2450, isDirectory: false, fileType: "typescript-react" },
  { path: "src/index.css", name: "index.css", sizeBytes: 680, isDirectory: false, fileType: "css" },
  { path: "README.md", name: "README.md", sizeBytes: 1100, isDirectory: false, fileType: "markdown" },
];

const mockSelectedContent: GeneratedFileContent = {
  path: "src/App.tsx",
  name: "App.tsx",
  content: "import React from 'react';\nexport default function App() {\n  return <h1>NPWR Automation MVP</h1>;\n}",
  sizeBytes: 120,
};

let cleanupFns: Array<() => void> = [];

function render(ui: React.ReactElement) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(ui);
  });
  const cleanup = () => {
    act(() => {
      root.unmount();
    });
    container.remove();
  };
  cleanupFns.push(cleanup);
  return {
    container,
    html: container.innerHTML,
  };
}

describe("GeneratedAppModal Component", () => {
  beforeEach(() => {
    cleanupFns = [];
    document.body.innerHTML = "";
    useSim.getState().reset();

    useSim.setState({
      startup: mockStartup,
      generatedApp: mockGeneratedApp,
      generatedFiles: mockFiles,
      selectedFileContent: mockSelectedContent,
      loadGeneratedApp: vi.fn().mockResolvedValue(undefined),
      loadGeneratedFiles: vi.fn().mockResolvedValue(undefined),
      selectGeneratedFile: vi.fn().mockResolvedValue(undefined),
      generateApp: vi.fn().mockResolvedValue(undefined),
      buildGeneratedApp: vi.fn().mockResolvedValue(undefined),
      startPreview: vi.fn().mockResolvedValue(undefined),
      stopPreview: vi.fn().mockResolvedValue(undefined),
    });
  });

  afterEach(() => {
    cleanupFns.forEach((fn) => fn());
    cleanupFns = [];
  });

  it("renders modal header with venture name and workspace isolation notice", () => {
    const onClose = vi.fn();
    const { html } = render(<GeneratedAppModal onClose={onClose} />);

    expect(html).toContain("Generated Web Application");
    expect(html).toContain("NPWR Automation");
    expect(html).toContain("#2");
    expect(html).toContain("ISOLATED WORKSPACE:");
    expect(html).toContain("./workspaces/startup-2/");
    expect(html).toContain("Production Ready");
  });

  it("renders generated project file tree in the left explorer", () => {
    const onClose = vi.fn();
    const { html } = render(<GeneratedAppModal onClose={onClose} />);

    expect(html).toContain("package.json");
    expect(html).toContain("vite.config.ts");
    expect(html).toContain("src/App.tsx");
    expect(html).toContain("src/index.css");
    expect(html).toContain("README.md");
  });

  it("displays source code of the selected file in the code tab", () => {
    const onClose = vi.fn();
    const { html } = render(<GeneratedAppModal onClose={onClose} />);

    expect(html).toContain("NPWR Automation MVP");
    expect(html).toContain("App.tsx");
  });

  it("switches to Build Output tab and shows build logs", () => {
    const onClose = vi.fn();
    const { container } = render(<GeneratedAppModal onClose={onClose} />);

    const buildTab = container.querySelector('[data-testid="tab-view-build"]') as HTMLButtonElement;
    expect(buildTab).not.toBeNull();

    act(() => {
      buildTab.click();
    });

    const html = container.innerHTML;
    expect(html).toContain("Build Status:");
    expect(html).toContain("SUCCESS");
    expect(html).toContain("vite v5.4.10 building for production");
    expect(html).toContain("built in 1.25s");
  });

  it("switches to Live Preview tab and shows embedded preview when previewUrl exists", () => {
    const onClose = vi.fn();
    const { container } = render(<GeneratedAppModal onClose={onClose} />);

    const previewTab = container.querySelector('[data-testid="tab-view-preview"]') as HTMLButtonElement;
    expect(previewTab).not.toBeNull();

    act(() => {
      previewTab.click();
    });

    const html = container.innerHTML;
    expect(html).toContain("/api/startups/2/app/preview/");
    expect(html).toContain("Open in New Tab");
    const iframe = container.querySelector('iframe');
    expect(iframe).not.toBeNull();
    expect(iframe?.getAttribute("src")).toBe("/api/startups/2/app/preview/");
  });

  it("invokes generateApp when clicking Re-generate App", () => {
    const generateSpy = vi.fn().mockResolvedValue(undefined);
    useSim.setState({ generateApp: generateSpy });

    const onClose = vi.fn();
    const { container } = render(<GeneratedAppModal onClose={onClose} />);

    const generateBtn = container.querySelector('[data-testid="generate-app-btn"]') as HTMLButtonElement;
    expect(generateBtn).not.toBeNull();

    act(() => {
      generateBtn.click();
    });

    expect(generateSpy).toHaveBeenCalledTimes(1);
  });

  it("switches to Coworker Chat tab when clicked", () => {
    const onClose = vi.fn();
    const { container } = render(<GeneratedAppModal onClose={onClose} />);

    const chatTab = container.querySelector('[data-testid="tab-view-chat"]') as HTMLButtonElement;
    expect(chatTab).not.toBeNull();

    act(() => {
      chatTab.click();
    });

    const html = container.innerHTML;
    expect(html).toContain("Target Agent:");
    expect(html).toContain("Approval Required");
  });

  it("invokes onClose when clicking close button", () => {
    const onClose = vi.fn();
    const { container } = render(<GeneratedAppModal onClose={onClose} />);

    const closeBtn = container.querySelector('[data-testid="close-app-modal"]') as HTMLButtonElement;
    expect(closeBtn).not.toBeNull();

    act(() => {
      closeBtn.click();
    });

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
