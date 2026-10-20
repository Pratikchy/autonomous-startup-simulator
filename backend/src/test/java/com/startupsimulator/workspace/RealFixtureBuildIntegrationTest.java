package com.startupsimulator.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.agent.LLMService;
import com.startupsimulator.generator.DomainAppSynthesizer;
import com.startupsimulator.generator.ProductSpecGenerator;
import com.startupsimulator.model.GeneratedApp;
import com.startupsimulator.model.Startup;
import com.startupsimulator.repository.AgentMessageRepository;
import com.startupsimulator.repository.GeneratedAppRepository;
import com.startupsimulator.repository.StartupRepository;
import com.startupsimulator.service.AppBuildService;
import com.startupsimulator.service.AppGeneratorService;
import com.startupsimulator.service.AppWorkspaceService;
import com.startupsimulator.trace.ExecutionTraceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RealFixtureBuildIntegrationTest {

    @TempDir
    Path tempWorkspaceRoot;

    private StartupRepository startupRepository;
    private GeneratedAppRepository appRepository;
    private ExecutionTraceService traceService;
    private AppWorkspaceService workspaceService;
    private LLMService llmService;
    private ProductSpecGenerator specGenerator;
    private DomainAppSynthesizer appSynthesizer;
    private AppBuildService realBuildService;
    private AgentMessageRepository messageRepository;
    private ObjectMapper objectMapper;
    private AppGeneratorService generatorService;

    @BeforeEach
    void setUp() {
        startupRepository = mock(StartupRepository.class);
        appRepository = mock(GeneratedAppRepository.class);
        traceService = mock(ExecutionTraceService.class);
        llmService = mock(LLMService.class);
        messageRepository = mock(AgentMessageRepository.class);
        objectMapper = new ObjectMapper();

        when(llmService.isRealProvider()).thenReturn(false);
        when(messageRepository.findByStartupIdOrderById(anyLong())).thenReturn(Collections.emptyList());

        workspaceService = new AppWorkspaceService(tempWorkspaceRoot.toString());
        specGenerator = new ProductSpecGenerator(llmService, objectMapper);
        appSynthesizer = new DomainAppSynthesizer();

        // Use real AppBuildService with host frontend/node_modules
        Path nodeModulesPath = Paths.get("../frontend/node_modules").toAbsolutePath().normalize();
        realBuildService = new AppBuildService(workspaceService, appRepository, nodeModulesPath.toString());

        generatorService = new AppGeneratorService(
                workspaceService,
                startupRepository,
                appRepository,
                traceService,
                specGenerator,
                appSynthesizer,
                realBuildService,
                messageRepository,
                objectMapper,
                nodeModulesPath.toString()
        );

        when(appRepository.save(any(GeneratedApp.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Build Fixture 1: DermaClear AI (Skincare) builds to real production bundle")
    void testRealBuildSkincareFixture() throws IOException {
        Long id = 601L;
        Startup s = new Startup();
        s.setId(id);
        s.setName("DermaClear AI");
        s.setOriginalIdea("Personalized skincare routine builder and AI barcode ingredient scanner for acne-prone skin");
        s.setValueProposition("Clinical skincare clarity in your pocket");
        when(startupRepository.findById(id)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(id);
        app.setAppName(s.getName());
        when(appRepository.findByStartupId(id)).thenReturn(Optional.of(app));

        GeneratedApp result = generatorService.generateApp(id);

        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getBuildStatus()).isEqualTo("SUCCESS");

        Path ws = workspaceService.getStartupWorkspace(id);
        assertThat(ws.resolve("dist/index.html")).exists();
        assertThat(ws.resolve("src/components/RoutineBuilder.tsx")).exists();
        assertThat(ws.resolve("src/components/ProductScanner.tsx")).exists();
    }

    @Test
    @DisplayName("Build Fixture 2: QuickFeast (Food Delivery) builds to real production bundle")
    void testRealBuildFoodDeliveryFixture() throws IOException {
        Long id = 602L;
        Startup s = new Startup();
        s.setId(id);
        s.setName("QuickFeast");
        s.setOriginalIdea("Hyperlocal gourmet food delivery app connecting independent chef kitchens with urban foodies");
        s.setValueProposition("Artisanal hot meals delivered in under 25 minutes");
        when(startupRepository.findById(id)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(id);
        app.setAppName(s.getName());
        when(appRepository.findByStartupId(id)).thenReturn(Optional.of(app));

        GeneratedApp result = generatorService.generateApp(id);

        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getBuildStatus()).isEqualTo("SUCCESS");

        Path ws = workspaceService.getStartupWorkspace(id);
        assertThat(ws.resolve("dist/index.html")).exists();
        assertThat(ws.resolve("src/components/RestaurantCatalog.tsx")).exists();
        assertThat(ws.resolve("src/components/CartDrawer.tsx")).exists();
        assertThat(ws.resolve("src/components/OrderStatusTracker.tsx")).exists();
    }

    @Test
    @DisplayName("Build Fixture 3: VeloFix Workshop (Bike Workshop) builds to real production bundle")
    void testRealBuildBikeWorkshopFixture() throws IOException {
        Long id = 603L;
        Startup s = new Startup();
        s.setId(id);
        s.setName("VeloFix Workshop");
        s.setOriginalIdea("Bicycle repair shop management software with service Kanban, customer booking, and spare parts inventory");
        s.setValueProposition("Zero lost repair tickets and 35% faster turnaround");
        when(startupRepository.findById(id)).thenReturn(Optional.of(s));

        GeneratedApp app = new GeneratedApp();
        app.setStartupId(id);
        app.setAppName(s.getName());
        when(appRepository.findByStartupId(id)).thenReturn(Optional.of(app));

        GeneratedApp result = generatorService.generateApp(id);

        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getBuildStatus()).isEqualTo("SUCCESS");

        Path ws = workspaceService.getStartupWorkspace(id);
        assertThat(ws.resolve("dist/index.html")).exists();
        assertThat(ws.resolve("src/components/JobKanbanBoard.tsx")).exists();
        assertThat(ws.resolve("src/components/ServiceBookingForm.tsx")).exists();
        assertThat(ws.resolve("src/components/InventoryManager.tsx")).exists();
    }
}
