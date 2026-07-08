package com.securevoting.repository;

import com.securevoting.entity.Election;
import com.securevoting.entity.Invitation;
import com.securevoting.entity.InvitationStatus;
import com.securevoting.entity.User;
import com.securevoting.entity.VoterRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByToken(String token);

    List<Invitation> findByElectionOrderByCreatedAtDesc(Election election);

    Optional<Invitation> findByElectionAndVoterRecord(Election election, VoterRecord voterRecord);

    long countByElectionAndStatus(Election election, InvitationStatus status);

    long countByElection(Election election);

    /** Every invitation the user has ACCEPTED, newest election start time first. */
    @Query("""
           SELECT i FROM Invitation i
           WHERE i.acceptedByUser = :user
             AND i.status = com.securevoting.entity.InvitationStatus.ACCEPTED
           ORDER BY i.election.startTime DESC
           """)
    List<Invitation> findAcceptedByUser(@Param("user") User user);

    /** True if the user has an accepted invitation for this specific election. */
    @Query("""
           SELECT COUNT(i) > 0 FROM Invitation i
           WHERE i.election = :election
             AND i.acceptedByUser = :user
             AND i.status = com.securevoting.entity.InvitationStatus.ACCEPTED
           """)
    boolean hasAcceptedInvitation(@Param("election") Election election, @Param("user") User user);
}