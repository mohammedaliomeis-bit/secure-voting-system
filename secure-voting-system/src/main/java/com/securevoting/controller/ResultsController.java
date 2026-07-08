package com.securevoting.controller;

import com.securevoting.dto.CandidateResult;
import com.securevoting.dto.ElectionResults;
import com.securevoting.entity.Election;
import com.securevoting.entity.ElectionStatus;
import com.securevoting.entity.User;
import com.securevoting.service.ElectionService;
import com.securevoting.service.ResultsService;
import com.securevoting.service.UserService;
import com.securevoting.service.VoteService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ResultsController {

    private final ResultsService resultsService;
    private final UserService userService;
    private final ElectionService electionService;
    private final VoteService voteService;

    public ResultsController(ResultsService resultsService,
                             UserService userService,
                             ElectionService electionService,
                             VoteService voteService) {
        this.resultsService = resultsService;
        this.userService = userService;
        this.electionService = electionService;
        this.voteService = voteService;
    }

    @GetMapping("/elections/{code}/results")
    public String results(@PathVariable String code,
                          Authentication auth,
                          Model model,
                          RedirectAttributes ra) {
        User actor = userService.requireByEmail(auth.getName());
        Election election = electionService.requireByCode(code);

        boolean isOwner  = electionService.isOrganizer(election, actor);
        boolean isVoter  = voteService.hasUserVoted(actor, election);
        boolean isClosed = election.getStatus() == ElectionStatus.CLOSED;
        boolean isDraft  = election.getStatus() == ElectionStatus.DRAFT;

        // Authorization
        //  - Organizer: can view any non-DRAFT election's results.
        //  - Voter:    can view only once the election is CLOSED.
        if (isDraft) {
            ra.addFlashAttribute("globalError",
                    "This election is still in draft. No votes have been cast yet.");
            return "redirect:/elections/" + code;
        }
        if (!isOwner) {
            if (!isVoter) {
                ra.addFlashAttribute("globalError",
                        "You can only view results for elections you organized or voted in.");
                return "redirect:/elections/" + code;
            }
            if (!isClosed) {
                ra.addFlashAttribute("globalError",
                        "Results will be available once the election is closed.");
                return "redirect:/elections/" + code;
            }
        }

        ElectionResults r = resultsService.compute(code);
        model.addAttribute("r", r);
        model.addAttribute("isOwner", isOwner);

        // Pre-serialize for Chart.js (no Jackson needed)
        StringBuilder labels = new StringBuilder("[");
        StringBuilder votes  = new StringBuilder("[");
        boolean first = true;
        for (CandidateResult c : r.results()) {
            if (!first) {
                labels.append(",");
                votes.append(",");
            }
            labels.append("\"").append(escapeJson(c.name())).append("\"");
            votes.append(c.votes());
            first = false;
        }
        labels.append("]");
        votes.append("]");

        model.addAttribute("chartLabelsJson", labels.toString());
        model.addAttribute("chartVotesJson",  votes.toString());

        return "elections/results";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"'  -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        return out.toString();
    }
}