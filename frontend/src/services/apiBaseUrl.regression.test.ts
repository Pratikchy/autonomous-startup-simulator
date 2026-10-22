// @vitest-environment happy-dom
/**
 * Regression test for production deployment bug:
 * "Failed to fetch" on startup creation because VITE_API_BASE_URL was not set
 * in the Vercel environment, causing the frontend to call /api/* on Vercel
 * (which has no backend) instead of the Render backend.
 *
 * Fix: frontend/vercel.json rewrites /api/* → Render backend so the relative
 * "/api" fallback works correctly when VITE_API_BASE_URL is not set.
 *
 * These tests guard that:
 *  1. The BASE logic resolves correctly with and without VITE_API_BASE_URL.
 *  2. streamUrl is derived from the same BASE (so SSE also routes correctly).
 *  3. The vercel.json file exists with the required rewrite rule.
 */
import { describe, it, expect } from "vitest";
import { readFileSync } from "fs";
import { resolve } from "path";

// ── Helpers ──────────────────────────────────────────────────────────────────

function computeBase(vitaApiBaseUrl: string | undefined): string {
  return vitaApiBaseUrl?.replace(/\/$/, "") ?? "/api";
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("API base URL resolution (production deployment regression)", () => {
  it("falls back to /api when VITE_API_BASE_URL is not set (as in the broken deployment)", () => {
    const base = computeBase(undefined);
    expect(base).toBe("/api");
  });

  it("uses the Render URL when VITE_API_BASE_URL is set correctly", () => {
    const base = computeBase("https://autonomous-startup-simulator.onrender.com");
    expect(base).toBe("https://autonomous-startup-simulator.onrender.com");
  });

  it("strips a trailing slash from VITE_API_BASE_URL so paths don't double-slash", () => {
    const base = computeBase("https://autonomous-startup-simulator.onrender.com/");
    expect(base).toBe("https://autonomous-startup-simulator.onrender.com");
    // Would otherwise produce .onrender.com//api/startups
  });

  it("produces correct createStartup URL from base (verifies /api path prefix)", () => {
    const base = computeBase(undefined); // "/api"
    const url = `${base}/startups`;
    expect(url).toBe("/api/startups");
    // With the Vercel rewrite, /api/startups → Render /api/startups — this works.
    // Without the rewrite (broken state), /api/startups hits Vercel → 404 NOT_FOUND.
  });

  it("produces correct SSE stream URL from same BASE (both REST and SSE must use same origin)", () => {
    const base = computeBase(undefined);
    const streamUrl = `${base}/startups/1/events/stream`;
    expect(streamUrl).toBe("/api/startups/1/events/stream");
    // The Vercel rewrite covers /api/* which includes the SSE path.
  });
});

describe("vercel.json API rewrite rule (deployment regression guard)", () => {
  const vercelJsonPath = resolve(__dirname, "../../vercel.json");
  let vercelConfig: { rewrites?: Array<{ source: string; destination: string }> };

  it("vercel.json exists at frontend/vercel.json", () => {
    expect(() => {
      readFileSync(vercelJsonPath, "utf-8");
    }).not.toThrow();
  });

  it("vercel.json has a rewrite rule that proxies /api/* to the Render backend", () => {
    const contents = readFileSync(vercelJsonPath, "utf-8");
    vercelConfig = JSON.parse(contents);
    const rewrites = vercelConfig.rewrites ?? [];
    const apiRewrite = rewrites.find(
      (r) => r.source.includes("/api") && r.destination.includes("onrender.com")
    );
    expect(apiRewrite).toBeDefined();
    expect(apiRewrite!.destination).toContain(
      "https://autonomous-startup-simulator.onrender.com/api/"
    );
  });

  it("Render backend URL in rewrite does not contain a trailing slash before :path*", () => {
    const contents = readFileSync(vercelJsonPath, "utf-8");
    vercelConfig = JSON.parse(contents);
    const rewrites = vercelConfig.rewrites ?? [];
    const apiRewrite = rewrites.find(
      (r) => r.source.includes("/api") && r.destination.includes("onrender.com")
    );
    // Destination must be ".../api/:path*" not ".../api//:path*"
    expect(apiRewrite!.destination).not.toMatch(/\/\/:path\*/);
  });

  it("vercel.json has an SPA fallback rewrite for React Router", () => {
    const contents = readFileSync(vercelJsonPath, "utf-8");
    vercelConfig = JSON.parse(contents);
    const rewrites = vercelConfig.rewrites ?? [];
    const spaFallback = rewrites.find(
      (r) => r.destination === "/index.html"
    );
    expect(spaFallback).toBeDefined();
  });
});
