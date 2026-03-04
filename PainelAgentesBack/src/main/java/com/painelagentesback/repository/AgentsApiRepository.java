package com.painelagentesback.repository;

import com.painelagentesback.models.enitity.AgentDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentsApiRepository extends JpaRepository<AgentDailyStats, Long> {
}
