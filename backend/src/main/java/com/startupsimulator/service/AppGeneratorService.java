package com.startupsimulator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.generator.DomainAppSynthesizer;
import com.startupsimulator.generator.ProductSpecGenerator;
import com.startupsimulator.generator.spec.ProductSpec;
import com.startupsimulator.model.AgentMessage;
import com.startupsimulator.model.GeneratedApp;
import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.repository.AgentMessageRepository;
import com.startupsimulator.repository.GeneratedAppRepository;
import com.startupsimulator.repository.StartupRepository;
import com.startupsimulator.trace.ExecutionTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * AI-driven product generator that synthesizes tailored, runnable React + TypeScript + Vite
 * applications based on structured ProductSpecs for any startup idea.
 */
@Service
public class AppGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AppGeneratorService.class);

    private final AppWorkspaceService workspaceService;
    private final StartupRepository startupRepository;
    private final GeneratedAppRepository appRepository;
    private final ExecutionTraceService traceService;
    private final ProductSpecGenerator specGenerator;
    private final DomainAppSynthesizer appSynthesizer;
    private final AppBuildService buildService;
    private final AgentMessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final Path frontendNodeModules;

    public AppGeneratorService(
            AppWorkspaceService workspaceService,
            StartupRepository startupRepository,
            GeneratedAppRepository appRepository,
            ExecutionTraceService traceService,
            ProductSpecGenerator specGenerator,
            DomainAppSynthesizer appSynthesizer,
            AppBuildService buildService,
            AgentMessageRepository messageRepository,
            ObjectMapper objectMapper,
            @Value("${app.frontend.node-modules:../frontend/node_modules}") String nodeModulesPath) {
        this.workspaceService = workspaceService;
        this.startupRepository = startupRepository;
        this.appRepository = appRepository;
        this.traceService = traceService;
        this.specGenerator = specGenerator;
        this.appSynthesizer = appSynthesizer;
        this.buildService = buildService;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.frontendNodeModules = Paths.get(nodeModulesPath).toAbsolutePath().normalize();
    }

    @Transactional
    public GeneratedApp generateApp(Long startupId) throws IOException {
        Startup startup = startupRepository.findById(startupId)
                .orElseThrow(() -> new IllegalArgumentException("Startup not found with ID: " + startupId));

        workspaceService.initializeStartupWorkspace(startupId);
        Path workspace = workspaceService.getStartupWorkspace(startupId);

        // Fetch or create GeneratedApp record
        GeneratedApp app = appRepository.findByStartupId(startupId).orElseGet(() -> {
            GeneratedApp newApp = new GeneratedApp();
            newApp.setStartupId(startupId);
            newApp.setAppName(startup.getName());
            return newApp;
        });

        Map<String, String> existingFilesSnapshot = new HashMap<>();
        if (Files.exists(workspace.resolve("src/App.tsx"))) {
            Path backupDir = workspaceService.getWorkspaceRoot().resolve("backups")
                    .resolve("startup-" + startupId).resolve("regen-" + System.currentTimeMillis());
            final Path finalBackup = backupDir;
            try {
                Files.createDirectories(finalBackup);
                try (Stream<Path> stream = Files.walk(workspace)) {
                    stream.filter(Files::isRegularFile).forEach(file -> {
                        String rel = workspace.relativize(file).toString();
                        if (!rel.startsWith("node_modules") && !rel.startsWith("dist") && !rel.startsWith(".")) {
                            try {
                                String content = Files.readString(file);
                                existingFilesSnapshot.put(rel, content);
                                Path dest = finalBackup.resolve(rel);
                                Files.createDirectories(dest.getParent());
                                Files.writeString(dest, content);
                            } catch (Exception e) {
                                log.warn("Could not backup file {}: {}", rel, e.getMessage());
                            }
                        }
                    });
                }
                log.info("Saved pre-regeneration backup snapshot for startup {} at {}", startupId, finalBackup);
            } catch (Exception e) {
                log.warn("Failed to create pre-generation backup for startup {}: {}", startupId, e.getMessage());
            }
        }

        app.setStatus("GENERATING");
        app.setUpdatedAt(Instant.now());
        app = appRepository.save(app);

        try {
            // Step 2: Read agent context and generate structured ProductSpec
            List<AgentMessage> messages = Collections.emptyList();
            try {
                messages = messageRepository.findByStartupIdOrderById(startupId);
            } catch (Exception e) {
                log.warn("Could not load agent messages for startup {}: {}", startupId, e.getMessage());
            }

            ProductSpec spec = specGenerator.generateProductSpec(startup, messages);
            String specJson = objectMapper.writeValueAsString(spec);
            app.setProductSpecJson(specJson);

            // Step 3: Synthesize product-specific source files and styles
            Map<String, String> files = appSynthesizer.synthesizeFiles(startup, spec);

            // Step 4: Validate all relative paths & write files into workspace
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String relPath = entry.getKey();
                String content = entry.getValue();

                // Safety invariant: path containment check
                workspaceService.resolveWorkspaceFile(startupId, relPath);
                workspaceService.writeFileContent(startupId, relPath, content);
            }

            // Step 5: Symlink node_modules for instant building
            tryLinkNodeModules(startupId);

            // Step 6: Isolated build validation
            GeneratedApp builtApp = buildService.buildApp(startupId);
            if (!"SUCCESS".equals(builtApp.getBuildStatus())) {
                log.error("Build verification failed for newly generated app of startup {}. Output: {}",
                        startupId, builtApp.getBuildOutput());

                // Rollback if previous working version exists
                if (!existingFilesSnapshot.isEmpty()) {
                    log.warn("Rolling back startup {} workspace to previous working snapshot.", startupId);
                    rollback(startupId, existingFilesSnapshot);
                    buildService.buildApp(startupId); // Rebuild restored version
                }

                app.setStatus("FAILED");
                app.setBuildStatus("FAILED");
                app.setBuildOutput(builtApp.getBuildOutput());
                app.setUpdatedAt(Instant.now());
                appRepository.save(app);
                throw new IllegalStateException("Generated application failed build verification: " + builtApp.getBuildOutput());
            }

            app.setStatus("READY");
            app.setBuildStatus("SUCCESS");
            app.setBuildOutput(builtApp.getBuildOutput());
            app.setUpdatedAt(Instant.now());
            app = appRepository.save(app);

            // Record authoritative action in execution trace
            traceService.recordToolCall(
                    startupId,
                    "run-" + startupId,
                    AgentType.DEVELOPMENT,
                    "AppGeneratorService",
                    Map.of(
                            "category", spec.category(),
                            "appName", spec.appName(),
                            "screens", spec.screens().size(),
                            "workspace", "startup-" + startupId
                    ),
                    "Generated customized multi-screen web application (" + spec.category() + ") for " + startup.getName()
            );

            log.info("Successfully generated and built tailored web app for startup {} ({})", startupId, spec.category());
            return app;
        } catch (Exception e) {
            log.error("Failed to generate application for startup {}", startupId, e);
            app.setStatus("FAILED");
            app.setUpdatedAt(Instant.now());
            appRepository.save(app);
            throw e;
        }
    }

    private void rollback(Long startupId, Map<String, String> originalContents) {
        try {
            for (Map.Entry<String, String> entry : originalContents.entrySet()) {
                workspaceService.writeFileContent(startupId, entry.getKey(), entry.getValue());
            }
            log.info("Rollback restored {} files for startup {}", originalContents.size(), startupId);
        } catch (Exception e) {
            log.error("Error restoring workspace rollback for startup {}: {}", startupId, e.getMessage());
        }
    }

    private void tryLinkNodeModules(Long startupId) {
        try {
            Path workspace = workspaceService.getStartupWorkspace(startupId);
            Path targetLink = workspace.resolve("node_modules");
            if (!Files.exists(targetLink) && Files.exists(frontendNodeModules)) {
                Files.createSymbolicLink(targetLink, frontendNodeModules);
                log.info("Symlinked node_modules into workspace for startup {}", startupId);
            }
        } catch (Exception e) {
            log.warn("Could not symlink node_modules for startup {}: {}", startupId, e.getMessage());
        }
    }
}
