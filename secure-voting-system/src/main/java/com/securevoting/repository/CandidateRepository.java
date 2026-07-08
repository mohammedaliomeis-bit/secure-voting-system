package com.securevoting.repository;

import com.securevoting.entity.Candidate;
import com.securevoting.entity.Election;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    List<Candidate> findByElectionOrderByDisplayOrderAsc(Election election);

    long countByElection(Election election);

    Optional<Candidate> findByIdAndElection(Long id, Election election);
}