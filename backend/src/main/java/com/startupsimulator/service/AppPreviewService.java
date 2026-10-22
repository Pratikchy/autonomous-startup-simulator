package com.startupsimulator.service;

import com.startupsimulator.model.GeneratedApp;
import com.startupsimulator.repository.GeneratedAppRepository;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages isolated live preview servers for generated startup applications.
 * Tracks child processes, manages dynamic ports, reverse-proxies preview requests,
 * and guarantees graceful process cleanup and workspace isolation.
 */
@Service
public class AppPreviewService {

    private static final Logger log = LoggerFactory.getLogger(AppPreviewService.class);

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host"
    );

    private final AppWorkspaceService workspaceService;
    private final AppBuildService buildService;
    private final GeneratedAppRepository appRepository;
    private final Path frontendNodeModules;
    private final String previewBaseUrl;

    private final Map<Long, Process> activeProcesses = new ConcurrentHashMap<>();
    private final Map<Long, Integer> activePorts = new ConcurrentHashMap<>();
    private final HttpClient httpClient;

    public AppPreviewService(
            AppWorkspaceService workspaceService,
            AppBuildService buildService,
            GeneratedAppRepository appRepository,
            @Value("${app.frontend.node-modules:../frontend/node_modules}") String nodeModulesPath,
            @Value("${app.preview.base-url:}") String previewBaseUrl) {
        this.workspaceService = workspaceService;
        this.buildService = buildService;
        this.appRepository = appRepository;
        this.frontendNodeModules = Paths.get(nodeModulesPath).toAbsolutePath().normalize();
        this.previewBaseUrl = previewBaseUrl != null ? previewBaseUrl.trim() : "";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Transactional
    public GeneratedApp startPreview(Long startupId) throws IOException {
        GeneratedApp app = appRepository.findByStartupId(startupId)
                .orElseThrow(() -> new IllegalStateException("App not generated for startup " + startupId));

        // If preview already running and responding, return it (idempotent)
        Process existing = activeProcesses.get(startupId);
        Integer existingPort = activePorts.get(startupId);
        if (existing != null && existing.isAlive() && existingPort != null) {
            if (isPortResponding(existingPort)) {
                String previewUrl = buildPreviewUrl(startupId);
                app.setPreviewPort(existingPort);
                app.setPreviewUrl(previewUrl);
                return appRepository.save(app);
            } else {
                log.warn("Existing preview process for startup {} on port {} not responding. Restarting...", startupId, existingPort);
                stopPreview(startupId);
            }
        }

        Path workspace = workspaceService.getStartupWorkspace(startupId);

        // Ensure app is built first with relative base
        if (!"SUCCESS".equals(app.getBuildStatus()) || !Files.exists(workspace.resolve("dist/index.html"))) {
            log.info("Building startup {} app before starting preview...", startupId);
            app = buildService.buildApp(startupId);
            if (!"SUCCESS".equals(app.getBuildStatus())) {
                throw new IllegalStateException("Cannot start preview: Build failed. Check build logs in the Build Output tab.");
            }
        }

        int port = findAvailablePort();
        Path viteBinary = workspace.resolve("node_modules/vite/bin/vite.js");
        if (!Files.exists(viteBinary) && Files.exists(frontendNodeModules)) {
            viteBinary = frontendNodeModules.resolve("vite/bin/vite.js");
        }

        List<String> command;
        if (Files.exists(viteBinary)) {
            command = List.of("node", viteBinary.toString(), "preview", "--port", String.valueOf(port), "--host", "127.0.0.1", "--base", "./");
        } else {
            command = List.of("npm", "run", "preview", "--", "--port", String.valueOf(port), "--host", "127.0.0.1", "--base", "./");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workspace.toFile());
        pb.redirectErrorStream(true);

        log.info("Starting preview for startup {} on port {}: {}", startupId, port, command);
        Process process = pb.start();

        activeProcesses.put(startupId, process);
        activePorts.put(startupId, port);

        // Watch for unexpected exit
        process.onExit().thenAccept(p -> {
            log.warn("Preview process for startup {} exited with code {}", startupId, p.exitValue());
            activeProcesses.remove(startupId);
            activePorts.remove(startupId);
        });

        // Actively check server readiness via HTTP before marking ready
        boolean ready = waitForServerReady(port, process, 10000);
        if (!ready) {
            stopPreview(startupId);
            throw new IllegalStateException("Preview server on port " + port + " failed to start or respond within 10 seconds.");
        }

        String previewUrl = buildPreviewUrl(startupId);
        app.setPreviewPort(port);
        app.setPreviewUrl(previewUrl);
        app.setUpdatedAt(Instant.now());

        return appRepository.save(app);
    }

    @Transactional
    public GeneratedApp stopPreview(Long startupId) {
        GeneratedApp app = appRepository.findByStartupId(startupId).orElse(null);

        Process process = activeProcesses.remove(startupId);
        activePorts.remove(startupId);

        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            log.info("Stopped preview process for startup {}", startupId);
        }

        if (app != null) {
            app.setPreviewPort(null);
            app.setPreviewUrl(null);
            app.setUpdatedAt(Instant.now());
            return appRepository.save(app);
        }
        return null;
    }

    public boolean isPreviewRunning(Long startupId) {
        Process p = activeProcesses.get(startupId);
        Integer port = activePorts.get(startupId);
        return p != null && p.isAlive() && port != null;
    }

    public Integer getPreviewPort(Long startupId) {
        return activePorts.get(startupId);
    }

    /**
     * Reverse-proxies browser preview requests to the internal Vite preview server,
     * ensuring assets, CSS, JS, fonts, and client-side routing are seamlessly accessible
     * over the primary application domain without exposing localhost ports.
     */
    public void proxyRequest(Long startupId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uri = request.getRequestURI();

        // 1. Ensure trailing slash on root preview URL for correct HTML relative path resolution
        if (uri.endsWith("/preview")) {
            String query = request.getQueryString() != null ? "?" + request.getQueryString() : "";
            response.sendRedirect(uri + "/" + query);
            return;
        }

        // 2. Extract subpath relative to /preview/
        int idx = uri.indexOf("/preview/");
        String subpath = "";
        if (idx != -1) {
            subpath = uri.substring(idx + "/preview/".length());
        }

        // 3. Security: Prevent directory traversal
        if (subpath.contains("..") || subpath.contains("\\")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid preview path traversal sequence");
            return;
        }

        // 4. Validate startup existence
        GeneratedApp app = appRepository.findByStartupId(startupId).orElse(null);
        if (app == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Startup app not found for ID: " + startupId);
            return;
        }

        // 5. Check if preview process is running
        Integer port = activePorts.get(startupId);
        Process process = activeProcesses.get(startupId);
        boolean isRunning = process != null && process.isAlive() && port != null;

        if (!isRunning) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "Live preview server is not running for startup #" + startupId + ". Click 'Preview App' in the simulator.");
            return;
        }

        // 6. Reverse-proxy request to local Vite preview server on 127.0.0.1:port
        String query = request.getQueryString();
        String targetUrl = "http://127.0.0.1:" + port + "/" + subpath + (query != null ? "?" + query : "");

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(15));

            Enumeration<String> headerNames = request.getHeaderNames();
            if (headerNames != null) {
                while (headerNames.hasMoreElements()) {
                    String hName = headerNames.nextElement();
                    if (isHopByHopHeader(hName)) continue;
                    Enumeration<String> vals = request.getHeaders(hName);
                    while (vals.hasMoreElements()) {
                        try {
                            reqBuilder.header(hName, vals.nextElement());
                        } catch (IllegalArgumentException ignored) {
                            // Ignore restricted headers
                        }
                    }
                }
            }

            reqBuilder.method(request.getMethod(), HttpRequest.BodyPublishers.noBody());
            HttpResponse<InputStream> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

            response.setStatus(resp.statusCode());

            for (Map.Entry<String, List<String>> header : resp.headers().map().entrySet()) {
                String hName = header.getKey();
                if (isHopByHopHeader(hName)) continue;
                // Never pass X-Frame-Options: DENY so the app can render cleanly inside the simulator iframe
                if (hName.equalsIgnoreCase("X-Frame-Options")) continue;
                for (String v : header.getValue()) {
                    response.addHeader(hName, v);
                }
            }

            try (InputStream in = resp.body(); OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
            }
        } catch (Exception e) {
            log.warn("Proxy to preview port {} failed for startup {}: {}. Attempting static file fallback...", port, startupId, e.getMessage());
            serveStaticFallback(startupId, subpath, response);
        }
    }

    private void serveStaticFallback(Long startupId, String subpath, HttpServletResponse response) throws IOException {
        Path workspace = workspaceService.getStartupWorkspace(startupId);
        Path dist = workspace.resolve("dist").normalize();
        if (!Files.exists(dist)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Build output not found for startup #" + startupId);
            return;
        }

        Path target = subpath.isEmpty() ? dist.resolve("index.html") : dist.resolve(subpath).normalize();
        if (!target.startsWith(dist)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return;
        }

        // SPA fallback: serve index.html for extensionless routes
        if (!Files.exists(target) || Files.isDirectory(target)) {
            target = dist.resolve("index.html");
        }

        if (!Files.exists(target)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found");
            return;
        }

        String mimeType = getMimeType(target.getFileName().toString());
        response.setContentType(mimeType);
        response.setContentLengthLong(Files.size(target));
        response.setStatus(HttpServletResponse.SC_OK);

        try (InputStream in = Files.newInputStream(target); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    private String getMimeType(String filename) {
        if (filename.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filename.endsWith(".js") || filename.endsWith(".mjs")) return "application/javascript; charset=UTF-8";
        if (filename.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filename.endsWith(".json")) return "application/json; charset=UTF-8";
        if (filename.endsWith(".svg")) return "image/svg+xml";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".ico")) return "image/x-icon";
        if (filename.endsWith(".woff2")) return "font/woff2";
        if (filename.endsWith(".woff")) return "font/woff";
        if (filename.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    private boolean isHopByHopHeader(String header) {
        return header == null || HOP_BY_HOP.contains(header.toLowerCase());
    }

    private boolean isPortResponding(int port) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                    .timeout(Duration.ofMillis(800))
                    .GET()
                    .build();
            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() >= 200 && res.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForServerReady(int port, Process process, long timeoutMs) {
        long start = System.currentTimeMillis();
        URI uri = URI.create("http://127.0.0.1:" + port + "/");

        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!process.isAlive()) {
                return false;
            }
            try {
                HttpRequest req = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofMillis(500))
                        .GET()
                        .build();
                HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                if (res.statusCode() >= 200 && res.statusCode() < 400) {
                    return true;
                }
            } catch (Exception ignored) {
                // Server still booting
            }
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private String buildPreviewUrl(Long startupId) {
        String base = (previewBaseUrl != null && !previewBaseUrl.isBlank())
                ? previewBaseUrl.replaceAll("/+$", "")
                : "";
        return base + "/api/startups/" + startupId + "/app/preview/";
    }

    @PreDestroy
    public void cleanupAllPreviews() {
        log.info("Cleaning up all active preview processes (count: {})...", activeProcesses.size());
        for (Map.Entry<Long, Process> entry : activeProcesses.entrySet()) {
            try {
                Process p = entry.getValue();
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
            } catch (Exception e) {
                log.warn("Error terminating preview for startup {}: {}", entry.getKey(), e.getMessage());
            }
        }
        activeProcesses.clear();
        activePorts.clear();
    }

    private int findAvailablePort() {
        for (int p = 5174; p <= 5250; p++) {
            if (!activePorts.containsValue(p) && isPortAvailable(p)) {
                return p;
            }
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return 5174;
        }
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
