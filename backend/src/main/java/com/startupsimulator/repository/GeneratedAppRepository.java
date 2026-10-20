package com.startupsimulator.repository;

import com.startupsimulator.model.GeneratedApp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeneratedAppRepository extends JpaRepository<GeneratedApp, Long> {
    Optional<GeneratedApp> findByStartupId(Long startupId);
    boolean existsByStartupId(Long startupId);
}
