package com.startupsimulator.service;

import com.startupsimulator.model.GeneratedApp;
import com.startupsimulator.repository.GeneratedAppRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages isolated live preview servers for generated startup applications.
 * Tracks child processes, manages dynamic ports, and guarantees graceful cleanup.
 */
@Service
public class AppPreviewService {

    private static final Logger log = LoggerFactory.getLogger(AppPreviewService.class);

    private final AppWorkspaceService workspaceService;
    private final AppBuildService buildService;
    private final GeneratedAppRepository appRepository;
    private final Path frontendNodeModules;

    private final Map<Long, Process> activeProcesses = new ConcurrentHashMap<>();
    private final Map<Long, Integer> activePorts = new ConcurrentHashMap<>();

    public AppPreviewService(
            AppWorkspaceService workspaceService,
            AppBuildService buildService,
            GeneratedAppRepository appRepository,
            @Value("${app.frontend.node-modules:../frontend/node_modules}") String nodeModulesPath) {
        this.workspaceService = workspaceService;
        this.buildService = buildService;
        this.appRepository = appRepository;
        this.frontendNodeModules = Paths.get(nodeModulesPath).toAbsolutePath().normalize();
    }

    @Transactional
    public GeneratedApp startPreview(Long startupId) throws IOException {
        GeneratedApp app = appRepository.findByStartupId(startupId)
                .orElseThrow(() -> new IllegalStateException("App not generated for startup " + startupId));

        // If preview already running and healthy, return it
        Process existing = activeProcesses.get(startupId);
        Integer existingPort = activePorts.get(startupId);
        if (existing != null && existing.isAlive() && existingPort != null) {
            String url = "http://localhost:" + existingPort;
            app.setPreviewPort(existingPort);
            app.setPreviewUrl(url);
            return appRepository.save(app);
        }

        Path workspace = workspaceService.getStartupWorkspace(startupId);

        // Ensure app is built first
        if (!"SUCCESS".equals(app.getBuildStatus()) || !Files.exists(workspace.resolve("dist/index.html"))) {
            log.info("Building startup {} app before starting preview...", startupId);
            app = buildService.buildApp(startupId);
            if (!"SUCCESS".equals(app.getBuildStatus())) {
                throw new IllegalStateException("Cannot start preview: Build failed. Check build logs.");
            }
        }

        int port = findAvailablePort();
        Path viteBinary = workspace.resolve("node_modules/vite/bin/vite.js");
        if (!Files.exists(viteBinary) && Files.exists(frontendNodeModules)) {
            viteBinary = frontendNodeModules.resolve("vite/bin/vite.js");
        }

        List<String> command;
        if (Files.exists(viteBinary)) {
            command = List.of("node", viteBinary.toString(), "preview", "--port", String.valueOf(port), "--host", "127.0.0.1");
        } else {
            command = List.of("npm", "run", "preview", "--", "--port", String.valueOf(port), "--host", "127.0.0.1");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workspace.toFile());
        pb.redirectErrorStream(true);

        log.info("Starting preview for startup {} on port {}: {}", startupId, port, command);
        Process process = pb.start();

        activeProcesses.put(startupId, process);
        activePorts.put(startupId, port);

        String previewUrl = "http://localhost:" + port;
        app.setPreviewPort(port);
        app.setPreviewUrl(previewUrl);
        app.setUpdatedAt(Instant.now());

        // Watch for unexpected exit
        process.onExit().thenAccept(p -> {
            log.warn("Preview process for startup {} exited with code {}", startupId, p.exitValue());
            activeProcesses.remove(startupId);
            activePorts.remove(startupId);
        });

        // Give server 1.5 seconds to bind
        try {
            TimeUnit.MILLISECONDS.sleep(1500);
        } catch (InterruptedException ignored) {}

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
        return p != null && p.isAlive();
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
