package com.painelagentesback.repository;

import com.painelagentesback.models.enitity.AgentDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentDailyStatsRepository extends JpaRepository<AgentDailyStats, Long> {
    Optional<AgentDailyStats> findByAgentIdAndDate(String agentId, LocalDate date);
    List<AgentDailyStats> findByDate(LocalDate date);
}