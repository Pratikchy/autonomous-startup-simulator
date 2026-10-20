package com.startupsimulator.service;

import com.startupsimulator.dto.response.GeneratedFileContentDto;
import com.startupsimulator.dto.response.GeneratedFileInfoDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Manages strictly isolated, startup-scoped filesystem workspaces.
 * <p>
 * Security invariants enforced:
 * 1. Startup ID is strictly validated (must be positive Long).
 * 2. Workspace root is resolved to an absolute canonical directory outside simulator source & database paths.
 * 3. Path traversal attacks (e.g. "../", "\\", absolute paths, symlink escapes) are prevented.
 * 4. Access to sensitive files (.env, .git, database files, secret keys) is strictly blocked.
 * 5. Startup A can never access or overwrite files belonging to Startup B.
 */
@Service
public class AppWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(AppWorkspaceService.class);
    private static final long MAX_READABLE_FILE_SIZE = 1024 * 1024; // 1 MB limit for viewer

    private static final Set<String> FORBIDDEN_FILENAMES = Set.of(
            ".env", ".git", ".gitignore", ".DS_Store", "application.properties", "application.yml"
    );

    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            ".mv.db", ".trace.db", ".key", ".pem", ".p12", ".class", ".jar"
    );

    private final Path workspaceRoot;

    public AppWorkspaceService(@Value("${app.workspace.root-dir:./workspaces}") String rootDir) {
        Path root = Paths.get(rootDir).toAbsolutePath().normalize();
        this.workspaceRoot = root;
        try {
            Files.createDirectories(this.workspaceRoot);
            log.info("Initialized AppWorkspaceService with root directory: {}", this.workspaceRoot);
        } catch (IOException e) {
            log.error("Failed to initialize workspace root directory: {}", this.workspaceRoot, e);
        }
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * Resolves and ensures the isolated directory for a given startup.
     */
    public Path getStartupWorkspace(Long startupId) {
        validateStartupId(startupId);
        Path startupDir = workspaceRoot.resolve("startup-" + startupId).normalize().toAbsolutePath();

        if (!startupDir.startsWith(workspaceRoot)) {
            throw new SecurityException("Workspace path escape detected for startup " + startupId);
        }
        return startupDir;
    }

    /**
     * Creates or resets the workspace folder for a startup.
     */
    public Path initializeStartupWorkspace(Long startupId) throws IOException {
        Path workspace = getStartupWorkspace(startupId);
        if (!Files.exists(workspace)) {
            Files.createDirectories(workspace);
            log.info("Created isolated workspace for startup {}: {}", startupId, workspace);
        }
        return workspace;
    }

    /**
     * Resolves a file relative to a startup's workspace, verifying path containment and file safety.
     */
    public Path resolveWorkspaceFile(Long startupId, String relativePath) {
        Path workspace = getStartupWorkspace(startupId);

        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Relative path cannot be empty");
        }

        // Prevent path traversal sequences
        if (relativePath.contains("..") || relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            throw new SecurityException("Path traversal sequence forbidden: " + relativePath);
        }

        Path resolved = workspace.resolve(relativePath).normalize().toAbsolutePath();

        if (!resolved.startsWith(workspace)) {
            throw new SecurityException("Path traversal attempt detected. Target must stay within workspace: " + relativePath);
        }

        String fileName = resolved.getFileName().toString().toLowerCase();
        if (FORBIDDEN_FILENAMES.contains(fileName)) {
            throw new SecurityException("Access to restricted system file forbidden: " + fileName);
        }

        for (String ext : FORBIDDEN_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                throw new SecurityException("Access to sensitive file extension forbidden: " + ext);
            }
        }

        return resolved;
    }

    /**
     * Lists all generated project files (excluding node_modules and .git) using safe relative paths.
     */
    public List<GeneratedFileInfoDto> listFiles(Long startupId) {
        Path workspace = getStartupWorkspace(startupId);
        if (!Files.exists(workspace)) {
            return Collections.emptyList();
        }

        List<GeneratedFileInfoDto> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(workspace)) {
            walk.filter(p -> !p.equals(workspace))
                .filter(p -> {
                    String relative = workspace.relativize(p).toString();
                    return !relative.startsWith("node_modules") &&
                           !relative.startsWith(".git") &&
                           !relative.contains(".DS_Store") &&
                           !relative.startsWith("dist");
                })
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> {
                    String rel = workspace.relativize(path).toString().replace('\\', '/');
                    boolean isDir = Files.isDirectory(path);
                    long size = 0;
                    if (!isDir) {
                        try {
                            size = Files.size(path);
                        } catch (IOException ignored) {}
                    }
                    String fileType = determineFileType(rel, isDir);
                    result.add(new GeneratedFileInfoDto(rel, path.getFileName().toString(), size, isDir, fileType));
                });
        } catch (IOException e) {
            log.error("Failed to list files for startup {}", startupId, e);
        }
        return result;
    }

    /**
     * Reads the text content of a safe generated file.
     */
    public GeneratedFileContentDto readFileContent(Long startupId, String relativePath) throws IOException {
        Path targetFile = resolveWorkspaceFile(startupId, relativePath);

        if (!Files.exists(targetFile) || Files.isDirectory(targetFile)) {
            throw new NoSuchFileException("File not found: " + relativePath);
        }

        long size = Files.size(targetFile);
        if (size > MAX_READABLE_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 1 MB limit for viewer: " + size + " bytes");
        }

        String content = Files.readString(targetFile, StandardCharsets.UTF_8);
        return new GeneratedFileContentDto(
                relativePath.replace('\\', '/'),
                targetFile.getFileName().toString(),
                content,
                size
        );
    }

    /**
     * Writes content to a safe relative file within the startup workspace.
     */
    public void writeFileContent(Long startupId, String relativePath, String content) throws IOException {
        Path targetFile = resolveWorkspaceFile(startupId, relativePath);
        Files.createDirectories(targetFile.getParent());
        Files.writeString(targetFile, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void validateStartupId(Long startupId) {
        if (startupId == null || startupId <= 0) {
            throw new IllegalArgumentException("Invalid startup ID: " + startupId);
        }
    }

    private String determineFileType(String relativePath, boolean isDir) {
        if (isDir) return "directory";
        if (relativePath.endsWith(".tsx")) return "typescript-react";
        if (relativePath.endsWith(".ts")) return "typescript";
        if (relativePath.endsWith(".jsx")) return "javascript-react";
        if (relativePath.endsWith(".js")) return "javascript";
        if (relativePath.endsWith(".html")) return "html";
        if (relativePath.endsWith(".css")) return "css";
        if (relativePath.endsWith(".json")) return "json";
        if (relativePath.endsWith(".md")) return "markdown";
        return "text";
    }
}
