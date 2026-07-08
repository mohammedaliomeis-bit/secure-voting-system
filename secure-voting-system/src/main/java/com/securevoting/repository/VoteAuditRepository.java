package com.securevoting.repository;

import com.securevoting.entity.Election;
import com.securevoting.entity.VoteAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoteAuditRepository extends JpaRepository<VoteAudit, Long> {

    long countByElection(Election election);

    List<VoteAudit> findByElectionOrderByVotedAtAsc(Election election);
}