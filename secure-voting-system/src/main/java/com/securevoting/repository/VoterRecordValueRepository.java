package com.securevoting.repository;

import com.securevoting.entity.VoterRecordValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoterRecordValueRepository extends JpaRepository<VoterRecordValue, Long> {
}