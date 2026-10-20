package com.startupsimulator.repository;

import com.startupsimulator.model.CoworkerChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoworkerChatMessageRepository extends JpaRepository<CoworkerChatMessage, Long> {
    List<CoworkerChatMessage> findByStartupIdOrderByCreatedAtAsc(Long startupId);
    void deleteByStartupId(Long startupId);
}
