package com.securevoting.controller;

import com.securevoting.crypto.HashUtil;
import com.securevoting.entity.Block;
import com.securevoting.entity.ReceiptIndex;
import com.securevoting.repository.BlockRepository;
import com.securevoting.repository.ReceiptIndexRepository;
import com.securevoting.service.BlockchainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Controller
@RequestMapping("/verify")
public class VerifyController {

    private static final Logger log = LoggerFactory.getLogger(VerifyController.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(ZoneId.systemDefault());

    private final ReceiptIndexRepository receiptRepo;
    private final BlockRepository blockRepo;
    private final BlockchainService blockchainService;

    public VerifyController(ReceiptIndexRepository receiptRepo,
                            BlockRepository blockRepo,
                            BlockchainService blockchainService) {
        this.receiptRepo = receiptRepo;
        this.blockRepo = blockRepo;
        this.blockchainService = blockchainService;
    }

    @GetMapping
    public String form() {
        return "verify/index";
    }

    @PostMapping
    public String verify(@RequestParam("receipt") String rawInput,
                         Model model,
                         RedirectAttributes ra) {

        String digits = rawInput == null ? "" : rawInput.replaceAll("\\D", "");

        if (digits.length() != 16) {
            ra.addFlashAttribute("error",
                    "Please enter your full 16-digit receipt code.");
            return "redirect:/verify";
        }

        String receiptHash = HashUtil.sha256Hex(digits);
        Optional<ReceiptIndex> opt = receiptRepo.findByReceiptHash(receiptHash);

        if (opt.isEmpty()) {
            AUDIT.info("VERIFY_NOT_FOUND hash={}", receiptHash.substring(0, 12));
            ra.addFlashAttribute("error",
                    "We couldn't find a vote matching that receipt. Check the code and try again.");
            return "redirect:/verify";
        }

        ReceiptIndex idx = opt.get();
        Optional<Block> blockOpt = blockRepo.findByBlockIndex(idx.getBlockIndex());
        if (blockOpt.isEmpty()) {
            log.error("Receipt {} points to missing block #{}",
                    receiptHash.substring(0, 12), idx.getBlockIndex());
            ra.addFlashAttribute("error",
                    "Internal error — please contact the election operator.");
            return "redirect:/verify";
        }
        Block block = blockOpt.get();

        BlockchainService.ValidationReport report = blockchainService.validateChain();
        String encryptedPreview = truncate(block.getEncryptedData(), 80);

        AUDIT.info("VERIFY_OK election={} block={}",
                idx.getElection().getElectionCode(), idx.getBlockIndex());

        model.addAttribute("electionTitle",  idx.getElection().getTitle());
        model.addAttribute("electionCode",   idx.getElection().getElectionCode());
        model.addAttribute("blockIndex",     block.getBlockIndex());
        model.addAttribute("blockHash",      block.getHash());
        model.addAttribute("prevHash",       block.getPrevHash());
        model.addAttribute("nonce",          block.getNonce());
        model.addAttribute("blockTimestamp", TS_FMT.format(Instant.ofEpochMilli(block.getTimestamp())));
        model.addAttribute("encryptedPreview", encryptedPreview);
        model.addAttribute("encryptedSize",
                block.getEncryptedData() == null ? 0 : block.getEncryptedData().length());

        // BUG FIX #4: removed the duplicate "chainValid" setter (was added twice).
        model.addAttribute("chainValid", report.isValid());
        model.addAttribute("chainMessage",
                report.isValid()
                        ? "All " + report.getTotalBlocks() + " blocks verified — no tampering detected."
                        : String.join(" | ", report.getErrors()));

        return "verify/result";
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}