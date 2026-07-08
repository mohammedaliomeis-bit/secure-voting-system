package com.securevoting.repository;

import com.securevoting.entity.Election;
import com.securevoting.entity.ElectionParticipation;
import com.securevoting.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ElectionParticipationRepository
        extends JpaRepository<ElectionParticipation, Long> {

    /** One-vote-per-account guard — used by VoteService.identify / cast. */
    boolean existsByUserAndElection(User user, Election election);

    /** Convenience lookup; useful for audits or receipt re-issuance flows. */
    Optional<ElectionParticipation> findByUserAndElection(User user, Election election);

    /**
     * All elections this user has voted in, newest first.
     * Used by the dashboard "Elections you voted in" section.
     */
    List<ElectionParticipation> findByUserOrderByVotedAtDesc(User user);
}