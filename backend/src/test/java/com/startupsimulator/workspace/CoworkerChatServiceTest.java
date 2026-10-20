package com.startupsimulator.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.agent.LLMService;
import com.startupsimulator.dto.response.AppChangeRequestDto;
import com.startupsimulator.dto.response.CoworkerChatMessageDto;
import com.startupsimulator.model.AppChangeRequest;
import com.startupsimulator.model.CoworkerChatMessage;
import com.startupsimulator.model.GeneratedApp;
import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.repository.AppChangeRequestRepository;
import com.startupsimulator.repository.CoworkerChatMessageRepository;
import com.startupsimulator.repository.GeneratedAppRepository;
import com.startupsimulator.repository.StartupRepository;
import com.startupsimulator.service.AppBuildService;
import com.startupsimulator.service.AppWorkspaceService;
import com.startupsimulator.service.CoworkerChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class CoworkerChatServiceTest {

    @TempDir
    Path tempWorkspaceRoot;

    private AppWorkspaceService workspaceService;
    private AppBuildService mockBuildService;
    private GeneratedAppRepository mockAppRepository;
    private CoworkerChatMessageRepository mockMessageRepository;
    private AppChangeRequestRepository mockChangeRequestRepository;
    private StartupRepository mockStartupRepository;
    private LLMService mockLlmService;
    private ObjectMapper objectMapper;

    private CoworkerChatService chatService;

    private final List<CoworkerChatMessage> messageStore = new ArrayList<>();
    private final Map<Long, AppChangeRequest> changeRequestStore = new HashMap<>();
    private long idSeq = 1;

    @BeforeEach
    void setUp() {
        workspaceService = new AppWorkspaceService(tempWorkspaceRoot.toString());
        mockBuildService = Mockito.mock(AppBuildService.class);
        mockAppRepository = Mockito.mock(GeneratedAppRepository.class);
        mockMessageRepository = Mockito.mock(CoworkerChatMessageRepository.class);
        mockChangeRequestRepository = Mockito.mock(AppChangeRequestRepository.class);
        mockStartupRepository = Mockito.mock(StartupRepository.class);
        mockLlmService = Mockito.mock(LLMService.class);
        objectMapper = new ObjectMapper();

        messageStore.clear();
        changeRequestStore.clear();

        // Wire mock repositories with in-memory stores for isolated unit testing
        when(mockMessageRepository.save(any(CoworkerChatMessage.class))).thenAnswer(invocation -> {
            CoworkerChatMessage m = invocation.getArgument(0);
            if (m.getId() == null) m.setId(idSeq++);
            messageStore.add(m);
            return m;
        });

        when(mockMessageRepository.findByStartupIdOrderByCreatedAtAsc(any(Long.class))).thenAnswer(invocation -> {
            Long sid = invocation.getArgument(0);
            return messageStore.stream().filter(m -> m.getStartupId().equals(sid)).toList();
        });

        when(mockChangeRequestRepository.save(any(AppChangeRequest.class))).thenAnswer(invocation -> {
            AppChangeRequest r = invocation.getArgument(0);
            if (r.getId() == null) r.setId(idSeq++);
            changeRequestStore.put(r.getId(), r);
            return r;
        });

        when(mockChangeRequestRepository.findByIdAndStartupId(any(Long.class), any(Long.class))).thenAnswer(invocation -> {
            Long rid = invocation.getArgument(0);
            Long sid = invocation.getArgument(1);
            AppChangeRequest r = changeRequestStore.get(rid);
            if (r != null && r.getStartupId().equals(sid)) {
                return Optional.of(r);
            }
            return Optional.empty();
        });

        when(mockChangeRequestRepository.findById(any(Long.class))).thenAnswer(invocation -> {
            Long rid = invocation.getArgument(0);
            return Optional.ofNullable(changeRequestStore.get(rid));
        });

        chatService = new CoworkerChatService(
                mockLlmService,
                workspaceService,
                mockBuildService,
                mockAppRepository,
                mockMessageRepository,
                mockChangeRequestRepository,
                mockStartupRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("Conversation history is strictly isolated per startup")
    void testChatHistoryScopedToStartup() {
        Startup s1 = new Startup();
        s1.setId(101L);
        s1.setName("AlphaApp");
        s1.setOriginalIdea("An AI notes app");

        Startup s2 = new Startup();
        s2.setId(102L);
        s2.setName("BetaApp");
        s2.setOriginalIdea("A crypto tracker");

        messageStore.add(new CoworkerChatMessage(101L, "USER", null, null, "Hello Alpha", null));
        messageStore.add(new CoworkerChatMessage(102L, "USER", null, null, "Hello Beta", null));

        List<CoworkerChatMessageDto> s1History = chatService.getHistory(101L);
        List<CoworkerChatMessageDto> s2History = chatService.getHistory(102L);

        assertThat(s1History).hasSize(1);
        assertThat(s1History.get(0).getContent()).isEqualTo("Hello Alpha");

        assertThat(s2History).hasSize(1);
        assertThat(s2History.get(0).getContent()).isEqualTo("Hello Beta");
    }

    @Test
    @DisplayName("Proposed changes require explicit approval before modifying workspace files")
    void testProposalRequiresApprovalBeforeFilesChange() throws IOException {
        Long startupId = 201L;
        Startup s = new Startup();
        s.setId(startupId);
        s.setName("NPWR");
        s.setOriginalIdea("Skincare scanner");
        when(mockStartupRepository.findById(startupId)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(startupId);
        app.setStatus("READY");
        when(mockAppRepository.findByStartupId(startupId)).thenReturn(Optional.of(app));

        // Create workspace and initial file
        workspaceService.initializeStartupWorkspace(startupId);
        String initialContent = "export default function App() { return <div>Initial V1</div>; }";
        workspaceService.writeFileContent(startupId, "src/App.tsx", initialContent);

        // User asks to add scanner
        CoworkerChatMessageDto response = chatService.processUserMessage(startupId, "Please add a product scanner page");

        // Verify: Chat response returned proposal
        assertThat(response.getRole()).isEqualTo("ASSISTANT");
        assertThat(response.getAgentType()).isEqualTo(AgentType.DEVELOPMENT);
        assertThat(response.getChangeRequest()).isNotNull();
        assertThat(response.getChangeRequest().getStatus()).isEqualTo("PROPOSED");

        // CRITICAL CHECK: File on disk MUST REMAIN UNTOUCHED before approval!
        String onDisk = workspaceService.readFileContent(startupId, "src/App.tsx").content();
        assertThat(onDisk).isEqualTo(initialContent);
    }

    @Test
    @DisplayName("Approving a proposal applies changes and triggers isolated build validation")
    void testApprovalAppliesChangesAndValidatesBuild() throws IOException {
        Long startupId = 202L;
        Startup s = new Startup();
        s.setId(startupId);
        s.setName("NPWR");
        s.setOriginalIdea("Skincare scanner");
        when(mockStartupRepository.findById(startupId)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(startupId);
        app.setStatus("READY");
        when(mockAppRepository.findByStartupId(startupId)).thenReturn(Optional.of(app));

        workspaceService.initializeStartupWorkspace(startupId);
        workspaceService.writeFileContent(startupId, "src/App.tsx", "<section className=\"bg-slate-900/40 backdrop-blur-md rounded-2xl p-8 border border-slate-800\"></section>");

        // Step 1: User requests scanner
        CoworkerChatMessageDto proposed = chatService.processUserMessage(startupId, "Add a product scanner feature");
        Long reqId = proposed.getChangeRequest().getId();

        // Configure mock build service to report SUCCESS
        GeneratedApp successfulBuild = new GeneratedApp();
        successfulBuild.setStartupId(startupId);
        successfulBuild.setBuildStatus("SUCCESS");
        successfulBuild.setStatus("READY");
        successfulBuild.setBuildOutput("Build finished 0 errors");
        when(mockBuildService.buildApp(startupId)).thenReturn(successfulBuild);

        // Step 2: User explicitly approves
        AppChangeRequestDto approved = chatService.approveChangeRequest(startupId, reqId);

        assertThat(approved.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(approved.getBuildOutput()).contains("Build finished 0 errors");

        // File on disk is now updated with the scanner feature
        String updated = workspaceService.readFileContent(startupId, "src/App.tsx").content();
        assertThat(updated).contains("AI Instant Product Scanner");
    }

    @Test
    @DisplayName("Failed build rolls back workspace to previous working version")
    void testFailedBuildRollsBackToPreviousWorkingVersion() throws IOException {
        Long startupId = 203L;
        Startup s = new Startup();
        s.setId(startupId);
        s.setName("NPWR");
        s.setOriginalIdea("Skincare scanner");
        when(mockStartupRepository.findById(startupId)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(startupId);
        app.setStatus("READY");
        when(mockAppRepository.findByStartupId(startupId)).thenReturn(Optional.of(app));

        workspaceService.initializeStartupWorkspace(startupId);
        String workingV1 = "export default function App() { return <div>Known Working V1</div>; }";
        workspaceService.writeFileContent(startupId, "src/App.tsx", workingV1);

        CoworkerChatMessageDto proposed = chatService.processUserMessage(startupId, "Add barcode camera scan");
        Long reqId = proposed.getChangeRequest().getId();

        // Configure mock build service to fail on the change
        GeneratedApp failedBuild = new GeneratedApp();
        failedBuild.setStartupId(startupId);
        failedBuild.setBuildStatus("FAILED");
        failedBuild.setStatus("FAILED");
        failedBuild.setBuildOutput("SyntaxError: Unexpected token");
        when(mockBuildService.buildApp(startupId)).thenReturn(failedBuild);

        // User approves, but build fails!
        AppChangeRequestDto result = chatService.approveChangeRequest(startupId, reqId);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorMessage()).contains("rolled back");

        // CRITICAL CHECK: File on disk MUST HAVE BEEN ROLLED BACK to workingV1!
        String currentContent = workspaceService.readFileContent(startupId, "src/App.tsx").content();
        assertThat(currentContent).isEqualTo(workingV1);
    }

    @Test
    @DisplayName("Rejecting a change request cancels the plan and leaves disk untouched")
    void testRejectionPreservesWorkspaceWithoutChanges() throws IOException {
        Long startupId = 204L;
        Startup s = new Startup();
        s.setId(startupId);
        s.setName("NPWR");
        when(mockStartupRepository.findById(startupId)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(startupId);
        when(mockAppRepository.findByStartupId(startupId)).thenReturn(Optional.of(app));

        workspaceService.initializeStartupWorkspace(startupId);
        String original = "<div>Untouched</div>";
        workspaceService.writeFileContent(startupId, "src/App.tsx", original);

        CoworkerChatMessageDto proposed = chatService.processUserMessage(startupId, "Rewrite theme");
        Long reqId = proposed.getChangeRequest().getId();

        AppChangeRequestDto rejected = chatService.rejectChangeRequest(startupId, reqId);

        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        assertThat(workspaceService.readFileContent(startupId, "src/App.tsx").content()).isEqualTo(original);
    }

    @Test
    @DisplayName("Coworker Prompt: 'Change the landing page to a premium, minimalist design'")
    void testCoworkerPromptMinimalistDesign() throws IOException {
        Long startupId = 501L;
        Startup s = new Startup();
        s.setId(startupId);
        s.setName("VeloCare");
        when(mockStartupRepository.findById(startupId)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(startupId);
        when(mockAppRepository.findByStartupId(startupId)).thenReturn(Optional.of(app));

        workspaceService.initializeStartupWorkspace(startupId);
        workspaceService.writeFileContent(startupId, "src/App.tsx", "<header className=\"bg-slate-950 text-slate-100 flex flex-col font-sans\"></header>");

        CoworkerChatMessageDto response = chatService.processUserMessage(startupId, "Change the landing page to a premium, minimalist design.");
        assertThat(response.getChangeRequest()).isNotNull();
        assertThat(response.getChangeRequest().getPlanSummary()).containsIgnoringCase("minimalist");

        // Set mock build success
        GeneratedApp okBuild = new GeneratedApp();
        okBuild.setBuildStatus("SUCCESS");
        when(mockBuildService.buildApp(startupId)).thenReturn(okBuild);

        AppChangeRequestDto approved = chatService.approveChangeRequest(startupId, response.getChangeRequest().getId());
        assertThat(approved.getStatus()).isEqualTo("SUCCEEDED");

        String content = workspaceService.readFileContent(startupId, "src/App.tsx").content();
        assertThat(content).contains("PREMIUM MINIMALIST ARCHITECTURE");
    }

    @Test
    @DisplayName("Coworker Prompt: 'Add user registration and login screens'")
    void testCoworkerPromptUserAuth() throws IOException {
        Long startupId = 502L;
        Startup s = new Startup();
        s.setId(startupId);
        s.setName("VeloCare");
        when(mockStartupRepository.findById(startupId)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(startupId);
        when(mockAppRepository.findByStartupId(startupId)).thenReturn(Optional.of(app));

        workspaceService.initializeStartupWorkspace(startupId);
        workspaceService.writeFileContent(startupId, "src/App.tsx", "import { useState, useEffect } from 'react';\nexport default function App() {\n  return <header></header>;\n}");

        CoworkerChatMessageDto response = chatService.processUserMessage(startupId, "Add user registration and login screens.");
        assertThat(response.getChangeRequest()).isNotNull();
        assertThat(response.getChangeRequest().getProposedChanges()).hasSize(2); // AuthModal.tsx and App.tsx

        GeneratedApp okBuild = new GeneratedApp();
        okBuild.setBuildStatus("SUCCESS");
        when(mockBuildService.buildApp(startupId)).thenReturn(okBuild);

        AppChangeRequestDto approved = chatService.approveChangeRequest(startupId, response.getChangeRequest().getId());
        assertThat(approved.getStatus()).isEqualTo("SUCCEEDED");

        assertThat(workspaceService.getStartupWorkspace(startupId).resolve("src/components/AuthModal.tsx")).exists();
        String modal = workspaceService.readFileContent(startupId, "src/components/AuthModal.tsx").content();
        assertThat(modal).contains("Sign In");
        assertThat(modal).contains("Register");
    }

    @Test
    @DisplayName("Coworker Prompt: 'Build a booking system with a calendar'")
    void testCoworkerPromptBookingCalendar() throws IOException {
        Long startupId = 503L;
        Startup s = new Startup();
        s.setId(startupId);
        s.setName("VeloCare");
        when(mockStartupRepository.findById(startupId)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(startupId);
        when(mockAppRepository.findByStartupId(startupId)).thenReturn(Optional.of(app));

        workspaceService.initializeStartupWorkspace(startupId);
        workspaceService.writeFileContent(startupId, "src/App.tsx", "import { useState, useEffect } from 'react';\nexport default function App() {\n  return <main></main>;\n}");

        CoworkerChatMessageDto response = chatService.processUserMessage(startupId, "Build a booking system with a calendar.");
        assertThat(response.getChangeRequest()).isNotNull();

        GeneratedApp okBuild = new GeneratedApp();
        okBuild.setBuildStatus("SUCCESS");
        when(mockBuildService.buildApp(startupId)).thenReturn(okBuild);

        AppChangeRequestDto approved = chatService.approveChangeRequest(startupId, response.getChangeRequest().getId());
        assertThat(approved.getStatus()).isEqualTo("SUCCEEDED");

        assertThat(workspaceService.getStartupWorkspace(startupId).resolve("src/components/BookingCalendar.tsx")).exists();
        String calendar = workspaceService.readFileContent(startupId, "src/components/BookingCalendar.tsx").content();
        assertThat(calendar).contains("Interactive Calendar Booking System");
        assertThat(calendar).contains("timeSlots");
    }

    @Test
    @DisplayName("Coworker Prompt: 'Add a product catalogue and shopping cart'")
    void testCoworkerPromptProductCatalogCart() throws IOException {
        Long startupId = 504L;
        Startup s = new Startup();
        s.setId(startupId);
        s.setName("VeloCare");
        when(mockStartupRepository.findById(startupId)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(startupId);
        when(mockAppRepository.findByStartupId(startupId)).thenReturn(Optional.of(app));

        workspaceService.initializeStartupWorkspace(startupId);
        workspaceService.writeFileContent(startupId, "src/App.tsx", "import { useState, useEffect } from 'react';\nexport default function App() {\n  return <main></main>;\n}");

        CoworkerChatMessageDto response = chatService.processUserMessage(startupId, "Add a product catalogue and shopping cart.");
        assertThat(response.getChangeRequest()).isNotNull();

        GeneratedApp okBuild = new GeneratedApp();
        okBuild.setBuildStatus("SUCCESS");
        when(mockBuildService.buildApp(startupId)).thenReturn(okBuild);

        AppChangeRequestDto approved = chatService.approveChangeRequest(startupId, response.getChangeRequest().getId());
        assertThat(approved.getStatus()).isEqualTo("SUCCEEDED");

        assertThat(workspaceService.getStartupWorkspace(startupId).resolve("src/components/ProductCatalogCart.tsx")).exists();
        String catalog = workspaceService.readFileContent(startupId, "src/components/ProductCatalogCart.tsx").content();
        assertThat(catalog).contains("Product Catalogue & Cart");
        assertThat(catalog).contains("Shopping Bag");
    }

    @Test
    @DisplayName("Coworker Prompt: 'Change the dashboard to show sales and customer analytics'")
    void testCoworkerPromptSalesAnalytics() throws IOException {
        Long startupId = 505L;
        Startup s = new Startup();
        s.setId(startupId);
        s.setName("VeloCare");
        when(mockStartupRepository.findById(startupId)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(startupId);
        when(mockAppRepository.findByStartupId(startupId)).thenReturn(Optional.of(app));

        workspaceService.initializeStartupWorkspace(startupId);
        workspaceService.writeFileContent(startupId, "src/App.tsx", "import { useState, useEffect } from 'react';\nexport default function App() {\n  return <main></main>;\n}");

        CoworkerChatMessageDto response = chatService.processUserMessage(startupId, "Change the dashboard to show sales and customer analytics.");
        assertThat(response.getChangeRequest()).isNotNull();

        GeneratedApp okBuild = new GeneratedApp();
        okBuild.setBuildStatus("SUCCESS");
        when(mockBuildService.buildApp(startupId)).thenReturn(okBuild);

        AppChangeRequestDto approved = chatService.approveChangeRequest(startupId, response.getChangeRequest().getId());
        assertThat(approved.getStatus()).isEqualTo("SUCCEEDED");

        assertThat(workspaceService.getStartupWorkspace(startupId).resolve("src/components/SalesAnalytics.tsx")).exists();
        String analytics = workspaceService.readFileContent(startupId, "src/components/SalesAnalytics.tsx").content();
        assertThat(analytics).contains("Sales & Customer Analytics Engine");
        assertThat(analytics).contains("Monthly Recurring Revenue");
    }

    @Test
    @DisplayName("Coworker Prompt: 'Make this website responsive on mobile'")
    void testCoworkerPromptMobileResponsiveness() throws IOException {
        Long startupId = 506L;
        Startup s = new Startup();
        s.setId(startupId);
        s.setName("VeloCare");
        when(mockStartupRepository.findById(startupId)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(startupId);
        when(mockAppRepository.findByStartupId(startupId)).thenReturn(Optional.of(app));

        workspaceService.initializeStartupWorkspace(startupId);
        workspaceService.writeFileContent(startupId, "src/App.tsx", "export default function App() {\n  return (\n    <header>\n      <nav className=\"flex items-center gap-1 bg-slate-950 p-1 rounded-xl border border-slate-800 text-xs font-semibold\"></nav>\n    </header>\n  );\n}");

        CoworkerChatMessageDto response = chatService.processUserMessage(startupId, "Make this website responsive on mobile.");
        assertThat(response.getChangeRequest()).isNotNull();

        GeneratedApp okBuild = new GeneratedApp();
        okBuild.setBuildStatus("SUCCESS");
        when(mockBuildService.buildApp(startupId)).thenReturn(okBuild);

        AppChangeRequestDto approved = chatService.approveChangeRequest(startupId, response.getChangeRequest().getId());
        assertThat(approved.getStatus()).isEqualTo("SUCCEEDED");

        String appTsx = workspaceService.readFileContent(startupId, "src/App.tsx").content();
        assertThat(appTsx).contains("mobileMenuOpen");
        assertThat(appTsx).contains("md:hidden");
    }

    @Test
    @DisplayName("Coworker Prompt: 'Fix the broken booking form'")
    void testCoworkerPromptFixBookingForm() throws IOException {
        Long startupId = 507L;
        Startup s = new Startup();
        s.setId(startupId);
        s.setName("VeloCare");
        when(mockStartupRepository.findById(startupId)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(startupId);
        when(mockAppRepository.findByStartupId(startupId)).thenReturn(Optional.of(app));

        workspaceService.initializeStartupWorkspace(startupId);
        workspaceService.writeFileContent(startupId, "src/components/ServiceBookingForm.tsx", "export default function ServiceBookingForm() { return <div>Broken Form</div>; }");

        CoworkerChatMessageDto response = chatService.processUserMessage(startupId, "Fix the broken booking form.");
        assertThat(response.getChangeRequest()).isNotNull();
        assertThat(response.getChangeRequest().getProposedChanges().get(0).getRelativePath()).isEqualTo("src/components/ServiceBookingForm.tsx");

        GeneratedApp okBuild = new GeneratedApp();
        okBuild.setBuildStatus("SUCCESS");
        when(mockBuildService.buildApp(startupId)).thenReturn(okBuild);

        AppChangeRequestDto approved = chatService.approveChangeRequest(startupId, response.getChangeRequest().getId());
        assertThat(approved.getStatus()).isEqualTo("SUCCEEDED");

        String formCode = workspaceService.readFileContent(startupId, "src/components/ServiceBookingForm.tsx").content();
        assertThat(formCode).contains("Create Repair Job Ticket");
        assertThat(formCode).contains("onBookingCreated");
    }
}
