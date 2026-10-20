package com.startupsimulator.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.agent.LLMService;
import com.startupsimulator.generator.spec.DesignSystemSpec;
import com.startupsimulator.generator.spec.ProductSpec;
import com.startupsimulator.generator.spec.ScreenSpec;
import com.startupsimulator.model.AgentMessage;
import com.startupsimulator.model.Startup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generates a structured product specification for a virtual startup.
 * Uses the configured AI provider when available, or a deterministic
 * domain semantic analyzer when running offline/fallback.
 */
@Component
public class ProductSpecGenerator {

    private static final Logger log = LoggerFactory.getLogger(ProductSpecGenerator.class);

    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public ProductSpecGenerator(LLMService llmService, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    public ProductSpec generateProductSpec(Startup startup, List<AgentMessage> messages) {
        if (llmService.isRealProvider()) {
            try {
                ProductSpec spec = generateWithLLM(startup, messages);
                if (spec != null && spec.screens() != null && !spec.screens().isEmpty()) {
                    log.info("Successfully synthesized ProductSpec via LLM for startup {}", startup.getId());
                    return spec;
                }
            } catch (Exception e) {
                log.warn("LLM ProductSpec generation failed for startup {}: {}. Falling back to domain synthesis.",
                        startup.getId(), e.getMessage());
            }
        }

        return synthesizeDeterministicSpec(startup);
    }

    private ProductSpec generateWithLLM(Startup startup, List<AgentMessage> messages) {
        String systemPrompt = """
                You are the Principal Product Architect for a startup studio.
                Task: Generate a detailed, structured product specification for an isolated React + TypeScript web application.

                OUTPUT RULES:
                1. Return ONLY a single raw JSON object (NO markdown backticks, NO markdown fences, NO extra commentary).
                2. Match this exact JSON schema:
                {
                  "appName": "string",
                  "category": "string (e.g. Skincare, Food Delivery, Bike Workshop, Project Management, etc.)",
                  "tagline": "string",
                  "summary": "string",
                  "targetUsers": ["string", "string"],
                  "primaryWorkflows": ["string", "string", "string"],
                  "screens": [
                    {
                      "id": "string (lowercase slug)",
                      "title": "string",
                      "icon": "emoji",
                      "description": "string",
                      "keyFeatures": ["string", "string"]
                    }
                  ],
                  "reusableComponents": ["string", "string"],
                  "designSystem": {
                    "primaryColor": "hex/hsl or tailwind color",
                    "secondaryColor": "hex/hsl or tailwind color",
                    "accentGlow": "string",
                    "themeMode": "dark",
                    "mood": "string"
                  },
                  "dataRequirements": ["string", "string"],
                  "acceptanceCriteria": ["string", "string"],
                  "simulatedNotice": "string (honest disclosure of mock client-side state vs production backend)"
                }
                3. The product MUST have at least 3-4 distinct functional screens appropriate to the exact idea.
                4. DO NOT generate generic workload sliders, generic KPI cards, or simple waitlists unless the startup is literally a workload simulator.
                """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Startup Name: ").append(startup.getName()).append("\n");
        userPrompt.append("Original Idea: ").append(startup.getOriginalIdea()).append("\n");
        if (startup.getProblem() != null) userPrompt.append("Problem: ").append(startup.getProblem()).append("\n");
        if (startup.getSolution() != null) userPrompt.append("Solution: ").append(startup.getSolution()).append("\n");
        if (startup.getValueProposition() != null) userPrompt.append("Value Prop: ").append(startup.getValueProposition()).append("\n");
        if (startup.getTargetAudience() != null) userPrompt.append("Target Audience: ").append(startup.getTargetAudience()).append("\n");
        if (startup.getBusinessModel() != null) userPrompt.append("Business Model: ").append(startup.getBusinessModel()).append("\n");
        if (startup.getMvpDirection() != null) userPrompt.append("MVP Direction: ").append(startup.getMvpDirection()).append("\n");

        if (messages != null && !messages.isEmpty()) {
            userPrompt.append("\nRecent Agent Insights:\n");
            for (AgentMessage m : messages.subList(Math.max(0, messages.size() - 4), messages.size())) {
                userPrompt.append("- [").append(m.getAgentType()).append("]: ").append(m.getContent()).append("\n");
            }
        }

        String rawResponse = llmService.generate(systemPrompt, userPrompt.toString());
        rawResponse = cleanJsonOutput(rawResponse);

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String appName = root.path("appName").asText(startup.getName());
            String category = root.path("category").asText("Web Application");
            String tagline = root.path("tagline").asText(startup.getValueProposition() != null ? startup.getValueProposition() : startup.getOriginalIdea());
            String summary = root.path("summary").asText(startup.getSolution() != null ? startup.getSolution() : startup.getOriginalIdea());

            List<String> targetUsers = readStringList(root.path("targetUsers"));
            List<String> primaryWorkflows = readStringList(root.path("primaryWorkflows"));
            List<String> reusableComponents = readStringList(root.path("reusableComponents"));
            List<String> dataRequirements = readStringList(root.path("dataRequirements"));
            List<String> acceptanceCriteria = readStringList(root.path("acceptanceCriteria"));
            String simulatedNotice = root.path("simulatedNotice").asText("Interactive client-side prototype. Data stored in local browser state.");

            JsonNode dsNode = root.path("designSystem");
            DesignSystemSpec designSystem = new DesignSystemSpec(
                    dsNode.path("primaryColor").asText("indigo-600"),
                    dsNode.path("secondaryColor").asText("sky-500"),
                    dsNode.path("accentGlow").asText("rgba(99, 102, 241, 0.25)"),
                    dsNode.path("themeMode").asText("dark"),
                    dsNode.path("mood").asText("Modern and responsive")
            );

            List<ScreenSpec> screens = new ArrayList<>();
            for (JsonNode sn : root.path("screens")) {
                screens.add(new ScreenSpec(
                        sn.path("id").asText("screen-" + screens.size()),
                        sn.path("title").asText("Overview"),
                        sn.path("icon").asText("📱"),
                        sn.path("description").asText("Screen overview"),
                        readStringList(sn.path("keyFeatures"))
                ));
            }

            if (!screens.isEmpty()) {
                return new ProductSpec(
                        appName, category, tagline, summary, targetUsers, primaryWorkflows,
                        screens, reusableComponents, designSystem, dataRequirements,
                        acceptanceCriteria, simulatedNotice
                );
            }
        } catch (Exception e) {
            log.warn("Failed to parse LLM ProductSpec JSON: {}", e.getMessage());
        }

        return null;
    }

    public ProductSpec synthesizeDeterministicSpec(Startup startup) {
        String name = startup.getName() != null ? startup.getName().toLowerCase() : "";
        String idea = startup.getOriginalIdea() != null ? startup.getOriginalIdea().toLowerCase() : "";
        String problem = startup.getProblem() != null ? startup.getProblem().toLowerCase() : "";
        String solution = startup.getSolution() != null ? startup.getSolution().toLowerCase() : "";
        String valProp = startup.getValueProposition() != null ? startup.getValueProposition().toLowerCase() : "";
        String audience = startup.getTargetAudience() != null ? startup.getTargetAudience().toLowerCase() : "";
        String corpus = String.join(" ", name, idea, problem, solution, valProp, audience);

        // 1. Skincare / Dermatology — requires explicit skincare or dermatological keywords
        boolean isSkincare = containsAny(corpus, "skincare", "skin care", "dermatol", "cosmetic", "beauty serum", "acne", "pore", "facial routine")
                || (corpus.contains("skin") && containsAny(corpus, "routine", "barrier", "face", "glow", "cleanser", "moisturiz", "ingredient", "epiderm"));
        if (isSkincare) {
            return buildSkincareSpec(startup);
        }

        // 2. Bicycle / Bike Workshop — requires bicycle/bike context AND workshop/repair/parts operations
        //    (Prevents unrelated startups with words like "repair" or "maintenance" from triggering bike workshop)
        boolean isBikeContext = containsAny(corpus, "bicycle", "bike", "cycling", "two-wheeler", "velo", "ebike", "e-bike", "motorcycle");
        boolean isWorkshopOps = containsAny(corpus, "workshop", "repair", "mechanic", "maintenance", "service", "tune-up", "parts", "technician", "job card");
        if (isBikeContext && isWorkshopOps) {
            return buildBikeWorkshopSpec(startup);
        }

        // 3. Food Delivery / Culinary Logistics — requires culinary/food + delivery/restaurant context
        boolean isFoodDelivery = containsAny(corpus, "food delivery", "restaurant", "culinary", "takeout", "eats delivery", "meal delivery", "dish delivery", "cloud kitchen")
                || (corpus.contains("food") && containsAny(corpus, "delivery", "order", "menu", "dishes", "restaurant", "chef"));
        if (isFoodDelivery) {
            return buildFoodDeliverySpec(startup);
        }

        // 4. Agile Project Management / Sprint Kanban — requires agile/kanban/project management context
        boolean isAgileProject = containsAny(corpus, "kanban", "sprint planning", "scrum", "jira", "agile workflow", "issue tracker", "linear board")
                || (corpus.contains("project") && containsAny(corpus, "kanban", "board", "sprint", "backlog", "tasks", "scrum", "agile"));
        if (isAgileProject) {
            return buildProjectManagementSpec(startup);
        }

        // 5. Clean Energy / Battery & Smart Grid
        boolean isCleanEnergy = containsAny(corpus, "clean energy", "solar power", "battery storage", "electricity grid", "kwh", "renewable energy", "smart grid", "power tariff");
        if (isCleanEnergy) {
            return buildCleanEnergySpec(startup);
        }

        // 6. E-Commerce / Online Store
        boolean isEcommerce = containsAny(corpus, "ecommerce", "e-commerce", "online store", "shopping cart", "retail catalog", "clothing brand", "apparel shop", "product catalog")
                || (containsAny(corpus, "shop", "store") && containsAny(corpus, "cart", "checkout", "catalog", "products"));
        if (isEcommerce) {
            return buildEcommerceSpec(startup);
        }

        // 7. Bespoke Custom Venture — tailored directly to the user's actual startup name, audience, problem, solution, and features
        return buildCustomVentureSpec(startup);
    }

    private ProductSpec buildSkincareSpec(Startup startup) {
        return new ProductSpec(
                startup.getName(),
                "Skincare & Personalized Dermatology",
                "Personalized skincare regimens with instant ingredient safety & barcode analysis",
                "End-to-end skincare companion featuring active routine tracking, camera barcode/ingredient scanner, formulation glossary, and customized skin profiling.",
                List.of("Skincare Enthusiasts", "Acne & Sensitive Skin Sufferers", "Clean Beauty Consumers"),
                List.of(
                        "Analyze cosmetic ingredients by scanning barcode or product photo",
                        "Build customized morning and evening routine with step completion tracking",
                        "Take clinical skin assessment to receive targeted ingredient recommendations",
                        "Search toxicology, pore-clogging ratings, and active concentrations"
                ),
                List.of(
                        new ScreenSpec("routine", "My Daily Routine", "✨", "Interactive morning & evening skincare steps with completion checklists and time-of-day filtering", List.of("AM / PM regime tabs", "Step completion toggles", "Add custom product to routine", "Persistence to browser storage")),
                        new ScreenSpec("scanner", "Product Scanner", "📷", "Simulated camera and barcode dropzone with real-time comedogenic ratings and active ingredient breakdowns", List.of("Camera scan simulator", "Barcode / photo drag-and-drop", "Safety & compatibility score (0-100%)", "Direct 'Add to Routine' action")),
                        new ScreenSpec("glossary", "Ingredient Analysis", "🔬", "Searchable clinical database of cosmetic ingredients, molecular safety ratings, and functions", List.of("Search by ingredient name (e.g. Niacinamide, Retinol)", "Category filter pills (Hydrators, Actives, Exfoliants)", "Irritation and comedogenic ratings")),
                        new ScreenSpec("profile", "Skin Profile & Quiz", "🧬", "Interactive diagnostic assessment calculating skin type, sensitivity levels, and personalized tips", List.of("Skin type selector (Dry, Oily, Combination, Sensitive)", "Concerns checklist (Acne, Redness, Anti-Aging)", "Instant regimen prescription generator"))
                ),
                List.of("RoutineStepCard", "ProductScannerDropzone", "IngredientCard", "SkinProfileQuiz", "HeaderNav"),
                new DesignSystemSpec("emerald-500", "teal-400", "rgba(16, 185, 129, 0.25)", "dark", "Clean, clinical, botanical aesthetic with soothing green accents"),
                List.of("Skin Profile Data", "Routine Steps (AM/PM)", "Scanned Product Logs", "Saved Ingredients"),
                List.of("Routine checklist persists state in local storage", "Scanner provides instant ingredient ratings", "Search filters glossary items in real-time"),
                "⚠️ Clinical notice: Client-side prototype. Medical evaluations and image recognition are simulated for demonstration."
        );
    }

    private ProductSpec buildFoodDeliverySpec(Startup startup) {
        return new ProductSpec(
                startup.getName(),
                "On-Demand Food & Culinary Logistics",
                "Fast, curated culinary delivery with live order tracking and interactive checkout",
                "Modern multi-restaurant food delivery platform featuring live cuisine filtering, interactive dish customizer, dynamic shopping cart with fee calculations, and step-by-step driver status tracker.",
                List.of("Busy Urban Professionals", "Gourmet Foodies", "Local Independent Restaurants"),
                List.of(
                        "Browse restaurant listings filtered by cuisine, rating, and delivery speed",
                        "Explore menus with dietary tags, dish descriptions, and custom quantity selection",
                        "Manage cart with instant subtotal, tax, delivery fee, and promo code discounts",
                        "Track live simulated delivery progress from kitchen preparation to doorstep"
                ),
                List.of(
                        new ScreenSpec("restaurants", "Explore Restaurants", "🍔", "Searchable catalog of local kitchens with cuisine badges, ratings, and delivery estimates", List.of("Real-time restaurant search", "Cuisine filter chips (Pizza, Sushi, Burgers, Healthy)", "Delivery time and minimum order indicators")),
                        new ScreenSpec("menu", "Restaurant Menu", "📋", "Categorized menu viewer with appetizers, chef mains, beverages, and dish customizers", List.of("Category quick navigation", "Dietary badges (Vegan, Gluten-Free, Chef Special)", "One-click 'Add to Cart' with instant counter")),
                        new ScreenSpec("cart", "Shopping Cart & Checkout", "🛒", "Full-featured cart management with quantity adjustments, promo discounts, and delivery fee calculation", List.of("Item increment / decrement / remove", "Promo code validation (e.g. FEAST20)", "Live order total breakdown", "Place order trigger with confirmation")),
                        new ScreenSpec("tracker", "Live Order Status", "🛵", "Real-time delivery progress tracker with driver card and stage progression", List.of("Multi-stage progress bar (Received -> Prep -> On The Way -> Delivered)", "Courier details with simulated contact", "Estimated arrival countdown timer"))
                ),
                List.of("RestaurantCard", "MenuItemRow", "CartSidebar", "OrderTrackerStepper", "HeaderNav"),
                new DesignSystemSpec("amber-500", "orange-400", "rgba(245, 158, 11, 0.25)", "dark", "Vibrant, appetizing culinary palette with warm golden tones"),
                List.of("Restaurant Catalog", "Menu Items", "Active Cart Items", "Submitted Orders"),
                List.of("Cart updates recalculate totals instantly", "Search filters restaurant listings", "Order placement initiates stage tracker"),
                "⚠️ Notice: Payment processing and GPS courier tracking are simulated client-side for evaluation."
        );
    }

    private ProductSpec buildBikeWorkshopSpec(Startup startup) {
        return new ProductSpec(
                startup.getName(),
                "Bike Workshop & Service Operations",
                "Streamlined job card Kanban, service booking, parts inventory, and invoice calculation",
                "Dedicated workshop management application for bicycle repair facilities, managing customer intake, service stages, spare parts stock, and labor cost estimation.",
                List.of("Workshop Mechanics & Service Leads", "Bike Shop Owners", "Cycling Customers"),
                List.of(
                        "Manage repair jobs across inspection, parts waiting, and repair stages on a live Kanban board",
                        "Book incoming repair appointments with customer details and bike specs",
                        "Monitor spare parts inventory with low-stock warnings and restock controls",
                        "Generate detailed itemized service invoices combining labor hours and parts cost"
                ),
                List.of(
                        new ScreenSpec("kanban", "Service Kanban Board", "🛠️", "Interactive job board organizing repair tickets by workflow stages with stage-advancement controls", List.of("Columns: Received, Inspection, Parts Waiting, In Repair, Ready", "Job priority badges (Urgent, High, Normal)", "Forward / Backward stage transition buttons", "Per-column counter badges")),
                        new ScreenSpec("booking", "Book New Service", "📝", "Customer intake form with bike make/model, service package selection, and drop-off scheduling", List.of("Service package picker (Tune-up, Overhaul, Brake Bleed)", "Form validation with instant customer ticket creation", "Adds new ticket directly to Kanban")),
                        new ScreenSpec("inventory", "Parts & Components", "⚙️", "Spare parts inventory ledger with current stock levels, unit costs, and one-click restock", List.of("Parts search & category filter (Chains, Brakes, Cassettes)", "Low-stock alert thresholds", "Quick +5 stock replenishment buttons")),
                        new ScreenSpec("invoices", "Invoice & Billing", "🧾", "Itemized service billing calculator combining labor hours and replacement components", List.of("Select repair ticket to generate invoice", "Labor rate calculator ($65/hr)", "Parts cost addition", "Print-ready itemized summary"))
                ),
                List.of("KanbanColumn", "JobCard", "BookingForm", "InventoryTable", "InvoiceCard", "HeaderNav"),
                new DesignSystemSpec("cyan-500", "blue-500", "rgba(6, 182, 212, 0.25)", "dark", "High-precision mechanical palette with neon cyan and industrial steel slate"),
                List.of("Repair Tickets", "Service Packages", "Inventory Parts Catalog", "Completed Invoices"),
                List.of("Moving jobs updates Kanban stage in local state", "Booking form creates valid tickets", "Inventory restock increments quantity"),
                "⚠️ Notice: Client-side operations MVP. Parts ordering and customer SMS alerts are simulated."
        );
    }

    private ProductSpec buildProjectManagementSpec(Startup startup) {
        return new ProductSpec(
                startup.getName(),
                "B2B Project & Agile Workflow SaaS",
                "High-velocity task tracking, interactive Kanban boards, and sprint analytics",
                "Collaborative project management workspace with interactive Kanban columns, task creation with priority and assignee controls, sprint burn-down metrics, and team activity feeds.",
                List.of("Engineering Teams", "Product Managers", "Agile Scrum Masters"),
                List.of(
                        "Track sprint user stories and tasks across To Do, In Progress, In Review, and Done",
                        "Create and edit tasks with assignees, story points, and priority badges",
                        "View sprint velocity charts, cycle time metrics, and completion percentage",
                        "Search and filter task backlog by status, assignee, and priority"
                ),
                List.of(
                        new ScreenSpec("board", "Sprint Kanban", "📋", "Drag/click agile board with column state transitions, assignee avatars, and priority badges", List.of("Columns: Backlog, In Progress, Review, Completed", "Move task status forward or backward", "Story points tally per column")),
                        new ScreenSpec("tasks", "Task Backlog", "✅", "Comprehensive list view of all project deliverables with search and priority filters", List.of("Filter by assignee and status", "Add new task modal with validation", "Task status toggle")),
                        new ScreenSpec("analytics", "Sprint Analytics", "📈", "Live velocity reports, completion percentages, and milestone burn-down meters", List.of("Sprint completion percentage meter", "Cycle time analytics", "Team workload distribution")),
                        new ScreenSpec("team", "Team Dashboard", "👥", "Team member capacity tracker with assigned tasks and status indicators", List.of("Member cards with active tasks count", "Role and availability badges"))
                ),
                List.of("KanbanBoard", "TaskRow", "NewTaskModal", "SprintMetrics", "HeaderNav"),
                new DesignSystemSpec("indigo-500", "violet-400", "rgba(99, 102, 241, 0.25)", "dark", "Modern productivity dark mode with violet accents"),
                List.of("Project Tasks", "Sprint Milestones", "Team Members"),
                List.of("Task state transitions update board instantly", "Search filters tasks list", "Metrics calculate completion rate"),
                "⚠️ Notice: Collaborative multi-tenant sync is simulated client-side in browser storage."
        );
    }

    private ProductSpec buildCleanEnergySpec(Startup startup) {
        return new ProductSpec(
                startup.getName(),
                "Clean Energy & Grid Telemetry",
                "Real-time grid tariff scheduling, battery telemetry, and autonomous peak shaving",
                "Industrial clean energy management platform providing real-time telemetry from battery storage systems, tariff price tracking, automated load balancing, and carbon reduction reports.",
                List.of("Industrial Facility Managers", "Microgrid Operators", "Energy Auditors"),
                List.of(
                        "Monitor real-time energy consumption, battery State-of-Charge (SoC), and grid tariff rates",
                        "Configure peak shaving thresholds and automated battery discharge triggers",
                        "Inspect historical savings, avoided peak surcharges, and carbon offset analytics",
                        "Manage industrial facility meters and equipment schedules"
                ),
                List.of(
                        new ScreenSpec("telemetry", "Live Grid Telemetry", "⚡", "Real-time battery storage status, kW demand curve, and dynamic grid pricing ticker", List.of("Battery State-of-Charge gauge (0-100%)", "Grid tariff rate tracker ($/kWh)", "Live power draw vs peak threshold indicator")),
                        new ScreenSpec("controls", "Demand Response Controls", "🎛️", "Automated peak-shaving settings with threshold sliders and battery dispatch mode", List.of("Peak threshold adjustment", "Autonomous vs Manual dispatch mode toggle", "Discharge test simulator")),
                        new ScreenSpec("analytics", "Savings & Carbon Reports", "📊", "Financial audit reports calculating avoided demand charges and net kWh saved", List.of("Projected monthly energy savings", "Carbon emissions offset indicator", "Peak demand event log")),
                        new ScreenSpec("facilities", "Facility Meters", "🏭", "Connected facility sub-meters with status indicators and historical peak records", List.of("Meters listing by factory zone", "Connection health indicators"))
                ),
                List.of("TelemetryGauge", "TariffTicker", "DispatchControls", "SavingsReport", "HeaderNav"),
                new DesignSystemSpec("emerald-500", "sky-400", "rgba(16, 185, 129, 0.25)", "dark", "Clean energy dark slate with electric emerald and sky cyan accents"),
                List.of("Meter Telemetry", "Battery Status", "Tariff Schedules", "Savings Logs"),
                List.of("Controls update simulated discharge rate", "Telemetry gauge updates in real time", "Reports reflect tariff savings"),
                "⚠️ Notice: Telemetry feeds and smart-meter APIs are simulated for prototype evaluation."
        );
    }

    private ProductSpec buildEcommerceSpec(Startup startup) {
        return new ProductSpec(
                startup.getName(),
                "Modern E-Commerce Storefront",
                "Curated product catalog, dynamic filtering, interactive cart, and streamlined checkout",
                "Next-generation retail shopping application featuring searchable product grids, category filtering, detailed item inspection, and responsive cart checkout.",
                List.of("Online Shoppers", "Brand Enthusiasts"),
                List.of(
                        "Browse product collection with price and category filters",
                        "View product details, customer ratings, and specifications",
                        "Add items to shopping bag and adjust quantities",
                        "Complete simulated checkout with coupon discounts"
                ),
                List.of(
                        new ScreenSpec("catalog", "Product Catalog", "🛍️", "Searchable product catalog with category pills, price sorting, and stock badges", List.of("Live product search", "Category filter chips", "Sort by price and rating", "Quick add to cart")),
                        new ScreenSpec("cart", "Shopping Bag", "🛒", "Interactive cart drawer with quantity updates, subtotal, and promo code support", List.of("Quantity increment / decrement", "Subtotal and tax calculations", "Promo coupon validation", "Checkout button")),
                        new ScreenSpec("wishlist", "Saved Favorites", "❤️", "Personalized collection of saved products with quick move-to-cart actions", List.of("Save / Unsave items", "Empty state with catalog link")),
                        new ScreenSpec("orders", "Order History", "📦", "Recent orders overview with tracking status and receipt summaries", List.of("Order cards with placed dates and status"))
                ),
                List.of("ProductCard", "CartDrawer", "WishlistGrid", "HeaderNav"),
                new DesignSystemSpec("indigo-600", "pink-500", "rgba(99, 102, 241, 0.25)", "dark", "Sleek retail aesthetic with vibrant neon highlights"),
                List.of("Products Catalog", "Shopping Cart", "Saved Items", "Orders"),
                List.of("Cart totals calculate accurately", "Search filters products list", "Wishlist persists favorites"),
                "⚠️ Notice: Payment gateway and inventory fulfillment are simulated client-side."
        );
    }

    private ProductSpec buildCustomVentureSpec(Startup startup) {
        String name = startup.getName() != null && !startup.getName().isBlank() ? startup.getName() : "Custom Venture";
        String idea = startup.getOriginalIdea() != null && !startup.getOriginalIdea().isBlank() ? startup.getOriginalIdea() : "Innovative modern application";
        String problem = startup.getProblem() != null && !startup.getProblem().isBlank() ? startup.getProblem() : "Manual processes and fragmented workflows";
        String solution = startup.getSolution() != null && !startup.getSolution().isBlank() ? startup.getSolution() : idea;
        String value = startup.getValueProposition() != null && !startup.getValueProposition().isBlank() ? startup.getValueProposition() : idea;

        // Derive industry category from idea and problem
        String category = "Custom Web Application";
        String lowerIdea = idea.toLowerCase();
        if (lowerIdea.contains("health") || lowerIdea.contains("clinic") || lowerIdea.contains("patient") || lowerIdea.contains("doctor")) {
            category = "Healthcare & Patient Portal";
        } else if (lowerIdea.contains("finance") || lowerIdea.contains("invest") || lowerIdea.contains("crypto") || lowerIdea.contains("money") || lowerIdea.contains("wealth")) {
            category = "FinTech & Financial Intelligence";
        } else if (lowerIdea.contains("learn") || lowerIdea.contains("education") || lowerIdea.contains("tutor") || lowerIdea.contains("course") || lowerIdea.contains("school")) {
            category = "EdTech & Learning Hub";
        } else if (lowerIdea.contains("ai") || lowerIdea.contains("copilot") || lowerIdea.contains("assistant") || lowerIdea.contains("intelligence")) {
            category = "AI Workflow & Productivity SaaS";
        } else if (lowerIdea.contains("real estate") || lowerIdea.contains("property") || lowerIdea.contains("rental") || lowerIdea.contains("housing")) {
            category = "Real Estate & Property Platform";
        } else if (lowerIdea.contains("fitness") || lowerIdea.contains("gym") || lowerIdea.contains("workout") || lowerIdea.contains("coach")) {
            category = "Fitness & Athletic Performance";
        }

        List<String> targetUsers = new ArrayList<>();
        if (startup.getTargetAudience() != null && !startup.getTargetAudience().isBlank()) {
            targetUsers.add(startup.getTargetAudience());
        }
        targetUsers.add("Operations Managers");
        targetUsers.add("Active End-Users");

        return new ProductSpec(
                name,
                category,
                value,
                "Description-driven application tailored to " + name + " for: " + solution,
                targetUsers,
                List.of(
                        "Monitor live operational KPI metrics and system activity for " + name,
                        "Search, inspect, and progress domain records through status stages",
                        "Submit and validate new entries via interactive intake form",
                        "Review performance breakdowns and audit distribution telemetry"
                ),
                List.of(
                        new ScreenSpec("overview", "Overview Dashboard", "📊", "Real-time KPI metrics, active pipeline summary, and live activity stream for " + name, List.of("Dynamic KPI metric cards", "Recent operational activity feed", "Quick action shortcuts")),
                        new ScreenSpec("records", "Domain Records", "📋", "Searchable database of operational records tailored to " + name, List.of("Real-time search & status filtering", "Interactive record inspection", "Stage advancement controls")),
                        new ScreenSpec("create", "Create Record", "➕", "Interactive intake form with validation for adding items to the workspace", List.of("Validated title, category, and priority inputs", "Instant synchronous list addition", "Success confirmation alerts")),
                        new ScreenSpec("analytics", "Analytics & Insights", "📈", "Detailed performance distribution and operational throughput metrics", List.of("Status and priority distribution meters", "Operational efficiency breakdown", "Audit history log"))
                ),
                List.of("DashboardOverview", "ItemManager", "CreateItemForm", "AnalyticsView", "NavigationHeader"),
                new DesignSystemSpec("indigo-600", "cyan-400", "rgba(99, 102, 241, 0.25)", "dark", "Clean enterprise dark mode with domain-tailored accent borders"),
                List.of("Domain Records", "Operational Metrics", "Activity Logs"),
                List.of("New records persist to local workspace storage", "Search filters records in real-time", "Form validates required fields synchronously"),
                "⚠️ Notice: Description-driven client-side prototype tailored to " + name + ". Data persists in browser workspace storage."
        );
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private List<String> readStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String val = item.asText();
                if (!val.isBlank()) list.add(val);
            }
        }
        return list;
    }

    private String cleanJsonOutput(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.trim();
    }
}
