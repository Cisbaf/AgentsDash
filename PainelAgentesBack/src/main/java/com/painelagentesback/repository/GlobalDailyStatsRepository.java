package com.painelagentesback.repository;

import com.painelagentesback.models.enitity.GlobalDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface GlobalDailyStatsRepository extends JpaRepository<GlobalDailyStats, Long> {
    Optional<GlobalDailyStats> findByDate(LocalDate date);
}