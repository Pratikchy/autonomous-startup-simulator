package com.startupsimulator.service;

import com.startupsimulator.agent.StartupContext;
import com.startupsimulator.model.*;
import com.startupsimulator.model.enums.AgentType;
import com.startupsimulator.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bridges the in-memory {@link StartupContext} (what agents mutate) and the
 * persistent tables. The orchestrator builds a context, lets the agents fill it
 * in, then calls {@link #persist} to write the shared state back.
 */
@Service
@RequiredArgsConstructor
public class StartupContextService {

    private final MvpFeatureRepository featureRepository;
    private final TechnicalPlanRepository technicalPlanRepository;
    private final MarketingPlanRepository marketingPlanRepository;
    private final BudgetRepository budgetRepository;
    private final RoadmapMilestoneRepository roadmapRepository;

    public StartupContext buildContext(Startup startup) {
        StartupContext ctx = new StartupContext(startup);
        if (startup != null && startup.getId() != null) {
            Long startupId = startup.getId();
            List<MvpFeature> features = featureRepository.findByStartupIdOrderByDisplayOrder(startupId);
            for (MvpFeature f : features) {
                ctx.addFeature(f);
            }
            List<RoadmapMilestone> milestones = roadmapRepository.findByStartupIdOrderByDisplayOrder(startupId);
            for (RoadmapMilestone rm : milestones) {
                ctx.addMilestone(rm);
            }
            if (startup.getRisks() != null && !startup.getRisks().isBlank()) {
                for (String r : startup.getRisks().split("\n")) {
                    ctx.addRisk(r);
                }
            }
            technicalPlanRepository.findByStartupId(startupId).ifPresent(tp -> copyTechnicalPlan(tp, ctx.getTechnicalPlan()));
            marketingPlanRepository.findByStartupId(startupId).ifPresent(mp -> copyMarketingPlan(mp, ctx.getMarketingPlan()));
            budgetRepository.findByStartupId(startupId).ifPresent(b -> copyBudget(b, ctx.getBudget()));
        }
        return ctx;
    }

    @Transactional
    public void persist(StartupContext ctx) {
        Long startupId = ctx.startupId();
        if (ctx.getTechnicalPlan().getId() == null) {
            technicalPlanRepository.findByStartupId(startupId)
                    .ifPresent(existing -> ctx.getTechnicalPlan().setId(existing.getId()));
        }
        if (ctx.getMarketingPlan().getId() == null) {
            marketingPlanRepository.findByStartupId(startupId)
                    .ifPresent(existing -> ctx.getMarketingPlan().setId(existing.getId()));
        }
        if (ctx.getBudget().getId() == null) {
            budgetRepository.findByStartupId(startupId)
                    .ifPresent(existing -> ctx.getBudget().setId(existing.getId()));
        }
        featureRepository.saveAll(ctx.getMvpFeatures());
        roadmapRepository.saveAll(ctx.getRoadmap());
        technicalPlanRepository.save(ctx.getTechnicalPlan());
        marketingPlanRepository.save(ctx.getMarketingPlan());
        budgetRepository.save(ctx.getBudget());
        ctx.getStartup().setRisks(String.join("\n", ctx.getRisks()));
    }

    private void copyTechnicalPlan(TechnicalPlan src, TechnicalPlan dest) {
        dest.setId(src.getId());
        dest.setArchitecture(src.getArchitecture());
        dest.setTechStack(src.getTechStack());
        dest.setTimeline(src.getTimeline());
        dest.setTechnicalRisks(src.getTechnicalRisks());
        dest.setEstimatedEngineeringMonths(src.getEstimatedEngineeringMonths());
        dest.setAnalysisProvider(src.getAnalysisProvider());
        dest.setAnalysisFailed(src.isAnalysisFailed());
        dest.setAnalysisError(src.getAnalysisError());
    }

    private void copyMarketingPlan(MarketingPlan src, MarketingPlan dest) {
        dest.setId(src.getId());
        dest.setPositioning(src.getPositioning());
        dest.setPricingStrategy(src.getPricingStrategy());
        dest.setChannels(src.getChannels());
        dest.setCompetitorAnalysis(src.getCompetitorAnalysis());
        dest.setGoToMarket(src.getGoToMarket());
        dest.setAnalysisProvider(src.getAnalysisProvider());
        dest.setAnalysisFailed(src.isAnalysisFailed());
        dest.setAnalysisError(src.getAnalysisError());
    }

    private void copyBudget(Budget src, Budget dest) {
        dest.setId(src.getId());
        dest.setDevelopmentCost(src.getDevelopmentCost());
        dest.setInfrastructureCost(src.getInfrastructureCost());
        dest.setMarketingBudget(src.getMarketingBudget());
        dest.setOperatingCost(src.getOperatingCost());
        dest.setStartingCapital(src.getStartingCapital());
        dest.setMonthlyBurn(src.getMonthlyBurn());
        dest.setProjectedMonthlyRevenue(src.getProjectedMonthlyRevenue());
        dest.setRunwayMonths(src.getRunwayMonths());
        dest.setBreakEvenAssumption(src.getBreakEvenAssumption());
        dest.setAnalysisProvider(src.getAnalysisProvider());
        dest.setAnalysisFailed(src.isAnalysisFailed());
        dest.setAnalysisError(src.getAnalysisError());
    }

    /** Persist just the parts that change when the boardroom trims scope. */
    @Transactional
    public void persistScopeChange(StartupContext ctx) {
        featureRepository.saveAll(ctx.getMvpFeatures());
        budgetRepository.save(ctx.getBudget());
    }

    /**
     * Persist a single department's re-run analysis safely. A retry builds a
     * FRESH {@link StartupContext}, so its plan rows have {@code id == null} and
     * its feature list starts empty. Blindly saving would insert a duplicate and
     * violate the {@code unique(startup_id)} constraint, so we upsert the plan by
     * copying the existing row's id, and for Development we replace the feature
     * set (delete-then-insert) since the LLM may propose a different scope.
     */
    @Transactional
    public void persistDepartmentRetry(StartupContext ctx, AgentType type) {
        Long startupId = ctx.startupId();
        switch (type) {
            case DEVELOPMENT -> {
                featureRepository.deleteByStartupId(startupId);
                featureRepository.flush();
                featureRepository.saveAll(ctx.getMvpFeatures());
                TechnicalPlan tp = ctx.getTechnicalPlan();
                technicalPlanRepository.findByStartupId(startupId)
                        .ifPresent(existing -> tp.setId(existing.getId()));
                technicalPlanRepository.save(tp);
            }
            case MARKETING -> {
                MarketingPlan mp = ctx.getMarketingPlan();
                marketingPlanRepository.findByStartupId(startupId)
                        .ifPresent(existing -> mp.setId(existing.getId()));
                marketingPlanRepository.save(mp);
            }
            case FINANCE -> {
                Budget b = ctx.getBudget();
                budgetRepository.findByStartupId(startupId)
                        .ifPresent(existing -> b.setId(existing.getId()));
                budgetRepository.save(b);
            }
            default -> { /* CEO has its own retry path; nothing to upsert here. */ }
        }
    }

    @Transactional(readOnly = true)
    public List<MvpFeature> features(Long startupId) {
        return featureRepository.findByStartupIdOrderByDisplayOrder(startupId);
    }

    @Transactional(readOnly = true)
    public TechnicalPlan technicalPlan(Long startupId) {
        return technicalPlanRepository.findByStartupId(startupId).orElse(null);
    }

    @Transactional(readOnly = true)
    public MarketingPlan marketingPlan(Long startupId) {
        return marketingPlanRepository.findByStartupId(startupId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Budget budget(Long startupId) {
        return budgetRepository.findByStartupId(startupId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<RoadmapMilestone> roadmap(Long startupId) {
        return roadmapRepository.findByStartupIdOrderByDisplayOrder(startupId);
    }
}
