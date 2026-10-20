package com.startupsimulator.repository;

import com.startupsimulator.model.AppChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppChangeRequestRepository extends JpaRepository<AppChangeRequest, Long> {
    List<AppChangeRequest> findByStartupIdOrderByCreatedAtDesc(Long startupId);
    Optional<AppChangeRequest> findByIdAndStartupId(Long id, Long startupId);
    void deleteByStartupId(Long startupId);
}
