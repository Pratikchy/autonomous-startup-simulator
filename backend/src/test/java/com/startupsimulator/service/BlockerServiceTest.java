package com.startupsimulator.service;

import com.startupsimulator.exception.ResourceNotFoundException;
import com.startupsimulator.model.ExecutionTask;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.model.enums.TaskPriority;
import com.startupsimulator.model.enums.TaskStatus;
import com.startupsimulator.repository.ExecutionTaskRepository;
import com.startupsimulator.trace.ExecutionTraceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockerServiceTest {
    @Mock private ExecutionTaskRepository taskRepository;
    @Mock private ExecutionTraceService traceService;
    private BlockerService blockerService;
    private ExecutionTask task;
    private ExecutionTask dependency;

    @BeforeEach
    void setUp() {
        blockerService = new BlockerService(taskRepository, traceService);
        task = new ExecutionTask(2L, AgentType.DEVELOPMENT, "Backend Eng",
                "Core Database & API Infrastructure", "Desc", TaskPriority.HIGH, 6);
        task.setId(3L);
        task.setStatus(TaskStatus.BLOCKED);
        task.setProgress(1.0);
        task.setDependencies(List.of(2L));
        task.setBlockerReason("DEVELOPMENT execution blocked on Day 8: Dependencies delayed due to technical risk review.");
        dependency = new ExecutionTask(2L, AgentType.DEVELOPMENT, "Dev Lead",
                "Repo & Environment Architecture Setup", "Desc", TaskPriority.HIGH, 4);
        dependency.setId(2L);
        dependency.setStatus(TaskStatus.COMPLETED);
        when(taskRepository.findById(3L)).thenReturn(Optional.of(task));
    }

    @Test
    void explicitResolutionPreservesWorkAndAuditsOriginalReason() {
        when(taskRepository.findByStartupIdOrderById(2L)).thenReturn(List.of(dependency, task));
        String originalReason = task.getBlockerReason();
        ExecutionTask resolved = blockerService.resolveBlocker(2L, 3L);
        assertEquals(TaskStatus.IN_PROGRESS, resolved.getStatus());
        assertEquals(1.0, resolved.getProgress());
        assertEquals(0, resolved.getActualDays());
        assertEquals(List.of(2L), resolved.getDependencies());
        assertNull(resolved.getBlockerReason());
        verify(taskRepository).save(task);
        verify(traceService).recordTaskStateChange(eq(2L), isNull(), eq("DEVELOPMENT"), eq(3L),
                eq(task.getTitle()), eq(TaskStatus.BLOCKED), eq(TaskStatus.IN_PROGRESS),
                argThat(reason -> reason.contains(originalReason) && reason.contains("Validated completed dependencies [2]")
                        && reason.contains("calendar-injected")));
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"PENDING", "IN_PROGRESS", "BLOCKED", "DEFERRED", "CANCELLED"})
    void cannotResolveWithIncompleteDependency(TaskStatus status) {
        dependency.setStatus(status);
        when(taskRepository.findByStartupIdOrderById(2L)).thenReturn(List.of(dependency, task));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> blockerService.resolveBlocker(2L, 3L));
        assertTrue(error.getMessage().contains("#2 Repo & Environment Architecture Setup (" + status + ")"));
        assertTrue(error.getMessage().contains("complete or explicitly replan"));
        assertEquals(TaskStatus.BLOCKED, task.getStatus());
        assertNotNull(task.getBlockerReason());
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(traceService);
    }

    @Test
    void missingOrCrossStartupDependencyCannotBeBypassed() {
        when(taskRepository.findByStartupIdOrderById(2L)).thenReturn(List.of(task));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> blockerService.resolveBlocker(2L, 3L));
        assertTrue(error.getMessage().contains("#2 (missing from this startup)"));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void cannotResolveTaskFromAnotherStartup() {
        assertThrows(ResourceNotFoundException.class, () -> blockerService.resolveBlocker(1L, 3L));
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(traceService);
    }

    @Test
    void repeatedResolutionDoesNotRestartOrRecordDuplicateHistory() {
        task.setStatus(TaskStatus.IN_PROGRESS);
        assertThrows(IllegalStateException.class, () -> blockerService.resolveBlocker(2L, 3L));
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(traceService);
    }

    @Test
    void explicitResolutionDoesNotOccupyAlreadyBusyDepartment() {
        ExecutionTask other = new ExecutionTask(2L, AgentType.DEVELOPMENT, "Dev Lead", "Other work", "", TaskPriority.HIGH, 3);
        other.setId(4L);
        other.setStatus(TaskStatus.IN_PROGRESS);
        when(taskRepository.findByStartupIdOrderById(2L)).thenReturn(List.of(dependency, task, other));
        assertThrows(IllegalStateException.class, () -> blockerService.resolveBlocker(2L, 3L));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void realBlockerRequiresExplicitAcknowledgementNotCalendarRecovery() {
        task.setBlockerReason("Security lead must approve threat model");
        when(taskRepository.findByStartupIdOrderById(2L)).thenReturn(List.of(dependency, task));
        blockerService.resolveBlocker(2L, 3L);
        verify(traceService).recordTaskStateChange(eq(2L), isNull(), eq("DEVELOPMENT"), eq(3L),
                eq(task.getTitle()), eq(TaskStatus.BLOCKED), eq(TaskStatus.IN_PROGRESS),
                argThat(reason -> reason.contains("explicitly acknowledged") && reason.contains("Security lead must approve threat model")
                        && !reason.contains("calendar-injected")));
    }
}
