package com.startupsimulator.workspace;

import com.startupsimulator.dto.response.GeneratedFileContentDto;
import com.startupsimulator.dto.response.GeneratedFileInfoDto;
import com.startupsimulator.service.AppWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratedAppWorkspaceTest {

    @TempDir
    Path tempWorkspaceRoot;

    private AppWorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        workspaceService = new AppWorkspaceService(tempWorkspaceRoot.toString());
    }

    @Test
    @DisplayName("Startup workspaces are strictly isolated by startup ID")
    void testWorkspaceIsolationBetweenStartups() throws IOException {
        Long startup1 = 101L;
        Long startup2 = 102L;

        Path ws1 = workspaceService.initializeStartupWorkspace(startup1);
        Path ws2 = workspaceService.initializeStartupWorkspace(startup2);

        assertThat(ws1).exists();
        assertThat(ws2).exists();
        assertThat(ws1).isNotEqualTo(ws2);
        assertThat(ws1.getFileName().toString()).isEqualTo("startup-101");
        assertThat(ws2.getFileName().toString()).isEqualTo("startup-102");

        // Write file in startup 1
        workspaceService.writeFileContent(startup1, "src/config.ts", "export const startupId = 101;");

        // Startup 1 can read it
        GeneratedFileContentDto content1 = workspaceService.readFileContent(startup1, "src/config.ts");
        assertThat(content1.content()).contains("startupId = 101;");

        // Startup 2 cannot find it in its own workspace
        assertThatThrownBy(() -> workspaceService.readFileContent(startup2, "src/config.ts"))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Path traversal attacks using ../ or leading slashes are rejected")
    void testPathTraversalPrevention() {
        Long startupId = 201L;

        assertThatThrownBy(() -> workspaceService.resolveWorkspaceFile(startupId, "../secret.txt"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");

        assertThatThrownBy(() -> workspaceService.resolveWorkspaceFile(startupId, "../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");

        assertThatThrownBy(() -> workspaceService.resolveWorkspaceFile(startupId, "/etc/hosts"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");

        assertThatThrownBy(() -> workspaceService.resolveWorkspaceFile(startupId, "\\windows\\system32"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");

        assertThatThrownBy(() -> workspaceService.resolveWorkspaceFile(startupId, "src/../../outside.txt"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");
    }

    @Test
    @DisplayName("Access to sensitive file names (.env, .git) or extensions (.key, .db) is blocked")
    void testForbiddenFileAccessBlocked() {
        Long startupId = 301L;

        assertThatThrownBy(() -> workspaceService.resolveWorkspaceFile(startupId, ".env"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("restricted");

        assertThatThrownBy(() -> workspaceService.resolveWorkspaceFile(startupId, ".git"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("restricted");

        assertThatThrownBy(() -> workspaceService.resolveWorkspaceFile(startupId, "server.key"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("sensitive file extension");

        assertThatThrownBy(() -> workspaceService.resolveWorkspaceFile(startupId, "startupsim.mv.db"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("sensitive file extension");
    }

    @Test
    @DisplayName("Valid file reading, writing, and directory listing work properly")
    void testFileListingAndReading() throws IOException {
        Long startupId = 401L;
        workspaceService.initializeStartupWorkspace(startupId);

        workspaceService.writeFileContent(startupId, "package.json", "{\"name\": \"test-app\"}");
        workspaceService.writeFileContent(startupId, "src/App.tsx", "export default function App() { return <h1>Test</h1>; }");
        workspaceService.writeFileContent(startupId, "src/index.css", "body { background: black; }");

        List<GeneratedFileInfoDto> files = workspaceService.listFiles(startupId);
        assertThat(files).extracting(GeneratedFileInfoDto::path)
                .contains("package.json", "src/App.tsx", "src/index.css");

        GeneratedFileContentDto readApp = workspaceService.readFileContent(startupId, "src/App.tsx");
        assertThat(readApp.content()).contains("<h1>Test</h1>");
        assertThat(readApp.name()).isEqualTo("App.tsx");
    }

    @Test
    @DisplayName("Startup ID input validation enforces positive numeric identifier")
    void testStartupIdValidation() {
        assertThatThrownBy(() -> workspaceService.getStartupWorkspace(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> workspaceService.getStartupWorkspace(0L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> workspaceService.getStartupWorkspace(-5L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
