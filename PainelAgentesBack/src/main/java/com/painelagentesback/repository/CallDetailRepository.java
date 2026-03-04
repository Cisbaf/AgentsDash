package com.painelagentesback.repository;

import com.painelagentesback.models.enitity.CallDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CallDetailRepository extends JpaRepository<CallDetail, Long> {
}