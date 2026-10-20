package com.startupsimulator.model;

import com.startupsimulator.model.enums.AgentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "coworker_chat_messages", indexes = {
        @Index(name = "idx_coworker_chat_startup", columnList = "startup_id")
})
@Getter
@Setter
@NoArgsConstructor
public class CoworkerChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "startup_id", nullable = false)
    private Long startupId;

    @Column(nullable = false, length = 16)
    private String role; // "USER", "ASSISTANT", "SYSTEM"

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", length = 32)
    private AgentType agentType;

    @Column(name = "agent_name", length = 64)
    private String agentName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "change_request_id")
    private Long changeRequestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public CoworkerChatMessage(Long startupId, String role, AgentType agentType, String agentName, String content, Long changeRequestId) {
        this.startupId = startupId;
        this.role = role;
        this.agentType = agentType;
        this.agentName = agentName;
        this.content = content;
        this.changeRequestId = changeRequestId;
        this.createdAt = Instant.now();
    }
}
