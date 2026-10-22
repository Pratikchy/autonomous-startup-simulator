package com.startupsimulator.controller;

import com.startupsimulator.dto.response.GeneratedFileContentDto;
import com.startupsimulator.dto.response.GeneratedFileInfoDto;
import com.startupsimulator.model.GeneratedApp;
import com.startupsimulator.repository.GeneratedAppRepository;
import com.startupsimulator.service.AppBuildService;
import com.startupsimulator.service.AppGeneratorService;
import com.startupsimulator.service.AppPreviewService;
import com.startupsimulator.service.AppWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GeneratedAppControllerTest {

    private MockMvc mockMvc;

    private AppGeneratorService generatorService;
    private AppBuildService buildService;
    private AppPreviewService previewService;
    private AppWorkspaceService workspaceService;
    private GeneratedAppRepository appRepository;
    private com.startupsimulator.service.CoworkerChatService chatService;

    @BeforeEach
    void setUp() {
        generatorService = mock(AppGeneratorService.class);
        buildService = mock(AppBuildService.class);
        previewService = mock(AppPreviewService.class);
        workspaceService = mock(AppWorkspaceService.class);
        appRepository = mock(GeneratedAppRepository.class);
        chatService = mock(com.startupsimulator.service.CoworkerChatService.class);

        GeneratedAppController controller = new GeneratedAppController(
                generatorService,
                buildService,
                previewService,
                workspaceService,
                appRepository,
                chatService
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /api/startups/{id}/app/generate correctly routes to generatorService and returns app DTO")
    void testGenerateEndpointRouting() throws Exception {
        Long startupId = 2L;

        GeneratedApp mockApp = new GeneratedApp();
        mockApp.setId(10L);
        mockApp.setStartupId(startupId);
        mockApp.setAppName("NPWR Automation");
        mockApp.setTemplate("REACT_VITE_TS");
        mockApp.setStatus("GENERATED");
        mockApp.setBuildStatus("NOT_BUILT");
        mockApp.setCreatedAt(Instant.now());
        mockApp.setUpdatedAt(Instant.now());

        when(generatorService.generateApp(startupId)).thenReturn(mockApp);

        mockMvc.perform(post("/api/startups/2/app/generate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.startupId").value(2))
                .andExpect(jsonPath("$.appName").value("NPWR Automation"))
                .andExpect(jsonPath("$.status").value("GENERATED"))
                .andExpect(jsonPath("$.buildStatus").value("NOT_BUILT"));

        verify(generatorService, times(1)).generateApp(eq(2L));
    }

    @Test
    @DisplayName("GET /api/startups/{id}/app returns persisted status or default UNGENERATED")
    void testGetStatusEndpointRouting() throws Exception {
        Long startupId = 2L;

        when(appRepository.findByStartupId(startupId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/startups/2/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startupId").value(2))
                .andExpect(jsonPath("$.status").value("UNGENERATED"));

        verify(appRepository, times(1)).findByStartupId(eq(2L));
    }

    @Test
    @DisplayName("GET /api/startups/{id}/app/files lists workspace files")
    void testListFilesEndpointRouting() throws Exception {
        Long startupId = 2L;

        List<GeneratedFileInfoDto> mockFiles = List.of(
                new GeneratedFileInfoDto("package.json", "package.json", 450, false, "json"),
                new GeneratedFileInfoDto("src/App.tsx", "App.tsx", 1200, false, "typescript-react")
        );

        when(workspaceService.listFiles(startupId)).thenReturn(mockFiles);

        mockMvc.perform(get("/api/startups/2/app/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("package.json"))
                .andExpect(jsonPath("$[1].path").value("src/App.tsx"));

        verify(workspaceService, times(1)).listFiles(eq(2L));
    }

    @Test
    @DisplayName("GET /api/startups/{id}/app/files/content returns file content")
    void testGetFileContentEndpointRouting() throws Exception {
        Long startupId = 2L;
        String filePath = "src/App.tsx";

        GeneratedFileContentDto mockContent = new GeneratedFileContentDto(
                filePath, "App.tsx", "export default function App() {}", 30
        );

        when(workspaceService.readFileContent(startupId, filePath)).thenReturn(mockContent);

        mockMvc.perform(get("/api/startups/2/app/files/content")
                        .param("path", filePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("src/App.tsx"))
                .andExpect(jsonPath("$.content").value("export default function App() {}"));

        verify(workspaceService, times(1)).readFileContent(eq(2L), eq("src/App.tsx"));
    }

    @Test
    @DisplayName("POST /api/startups/{id}/app/build triggers build runner")
    void testBuildEndpointRouting() throws Exception {
        Long startupId = 2L;

        GeneratedApp mockApp = new GeneratedApp();
        mockApp.setStartupId(startupId);
        mockApp.setStatus("READY");
        mockApp.setBuildStatus("SUCCESS");
        mockApp.setBuildOutput("Build successful");

        when(buildService.buildApp(startupId)).thenReturn(mockApp);

        mockMvc.perform(post("/api/startups/2/app/build"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildStatus").value("SUCCESS"));

        verify(buildService, times(1)).buildApp(eq(2L));
    }

    @Test
    @DisplayName("POST /api/startups/{id}/app/preview/start and stop trigger preview service")
    void testPreviewEndpointsRouting() throws Exception {
        Long startupId = 2L;

        GeneratedApp mockApp = new GeneratedApp();
        mockApp.setStartupId(startupId);
        mockApp.setPreviewPort(5174);
        mockApp.setPreviewUrl("/api/startups/2/app/preview/");

        when(previewService.startPreview(startupId)).thenReturn(mockApp);
        when(previewService.stopPreview(startupId)).thenReturn(mockApp);

        mockMvc.perform(post("/api/startups/2/app/preview/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewUrl").value("/api/startups/2/app/preview/"));

        mockMvc.perform(post("/api/startups/2/app/preview/stop"))
                .andExpect(status().isOk());

        verify(previewService, times(1)).startPreview(eq(2L));
        verify(previewService, times(1)).stopPreview(eq(2L));
    }

    @Test
    @DisplayName("GET /api/startups/{id}/app/preview/** delegates to previewService.proxyRequest")
    void testPreviewProxyRouting() throws Exception {
        Long startupId = 2L;

        mockMvc.perform(get("/api/startups/2/app/preview/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/startups/2/app/preview/assets/index.js"))
                .andExpect(status().isOk());

        verify(previewService, times(2)).proxyRequest(eq(startupId), any(), any());
    }

    @Test
    @DisplayName("GET and POST /api/startups/{id}/app/chat route to chatService")
    void testChatEndpointsRouting() throws Exception {
        Long startupId = 2L;

        when(chatService.getHistory(startupId)).thenReturn(java.util.List.of(
                new com.startupsimulator.dto.response.CoworkerChatMessageDto(
                        1L, startupId, "USER", null, null, "Hello", null, null, java.time.Instant.now()
                )
        ));

        mockMvc.perform(get("/api/startups/2/app/chat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Hello"));

        when(chatService.processUserMessage(eq(startupId), eq("Add scanner"))).thenReturn(
                new com.startupsimulator.dto.response.CoworkerChatMessageDto(
                        2L, startupId, "ASSISTANT", com.startupsimulator.model.enums.AgentType.DEVELOPMENT,
                        "Dev Lead", "Added scanner plan", 10L, null, java.time.Instant.now()
                )
        );

        mockMvc.perform(post("/api/startups/2/app/chat")
                        .contentType("application/json")
                        .content("{\"message\":\"Add scanner\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Added scanner plan"));

        verify(chatService, times(1)).getHistory(eq(2L));
        verify(chatService, times(1)).processUserMessage(eq(2L), eq("Add scanner"));
    }
}
