package com.securevoting.repository;

import com.securevoting.entity.Election;
import com.securevoting.entity.ElectionStatus;
import com.securevoting.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ElectionRepository extends JpaRepository<Election, Long> {

    Optional<Election> findByElectionCode(String electionCode);

    boolean existsByElectionCode(String electionCode);

    List<Election> findByStatusAndStartTimeBeforeAndEndTimeAfter(
            ElectionStatus status, Instant startBefore, Instant endAfter);

    /** Used by the scheduler to find SCHEDULED elections whose start time has arrived. */
    List<Election> findByStatusAndStartTimeLessThanEqual(ElectionStatus status, Instant time);

    /** Used by the scheduler to find elections whose end time has already passed. */
    List<Election> findByStatusAndEndTimeLessThanEqual(ElectionStatus status, Instant time);

    List<Election> findByCreatorOrderByCreatedAtDesc(User creator);
}