package com.startupsimulator.controller;

import com.startupsimulator.dto.response.GeneratedAppDto;
import com.startupsimulator.dto.response.GeneratedFileContentDto;
import com.startupsimulator.dto.response.GeneratedFileInfoDto;
import com.startupsimulator.model.GeneratedApp;
import com.startupsimulator.repository.GeneratedAppRepository;
import com.startupsimulator.service.AppBuildService;
import com.startupsimulator.service.AppGeneratorService;
import com.startupsimulator.service.AppPreviewService;
import com.startupsimulator.service.AppWorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

import com.startupsimulator.dto.request.CoworkerChatRequestDto;
import com.startupsimulator.dto.response.AppChangeRequestDto;
import com.startupsimulator.dto.response.CoworkerChatMessageDto;
import com.startupsimulator.service.CoworkerChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/startups/{id}/app")
public class GeneratedAppController {

    private final AppGeneratorService generatorService;
    private final AppBuildService buildService;
    private final AppPreviewService previewService;
    private final AppWorkspaceService workspaceService;
    private final GeneratedAppRepository appRepository;
    private final CoworkerChatService chatService;

    public GeneratedAppController(
            AppGeneratorService generatorService,
            AppBuildService buildService,
            AppPreviewService previewService,
            AppWorkspaceService workspaceService,
            GeneratedAppRepository appRepository,
            CoworkerChatService chatService) {
        this.generatorService = generatorService;
        this.buildService = buildService;
        this.previewService = previewService;
        this.workspaceService = workspaceService;
        this.appRepository = appRepository;
        this.chatService = chatService;
    }

    @PostMapping("/generate")
    public ResponseEntity<GeneratedAppDto> generate(@PathVariable Long id) throws IOException {
        GeneratedApp app = generatorService.generateApp(id);
        return ResponseEntity.ok(GeneratedAppDto.fromEntity(app));
    }

    @GetMapping
    public ResponseEntity<GeneratedAppDto> getStatus(@PathVariable Long id) {
        return appRepository.findByStartupId(id)
                .map(app -> ResponseEntity.ok(GeneratedAppDto.fromEntity(app)))
                .orElse(ResponseEntity.ok(GeneratedAppDto.ungenerated(id)));
    }

    @GetMapping("/files")
    public ResponseEntity<List<GeneratedFileInfoDto>> listFiles(@PathVariable Long id) {
        List<GeneratedFileInfoDto> files = workspaceService.listFiles(id);
        return ResponseEntity.ok(files);
    }

    @GetMapping("/files/content")
    public ResponseEntity<GeneratedFileContentDto> getFileContent(
            @PathVariable Long id,
            @RequestParam String path) throws IOException {
        GeneratedFileContentDto file = workspaceService.readFileContent(id, path);
        return ResponseEntity.ok(file);
    }

    @PostMapping("/build")
    public ResponseEntity<GeneratedAppDto> build(@PathVariable Long id) {
        GeneratedApp app = buildService.buildApp(id);
        return ResponseEntity.ok(GeneratedAppDto.fromEntity(app));
    }

    @PostMapping("/preview/start")
    public ResponseEntity<GeneratedAppDto> startPreview(@PathVariable Long id) throws IOException {
        GeneratedApp app = previewService.startPreview(id);
        return ResponseEntity.ok(GeneratedAppDto.fromEntity(app));
    }

    @PostMapping("/preview/stop")
    public ResponseEntity<GeneratedAppDto> stopPreview(@PathVariable Long id) {
        GeneratedApp app = previewService.stopPreview(id);
        return ResponseEntity.ok(GeneratedAppDto.fromEntity(app));
    }

    @RequestMapping(value = {"/preview", "/preview/", "/preview/**"}, method = {RequestMethod.GET, RequestMethod.HEAD})
    public void proxyPreview(
            @PathVariable Long id,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        previewService.proxyRequest(id, request, response);
    }

    @GetMapping("/chat")
    public ResponseEntity<List<CoworkerChatMessageDto>> getChatHistory(@PathVariable Long id) {
        List<CoworkerChatMessageDto> history = chatService.getHistory(id);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/chat")
    public ResponseEntity<CoworkerChatMessageDto> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody CoworkerChatRequestDto request) {
        CoworkerChatMessageDto response = chatService.processUserMessage(id, request.getMessage());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/change-requests")
    public ResponseEntity<List<AppChangeRequestDto>> getChangeRequests(@PathVariable Long id) {
        List<AppChangeRequestDto> requests = chatService.getChangeRequests(id);
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/change-requests/{requestId}/approve")
    public ResponseEntity<AppChangeRequestDto> approveChangeRequest(
            @PathVariable Long id,
            @PathVariable Long requestId) {
        AppChangeRequestDto updated = chatService.approveChangeRequest(id, requestId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/change-requests/{requestId}/reject")
    public ResponseEntity<AppChangeRequestDto> rejectChangeRequest(
            @PathVariable Long id,
            @PathVariable Long requestId) {
        AppChangeRequestDto updated = chatService.rejectChangeRequest(id, requestId);
        return ResponseEntity.ok(updated);
    }
}
