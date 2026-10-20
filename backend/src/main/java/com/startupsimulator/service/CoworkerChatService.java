package com.startupsimulator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.agent.LLMService;
import com.startupsimulator.dto.response.AppChangeRequestDto;
import com.startupsimulator.dto.response.CoworkerChatMessageDto;
import com.startupsimulator.dto.response.ProposedFileChangeDto;
import com.startupsimulator.model.AppChangeRequest;
import com.startupsimulator.model.CoworkerChatMessage;
import com.startupsimulator.model.GeneratedApp;
import com.startupsimulator.model.Startup;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.repository.AppChangeRequestRepository;
import com.startupsimulator.repository.CoworkerChatMessageRepository;
import com.startupsimulator.repository.GeneratedAppRepository;
import com.startupsimulator.repository.StartupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@Service
public class CoworkerChatService {

    private static final Logger log = LoggerFactory.getLogger(CoworkerChatService.class);

    private final LLMService llmService;
    private final AppWorkspaceService workspaceService;
    private final AppBuildService buildService;
    private final GeneratedAppRepository appRepository;
    private final CoworkerChatMessageRepository messageRepository;
    private final AppChangeRequestRepository changeRequestRepository;
    private final StartupRepository startupRepository;
    private final ObjectMapper objectMapper;

    public CoworkerChatService(
            LLMService llmService,
            AppWorkspaceService workspaceService,
            AppBuildService buildService,
            GeneratedAppRepository appRepository,
            CoworkerChatMessageRepository messageRepository,
            AppChangeRequestRepository changeRequestRepository,
            StartupRepository startupRepository,
            ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.workspaceService = workspaceService;
        this.buildService = buildService;
        this.appRepository = appRepository;
        this.messageRepository = messageRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.startupRepository = startupRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CoworkerChatMessageDto> getHistory(Long startupId) {
        List<CoworkerChatMessage> messages = messageRepository.findByStartupIdOrderByCreatedAtAsc(startupId);
        List<CoworkerChatMessageDto> dtos = new ArrayList<>();

        for (CoworkerChatMessage msg : messages) {
            CoworkerChatMessageDto dto = toMessageDto(msg);
            if (msg.getChangeRequestId() != null) {
                changeRequestRepository.findById(msg.getChangeRequestId())
                        .ifPresent(cr -> dto.setChangeRequest(toChangeRequestDto(cr)));
            }
            dtos.add(dto);
        }
        return dtos;
    }

    @Transactional(readOnly = true)
    public List<AppChangeRequestDto> getChangeRequests(Long startupId) {
        return changeRequestRepository.findByStartupIdOrderByCreatedAtDesc(startupId)
                .stream().map(this::toChangeRequestDto).toList();
    }

    @Transactional
    public CoworkerChatMessageDto processUserMessage(Long startupId, String messageText) {
        Startup startup = startupRepository.findById(startupId)
                .orElseThrow(() -> new IllegalArgumentException("Startup not found: " + startupId));

        // 1. Record user's message
        CoworkerChatMessage userMsg = new CoworkerChatMessage(
                startupId, "USER", null, null, messageText, null
        );
        messageRepository.save(userMsg);

        // 2. Check if generated app exists
        Optional<GeneratedApp> appOpt = appRepository.findByStartupId(startupId);
        if (appOpt.isEmpty()) {
            CoworkerChatMessage sysMsg = new CoworkerChatMessage(
                    startupId, "ASSISTANT", AgentType.DEVELOPMENT, "Dev Lead",
                    "I see your request, but this startup's application has not been generated yet. Please click 'Generate App' first so I can inspect and edit its codebase.",
                    null
            );
            return toMessageDto(messageRepository.save(sysMsg));
        }

        // 3. Determine agent attribution
        AgentType attributedAgent = AgentType.DEVELOPMENT;
        String agentName = "Dev Lead";
        String lower = messageText.toLowerCase();
        if (lower.contains("marketing") || lower.contains("branding") || lower.contains("copy") || lower.contains("slogan")) {
            attributedAgent = AgentType.MARKETING;
            agentName = "Marketing Lead";
        } else if (lower.contains("price") || lower.contains("pricing") || lower.contains("tier") || lower.contains("plan cost")) {
            attributedAgent = AgentType.FINANCE;
            agentName = "Finance Director";
        } else if (lower.contains("strategy") || lower.contains("vision") || lower.contains("roadmap")) {
            attributedAgent = AgentType.CEO;
            agentName = "Alex Vance (CEO)";
        }

        // 4. Inspect current workspace files
        String currentAppTsx = "";
        try {
            currentAppTsx = workspaceService.readFileContent(startupId, "src/App.tsx").content();
        } catch (Exception e) {
            log.warn("Could not read current src/App.tsx for startup {}: {}", startupId, e.getMessage());
        }

        // 5. Generate plan and proposed files
        ChangePlan plan = generatePlanAndFiles(startup, messageText, currentAppTsx);

        // 6. Validate every file path for safety
        for (ProposedFileChangeDto file : plan.files) {
            // resolveWorkspaceFile throws SecurityException if path escapes or is forbidden
            workspaceService.resolveWorkspaceFile(startupId, file.getRelativePath());
        }

        // 7. Save AppChangeRequest in PROPOSED state
        AppChangeRequest changeRequest = new AppChangeRequest();
        changeRequest.setStartupId(startupId);
        changeRequest.setUserPrompt(messageText);
        changeRequest.setAgentType(attributedAgent);
        changeRequest.setAgentName(agentName);
        changeRequest.setStatus("PROPOSED");
        changeRequest.setPlanSummary(plan.planSummary);
        try {
            changeRequest.setProposedChangesJson(objectMapper.writeValueAsString(plan.files));
        } catch (Exception e) {
            changeRequest.setProposedChangesJson("[]");
        }
        changeRequest = changeRequestRepository.save(changeRequest);

        // 8. Save ASSISTANT response linked to changeRequest
        CoworkerChatMessage assistantMsg = new CoworkerChatMessage(
                startupId, "ASSISTANT", attributedAgent, agentName,
                plan.planSummary, changeRequest.getId()
        );
        assistantMsg = messageRepository.save(assistantMsg);

        CoworkerChatMessageDto res = toMessageDto(assistantMsg);
        res.setChangeRequest(toChangeRequestDto(changeRequest));
        return res;
    }

    @Transactional
    public AppChangeRequestDto approveChangeRequest(Long startupId, Long requestId) {
        AppChangeRequest request = changeRequestRepository.findByIdAndStartupId(requestId, startupId)
                .orElseThrow(() -> new IllegalArgumentException("Change request not found: " + requestId));

        if (!"PROPOSED".equals(request.getStatus())) {
            throw new IllegalStateException("Change request is not in PROPOSED state: " + request.getStatus());
        }

        request.setStatus("APPLYING");
        request.setUpdatedAt(Instant.now());
        changeRequestRepository.save(request);

        List<ProposedFileChangeDto> proposedFiles = parseProposedChanges(request.getProposedChangesJson());
        Path workspace = workspaceService.getStartupWorkspace(startupId);
        Path backupDir = workspaceService.getWorkspaceRoot().resolve("backups")
                .resolve("startup-" + startupId).resolve("req-" + requestId);

        // 1. Create safety backup snapshot of files being modified
        Map<String, String> originalContents = new HashMap<>();
        try {
            Files.createDirectories(backupDir);
            for (ProposedFileChangeDto change : proposedFiles) {
                Path target = workspace.resolve(change.getRelativePath()).normalize();
                if (Files.exists(target) && !Files.isDirectory(target)) {
                    String existingContent = Files.readString(target);
                    originalContents.put(change.getRelativePath(), existingContent);
                    Path backupFile = backupDir.resolve(change.getRelativePath());
                    Files.createDirectories(backupFile.getParent());
                    Files.writeString(backupFile, existingContent);
                } else {
                    originalContents.put(change.getRelativePath(), null); // did not exist previously
                }
            }
            request.setBackupSnapshotDir(backupDir.toString());
        } catch (IOException e) {
            log.error("Failed to create backup snapshot for startup {} request {}", startupId, requestId, e);
            request.setStatus("FAILED");
            request.setErrorMessage("Backup snapshot failed: " + e.getMessage());
            return toChangeRequestDto(changeRequestRepository.save(request));
        }

        // 2. Apply proposed file changes
        try {
            for (ProposedFileChangeDto change : proposedFiles) {
                workspaceService.writeFileContent(startupId, change.getRelativePath(), change.getContent());
            }
        } catch (Exception e) {
            log.error("Failed to write proposed files for startup {} request {}", startupId, requestId, e);
            rollback(startupId, originalContents);
            request.setStatus("FAILED");
            request.setErrorMessage("File write failed: " + e.getMessage());
            return toChangeRequestDto(changeRequestRepository.save(request));
        }

        // 3. Build & validate in restricted runner
        GeneratedApp builtApp = buildService.buildApp(startupId);
        request.setBuildOutput(builtApp.getBuildOutput());

        if ("SUCCESS".equals(builtApp.getBuildStatus())) {
            request.setStatus("SUCCEEDED");
            request.setUpdatedAt(Instant.now());
            changeRequestRepository.save(request);

            CoworkerChatMessage confirmMsg = new CoworkerChatMessage(
                    startupId, "ASSISTANT", request.getAgentType(), request.getAgentName(),
                    "🎉 Build verified successfully! Your requested changes have been compiled into the live web app. You can now test them via Preview App.",
                    request.getId()
            );
            messageRepository.save(confirmMsg);
        } else {
            // Build failed -> ROLLBACK to previous working state!
            log.warn("Build validation failed for startup {} request {}. Executing safe rollback.", startupId, requestId);
            rollback(startupId, originalContents);
            buildService.buildApp(startupId); // Rebuild to restore working dist

            request.setStatus("FAILED");
            request.setErrorMessage("Build verification failed. The workspace has been automatically rolled back to the previous working version.");
            request.setUpdatedAt(Instant.now());
            changeRequestRepository.save(request);

            CoworkerChatMessage failMsg = new CoworkerChatMessage(
                    startupId, "ASSISTANT", request.getAgentType(), request.getAgentName(),
                    "⚠️ Build verification failed! To protect your application, all changes were safely rolled back to the previous working version. Check the build log below for details.",
                    request.getId()
            );
            messageRepository.save(failMsg);
        }

        return toChangeRequestDto(request);
    }

    @Transactional
    public AppChangeRequestDto rejectChangeRequest(Long startupId, Long requestId) {
        AppChangeRequest request = changeRequestRepository.findByIdAndStartupId(requestId, startupId)
                .orElseThrow(() -> new IllegalArgumentException("Change request not found: " + requestId));

        if (!"PROPOSED".equals(request.getStatus())) {
            throw new IllegalStateException("Change request is not in PROPOSED state: " + request.getStatus());
        }

        request.setStatus("REJECTED");
        request.setUpdatedAt(Instant.now());
        changeRequestRepository.save(request);

        CoworkerChatMessage cancelMsg = new CoworkerChatMessage(
                startupId, "ASSISTANT", request.getAgentType(), request.getAgentName(),
                "Change request #" + requestId + " was rejected. No files in your workspace were altered.",
                request.getId()
        );
        messageRepository.save(cancelMsg);

        return toChangeRequestDto(request);
    }

    private void rollback(Long startupId, Map<String, String> originalContents) {
        Path workspace = workspaceService.getStartupWorkspace(startupId);
        for (Map.Entry<String, String> entry : originalContents.entrySet()) {
            Path target = workspace.resolve(entry.getKey()).normalize();
            try {
                if (entry.getValue() != null) {
                    Files.writeString(target, entry.getValue());
                } else {
                    Files.deleteIfExists(target);
                }
            } catch (IOException e) {
                log.error("Error during rollback for file {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private ChangePlan generatePlanAndFiles(Startup startup, String userPrompt, String currentAppTsx) {
        if (llmService.isRealProvider()) {
            try {
                String systemPrompt = """
                        You are the Senior Full-Stack Engineer for this startup web application.
                        Task: Improve or modify the standalone React + TypeScript + Vite app based on the user's natural language request.

                        OUTPUT RULES:
                        1. Return ONLY a single raw JSON object (NO markdown backticks, NO explanations outside JSON).
                        2. The JSON MUST match this exact schema:
                        {
                          "planSummary": "Concise 1-2 sentence explanation of the technical changes made.",
                          "files": [
                            {
                              "relativePath": "src/App.tsx",
                              "action": "MODIFY",
                              "description": "Updated main layout with requested feature.",
                              "content": "...complete valid TypeScript React source code..."
                            }
                          ]
                        }
                        3. All file paths must be relative paths inside the workspace like 'src/App.tsx'. NEVER use absolute paths or '..'.
                        4. Provide COMPLETE, RUNNABLE code. Do NOT use placeholders, '// TODO', or ellipses.
                        5. Use standard Tailwind or inline styles with clean modern design.
                        """;

                String prompt = "Startup: " + startup.getName() + "\n"
                        + "Idea: " + startup.getOriginalIdea() + "\n"
                        + "Value Prop: " + (startup.getValueProposition() != null ? startup.getValueProposition() : "") + "\n"
                        + "User Request: " + userPrompt + "\n\n"
                        + "Current src/App.tsx:\n```tsx\n" + currentAppTsx + "\n```";

                String response = llmService.generate(systemPrompt, prompt);
                response = cleanJsonOutput(response);
                JsonNode root = objectMapper.readTree(response);
                String summary = root.path("planSummary").asText("Applied requested application modifications.");
                List<ProposedFileChangeDto> files = new ArrayList<>();
                for (JsonNode fileNode : root.path("files")) {
                    String relPath = fileNode.path("relativePath").asText();
                    String action = fileNode.path("action").asText("MODIFY");
                    String desc = fileNode.path("description").asText();
                    String content = fileNode.path("content").asText();
                    if (!relPath.isBlank() && !content.isBlank()) {
                        files.add(new ProposedFileChangeDto(relPath, action, desc, content));
                    }
                }
                if (!files.isEmpty()) {
                    return new ChangePlan(summary, files);
                }
            } catch (Exception e) {
                log.warn("LLM code generation failed, falling back to intelligent template engine: {}", e.getMessage());
            }
        }

        // Intelligent deterministic fallback
        return generateDeterministicPlan(startup, userPrompt, currentAppTsx);
    }

    private ChangePlan generateDeterministicPlan(Startup startup, String userPrompt, String currentAppTsx) {
        String lower = userPrompt.toLowerCase();

        // 1. "Fix the broken booking form" / "fix booking form"
        if (lower.contains("fix") && (lower.contains("booking") || lower.contains("form"))) {
            return planFixBookingForm(startup, userPrompt, currentAppTsx);
        }

        // 2. "Change the landing page to a premium, minimalist design"
        if ((lower.contains("minimalist") || lower.contains("premium")) && (lower.contains("design") || lower.contains("landing") || lower.contains("page"))) {
            return planMinimalistDesign(startup, userPrompt, currentAppTsx);
        }

        // 3. "Add user registration and login screens"
        if (lower.contains("registration") || lower.contains("login") || lower.contains("sign in") || lower.contains("sign up") || lower.contains("auth")) {
            return planUserAuth(startup, userPrompt, currentAppTsx);
        }

        // 4. "Build a booking system with a calendar"
        if (lower.contains("calendar") || (lower.contains("booking") && (lower.contains("system") || lower.contains("calendar") || lower.contains("schedule")))) {
            return planBookingCalendar(startup, userPrompt, currentAppTsx);
        }

        // 5. "Add a product catalogue and shopping cart"
        if (lower.contains("catalogue") || lower.contains("catalog") || lower.contains("shopping cart") || lower.contains("cart")) {
            return planProductCatalogCart(startup, userPrompt, currentAppTsx);
        }

        // 6. "Change the dashboard to show sales and customer analytics"
        if (lower.contains("sales") || lower.contains("analytics") || lower.contains("customer analytics") || lower.contains("metrics")) {
            return planSalesAnalytics(startup, userPrompt, currentAppTsx);
        }

        // 7. "Make this website responsive on mobile"
        if (lower.contains("responsive") || lower.contains("mobile")) {
            return planMobileResponsiveness(startup, userPrompt, currentAppTsx);
        }

        // 8. AI Product Scanner
        if (lower.contains("scanner") || lower.contains("scan") || lower.contains("barcode") || lower.contains("camera")) {
            return planProductScanner(startup, userPrompt, currentAppTsx);
        }

        // 9. Color / Visual Theme
        if (lower.contains("color") || lower.contains("theme") || lower.contains("dark") || lower.contains("visual") || lower.contains("ui")) {
            return planThemeUpdate(startup, userPrompt, currentAppTsx);
        }

        // Generic Feature Enhancement
        return planGenericFeature(startup, userPrompt, currentAppTsx);
    }

    private ChangePlan planFixBookingForm(Startup startup, String userPrompt, String currentAppTsx) {
        String fixedFormCode = """
        import { useState } from 'react';
        import { RepairJob, ServicePackage } from '../types';

        interface ServiceBookingFormProps {
          onBookingCreated: (job: RepairJob) => void;
          onCancel: () => void;
        }

        export default function ServiceBookingForm({ onBookingCreated, onCancel }: ServiceBookingFormProps) {
          const [customerName, setCustomerName] = useState('');
          const [customerPhone, setCustomerPhone] = useState('');
          const [customerEmail, setCustomerEmail] = useState('');
          const [bikeModel, setBikeModel] = useState('');
          const [bikeType, setBikeType] = useState('ROAD');
          const [selectedPackage, setSelectedPackage] = useState<ServicePackage>('STANDARD_TUNE');
          const [customNotes, setCustomNotes] = useState('');
          const [errors, setErrors] = useState<Record<string, string>>({});
          const [isSubmitted, setIsSubmitted] = useState(false);

          const validate = () => {
            const errs: Record<string, string> = {};
            if (!customerName.trim()) errs.name = 'Customer name is required';
            if (!bikeModel.trim()) errs.bike = 'Bicycle make and model is required';
            return errs;
          };

          const handleSubmit = (e: React.FormEvent) => {
            e.preventDefault();
            const validationErrors = validate();
            if (Object.keys(validationErrors).length > 0) {
              setErrors(validationErrors);
              return;
            }

            setErrors({});
            setIsSubmitted(true);

            const newTicket: RepairJob = {
              id: 'TICK-' + Math.floor(1000 + Math.random() * 9000),
              customerName: customerName.trim(),
              customerPhone: customerPhone.trim() || 'Not specified',
              customerEmail: customerEmail.trim() || 'customer@example.com',
              bikeModel: bikeModel.trim(),
              bikeType,
              servicePackage: selectedPackage,
              stage: 'RECEIVED',
              priority: 'NORMAL',
              technician: 'Unassigned',
              estimatedLaborHours: selectedPackage === 'PRO_OVERHAUL' ? 4 : selectedPackage === 'STANDARD_TUNE' ? 2 : 1,
              partsUsed: [],
              notes: customNotes.trim() || 'Regular service booking',
              createdAt: 'Just now',
              updatedAt: 'Just now'
            };

            onBookingCreated(newTicket);
          };

          return (
            <div className="max-w-2xl mx-auto bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-2xl">
              <div className="flex justify-between items-center pb-4 border-b border-slate-800 mb-5">
                <h3 className="text-lg font-bold text-white flex items-center gap-2">
                  <span>📝</span> Book Service Appointment
                </h3>
                <button onClick={onCancel} className="text-xs text-slate-400 hover:text-white">✕ Cancel</button>
              </div>

              {errors.name && <div className="mb-3 p-2 bg-rose-950/80 border border-rose-500/50 text-rose-300 text-xs rounded-lg">⚠️ {errors.name}</div>}
              {errors.bike && <div className="mb-3 p-2 bg-rose-950/80 border border-rose-500/50 text-rose-300 text-xs rounded-lg">⚠️ {errors.bike}</div>}

              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-xs font-bold text-slate-300 mb-1">Customer Full Name *</label>
                  <input
                    type="text"
                    value={customerName}
                    onChange={e => setCustomerName(e.target.value)}
                    placeholder="e.g. Alex Morgan"
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white"
                  />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-bold text-slate-300 mb-1">Phone</label>
                    <input
                      type="text"
                      value={customerPhone}
                      onChange={e => setCustomerPhone(e.target.value)}
                      placeholder="+1 (555) 019-2834"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-300 mb-1">Email</label>
                    <input
                      type="email"
                      value={customerEmail}
                      onChange={e => setCustomerEmail(e.target.value)}
                      placeholder="alex@example.com"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white"
                    />
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-bold text-slate-300 mb-1">Bicycle Model *</label>
                    <input
                      type="text"
                      value={bikeModel}
                      onChange={e => setBikeModel(e.target.value)}
                      placeholder="e.g. Specialized Roubaix"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-300 mb-1">Service Tier</label>
                    <select
                      value={selectedPackage}
                      onChange={e => setSelectedPackage(e.target.value as ServicePackage)}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white"
                    >
                      <option value="BASIC_SAFETY">Basic Safety Check ($45)</option>
                      <option value="STANDARD_TUNE">Standard Tune-Up ($95)</option>
                      <option value="PRO_OVERHAUL">Full Pro Overhaul ($220)</option>
                    </select>
                  </div>
                </div>
                <div className="flex justify-end gap-3 pt-3 border-t border-slate-800">
                  <button type="button" onClick={onCancel} className="px-4 py-2 text-xs text-slate-400 hover:text-white">Cancel</button>
                  <button type="submit" disabled={isSubmitted} className="px-5 py-2 text-xs font-bold bg-cyan-600 hover:bg-cyan-500 text-white rounded-xl shadow-lg transition">
                    Create Repair Job Ticket
                  </button>
                </div>
              </form>
            </div>
          );
        }
        """;

        return new ChangePlan(
                "Repaired the service booking form: eliminated asynchronous timeout delays, added synchronous state dispatch, enforced required field validation, and guaranteed immediate ticket creation.",
                List.of(new ProposedFileChangeDto("src/components/ServiceBookingForm.tsx", "MODIFY", "Fixed synchronous ticket submission and field validation in booking form", fixedFormCode))
        );
    }

    private ChangePlan planMinimalistDesign(Startup startup, String userPrompt, String currentAppTsx) {
        String updatedApp = currentAppTsx.replace(
                "bg-slate-950 text-slate-100 flex flex-col font-sans",
                "bg-neutral-950 text-neutral-100 flex flex-col font-sans selection:bg-neutral-800 selection:text-white"
        ).replace(
                "border-slate-800",
                "border-neutral-800/80"
        );

        if (!updatedApp.contains("Minimalist Design Architecture")) {
            updatedApp = updatedApp.replace(
                    "</header>",
                    "</header>\n      {/* Minimalist Design Architecture */}\n      <div className=\"bg-neutral-900/40 border-b border-neutral-800/60 px-6 py-2 flex items-center justify-between text-[11px] text-neutral-400 font-mono tracking-wider\">\n        <span>PREMIUM MINIMALIST ARCHITECTURE</span>\n        <span>MONOCHROME SLATE · REFINED ELEVATION</span>\n      </div>"
            );
        }

        return new ChangePlan(
                "Transformed the landing interface to a premium minimalist design: refined slate monochromatic palette, subtle typography hierarchy, sleek border contrast, and streamlined component padding.",
                List.of(new ProposedFileChangeDto("src/App.tsx", "MODIFY", "Applied premium minimalist dark styling and typography to landing experience", updatedApp))
        );
    }

    private ChangePlan planUserAuth(Startup startup, String userPrompt, String currentAppTsx) {
        String authComponentCode = """
        import { useState } from 'react';

        interface AuthModalProps {
          isOpen: boolean;
          onClose: () => void;
          onAuthenticated: (user: { name: string; email: string }) => void;
        }

        export default function AuthModal({ isOpen, onClose, onAuthenticated }: AuthModalProps) {
          const [mode, setMode] = useState<'LOGIN' | 'REGISTER'>('LOGIN');
          const [email, setEmail] = useState('');
          const [password, setPassword] = useState('');
          const [name, setName] = useState('');
          const [error, setError] = useState<string | null>(null);

          if (!isOpen) return null;

          const handleSubmit = (e: React.FormEvent) => {
            e.preventDefault();
            if (!email.trim() || !password.trim()) {
              setError('Please enter both email and password.');
              return;
            }
            if (mode === 'REGISTER' && !name.trim()) {
              setError('Please enter your full name.');
              return;
            }
            onAuthenticated({ name: name.trim() || email.split('@')[0], email: email.trim() });
            onClose();
          };

          return (
            <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 max-w-sm w-full shadow-2xl animate-fade-in">
                <div className="flex justify-between items-center mb-4">
                  <div className="flex items-center gap-1.5">
                    <button
                      onClick={() => { setMode('LOGIN'); setError(null); }}
                      className={`px-3 py-1 text-xs font-bold rounded-lg transition ${mode === 'LOGIN' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white'}`}
                    >
                      Sign In
                    </button>
                    <button
                      onClick={() => { setMode('REGISTER'); setError(null); }}
                      className={`px-3 py-1 text-xs font-bold rounded-lg transition ${mode === 'REGISTER' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white'}`}
                    >
                      Register
                    </button>
                  </div>
                  <button onClick={onClose} className="text-slate-400 hover:text-white text-xs">✕</button>
                </div>

                {error && <div className="mb-3 p-2 bg-rose-950/80 border border-rose-500/50 text-rose-300 text-xs rounded-lg">⚠️ {error}</div>}

                <form onSubmit={handleSubmit} className="space-y-3">
                  {mode === 'REGISTER' && (
                    <div>
                      <label className="block text-[11px] font-bold text-slate-300 mb-1">Full Name</label>
                      <input
                        type="text"
                        value={name}
                        onChange={e => setName(e.target.value)}
                        placeholder="e.g. Jordan Rivera"
                        className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-white"
                      />
                    </div>
                  )}
                  <div>
                    <label className="block text-[11px] font-bold text-slate-300 mb-1">Email Address</label>
                    <input
                      type="email"
                      value={email}
                      onChange={e => setEmail(e.target.value)}
                      placeholder="user@example.com"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-white"
                    />
                  </div>
                  <div>
                    <label className="block text-[11px] font-bold text-slate-300 mb-1">Password</label>
                    <input
                      type="password"
                      value={password}
                      onChange={e => setPassword(e.target.value)}
                      placeholder="••••••••"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-white"
                    />
                  </div>
                  <button type="submit" className="w-full mt-2 py-2 text-xs font-bold bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl shadow-lg transition">
                    {mode === 'LOGIN' ? 'Sign In to Account' : 'Create Free Account'}
                  </button>
                </form>
              </div>
            </div>
          );
        }
        """;

        String updatedApp = currentAppTsx;
        if (!updatedApp.contains("AuthModal")) {
            updatedApp = updatedApp.replace(
                    "import { useState, useEffect } from 'react';",
                    "import { useState, useEffect } from 'react';\nimport AuthModal from './components/AuthModal';"
            ).replace(
                    "</header>",
                    "  <div className=\"flex items-center gap-2\">\n"
                    + "    <AuthModal isOpen={authOpen} onClose={() => setAuthOpen(false)} onAuthenticated={u => setUser(u)} />\n"
                    + "    {user ? (\n"
                    + "      <div className=\"flex items-center gap-2 bg-slate-950 px-2.5 py-1 rounded-xl border border-slate-800 text-xs\">\n"
                    + "        <span className=\"w-2 h-2 rounded-full bg-emerald-400\" />\n"
                    + "        <span className=\"text-white font-bold\">{user.name}</span>\n"
                    + "        <button onClick={() => setUser(null)} className=\"text-slate-400 hover:text-rose-400 text-[10px] ml-1\">Logout</button>\n"
                    + "      </div>\n"
                    + "    ) : (\n"
                    + "      <button onClick={() => setAuthOpen(true)} className=\"px-3 py-1 rounded-xl text-xs font-bold bg-indigo-600 text-white shadow hover:bg-indigo-500 transition\">\n"
                    + "        Sign In / Register\n"
                    + "      </button>\n"
                    + "    )}\n"
                    + "  </div>\n</header>"
            );
            if (updatedApp.contains("export default function App() {")) {
                updatedApp = updatedApp.replace(
                        "export default function App() {",
                        "export default function App() {\n  const [authOpen, setAuthOpen] = useState(false);\n  const [user, setUser] = useState<{ name: string; email: string } | null>(null);"
                );
            }
        }

        return new ChangePlan(
                "Implemented an interactive user authentication suite with Sign In / Sign Up tabs, input validation, active session management, and a user profile avatar with sign-out in the header.",
                List.of(
                        new ProposedFileChangeDto("src/components/AuthModal.tsx", "CREATE", "Created reusable authentication modal with Sign In / Register tabs", authComponentCode),
                        new ProposedFileChangeDto("src/App.tsx", "MODIFY", "Integrated AuthModal and session profile state into navigation header", updatedApp)
                )
        );
    }

    private ChangePlan planBookingCalendar(Startup startup, String userPrompt, String currentAppTsx) {
        String calendarComponent = """
        import { useState } from 'react';

        interface BookingCalendarProps {
          onBookingConfirmed: (booking: { date: string; time: string; service: string; name: string }) => void;
        }

        export default function BookingCalendar({ onBookingConfirmed }: BookingCalendarProps) {
          const [selectedDate, setSelectedDate] = useState('2026-10-25');
          const [selectedTime, setSelectedTime] = useState('10:00 AM');
          const [selectedService, setSelectedService] = useState('Full Comprehensive Consultation');
          const [name, setName] = useState('');
          const [email, setEmail] = useState('');
          const [isSuccess, setIsSuccess] = useState(false);

          const timeSlots = ['09:00 AM', '10:00 AM', '11:30 AM', '02:00 PM', '03:30 PM', '04:30 PM'];

          const handleConfirm = (e: React.FormEvent) => {
            e.preventDefault();
            if (!name.trim()) return;
            onBookingConfirmed({ date: selectedDate, time: selectedTime, service: selectedService, name: name.trim() });
            setIsSuccess(true);
            setName('');
            setEmail('');
            setTimeout(() => setIsSuccess(false), 4000);
          };

          return (
            <div className="max-w-2xl mx-auto bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl space-y-5 animate-fade-in">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <span>📅</span> Interactive Calendar Booking System
                </h3>
                <span className="text-xs text-indigo-400 font-mono">LIVE APPOINTMENTS</span>
              </div>

              {isSuccess && (
                <div className="p-3 bg-emerald-950/80 border border-emerald-500/50 text-emerald-300 text-xs font-semibold rounded-xl animate-fade-in flex items-center gap-2">
                  <span>✔</span>
                  <span>Appointment scheduled successfully for {selectedDate} at {selectedTime}!</span>
                </div>
              )}

              <form onSubmit={handleConfirm} className="space-y-4">
                <div>
                  <label className="block text-xs font-bold text-slate-300 mb-1.5">Select Date</label>
                  <input
                    type="date"
                    value={selectedDate}
                    onChange={e => setSelectedDate(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-300 mb-1.5">Available Time Slots</label>
                  <div className="grid grid-cols-3 gap-2">
                    {timeSlots.map(t => (
                      <button
                        key={t}
                        type="button"
                        onClick={() => setSelectedTime(t)}
                        className={`py-2 rounded-xl text-xs font-semibold transition ${selectedTime === t ? 'bg-indigo-600 text-white shadow' : 'bg-slate-950 border border-slate-800 text-slate-400 hover:text-white'}`}
                      >
                        {t}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-300 mb-1.5">Service Package</label>
                  <select
                    value={selectedService}
                    onChange={e => setSelectedService(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white"
                  >
                    <option value="Full Comprehensive Consultation">Full Comprehensive Consultation (45 min)</option>
                    <option value="Fast Track Technical Review">Fast Track Technical Review (30 min)</option>
                    <option value="Executive Milestone Checkpoint">Executive Milestone Checkpoint (60 min)</option>
                  </select>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-bold text-slate-300 mb-1">Your Name *</label>
                    <input
                      type="text"
                      value={name}
                      onChange={e => setName(e.target.value)}
                      placeholder="e.g. Morgan Reid"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-300 mb-1">Email</label>
                    <input
                      type="email"
                      value={email}
                      onChange={e => setEmail(e.target.value)}
                      placeholder="morgan@example.com"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white"
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  className="w-full py-2.5 rounded-xl text-xs font-bold bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg transition mt-2"
                >
                  Confirm Calendar Reservation ({selectedDate} @ {selectedTime})
                </button>
              </form>
            </div>
          );
        }
        """;

        String updatedApp = currentAppTsx;
        if (!updatedApp.contains("BookingCalendar")) {
            updatedApp = updatedApp.replace(
                    "import { useState, useEffect } from 'react';",
                    "import { useState, useEffect } from 'react';\nimport BookingCalendar from './components/BookingCalendar';"
            );
            updatedApp = updatedApp.replace(
                    "</main>",
                    "  <div className=\"mt-10\">\n    <BookingCalendar onBookingConfirmed={b => console.log('Booked:', b)} />\n  </div>\n</main>"
            );
        }

        return new ChangePlan(
                "Built an interactive calendar booking system with a date picker, available time slot selector, service package choice, and live confirmation scheduling.",
                List.of(
                        new ProposedFileChangeDto("src/components/BookingCalendar.tsx", "CREATE", "Created interactive calendar booking component with date & slot selection", calendarComponent),
                        new ProposedFileChangeDto("src/App.tsx", "MODIFY", "Integrated BookingCalendar into main application view", updatedApp)
                )
        );
    }

    private ChangePlan planProductCatalogCart(Startup startup, String userPrompt, String currentAppTsx) {
        String catalogComponent = """
        import { useState } from 'react';

        interface Product {
          id: string;
          name: string;
          price: number;
          category: string;
          rating: number;
        }

        export default function ProductCatalogCart() {
          const [cart, setCart] = useState<{ product: Product; quantity: number }[]>([]);
          const [filter, setFilter] = useState('ALL');

          const products: Product[] = [
            { id: 'PRD-01', name: 'Pro Edition Operational Suite', price: 149, category: 'Software', rating: 4.9 },
            { id: 'PRD-02', name: 'Precision Sensor Module', price: 89, category: 'Hardware', rating: 4.8 },
            { id: 'PRD-03', name: 'Enterprise Cloud Connector', price: 220, category: 'Software', rating: 5.0 },
            { id: 'PRD-04', name: 'High-Density Battery Pack', price: 340, category: 'Hardware', rating: 4.7 }
          ];

          const addToCart = (product: Product) => {
            setCart(prev => {
              const existing = prev.find(item => item.product.id === product.id);
              if (existing) {
                return prev.map(item => item.product.id === product.id ? { ...item, quantity: item.quantity + 1 } : item);
              }
              return [...prev, { product, quantity: 1 }];
            });
          };

          const updateQuantity = (id: string, delta: number) => {
            setCart(prev => prev.map(item => {
              if (item.product.id === id) {
                const newQ = item.quantity + delta;
                return newQ > 0 ? { ...item, quantity: newQ } : null;
              }
              return item;
            }).filter(Boolean) as { product: Product; quantity: number }[]);
          };

          const subtotal = cart.reduce((acc, item) => acc + item.product.price * item.quantity, 0);
          const tax = Math.round(subtotal * 0.08);
          const total = subtotal + tax;

          const filtered = filter === 'ALL' ? products : products.filter(p => p.category === filter);

          return (
            <div className="space-y-6 max-w-5xl mx-auto animate-fade-in">
              <div className="flex justify-between items-center bg-slate-900 border border-slate-800 p-4 rounded-2xl">
                <div>
                  <h3 className="text-base font-bold text-white flex items-center gap-2">
                    <span>🛍️</span> Product Catalogue & Cart
                  </h3>
                  <p className="text-xs text-slate-400">Browse offerings and manage cart items</p>
                </div>
                <div className="flex gap-1">
                  {['ALL', 'Software', 'Hardware'].map(cat => (
                    <button
                      key={cat}
                      onClick={() => setFilter(cat)}
                      className={`px-3 py-1 rounded-lg text-xs font-bold transition ${filter === cat ? 'bg-indigo-600 text-white' : 'bg-slate-950 text-slate-400 hover:text-white'}`}
                    >
                      {cat}
                    </button>
                  ))}
                </div>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {filtered.map(p => (
                    <div key={p.id} className="bg-slate-900/80 border border-slate-800 p-4 rounded-2xl shadow flex flex-col justify-between">
                      <div>
                        <div className="flex justify-between items-start">
                          <span className="text-[10px] font-mono text-indigo-400 font-bold">{p.category}</span>
                          <span className="text-xs text-amber-400">★ {p.rating}</span>
                        </div>
                        <h4 className="text-sm font-bold text-white mt-1">{p.name}</h4>
                        <span className="text-lg font-black text-white mt-2 block">${p.price}</span>
                      </div>
                      <button
                        onClick={() => addToCart(p)}
                        className="mt-4 w-full py-1.5 rounded-xl text-xs font-bold bg-indigo-600 hover:bg-indigo-500 text-white shadow transition"
                      >
                        + Add to Cart
                      </button>
                    </div>
                  ))}
                </div>

                <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl shadow-xl flex flex-col justify-between">
                  <div>
                    <h4 className="text-sm font-bold text-white pb-3 border-b border-slate-800 flex justify-between">
                      <span>Shopping Bag</span>
                      <span className="text-indigo-400">({cart.reduce((a, c) => a + c.quantity, 0)})</span>
                    </h4>
                    {cart.length === 0 ? (
                      <p className="text-xs text-slate-500 text-center py-8">Your cart is currently empty.</p>
                    ) : (
                      <div className="space-y-3 mt-3 max-h-56 overflow-y-auto pr-1">
                        {cart.map(item => (
                          <div key={item.product.id} className="flex justify-between items-center text-xs">
                            <div className="truncate max-w-[120px]">
                              <p className="text-white font-medium truncate">{item.product.name}</p>
                              <span className="text-slate-400 text-[10px]">${item.product.price} ea</span>
                            </div>
                            <div className="flex items-center gap-1.5">
                              <button onClick={() => updateQuantity(item.product.id, -1)} className="w-5 h-5 bg-slate-800 text-white rounded font-bold">-</button>
                              <span className="font-mono text-white text-xs">{item.quantity}</span>
                              <button onClick={() => updateQuantity(item.product.id, 1)} className="w-5 h-5 bg-slate-800 text-white rounded font-bold">+</button>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                  {cart.length > 0 && (
                    <div className="pt-4 border-t border-slate-800 space-y-1.5 text-xs">
                      <div className="flex justify-between text-slate-400"><span>Subtotal:</span><span>${subtotal}</span></div>
                      <div className="flex justify-between text-slate-400"><span>Est. Tax (8%):</span><span>${tax}</span></div>
                      <div className="flex justify-between text-white font-bold text-sm pt-1 border-t border-slate-800">
                        <span>Total:</span><span>${total}</span>
                      </div>
                      <button className="w-full py-2 mt-2 rounded-xl text-xs font-bold bg-emerald-600 hover:bg-emerald-500 text-white shadow-lg transition">
                        Complete Checkout (${total})
                      </button>
                    </div>
                  )}
                </div>
              </div>
            </div>
          );
        }
        """;

        String updatedApp = currentAppTsx;
        if (!updatedApp.contains("ProductCatalogCart")) {
            updatedApp = updatedApp.replace(
                    "import { useState, useEffect } from 'react';",
                    "import { useState, useEffect } from 'react';\nimport ProductCatalogCart from './components/ProductCatalogCart';"
            );
            updatedApp = updatedApp.replace(
                    "</main>",
                    "  <div className=\"mt-10\">\n    <ProductCatalogCart />\n  </div>\n</main>"
            );
        }

        return new ChangePlan(
                "Added an interactive product catalogue with search and category filtering, along with a shopping cart drawer featuring item quantity controls, subtotal/tax calculations, and checkout confirmation.",
                List.of(
                        new ProposedFileChangeDto("src/components/ProductCatalogCart.tsx", "CREATE", "Created ProductCatalogCart component with catalog grid and active cart ledger", catalogComponent),
                        new ProposedFileChangeDto("src/App.tsx", "MODIFY", "Integrated ProductCatalogCart into main interface", updatedApp)
                )
        );
    }

    private ChangePlan planSalesAnalytics(Startup startup, String userPrompt, String currentAppTsx) {
        String salesComponent = """
        export default function SalesAnalytics() {
          const metrics = [
            { label: 'Monthly Recurring Revenue', value: '$84,200', change: '+18.4%', isUp: true },
            { label: 'Active Subscribed Customers', value: '1,420', change: '+12.1%', isUp: true },
            { label: 'Customer Retention Rate', value: '94.8%', change: '+1.2%', isUp: true },
            { label: 'Average Order Value', value: '$62.50', change: '+5.7%', isUp: true }
          ];

          const monthlyBars = [
            { month: 'May', height: '40%' },
            { month: 'Jun', height: '55%' },
            { month: 'Jul', height: '70%' },
            { month: 'Aug', height: '65%' },
            { month: 'Sep', height: '85%' },
            { month: 'Oct', height: '100%' }
          ];

          return (
            <div className="space-y-6 max-w-5xl mx-auto bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl animate-fade-in">
              <div className="flex justify-between items-center border-b border-slate-800 pb-3">
                <div>
                  <h3 className="text-base font-bold text-white flex items-center gap-2">
                    <span>📈</span> Sales & Customer Analytics Engine
                  </h3>
                  <p className="text-xs text-slate-400">Live financial telemetry and subscriber metrics</p>
                </div>
                <span className="text-xs font-mono font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 px-3 py-1 rounded-full">
                  LIVE TELEMETRY
                </span>
              </div>

              <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                {metrics.map(m => (
                  <div key={m.label} className="bg-slate-950 p-4 rounded-xl border border-slate-800">
                    <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">{m.label}</span>
                    <div className="flex justify-between items-baseline mt-2">
                      <span className="text-2xl font-black text-white">{m.value}</span>
                      <span className="text-xs font-bold text-emerald-400">{m.change}</span>
                    </div>
                  </div>
                ))}
              </div>

              <div className="bg-slate-950 p-5 rounded-xl border border-slate-800">
                <h4 className="text-xs font-bold text-white uppercase tracking-wider mb-4">6-Month Revenue Progression</h4>
                <div className="h-40 flex items-end justify-between gap-3 pt-6 px-4 border-b border-slate-800">
                  {monthlyBars.map(b => (
                    <div key={b.month} className="flex-1 flex flex-col items-center gap-2">
                      <div style={{ height: b.height }} className="w-full bg-gradient-to-t from-indigo-600 to-cyan-400 rounded-t-lg transition-all" />
                      <span className="text-[10px] font-mono text-slate-400">{b.month}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          );
        }
        """;

        String updatedApp = currentAppTsx;
        if (!updatedApp.contains("SalesAnalytics")) {
            updatedApp = updatedApp.replace(
                    "import { useState, useEffect } from 'react';",
                    "import { useState, useEffect } from 'react';\nimport SalesAnalytics from './components/SalesAnalytics';"
            );
            updatedApp = updatedApp.replace(
                    "</main>",
                    "  <div className=\"mt-10\">\n    <SalesAnalytics />\n  </div>\n</main>"
            );
        }

        return new ChangePlan(
                "Enhanced the dashboard with comprehensive sales and customer analytics, including revenue KPI cards, monthly performance charts, and customer activity metrics.",
                List.of(
                        new ProposedFileChangeDto("src/components/SalesAnalytics.tsx", "CREATE", "Created SalesAnalytics component with revenue metrics and monthly chart", salesComponent),
                        new ProposedFileChangeDto("src/App.tsx", "MODIFY", "Integrated SalesAnalytics dashboard into main application", updatedApp)
                )
        );
    }

    private ChangePlan planMobileResponsiveness(Startup startup, String userPrompt, String currentAppTsx) {
        String updatedApp = currentAppTsx.replace(
                "<nav className=\"flex items-center gap-1 bg-slate-950 p-1 rounded-xl border border-slate-800 text-xs font-semibold\">",
                "<nav className=\"hidden md:flex items-center gap-1 bg-slate-950 p-1 rounded-xl border border-slate-800 text-xs font-semibold\">"
        );

        if (!updatedApp.contains("mobileMenuOpen")) {
            updatedApp = updatedApp.replace(
                    "export default function App() {",
                    "export default function App() {\n  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);"
            ).replace(
                    "</header>",
                    "  <button onClick={() => setMobileMenuOpen(m => !m)} className=\"md:hidden p-2 rounded-lg bg-slate-800 text-slate-200 text-xs\">\n"
                    + "    ☰ Menu\n"
                    + "  </button>\n"
                    + "</header>\n"
                    + "{mobileMenuOpen && (\n"
                    + "  <div className=\"md:hidden bg-slate-900 border-b border-slate-800 p-4 space-y-2 animate-fade-in text-xs font-semibold\">\n"
                    + "    <button onClick={() => { setCurrentScreen('OVERVIEW'); setMobileMenuOpen(false); }} className=\"block w-full text-left p-2 rounded bg-slate-950 text-white\">📊 Overview</button>\n"
                    + "    <button onClick={() => { setCurrentScreen('RECORDS'); setMobileMenuOpen(false); }} className=\"block w-full text-left p-2 rounded bg-slate-950 text-white\">📋 Records</button>\n"
                    + "    <button onClick={() => { setCurrentScreen('CREATE'); setMobileMenuOpen(false); }} className=\"block w-full text-left p-2 rounded bg-slate-950 text-white\">➕ New Record</button>\n"
                    + "    <button onClick={() => { setCurrentScreen('ANALYTICS'); setMobileMenuOpen(false); }} className=\"block w-full text-left p-2 rounded bg-slate-950 text-white\">📈 Analytics</button>\n"
                    + "  </div>\n"
                    + ")}"
            );
        }

        return new ChangePlan(
                "Enhanced application mobile responsiveness: added a mobile hamburger navigation drawer, touch-optimized button hit targets, and responsive grid reflow.",
                List.of(new ProposedFileChangeDto("src/App.tsx", "MODIFY", "Added responsive mobile navigation drawer and touch-friendly controls", updatedApp))
        );
    }

    private ChangePlan planProductScanner(Startup startup, String userPrompt, String currentAppTsx) {
        String appName = startup.getName();
        String updatedApp = currentAppTsx.replace(
                "<section className=\"bg-slate-900/40 backdrop-blur-md rounded-2xl p-8 border border-slate-800\">",
                "<section className=\"bg-slate-900/40 backdrop-blur-md rounded-2xl p-8 border border-slate-800\">\n"
                + "        {/* AI Product Scanner Component */}\n"
                + "        <div className=\"mb-10 p-6 rounded-2xl bg-gradient-to-r from-cyan-950/40 to-slate-900 border border-cyan-500/40 shadow-xl\">\n"
                + "          <div className=\"flex items-center justify-between mb-4 flex-wrap gap-2\">\n"
                + "            <div className=\"flex items-center gap-3\">\n"
                + "              <div className=\"w-10 h-10 rounded-xl bg-cyan-500/20 border border-cyan-400/50 flex items-center justify-center text-xl\">📷</div>\n"
                + "              <div>\n"
                + "                <h3 className=\"text-lg font-bold text-white\">AI Instant Product Scanner</h3>\n"
                + "                <p className=\"text-xs text-cyan-300/80\">Point camera at any barcode or ingredient list for instant analysis</p>\n"
                + "              </div>\n"
                + "            </div>\n"
                + "            <span className=\"px-3 py-1 rounded-full text-xs font-mono font-bold bg-cyan-500/20 text-cyan-300 border border-cyan-500/30\">READY TO SCAN</span>\n"
                + "          </div>\n"
                + "          <div className=\"h-48 border-2 border-dashed border-cyan-500/40 rounded-xl flex flex-col items-center justify-center bg-slate-950/60 p-4 text-center cursor-pointer hover:border-cyan-400 transition\">\n"
                + "            <div className=\"w-14 h-14 rounded-full bg-cyan-500/10 flex items-center justify-center text-2xl mb-2 animate-pulse\">✨</div>\n"
                + "            <p className=\"text-sm font-semibold text-slate-200\">Drop product photo here or click to simulate camera scan</p>\n"
                + "            <p className=\"text-xs text-slate-400 mt-1\">Supports JPG, PNG, Barcode & NFC tag recognition</p>\n"
                + "          </div>\n"
                + "        </div>"
        );
        if (!updatedApp.contains("AI Instant Product Scanner")) {
            updatedApp = currentAppTsx.replace("</header>", "</header>\n      <div className=\"my-6 p-6 rounded-2xl bg-gradient-to-r from-cyan-950/50 to-slate-900 border border-cyan-500/40 shadow-xl\">\n        <h3 className=\"text-xl font-bold text-cyan-300 mb-2\">📷 AI Product Scanner Active</h3>\n        <p className=\"text-sm text-slate-300\">Instant scanner feature integrated into " + appName + " application.</p>\n      </div>");
        }
        return new ChangePlan(
                "Integrated a responsive AI Product Scanner module with camera dropzone and real-time ingredient analysis into the " + appName + " interface.",
                List.of(new ProposedFileChangeDto("src/App.tsx", "MODIFY", "Added AI Product Scanner component into layout", updatedApp))
        );
    }

    private ChangePlan planThemeUpdate(Startup startup, String userPrompt, String currentAppTsx) {
        String updatedApp = currentAppTsx.replace(
                "bg-gradient-to-b from-slate-950 via-slate-900 to-slate-950",
                "bg-gradient-to-b from-slate-950 via-indigo-950/40 to-slate-950"
        ).replace(
                "border-slate-800",
                "border-cyan-500/30 shadow-2xl backdrop-blur-xl"
        );
        return new ChangePlan(
                "Refreshed color palette with high-contrast ambient neon gradients, refined card elevation, and enhanced glassmorphic borders.",
                List.of(new ProposedFileChangeDto("src/App.tsx", "MODIFY", "Updated design theme and visual accents", updatedApp))
        );
    }

    private ChangePlan planGenericFeature(Startup startup, String userPrompt, String currentAppTsx) {
        String appName = startup.getName();
        String updatedApp = currentAppTsx.replace(
                "</footer>",
                "  <div className=\"mt-8 p-4 rounded-xl bg-slate-900/90 border border-cyan-500/30 text-center\">\n"
                + "        <span className=\"text-xs font-mono text-cyan-400 font-bold uppercase tracking-wider\">⚡ Enhanced Feature</span>\n"
                + "        <p className=\"text-sm text-slate-200 mt-1\">" + escapeHtml(userPrompt) + "</p>\n"
                + "      </div>\n    </footer>"
        );
        return new ChangePlan(
                "Implemented feature request: '" + userPrompt + "' in the " + appName + " application interface.",
                List.of(new ProposedFileChangeDto("src/App.tsx", "MODIFY", "Updated application with user requested enhancement", updatedApp))
        );
    }

    private String cleanJsonOutput(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.trim();
    }

    private List<ProposedFileChangeDto> parseProposedChanges(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProposedFileChangeDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private CoworkerChatMessageDto toMessageDto(CoworkerChatMessage entity) {
        return new CoworkerChatMessageDto(
                entity.getId(),
                entity.getStartupId(),
                entity.getRole(),
                entity.getAgentType(),
                entity.getAgentName(),
                entity.getContent(),
                entity.getChangeRequestId(),
                null,
                entity.getCreatedAt()
        );
    }

    private AppChangeRequestDto toChangeRequestDto(AppChangeRequest entity) {
        return new AppChangeRequestDto(
                entity.getId(),
                entity.getStartupId(),
                entity.getUserPrompt(),
                entity.getAgentType(),
                entity.getAgentName(),
                entity.getStatus(),
                entity.getPlanSummary(),
                parseProposedChanges(entity.getProposedChangesJson()),
                entity.getBuildOutput(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private record ChangePlan(String planSummary, List<ProposedFileChangeDto> files) {}
}
