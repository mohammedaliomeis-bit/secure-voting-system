package com.securevoting.repository;

import com.securevoting.entity.Election;
import com.securevoting.entity.OtpPurpose;
import com.securevoting.entity.OtpToken;
import com.securevoting.entity.User;
import com.securevoting.entity.VoterRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpToken> findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(User user, OtpPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpToken> findTopByElectionAndVoterRecordAndPurposeAndUsedFalseOrderByCreatedAtDesc(
            Election election, VoterRecord voterRecord, OtpPurpose purpose);

    @Modifying
    @Query("delete from OtpToken o where o.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}