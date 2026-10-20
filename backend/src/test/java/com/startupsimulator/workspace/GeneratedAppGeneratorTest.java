package com.startupsimulator.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.agent.LLMService;
import com.startupsimulator.generator.DomainAppSynthesizer;
import com.startupsimulator.generator.ProductSpecGenerator;
import com.startupsimulator.generator.spec.ProductSpec;
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
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeneratedAppGeneratorTest {

    @TempDir
    Path tempWorkspaceRoot;

    private StartupRepository startupRepository;
    private GeneratedAppRepository appRepository;
    private ExecutionTraceService traceService;
    private AppWorkspaceService workspaceService;
    private LLMService llmService;
    private ProductSpecGenerator specGenerator;
    private DomainAppSynthesizer appSynthesizer;
    private AppBuildService buildService;
    private AgentMessageRepository messageRepository;
    private ObjectMapper objectMapper;
    private AppGeneratorService generatorService;

    @BeforeEach
    void setUp() {
        startupRepository = mock(StartupRepository.class);
        appRepository = mock(GeneratedAppRepository.class);
        traceService = mock(ExecutionTraceService.class);
        llmService = mock(LLMService.class);
        buildService = mock(AppBuildService.class);
        messageRepository = mock(AgentMessageRepository.class);
        objectMapper = new ObjectMapper();

        when(llmService.isRealProvider()).thenReturn(false); // Deterministic domain analyzer path
        when(messageRepository.findByStartupIdOrderById(anyLong())).thenReturn(Collections.emptyList());

        workspaceService = new AppWorkspaceService(tempWorkspaceRoot.toString());
        specGenerator = new ProductSpecGenerator(llmService, objectMapper);
        appSynthesizer = new DomainAppSynthesizer();

        generatorService = new AppGeneratorService(
                workspaceService,
                startupRepository,
                appRepository,
                traceService,
                specGenerator,
                appSynthesizer,
                buildService,
                messageRepository,
                objectMapper,
                "../frontend/node_modules"
        );

        when(appRepository.save(any(GeneratedApp.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock buildService to report SUCCESS by default
        GeneratedApp successfulBuild = new GeneratedApp();
        successfulBuild.setBuildStatus("SUCCESS");
        successfulBuild.setBuildOutput("vite v5.4.10 built in 1.1s");
        when(buildService.buildApp(anyLong())).thenReturn(successfulBuild);
    }

    @Test
    @DisplayName("Fixture 1: Skincare App generates routine tracker, barcode scanner, and ingredient glossary")
    void testSkincareAppGeneration() throws IOException {
        Long startupId = 101L;

        Startup startup = new Startup();
        startup.setId(startupId);
        startup.setName("DermaClear AI");
        startup.setOriginalIdea("Personalized skincare routine builder and AI barcode ingredient scanner for acne-prone skin");
        startup.setProblem("Consumers struggle to determine if cosmetics contain pore-clogging or irritating ingredients");
        startup.setSolution("Instant camera ingredient analysis and customized AM/PM routine checklist");
        startup.setValueProposition("Clinical skincare clarity in your pocket");
        startup.setTargetAudience("Acne sufferers and clean skincare shoppers");
        startup.setBusinessModel("Freemium + Brand Partnerships");

        when(startupRepository.findById(startupId)).thenReturn(Optional.of(startup));
        when(appRepository.findByStartupId(startupId)).thenReturn(Optional.empty());

        GeneratedApp app = generatorService.generateApp(startupId);

        assertThat(app).isNotNull();
        assertThat(app.getStatus()).isEqualTo("READY");
        assertThat(app.getBuildStatus()).isEqualTo("SUCCESS");
        assertThat(app.getProductSpecJson()).contains("Skincare & Personalized Dermatology");

        Path ws = workspaceService.getStartupWorkspace(startupId);
        assertThat(ws.resolve("src/App.tsx")).exists();
        assertThat(ws.resolve("src/types.ts")).exists();
        assertThat(ws.resolve("src/mockData.ts")).exists();
        assertThat(ws.resolve("src/components/RoutineBuilder.tsx")).exists();
        assertThat(ws.resolve("src/components/ProductScanner.tsx")).exists();
        assertThat(ws.resolve("src/components/IngredientGlossary.tsx")).exists();
        assertThat(ws.resolve("src/components/SkinProfile.tsx")).exists();

        // Verify product-specific code
        String appContent = Files.readString(ws.resolve("src/App.tsx"));
        assertThat(appContent).contains("DermaClear AI");
        assertThat(appContent).contains("Personalized Dermatology Companion");
        assertThat(appContent).contains("RoutineBuilder");
        assertThat(appContent).contains("ProductScanner");

        // Verify NO generic workload slider or generic waitlist
        assertThat(appContent).doesNotContain("Projected Daily Savings");
        assertThat(appContent).doesNotContain("Simulated Demand Load");
    }

    @Test
    @DisplayName("Fixture 2: Food Delivery App generates restaurant catalog, menu viewer, cart, and live tracker")
    void testFoodDeliveryAppGeneration() throws IOException {
        Long startupId = 102L;

        Startup startup = new Startup();
        startup.setId(startupId);
        startup.setName("QuickFeast");
        startup.setOriginalIdea("Hyperlocal gourmet food delivery app connecting independent chef kitchens with busy urban professionals");
        startup.setProblem("High restaurant aggregator markups and cold, delayed delivery");
        startup.setSolution("Direct culinary ordering with real-time driver tracking and curated menus");
        startup.setValueProposition("Artisanal hot meals delivered in under 25 minutes");
        startup.setTargetAudience("Urban foodies and remote professionals");
        startup.setBusinessModel("10% order commission + delivery fee");

        when(startupRepository.findById(startupId)).thenReturn(Optional.of(startup));
        when(appRepository.findByStartupId(startupId)).thenReturn(Optional.empty());

        GeneratedApp app = generatorService.generateApp(startupId);

        assertThat(app).isNotNull();
        assertThat(app.getStatus()).isEqualTo("READY");
        assertThat(app.getProductSpecJson()).contains("Food");

        Path ws = workspaceService.getStartupWorkspace(startupId);
        assertThat(ws.resolve("src/App.tsx")).exists();
        assertThat(ws.resolve("src/components/RestaurantCatalog.tsx")).exists();
        assertThat(ws.resolve("src/components/MenuViewer.tsx")).exists();
        assertThat(ws.resolve("src/components/CartDrawer.tsx")).exists();
        assertThat(ws.resolve("src/components/OrderStatusTracker.tsx")).exists();

        String appContent = Files.readString(ws.resolve("src/App.tsx"));
        assertThat(appContent).contains("QuickFeast");
        assertThat(appContent).contains("Curated Culinary Delivery");
        assertThat(appContent).contains("CartDrawer");
        assertThat(appContent).contains("OrderStatusTracker");
    }

    @Test
    @DisplayName("Fixture 3: Bike Workshop App generates service Kanban, intake booking, and parts inventory")
    void testBikeWorkshopAppGeneration() throws IOException {
        Long startupId = 103L;

        Startup startup = new Startup();
        startup.setId(startupId);
        startup.setName("VeloCare Workshop");
        startup.setOriginalIdea("Bicycle repair shop management software with service Kanban, customer booking, and spare parts inventory");
        startup.setProblem("Independent bike shops use paper job cards and lose track of spare parts and customer communications");
        startup.setSolution("Digital repair stand Kanban board with automated parts inventory tracking");
        startup.setValueProposition("Zero lost repair tickets and 35% faster turnaround");
        startup.setTargetAudience("Bike mechanics and cycling shop owners");
        startup.setBusinessModel("Monthly SaaS subscription");

        when(startupRepository.findById(startupId)).thenReturn(Optional.of(startup));
        when(appRepository.findByStartupId(startupId)).thenReturn(Optional.empty());

        GeneratedApp app = generatorService.generateApp(startupId);

        assertThat(app).isNotNull();
        assertThat(app.getStatus()).isEqualTo("READY");
        assertThat(app.getProductSpecJson()).contains("Workshop");

        Path ws = workspaceService.getStartupWorkspace(startupId);
        assertThat(ws.resolve("src/App.tsx")).exists();
        assertThat(ws.resolve("src/components/JobKanbanBoard.tsx")).exists();
        assertThat(ws.resolve("src/components/ServiceBookingForm.tsx")).exists();
        assertThat(ws.resolve("src/components/InventoryManager.tsx")).exists();

        String appContent = Files.readString(ws.resolve("src/App.tsx"));
        assertThat(appContent).contains("VeloCare Workshop");
        assertThat(appContent).contains("Bicycle Workshop Operations");
        assertThat(appContent).contains("JobKanbanBoard");
        assertThat(appContent).contains("InventoryManager");
    }

    @Test
    @DisplayName("Structural Diversity: Distinct startup ideas produce completely different components and architectures")
    void testStructuralDiversityBetweenFixtures() throws IOException {
        Long idSkincare = 201L;
        Long idFood = 202L;
        Long idBike = 203L;

        Startup s1 = new Startup();
        s1.setId(idSkincare);
        s1.setName("DermaAI");
        s1.setOriginalIdea("Skincare ingredient scanner and routine builder");
        when(startupRepository.findById(idSkincare)).thenReturn(Optional.of(s1));

        Startup s2 = new Startup();
        s2.setId(idFood);
        s2.setName("UrbanMeal");
        s2.setOriginalIdea("Food delivery app with restaurant menus and live courier tracking");
        when(startupRepository.findById(idFood)).thenReturn(Optional.of(s2));

        Startup s3 = new Startup();
        s3.setId(idBike);
        s3.setName("SprocketFlow");
        s3.setOriginalIdea("Bike repair workshop job card and parts inventory management");
        when(startupRepository.findById(idBike)).thenReturn(Optional.of(s3));

        generatorService.generateApp(idSkincare);
        generatorService.generateApp(idFood);
        generatorService.generateApp(idBike);

        Path ws1 = workspaceService.getStartupWorkspace(idSkincare);
        Path ws2 = workspaceService.getStartupWorkspace(idFood);
        Path ws3 = workspaceService.getStartupWorkspace(idBike);

        // Skincare has RoutineBuilder and ProductScanner; neither of the others do
        assertThat(ws1.resolve("src/components/RoutineBuilder.tsx")).exists();
        assertThat(ws2.resolve("src/components/RoutineBuilder.tsx")).doesNotExist();
        assertThat(ws3.resolve("src/components/RoutineBuilder.tsx")).doesNotExist();

        // Food has CartDrawer and OrderStatusTracker; neither of the others do
        assertThat(ws2.resolve("src/components/CartDrawer.tsx")).exists();
        assertThat(ws1.resolve("src/components/CartDrawer.tsx")).doesNotExist();
        assertThat(ws3.resolve("src/components/CartDrawer.tsx")).doesNotExist();

        // Workshop has JobKanbanBoard and InventoryManager; neither of the others do
        assertThat(ws3.resolve("src/components/JobKanbanBoard.tsx")).exists();
        assertThat(ws1.resolve("src/components/JobKanbanBoard.tsx")).doesNotExist();
        assertThat(ws2.resolve("src/components/JobKanbanBoard.tsx")).doesNotExist();
    }

    @Test
    @DisplayName("Rollback on build failure: Preserves previously working version when regeneration fails")
    void testRollbackOnBuildFailure() throws IOException {
        Long startupId = 301L;

        Startup startup = new Startup();
        startup.setId(startupId);
        startup.setName("SafeVenture");
        startup.setOriginalIdea("Skincare routine and ingredient analyzer");
        when(startupRepository.findById(startupId)).thenReturn(Optional.of(startup));

        // 1. First generation succeeds
        GeneratedApp app = generatorService.generateApp(startupId);
        assertThat(app.getStatus()).isEqualTo("READY");

        Path ws = workspaceService.getStartupWorkspace(startupId);
        String workingAppCode = Files.readString(ws.resolve("src/App.tsx"));
        assertThat(workingAppCode).contains("SafeVenture");

        // 2. Second generation (regeneration) encounters a build failure
        GeneratedApp failedBuild = new GeneratedApp();
        failedBuild.setBuildStatus("FAILED");
        failedBuild.setBuildOutput("TS2304: Cannot find name 'BrokenSymbol'");
        when(buildService.buildApp(startupId)).thenReturn(failedBuild);

        assertThatThrownBy(() -> generatorService.generateApp(startupId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed build verification");

        // 3. Verify workspace was restored to the working version
        String restoredAppCode = Files.readString(ws.resolve("src/App.tsx"));
        assertThat(restoredAppCode).isEqualTo(workingAppCode);
    }

    @Test
    @DisplayName("Regression: Skincare startup with 'skin barrier repair' and 'routine maintenance' does NOT match bike workshop")
    void testKeywordCollisionRegressionSkincareDoesNotGenerateBikeWorkshop() throws IOException {
        Long startupId = 401L;

        Startup startup = new Startup();
        startup.setId(startupId);
        startup.setName("GlowRestore");
        startup.setOriginalIdea("A dermatological solution for skin barrier repair and daily routine maintenance to treat severe dryness");
        startup.setProblem("Patients struggle with damaged skin moisture barriers and lack proper repair maintenance schedules");
        startup.setSolution("AI routine tracking and ingredient analysis to accelerate skin barrier repair");
        startup.setValueProposition("Restore your skin barrier within 14 days");
        startup.setTargetAudience("Dermatology patients and skincare enthusiasts");
        startup.setBusinessModel("D2C subscription");

        when(startupRepository.findById(startupId)).thenReturn(Optional.of(startup));
        when(appRepository.findByStartupId(startupId)).thenReturn(Optional.empty());

        GeneratedApp app = generatorService.generateApp(startupId);

        assertThat(app).isNotNull();
        assertThat(app.getStatus()).isEqualTo("READY");
        assertThat(app.getProductSpecJson()).contains("Skincare");
        assertThat(app.getProductSpecJson()).doesNotContain("Bicycle Workshop");

        Path ws = workspaceService.getStartupWorkspace(startupId);
        // Verify Skincare components exist
        assertThat(ws.resolve("src/components/RoutineBuilder.tsx")).exists();
        assertThat(ws.resolve("src/components/ProductScanner.tsx")).exists();

        // Verify Bike Workshop components do NOT exist
        assertThat(ws.resolve("src/components/JobKanbanBoard.tsx")).doesNotExist();
        assertThat(ws.resolve("src/components/InventoryManager.tsx")).doesNotExist();

        String appContent = Files.readString(ws.resolve("src/App.tsx"));
        assertThat(appContent).contains("GlowRestore");
        assertThat(appContent).contains("RoutineBuilder");
        assertThat(appContent).doesNotContain("Bicycle Workshop Operations");
        assertThat(appContent).doesNotContain("VeloCare");
    }

    @Test
    @DisplayName("Custom Venture: CleanTech logistics SaaS produces tailored enterprise app instead of generic templates")
    void testCustomVentureGenerationCleanTech() throws IOException {
        Long startupId = 402L;

        Startup startup = new Startup();
        startup.setId(startupId);
        startup.setName("EcoFleet Audit");
        startup.setOriginalIdea("Enterprise carbon fleet accounting and automated emission credit offset marketplace for freight carriers");
        startup.setProblem("Logistics carriers struggle to measure diesel fleet emissions and purchase verified offset credits");
        startup.setSolution("Real-time telematics GHG analytics and direct carbon credit procurement ledger");
        startup.setValueProposition("Instant ESG compliance and 40% cheaper carbon offsets");
        startup.setTargetAudience("Fleet directors and ESG officers");
        startup.setBusinessModel("Enterprise annual software license + 2% transaction fee");

        when(startupRepository.findById(startupId)).thenReturn(Optional.of(startup));
        when(appRepository.findByStartupId(startupId)).thenReturn(Optional.empty());

        GeneratedApp app = generatorService.generateApp(startupId);

        assertThat(app).isNotNull();
        assertThat(app.getStatus()).isEqualTo("READY");

        Path ws = workspaceService.getStartupWorkspace(startupId);
        // Custom app structure
        assertThat(ws.resolve("src/components/DashboardOverview.tsx")).exists();
        assertThat(ws.resolve("src/components/ItemManager.tsx")).exists();
        assertThat(ws.resolve("src/components/CreateItemForm.tsx")).exists();
        assertThat(ws.resolve("src/components/AnalyticsView.tsx")).exists();
        assertThat(ws.resolve("src/mockData.ts")).exists();

        // Ensure NO unrelated bicycle or skincare components
        assertThat(ws.resolve("src/components/JobKanbanBoard.tsx")).doesNotExist();
        assertThat(ws.resolve("src/components/RoutineBuilder.tsx")).doesNotExist();
        assertThat(ws.resolve("src/components/RestaurantCatalog.tsx")).doesNotExist();

        String appContent = Files.readString(ws.resolve("src/App.tsx"));
        assertThat(appContent).contains("EcoFleet Audit");
        assertThat(appContent).contains("DashboardOverview");
        assertThat(appContent).contains("ItemManager");
    }
}
