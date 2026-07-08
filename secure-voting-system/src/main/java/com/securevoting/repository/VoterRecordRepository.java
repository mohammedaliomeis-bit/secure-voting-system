package com.securevoting.repository;

import com.securevoting.entity.Election;
import com.securevoting.entity.VoterRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoterRecordRepository extends JpaRepository<VoterRecord, Long> {

    long countByElection(Election election);

    /** Used by InvitationService to iterate the roll when generating invitations. */
    List<VoterRecord> findByElection(Election election);

    boolean existsByElectionAndIdentityHash(Election election, String identityHash);

    Optional<VoterRecord> findByElectionAndIdentityHash(Election election, String identityHash);

    Optional<VoterRecord> findByElectionAndContactHash(Election election, String contactHash);

    void deleteAllByElection(Election election);

    /**
     * Returns every voter record on ACTIVE elections currently inside their voting window
     * whose contact-email hash matches the given user's email hash and which have not yet voted.
     */
    @Query("""
           SELECT vr FROM VoterRecord vr
           WHERE vr.contactHash = :contactHash
             AND vr.voted = false
             AND vr.election.status = com.securevoting.entity.ElectionStatus.ACTIVE
             AND vr.election.startTime <= CURRENT_TIMESTAMP
             AND vr.election.endTime   >  CURRENT_TIMESTAMP
           ORDER BY vr.election.endTime ASC
           """)
    List<VoterRecord> findAvailableForContactHash(@Param("contactHash") String contactHash);
}