// @vitest-environment happy-dom
/**
 * Regression test for production deployment bug:
 *
 * Problem:
 * When VITE_API_BASE_URL was configured in Vercel to point directly to Render
 * (https://autonomous-startup-simulator.onrender.com), Vite inlined that direct
 * backend URL into the production bundle.
 * When users accessed preview deployments (e.g. https://*-projects.vercel.app),
 * the browser made direct cross-origin requests to Render, which blocked them
 * with a CORS error because only the primary production origin was allowed.
 *
 * Solution:
 * All browser-side API requests and SSE streams use the same-origin relative "/api"
 * path in both production and development:
 *  - Production (Vercel): frontend/vercel.json rewrites /api/* -> Render backend.
 *    Traffic is proxied server-to-server, so the browser sees a same-origin call
 *    with zero CORS restrictions on ANY Vercel preview or production domain.
 *  - Development (Vite): vite.config.ts proxies /api -> http://127.0.0.1:8080.
 *
 * This test suite guards that:
 *  1. BASE in api.ts is always "/api" (immune to VITE_API_BASE_URL in production).
 *  2. streamUrl uses the same relative "/api" base.
 *  3. frontend/vercel.json has the exact required rewrite rule to proxy /api/* to Render.
 *  4. frontend/vercel.json has the SPA fallback for client-side routing.
 */
import { describe, it, expect } from "vitest";
import { readFileSync } from "fs";
import { resolve } from "path";
import { BASE, streamUrl } from "./api";

describe("API base URL resolution (proxy preservation & CORS prevention)", () => {
  it("BASE is strictly same-origin '/api' to ensure all calls route via Vercel proxy", () => {
    expect(BASE).toBe("/api");
  });

  it("streamUrl derives from BASE and uses same-origin relative path", () => {
    expect(streamUrl(42)).toBe("/api/startups/42/events/stream");
    expect(streamUrl(1)).toMatch(/^\/api\/startups\/1\/events\/stream$/);
  });

  it("BASE does not contain any absolute URL or protocol prefix", () => {
    expect(BASE).not.toMatch(/^https?:\/\//);
    expect(BASE.startsWith("/")).toBe(true);
  });

  it("streamUrl does not route directly to Render", () => {
    const url = streamUrl(10);
    expect(url).not.toContain("onrender.com");
    expect(url.startsWith("/api/")).toBe(true);
  });
});

describe("vercel.json API rewrite rule (deployment proxy guard)", () => {
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
    expect(apiRewrite!.destination).toBe(
      "https://autonomous-startup-simulator.onrender.com/api/:path*"
    );
  });

  it("Render backend URL in rewrite does not contain double slashes before :path*", () => {
    const contents = readFileSync(vercelJsonPath, "utf-8");
    vercelConfig = JSON.parse(contents);
    const rewrites = vercelConfig.rewrites ?? [];
    const apiRewrite = rewrites.find(
      (r) => r.source.includes("/api") && r.destination.includes("onrender.com")
    );
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
