package com.startupsimulator.service;

import com.startupsimulator.agent.AgentMessageIntent;
import com.startupsimulator.agent.StartupContext;
import com.startupsimulator.model.AgentMessage;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.EventType;
import com.startupsimulator.repository.AgentMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 3 messaging infrastructure: the real, addressable agent inbox over the
 * persisted {@link AgentMessage} store.
 *
 * <p>An agent expresses a send <em>intent</em> ({@link AgentMessageIntent}); the
 * inbox is solely responsible for VALIDATING the recipient against
 * {@link AgentType} and DELIVERING (persisting) the message. Delivery is a real
 * DB row — not a transcript string and not an in-memory Java hand-off. A
 * recipient reads its inbox via {@link #deliverTo}, which marks messages
 * consumed so they are never delivered twice.</p>
 *
 * <p>Robustness (req 15): a null sender, blank content, or a recipient that does
 * not resolve to a valid {@link AgentType} is an explicit, logged failure that
 * emits {@link EventType#AGENT_MESSAGE_FAILED}; it never throws, never writes an
 * invalid row, and never silently redirects the message to a different agent.
 * Communication is bounded by the orchestrator (each department sends at most
 * once per analysis phase; the debate is a fixed three rounds) — there is no
 * open-ended autonomous conversation loop.</p>
 */
import com.startupsimulator.trace.ExecutionTraceService;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
public class AgentInbox {

    private static final String DIRECT = "DIRECT";

    private final AgentMessageRepository messageRepository;
    private final EventService eventService;
    private final ExecutionTraceService traceService;

    public AgentInbox(AgentMessageRepository messageRepository, EventService eventService) {
        this(messageRepository, eventService, null);
    }

    @Autowired
    public AgentInbox(AgentMessageRepository messageRepository, EventService eventService, ExecutionTraceService traceService) {
        this.messageRepository = messageRepository;
        this.eventService = eventService;
        this.traceService = traceService;
    }

    /**
     * Resolve a free-form recipient string (as chosen by an agent's LLM) to a
     * valid {@link AgentType}, by enum name or by display name, case-insensitive.
     * Returns empty for anything that is not a real department.
     */
    public static Optional<AgentType> resolveRecipient(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = raw.trim();
        for (AgentType t : AgentType.values()) {
            if (t.name().equalsIgnoreCase(s) || t.getDisplayName().equalsIgnoreCase(s)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /**
     * Validate and deliver a send intent from {@code sender}. On success a real
     * AgentMessage row is persisted (unconsumed) and {@code AGENT_MESSAGE_SENT}
     * is emitted. On any validation failure nothing is persisted and
     * {@code AGENT_MESSAGE_FAILED} is emitted.
     *
     * @return the persisted message, or empty when validation failed.
     */
    @Transactional
    public Optional<AgentMessage> send(Long startupId, AgentType sender, AgentMessageIntent intent) {
        if (sender == null) {
            fail(startupId, null, intent == null ? null : intent.normalizedRecipient(),
                    "Message rejected: null sender.");
            return Optional.empty();
        }
        if (intent == null || !intent.isPresent()) {
            fail(startupId, sender, intent == null ? null : intent.normalizedRecipient(),
                    "Message rejected: empty recipient or content.");
            return Optional.empty();
        }
        Optional<AgentType> recipient = resolveRecipient(intent.normalizedRecipient());
        if (recipient.isEmpty()) {
            fail(startupId, sender, intent.normalizedRecipient(),
                    "Message rejected: '" + intent.normalizedRecipient() + "' is not a valid recipient.");
            return Optional.empty();
        }

        AgentMessage message = new AgentMessage(
                startupId, sender, recipient.get(), intent.normalizedSubject(), intent.normalizedMessage());
        message.setMessageType(DIRECT);
        message.setConsumed(false);
        AgentMessage saved = messageRepository.save(message);

        eventService.record(startupId, EventType.AGENT_MESSAGE_SENT,
                sender.getDisplayName() + " → " + recipient.get().getDisplayName()
                        + (intent.normalizedSubject() == null ? "" : ": " + intent.normalizedSubject()),
                Map.of("messageId", saved.getId(),
                        "sender", sender.name(),
                        "recipient", recipient.get().name()));

        if (traceService != null) {
            traceService.recordAgentMessage(startupId, null, sender, recipient.get(),
                    intent.normalizedSubject() != null ? intent.normalizedSubject() : "MESSAGE_SENT",
                    intent.normalizedMessage());
        }

        return Optional.of(saved);
    }

    /**
     * Direct programmatic helper to persist and deliver an AgentMessage between agents (e.g. for recovery handoffs).
     */
    @Transactional
    public Optional<AgentMessage> sendMessage(Long startupId, AgentType sender, AgentType recipient, String type, String subject, String content) {
        if (startupId == null || sender == null || recipient == null) {
            return Optional.empty();
        }
        AgentMessage message = new AgentMessage(startupId, sender, recipient, subject, content);
        message.setMessageType(type != null ? type : DIRECT);
        message.setConsumed(false);
        AgentMessage saved = messageRepository.save(message);

        eventService.record(startupId, EventType.AGENT_MESSAGE_SENT,
                sender.getDisplayName() + " → " + recipient.getDisplayName()
                        + (subject == null ? "" : ": " + subject),
                Map.of("messageId", saved.getId(),
                        "sender", sender.name(),
                        "recipient", recipient.name()));

        if (traceService != null) {
            traceService.recordAgentMessage(startupId, null, sender, recipient, subject != null ? subject : type, content);
        }
        return Optional.of(saved);
    }

    /**
     * Retrieve the unread messages addressed to {@code recipient}, mark them
     * consumed, emit {@code AGENT_MESSAGE_RECEIVED} for each, and load both the
     * inbox (section C) and the recipient's own sent history (section D) into the
     * context so the agent's prompt can render them. This is the step that
     * actually exercises the persisted communication path at runtime.
     */
    @Transactional
    public List<AgentMessage> deliverTo(StartupContext ctx, AgentType recipient) {
        Long startupId = ctx.startupId();
        List<AgentMessage> unread = messageRepository
                .findByStartupIdAndTargetAgentAndConsumedFalseOrderByIdAsc(startupId, recipient);
        for (AgentMessage m : unread) {
            m.setConsumed(true);
            messageRepository.save(m);
            eventService.record(startupId, EventType.AGENT_MESSAGE_RECEIVED,
                    recipient.getDisplayName() + " read a message from " + m.getAgentType().getDisplayName(),
                    Map.of("messageId", m.getId(),
                            "sender", m.getAgentType().name(),
                            "recipient", recipient.name()));

            if (traceService != null) {
                traceService.recordAgentMessage(startupId, null, m.getAgentType(), recipient, "MESSAGE_RECEIVED", m.getContent());
            }
        }
        ctx.deliverInbox(recipient, unread);
        ctx.recordSent(recipient, messageRepository
                .findByStartupIdAndAgentTypeOrderByIdAsc(startupId, recipient));
        return unread;
    }

    /** Dispatch every queued outgoing intent in {@code ctx} through {@link #send}. */
    @Transactional
    public void dispatchOutgoing(StartupContext ctx) {
        for (StartupContext.OutgoingMessage out : ctx.drainOutgoing()) {
            send(ctx.startupId(), out.sender(), out.intent());
        }
    }

    private void fail(Long startupId, AgentType sender, String recipient, String reason) {
        log.warn("AGENT_MESSAGE_FAILED startup={} sender={} recipient={} reason={}",
                startupId, sender, recipient, reason);
        eventService.record(startupId, EventType.AGENT_MESSAGE_FAILED, reason,
                Map.of("sender", sender == null ? "null" : sender.name(),
                        "recipient", recipient == null ? "null" : recipient));
    }
}
