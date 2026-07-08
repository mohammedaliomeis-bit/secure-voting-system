package com.securevoting.service;

import com.securevoting.entity.Election;
import com.securevoting.entity.ElectionStatus;
import com.securevoting.repository.ElectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Periodic status transitions for elections:
 *   SCHEDULED -> ACTIVE        (when startTime arrives)
 *   SCHEDULED -> CLOSED        (when the whole window was missed)
 *   ACTIVE    -> CLOSED        (when endTime passes)
 *
 * Runs every 30 seconds with a 5 second startup delay.
 */
@Service
public class ElectionSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ElectionSchedulerService.class);

    private final ElectionRepository electionRepo;

    public ElectionSchedulerService(ElectionRepository electionRepo) {
        this.electionRepo = electionRepo;
    }

    @Scheduled(fixedDelay = 30_000L, initialDelay = 5_000L)
    @Transactional
    public void tick() {
        Instant now = Instant.now();

        // 1) SCHEDULED elections whose entire window has already passed -> CLOSED
        List<Election> missedScheduled =
                electionRepo.findByStatusAndEndTimeLessThanEqual(ElectionStatus.SCHEDULED, now);
        for (Election e : missedScheduled) {
            log.info("Scheduler: SCHEDULED -> CLOSED (missed window) for {}", e.getElectionCode());
            e.setStatus(ElectionStatus.CLOSED);
        }

        // 2) SCHEDULED elections whose startTime has arrived but endTime is still in the future -> ACTIVE
        List<Election> dueToStart =
                electionRepo.findByStatusAndStartTimeLessThanEqual(ElectionStatus.SCHEDULED, now);
        for (Election e : dueToStart) {
            if (e.getEndTime() != null && !e.getEndTime().isAfter(now)) {
                // Already handled by step 1, but defensive guard
                continue;
            }
            log.info("Scheduler: SCHEDULED -> ACTIVE for {}", e.getElectionCode());
            e.setStatus(ElectionStatus.ACTIVE);
        }

        // 3) ACTIVE elections whose endTime has passed -> CLOSED
        List<Election> dueToClose =
                electionRepo.findByStatusAndEndTimeLessThanEqual(ElectionStatus.ACTIVE, now);
        for (Election e : dueToClose) {
            log.info("Scheduler: ACTIVE -> CLOSED for {}", e.getElectionCode());
            e.setStatus(ElectionStatus.CLOSED);
        }
    }
}