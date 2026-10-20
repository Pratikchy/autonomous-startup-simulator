import { useEffect, useRef, useState } from "react";
import { streamUrl } from "../services/api";
import type { StartupEvent } from "../types";

export interface UseEventStreamOptions {
  onReconnect?: () => void | Promise<void>;
}

/**
 * Subscribes to the backend SSE stream for a startup and invokes `onEvent` for
 * every `startup-event`. Reconnects are handled by the browser's EventSource.
 * When a genuine reconnection occurs (after an error or disconnection),
 * `onReconnect` is invoked to rehydrate authoritative startup snapshot state.
 */
export function useEventStream(
  startupId: number | null,
  onEvent: (event: StartupEvent) => void,
  onReconnectOrOptions?: (() => void | Promise<void>) | UseEventStreamOptions
) {
  const onReconnect =
    typeof onReconnectOrOptions === "function"
      ? onReconnectOrOptions
      : onReconnectOrOptions?.onReconnect;

  const [connected, setConnected] = useState(false);
  const handlerRef = useRef(onEvent);
  handlerRef.current = onEvent;

  const reconnectHandlerRef = useRef(onReconnect);
  reconnectHandlerRef.current = onReconnect;

  useEffect(() => {
    if (startupId == null) return;

    let isStale = false;
    let hasConnectedOnce = false;
    let hasDisconnected = false;
    let lastReconnectTime = 0;
    let isRehydrating = false;

    const es = new EventSource(streamUrl(startupId));

    const handleConnected = () => {
      if (isStale) return;
      setConnected(true);

      // A genuine reconnection occurs when the stream had connected previously
      // and subsequently experienced a disconnection or error.
      if (hasConnectedOnce && hasDisconnected) {
        hasDisconnected = false;
        const now = Date.now();
        // Debounce / throttle reconnection rehydration to prevent loop storms (min 1 sec)
        if (!isRehydrating && now - lastReconnectTime > 1000) {
          lastReconnectTime = now;
          isRehydrating = true;
          try {
            const result = reconnectHandlerRef.current?.();
            if (result && typeof (result as Promise<void>).then === "function") {
              (result as Promise<void>).finally(() => {
                isRehydrating = false;
              });
            } else {
              isRehydrating = false;
            }
          } catch {
            isRehydrating = false;
          }
        }
      } else {
        hasConnectedOnce = true;
      }
    };

    const handleStartupEvent = (e: MessageEvent) => {
      if (isStale) return;
      try {
        const data = JSON.parse(e.data) as StartupEvent;
        handlerRef.current(data);
      } catch {
        /* ignore malformed frame */
      }
    };

    const handleError = () => {
      if (isStale) return;
      setConnected(false);
      hasDisconnected = true;
    };

    es.addEventListener("connected", handleConnected);
    es.addEventListener("startup-event", handleStartupEvent as EventListener);
    es.onerror = handleError;

    return () => {
      isStale = true;
      es.removeEventListener("connected", handleConnected);
      es.removeEventListener("startup-event", handleStartupEvent as EventListener);
      es.onerror = null;
      es.close();
      setConnected(false);
    };
  }, [startupId]);

  return { connected };
}
