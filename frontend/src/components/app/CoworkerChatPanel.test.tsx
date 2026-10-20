// @vitest-environment happy-dom
// @ts-expect-error React 18 testing flag
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import CoworkerChatPanel from "./CoworkerChatPanel";
import { api } from "../../services/api";
import type { CoworkerChatMessage, AppChangeRequest } from "../../types";

const mockChangeRequest: AppChangeRequest = {
  id: 101,
  startupId: 2,
  agentType: "DEVELOPMENT",
  agentName: "Alex Rivera (Lead Engineer)",
  userPrompt: "Add AI product scanner",
  status: "PROPOSED",
  planSummary: "Implemented responsive barcode and edge AI scanner inside App.tsx.",
  proposedChanges: [
    {
      relativePath: "src/App.tsx",
      action: "MODIFY",
      description: "Added AI Scanner camera overlay and confidence score widget",
      content: "export default function App() {}",
    },
  ],
  buildOutput: null,
  errorMessage: null,
  createdAt: "2026-10-19T12:00:00Z",
  updatedAt: "2026-10-19T12:00:00Z",
};

const mockChatMessages: CoworkerChatMessage[] = [
  {
    id: 1,
    startupId: 2,
    role: "USER",
    agentType: null,
    agentName: null,
    content: "Please add an AI product scanner to our app",
    changeRequestId: null,
    createdAt: "2026-10-19T12:00:00Z",
  },
  {
    id: 2,
    startupId: 2,
    role: "ASSISTANT",
    agentType: "DEVELOPMENT",
    agentName: "Alex Rivera",
    content: "I inspected your workspace and drafted the requested AI Scanner.",
    changeRequestId: 101,
    changeRequest: mockChangeRequest,
    createdAt: "2026-10-19T12:00:05Z",
  },
];

let cleanupFns: Array<() => void> = [];

async function renderAsync(ui: React.ReactElement) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);

  await act(async () => {
    root.render(ui);
  });

  // Flush pending microtasks so async fetch resolves
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
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
    get html() {
      return container.innerHTML;
    },
  };
}

describe("CoworkerChatPanel Component", () => {
  beforeEach(() => {
    cleanupFns = [];
    document.body.innerHTML = "";
    vi.restoreAllMocks();

    window.HTMLElement.prototype.scrollIntoView = vi.fn();

    vi.spyOn(api, "getCoworkerChat").mockImplementation(() => Promise.resolve([...mockChatMessages]));
    vi.spyOn(api, "sendCoworkerChatMessage").mockImplementation(() =>
      Promise.resolve({
        id: 3,
        startupId: 2,
        role: "ASSISTANT",
        agentType: "DEVELOPMENT",
        agentName: "Alex Rivera",
        content: "Plan created.",
        changeRequestId: null,
        createdAt: "2026-10-19T12:01:00Z",
      })
    );
    vi.spyOn(api, "approveAppChangeRequest").mockImplementation(() =>
      Promise.resolve({
        ...mockChangeRequest,
        status: "SUCCEEDED",
        buildOutput: "vite v5.4.10 built in 1.1s",
      })
    );
    vi.spyOn(api, "rejectAppChangeRequest").mockImplementation(() =>
      Promise.resolve({
        ...mockChangeRequest,
        status: "REJECTED",
      })
    );
  });

  afterEach(() => {
    cleanupFns.forEach((fn) => fn());
    cleanupFns = [];
  });

  it("fetches chat history scoped to the given startupId on mount", async () => {
    await renderAsync(<CoworkerChatPanel startupId={2} isAppGenerated={true} />);
    expect(api.getCoworkerChat).toHaveBeenCalledWith(2);
  });

  it("renders messages with agent attribution and workspace isolation notice", async () => {
    const rendered = await renderAsync(<CoworkerChatPanel startupId={2} isAppGenerated={true} />);

    expect(rendered.html).toContain("Alex Rivera");
    expect(rendered.html).toContain("Please add an AI product scanner to our app");
    expect(rendered.html).toContain("Isolated in ./workspaces/startup-2/");
    expect(rendered.html).toContain("Approval Required");
  });

  it("renders proposed file modifications and requires user approval", async () => {
    const rendered = await renderAsync(<CoworkerChatPanel startupId={2} isAppGenerated={true} />);

    expect(rendered.container.textContent).toContain("Change Plan #101");
    expect(rendered.container.textContent).toContain("Awaiting User Approval");
    expect(rendered.container.textContent).toContain("src/App.tsx");
    expect(rendered.container.textContent).toContain("MODIFY");
    expect(rendered.container.textContent).toContain("Approve & Apply Changes");
    expect(rendered.container.textContent).toContain("Reject");
  });

  it("calls approveAppChangeRequest when clicking Approve", async () => {
    const onAppUpdated = vi.fn();
    const rendered = await renderAsync(
      <CoworkerChatPanel startupId={2} isAppGenerated={true} onAppUpdated={onAppUpdated} />
    );

    const approveBtn = Array.from(rendered.container.querySelectorAll("button")).find((b) =>
      b.textContent?.includes("Approve & Apply Changes")
    );
    expect(approveBtn).toBeDefined();

    await act(async () => {
      approveBtn?.click();
    });

    expect(api.approveAppChangeRequest).toHaveBeenCalledWith(2, 101);
    expect(onAppUpdated).toHaveBeenCalled();
  });

  it("calls rejectAppChangeRequest when clicking Reject", async () => {
    const rendered = await renderAsync(<CoworkerChatPanel startupId={2} isAppGenerated={true} />);

    const rejectBtn = Array.from(rendered.container.querySelectorAll("button")).find((b) =>
      b.textContent?.trim() === "Reject"
    );
    expect(rejectBtn).toBeDefined();

    await act(async () => {
      rejectBtn?.click();
    });

    expect(api.rejectAppChangeRequest).toHaveBeenCalledWith(2, 101);
  });
});
