// @vitest-environment happy-dom
// @ts-expect-error React 18 testing flag
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { useEventStream } from "./useEventStream";
import type { StartupEvent } from "../types";

class MockEventSource {
  static instances: MockEventSource[] = [];
  url: string;
  listeners: Record<string, ((e: any) => void)[]> = {};
  onerror: ((e: any) => void) | null = null;
  closed = false;

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
  }

  addEventListener(event: string, callback: (e: any) => void) {
    if (!this.listeners[event]) this.listeners[event] = [];
    this.listeners[event].push(callback);
  }

  removeEventListener(event: string, callback: (e: any) => void) {
    if (this.listeners[event]) {
      this.listeners[event] = this.listeners[event].filter((cb) => cb !== callback);
    }
  }

  simulateConnected() {
    this.listeners["connected"]?.forEach((cb) => cb({ type: "connected" }));
  }

  simulateEvent(eventData: any) {
    this.listeners["startup-event"]?.forEach((cb) =>
      cb({ data: JSON.stringify(eventData) } as MessageEvent)
    );
  }

  simulateError() {
    if (this.onerror) this.onerror(new Event("error"));
  }

  close() {
    this.closed = true;
  }
}

describe("useEventStream hook", () => {
  let originalEventSource: any;
  let container: HTMLDivElement;
  let root: any;

  beforeEach(() => {
    MockEventSource.instances = [];
    originalEventSource = (globalThis as any).EventSource;
    (globalThis as any).EventSource = MockEventSource;
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => {
      root.unmount();
    });
    container.remove();
    (globalThis as any).EventSource = originalEventSource;
    vi.restoreAllMocks();
  });

  function TestHarness({
    startupId,
    onEvent,
    onReconnect,
  }: {
    startupId: number | null;
    onEvent: (e: StartupEvent) => void;
    onReconnect?: () => void;
  }) {
    const { connected } = useEventStream(startupId, onEvent, onReconnect);
    return <div data-testid="status">{connected ? "CONNECTED" : "DISCONNECTED"}</div>;
  }

  it("subscribes with correct URL and sets connected state without triggering onReconnect initially", () => {
    const onEvent = vi.fn();
    const onReconnect = vi.fn();

    act(() => {
      root.render(
        <TestHarness startupId={2} onEvent={onEvent} onReconnect={onReconnect} />
      );
    });

    expect(MockEventSource.instances.length).toBe(1);
    const es = MockEventSource.instances[0];
    expect(es.url).toContain("/api/startups/2/events/stream");
    expect(container.textContent).toContain("DISCONNECTED");

    // Simulate initial connection
    act(() => {
      es.simulateConnected();
    });

    expect(container.textContent).toContain("CONNECTED");
    // Initial connect must not call onReconnect
    expect(onReconnect).not.toHaveBeenCalled();
  });

  it("dispatches startup-event messages to the onEvent callback", () => {
    const onEvent = vi.fn();

    act(() => {
      root.render(<TestHarness startupId={2} onEvent={onEvent} />);
    });

    const es = MockEventSource.instances[0];
    const sampleEvent: Partial<StartupEvent> = {
      id: 201,
      type: "TASK_PROGRESS",
      message: "Progress update",
    };

    act(() => {
      es.simulateConnected();
      es.simulateEvent(sampleEvent);
    });

    expect(onEvent).toHaveBeenCalledTimes(1);
    expect(onEvent).toHaveBeenCalledWith(sampleEvent);
  });

  it("handles disconnection and triggers rehydration on a genuine subsequent reconnection", () => {
    const onEvent = vi.fn();
    const onReconnect = vi.fn();

    act(() => {
      root.render(
        <TestHarness startupId={2} onEvent={onEvent} onReconnect={onReconnect} />
      );
    });

    const es = MockEventSource.instances[0];

    // 1. Initial connection
    act(() => {
      es.simulateConnected();
    });
    expect(container.textContent).toContain("CONNECTED");
    expect(onReconnect).not.toHaveBeenCalled();

    // 2. Disconnection / error
    act(() => {
      es.simulateError();
    });
    expect(container.textContent).toContain("DISCONNECTED");
    expect(onReconnect).not.toHaveBeenCalled();

    // 3. Genuine Reconnection
    act(() => {
      es.simulateConnected();
    });
    expect(container.textContent).toContain("CONNECTED");
    expect(onReconnect).toHaveBeenCalledTimes(1);
  });

  it("throttles rehydration calls to prevent storm loops on rapid reconnect flapping", () => {
    const onEvent = vi.fn();
    const onReconnect = vi.fn();

    act(() => {
      root.render(
        <TestHarness startupId={2} onEvent={onEvent} onReconnect={onReconnect} />
      );
    });

    const es = MockEventSource.instances[0];

    // Initial connection
    act(() => {
      es.simulateConnected();
    });

    // Disconnect -> Reconnect (Call 1)
    act(() => {
      es.simulateError();
      es.simulateConnected();
    });
    expect(onReconnect).toHaveBeenCalledTimes(1);

    // Immediate second flap within throttle window (less than 1s)
    act(() => {
      es.simulateError();
      es.simulateConnected();
    });
    // Should still be 1 due to throttling guard
    expect(onReconnect).toHaveBeenCalledTimes(1);
  });

  it("cleans up listeners and closes connection on unmount, ignoring stale events", () => {
    const onEvent = vi.fn();
    const onReconnect = vi.fn();

    act(() => {
      root.render(
        <TestHarness startupId={2} onEvent={onEvent} onReconnect={onReconnect} />
      );
    });

    const es = MockEventSource.instances[0];
    act(() => {
      es.simulateConnected();
    });

    // Unmount
    act(() => {
      root.unmount();
    });

    expect(es.closed).toBe(true);

    // Further events on stale instance should be ignored
    act(() => {
      es.simulateError();
      es.simulateConnected();
      es.simulateEvent({ id: 999 });
    });

    expect(onEvent).not.toHaveBeenCalled();
    expect(onReconnect).not.toHaveBeenCalled();
  });
});
