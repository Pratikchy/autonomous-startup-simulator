package com.startupsimulator.dto.response;

import com.startupsimulator.model.enums.AgentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppChangeRequestDto {
    private Long id;
    private Long startupId;
    private String userPrompt;
    private AgentType agentType;
    private String agentName;
    private String status;
    private String planSummary;
    private List<ProposedFileChangeDto> proposedChanges;
    private String buildOutput;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
