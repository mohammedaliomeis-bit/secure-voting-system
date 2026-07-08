package com.securevoting.repository;

import com.securevoting.entity.Election;
import com.securevoting.entity.VoterSchemaField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoterSchemaFieldRepository extends JpaRepository<VoterSchemaField, Long> {

    List<VoterSchemaField> findByElectionOrderByDisplayOrderAsc(Election election);

    List<VoterSchemaField> findByElectionAndIdentityFieldTrueOrderByDisplayOrderAsc(Election election);

    long countByElection(Election election);

    Optional<VoterSchemaField> findByIdAndElection(Long id, Election election);
}