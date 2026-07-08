package com.securevoting.service;

import com.securevoting.config.AppProperties;
import com.securevoting.crypto.ProofOfWork;
import com.securevoting.entity.Block;
import com.securevoting.repository.BlockRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

@Service
public class BlockchainService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);
    private static final String ZERO64 = "0".repeat(64);
    private static final String GENESIS_PAYLOAD = "GENESIS";

    private final BlockRepository blockRepo;
    private final int difficulty;
    private final Path ledgerPath;

    public BlockchainService(BlockRepository blockRepo, AppProperties props) {
        this.blockRepo = blockRepo;
        this.difficulty = props.getBlockchain().getDifficulty();
        this.ledgerPath = Paths.get(props.getBlockchain().getLedgerPath());
    }

    /* ==================== Lifecycle ==================== */

    @PostConstruct
    @Transactional
    public void ensureGenesis() {
        try {
            if (ledgerPath.getParent() != null) {
                Files.createDirectories(ledgerPath.getParent());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create blockchain ledger directory: " + ledgerPath, ex);
        }

        if (blockRepo.count() == 0) {
            log.info("No blocks found. Mining genesis block (difficulty={})...", difficulty);
            ProofOfWork.MinedBlock mined = ProofOfWork.mine(0, ZERO64, GENESIS_PAYLOAD, difficulty);
            Block genesis = new Block();
            genesis.setBlockIndex(0);
            genesis.setPrevHash(ZERO64);
            genesis.setHash(mined.hash());
            genesis.setNonce(mined.nonce());
            genesis.setTimestamp(mined.timestamp());
            genesis.setEncryptedData(GENESIS_PAYLOAD);
            blockRepo.save(genesis);
            appendToLedger(genesis);
            log.info("Genesis block mined: hash={}", mined.hash());
        } else {
            log.info("Blockchain ready. {} block(s) in chain.", blockRepo.count());
        }
    }

    /* ==================== Public API ==================== */

    /**
     * Mines and persists a new block on top of the chain.
     * @param encryptedData opaque payload (Phase 9: Base64 of RSA-encrypted vote JSON).
     * @return the saved Block.
     */
    @Transactional
    public synchronized Block addBlock(String encryptedData) {
        if (encryptedData == null || encryptedData.isBlank()) {
            throw new IllegalArgumentException("encryptedData is required.");
        }
        Block prev = blockRepo.findTopByOrderByBlockIndexDesc()
                .orElseThrow(() -> new IllegalStateException(
                        "Chain is empty. Genesis block missing — restart the application."));

        long nextIndex = prev.getBlockIndex() + 1;
        ProofOfWork.MinedBlock mined = ProofOfWork.mine(
                nextIndex, prev.getHash(), encryptedData, difficulty);

        Block block = new Block();
        block.setBlockIndex(nextIndex);
        block.setPrevHash(prev.getHash());
        block.setHash(mined.hash());
        block.setNonce(mined.nonce());
        block.setTimestamp(mined.timestamp());
        block.setEncryptedData(encryptedData);
        Block saved = blockRepo.save(block);
        appendToLedger(saved);
        log.info("Block #{} mined and persisted. nonce={}, hash={}",
                nextIndex, mined.nonce(), mined.hash());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Block> getAllBlocks() {
        return blockRepo.findAllByOrderByBlockIndexAsc();
    }

    @Transactional(readOnly = true)
    public Block getByIndex(long index) {
        return blockRepo.findByBlockIndex(index)
                .orElseThrow(() -> new IllegalArgumentException("Block not found at index " + index));
    }

    @Transactional(readOnly = true)
    public long size() {
        return blockRepo.count();
    }

    /**
     * Validates the full chain. Returns a report listing any tampering.
     */
    @Transactional(readOnly = true)
    public ValidationReport validateChain() {
        List<Block> blocks = blockRepo.findAllByOrderByBlockIndexAsc();
        ValidationReport report = new ValidationReport(blocks.size());
        String target = "0".repeat(difficulty);

        String expectedPrevHash = ZERO64;
        long expectedIndex = 0;
        for (Block b : blocks) {
            if (b.getBlockIndex() != expectedIndex) {
                report.addError(b.getBlockIndex(), "Index gap. Expected " + expectedIndex);
            }
            if (!b.getPrevHash().equals(expectedPrevHash)) {
                report.addError(b.getBlockIndex(),
                        "prevHash mismatch. Expected " + expectedPrevHash + ", got " + b.getPrevHash());
            }
            String recomputed = ProofOfWork.hashOf(
                    b.getBlockIndex(), b.getPrevHash(), b.getTimestamp(),
                    b.getEncryptedData(), b.getNonce());
            if (!recomputed.equals(b.getHash())) {
                report.addError(b.getBlockIndex(),
                        "Hash mismatch. Block tampered. Stored=" + b.getHash() + ", recomputed=" + recomputed);
            }
            if (!b.getHash().startsWith(target)) {
                report.addError(b.getBlockIndex(),
                        "Hash does not satisfy difficulty " + difficulty + ".");
            }
            expectedPrevHash = b.getHash();
            expectedIndex++;
        }
        return report;
    }

    /* ==================== Internal ==================== */

    private void appendToLedger(Block b) {
        String line = String.format(
                "{\"index\":%d,\"prevHash\":\"%s\",\"hash\":\"%s\",\"nonce\":%d,\"timestamp\":%d,\"payloadLen\":%d}%n",
                b.getBlockIndex(), b.getPrevHash(), b.getHash(),
                b.getNonce(), b.getTimestamp(), b.getEncryptedData().length());
        try {
            Files.writeString(ledgerPath, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            log.error("Failed to append block #{} to ledger file {}", b.getBlockIndex(), ledgerPath, ex);
        }
    }

    /* ==================== Report DTO ==================== */

    public static class ValidationReport {
        private final int totalBlocks;
        private final java.util.List<String> errors = new java.util.ArrayList<>();

        public ValidationReport(int totalBlocks) { this.totalBlocks = totalBlocks; }
        public void addError(long blockIndex, String message) {
            errors.add("Block #" + blockIndex + ": " + message);
        }
        public boolean isValid()              { return errors.isEmpty(); }
        public int getTotalBlocks()           { return totalBlocks; }
        public java.util.List<String> getErrors() { return errors; }
    }
}