package com.startupsimulator.dto.response;

import com.startupsimulator.model.enums.AgentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoworkerChatMessageDto {
    private Long id;
    private Long startupId;
    private String role;
    private AgentType agentType;
    private String agentName;
    private String content;
    private Long changeRequestId;
    private AppChangeRequestDto changeRequest;
    private Instant createdAt;
}
