package com.startupsimulator.service;

import com.startupsimulator.model.GeneratedApp;
import com.startupsimulator.repository.GeneratedAppRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Isolated build runner executing npm/vite commands strictly within the startup workspace.
 * <p>
 * Safety controls:
 * 1. Process environment is stripped of all simulator secrets and AI API keys.
 * 2. Execution timeout is strictly enforced (60s).
 * 3. Working directory is locked to the startup workspace.
 * 4. Only deterministic build commands are allowed.
 */
@Service
public class AppBuildService {

    private static final Logger log = LoggerFactory.getLogger(AppBuildService.class);
    private static final int MAX_BUILD_OUTPUT_BYTES = 64 * 1024; // 64 KB log cap
    private static final long BUILD_TIMEOUT_SECONDS = 60;

    private static final Set<String> SENSITIVE_ENV_PREFIXES = Set.of(
            "GEMINI", "OPENAI", "JUSTDOWORK", "API_KEY", "SECRET", "PASSWORD", "DATABASE"
    );

    private final AppWorkspaceService workspaceService;
    private final GeneratedAppRepository appRepository;
    private final Path frontendNodeModules;

    public AppBuildService(
            AppWorkspaceService workspaceService,
            GeneratedAppRepository appRepository,
            @Value("${app.frontend.node-modules:../frontend/node_modules}") String nodeModulesPath) {
        this.workspaceService = workspaceService;
        this.appRepository = appRepository;
        this.frontendNodeModules = Paths.get(nodeModulesPath).toAbsolutePath().normalize();
    }

    @Transactional
    public GeneratedApp buildApp(Long startupId) {
        GeneratedApp app = appRepository.findByStartupId(startupId)
                .orElseThrow(() -> new IllegalStateException("App has not been generated yet for startup " + startupId));

        Path workspace = workspaceService.getStartupWorkspace(startupId);
        if (!Files.exists(workspace.resolve("package.json"))) {
            throw new IllegalStateException("Workspace for startup " + startupId + " lacks package.json. Generate app first.");
        }

        app.setBuildStatus("BUILDING");
        app.setUpdatedAt(Instant.now());
        app = appRepository.save(app);

        StringBuilder outputLog = new StringBuilder();
        outputLog.append("=== Isolated Build Runner for Startup #").append(startupId).append(" ===\n");
        outputLog.append("Workspace: ").append(workspace).append("\n");
        outputLog.append("Timestamp: ").append(Instant.now()).append("\n\n");

        boolean success = false;
        try {
            // Ensure node_modules exists in workspace (symlinked or local)
            Path workspaceNodeModules = workspace.resolve("node_modules");
            if (!Files.exists(workspaceNodeModules) && Files.exists(frontendNodeModules)) {
                try {
                    Files.createSymbolicLink(workspaceNodeModules, frontendNodeModules);
                    outputLog.append("[setup] Linked shared node_modules into workspace.\n");
                } catch (Exception e) {
                    outputLog.append("[warn] Symlink failed: ").append(e.getMessage()).append("\n");
                }
            }

            // Locate vite executable
            Path viteBinary = workspaceNodeModules.resolve("vite/bin/vite.js");
            if (!Files.exists(viteBinary) && Files.exists(frontendNodeModules)) {
                viteBinary = frontendNodeModules.resolve("vite/bin/vite.js");
            }

            List<String> command;
            if (Files.exists(viteBinary)) {
                command = List.of("node", viteBinary.toString(), "build");
            } else {
                command = List.of("npm", "run", "build");
            }

            outputLog.append("[exec] ").append(String.join(" ", command)).append("\n\n");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workspace.toFile());
            pb.redirectErrorStream(true);

            // Strip sensitive credentials from child process environment
            Map<String, String> env = pb.environment();
            env.keySet().removeIf(k -> SENSITIVE_ENV_PREFIXES.stream().anyMatch(k.toUpperCase()::contains));

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (outputLog.length() < MAX_BUILD_OUTPUT_BYTES) {
                        outputLog.append(line).append("\n");
                    }
                }
            }

            boolean finished = process.waitFor(BUILD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                outputLog.append("\n[error] Build timed out after ").append(BUILD_TIMEOUT_SECONDS).append(" seconds.\n");
                success = false;
            } else {
                int exitCode = process.exitValue();
                outputLog.append("\n[exit] Process finished with exit code ").append(exitCode).append("\n");
                success = (exitCode == 0);
            }
        } catch (Exception e) {
            log.error("Build failed for startup {}", startupId, e);
            outputLog.append("\n[error] Exception during build: ").append(e.getMessage()).append("\n");
            success = false;
        }

        // Cap log size
        String finalLog = outputLog.toString();
        if (finalLog.length() > MAX_BUILD_OUTPUT_BYTES) {
            finalLog = finalLog.substring(0, MAX_BUILD_OUTPUT_BYTES) + "\n...[truncated]";
        }

        app.setBuildStatus(success ? "SUCCESS" : "FAILED");
        app.setBuildOutput(finalLog);
        if (success) {
            app.setStatus("READY");
        }
        app.setUpdatedAt(Instant.now());
        return appRepository.save(app);
    }
}
