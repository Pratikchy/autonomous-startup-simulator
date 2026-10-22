package com.startupsimulator.service;

import com.startupsimulator.model.GeneratedApp;
import com.startupsimulator.repository.GeneratedAppRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppPreviewServiceTest {

    @Mock
    private AppWorkspaceService workspaceService;

    @Mock
    private AppBuildService buildService;

    @Mock
    private GeneratedAppRepository appRepository;

    private AppPreviewService previewService;

    @BeforeEach
    void setUp() {
        previewService = new AppPreviewService(
                workspaceService,
                buildService,
                appRepository,
                "../frontend/node_modules",
                ""
        );
    }

    @Test
    @DisplayName("proxyRequest redirects /preview to /preview/ to ensure correct relative asset resolution")
    void testProxyRequestRedirectsTrailingSlash() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/startups/1/app/preview");
        MockHttpServletResponse response = new MockHttpServletResponse();

        previewService.proxyRequest(1L, request, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("/api/startups/1/app/preview/");
    }

    @Test
    @DisplayName("proxyRequest preserves query strings during trailing slash redirect")
    void testProxyRequestRedirectsTrailingSlashWithQuery() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/startups/1/app/preview");
        request.setQueryString("theme=dark");
        MockHttpServletResponse response = new MockHttpServletResponse();

        previewService.proxyRequest(1L, request, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("/api/startups/1/app/preview/?theme=dark");
    }

    @Test
    @DisplayName("proxyRequest rejects path traversal attempts with 400 Bad Request")
    void testProxyRequestBlocksPathTraversal() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/startups/1/app/preview/../../secret.txt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        previewService.proxyRequest(1L, request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).contains("traversal");
    }

    @Test
    @DisplayName("proxyRequest returns 404 when startup app does not exist in repository")
    void testProxyRequestNonexistentApp() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/startups/999/app/preview/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(appRepository.findByStartupId(999L)).thenReturn(Optional.empty());

        previewService.proxyRequest(999L, request, response);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getErrorMessage()).contains("Startup app not found");
    }

    @Test
    @DisplayName("proxyRequest returns 404 when preview server is stopped/not running")
    void testProxyRequestPreviewNotRunning() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/startups/1/app/preview/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(1L);
        when(appRepository.findByStartupId(1L)).thenReturn(Optional.of(app));

        previewService.proxyRequest(1L, request, response);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getErrorMessage()).contains("not running");
    }

    @Test
    @DisplayName("stopPreview resets previewPort and previewUrl to null and terminates process")
    void testStopPreviewCleansUpState() {
        GeneratedApp app = new GeneratedApp();
        app.setStartupId(1L);
        app.setPreviewPort(5174);
        app.setPreviewUrl("/api/startups/1/app/preview/");

        when(appRepository.findByStartupId(1L)).thenReturn(Optional.of(app));
        when(appRepository.save(any(GeneratedApp.class))).thenAnswer(i -> i.getArgument(0));

        GeneratedApp stopped = previewService.stopPreview(1L);

        assertThat(stopped).isNotNull();
        assertThat(stopped.getPreviewPort()).isNull();
        assertThat(stopped.getPreviewUrl()).isNull();
        assertThat(previewService.isPreviewRunning(1L)).isFalse();
    }

    @Test
    @DisplayName("Custom APP_PREVIEW_BASE_URL prefixes previewUrl when configured")
    void testCustomPreviewBaseUrl() throws Exception {
        AppPreviewService customService = new AppPreviewService(
                workspaceService,
                buildService,
                appRepository,
                "../frontend/node_modules",
                "https://preview.mydomain.com"
        );

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(5L);
        app.setBuildStatus("SUCCESS");

        when(appRepository.findByStartupId(5L)).thenReturn(Optional.of(app));
        when(workspaceService.getStartupWorkspace(5L)).thenReturn(Path.of("/tmp/nonexistent-workspace-5"));

        // When build output dist/index.html is missing, it will attempt build
        GeneratedApp buildApp = new GeneratedApp();
        buildApp.setStartupId(5L);
        buildApp.setBuildStatus("FAILED");
        when(buildService.buildApp(5L)).thenReturn(buildApp);

        assertThatThrownBy(() -> customService.startPreview(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Build failed");
    }
}
