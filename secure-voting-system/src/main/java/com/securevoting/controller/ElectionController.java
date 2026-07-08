package com.securevoting.controller;

import com.securevoting.dto.CandidateForm;
import com.securevoting.dto.ElectionForm;
import com.securevoting.dto.SchemaFieldForm;
import com.securevoting.entity.Election;
import com.securevoting.entity.User;
import com.securevoting.exception.ResourceNotFoundException;
import com.securevoting.exception.ValidationException;
import com.securevoting.repository.UserRepository;
import com.securevoting.repository.VoterRecordRepository;
import com.securevoting.service.CandidatePhotoService;
import com.securevoting.service.ElectionService;
import com.securevoting.service.VoterRollService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/elections")
public class ElectionController {

    private final ElectionService electionService;
    private final VoterRollService voterRollService;
    private final VoterRecordRepository voterRecordRepository;
    private final UserRepository userRepository;
    private final CandidatePhotoService candidatePhotoService;

    public ElectionController(ElectionService electionService,
                              VoterRollService voterRollService,
                              VoterRecordRepository voterRecordRepository,
                              UserRepository userRepository,
                              CandidatePhotoService candidatePhotoService) {
        this.electionService = electionService;
        this.voterRollService = voterRollService;
        this.voterRecordRepository = voterRecordRepository;
        this.userRepository = userRepository;
        this.candidatePhotoService = candidatePhotoService;
    }

    // ============ Create ============
    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ElectionForm());
        }
        return "elections/new";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") ElectionForm form,
                         BindingResult br,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        if (br.hasErrors()) return "elections/new";
        User creator = requireUser(principal);
        try {
            Election created = electionService.createElection(form, creator);
            ra.addFlashAttribute("info", "Election created. Add candidates and your voter schema next.");
            return "redirect:/elections/" + created.getElectionCode() + "/edit";
        } catch (ValidationException ex) {
            br.reject("globalError", ex.getMessage());
            return "elections/new";
        }
    }

    // ============ Public show ============
    @GetMapping("/{code}")
    public String show(@PathVariable String code,
                       @AuthenticationPrincipal UserDetails principal,
                       Model model) {
        Election election = electionService.requireByCode(code);
        User viewer = principal == null ? null : findUser(principal);
        model.addAttribute("election", election);
        model.addAttribute("isOrganizer", electionService.isOrganizer(election, viewer));
        return "elections/show";
    }

    // ============ Manage ============
    @GetMapping("/{code}/edit")
    public String edit(@PathVariable String code,
                       @AuthenticationPrincipal UserDetails principal,
                       Model model) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        model.addAttribute("election", election);
        model.addAttribute("voterCount", voterRecordRepository.countByElection(election));
        if (!model.containsAttribute("metaForm"))      model.addAttribute("metaForm", ElectionForm.from(election));
        if (!model.containsAttribute("candidateForm")) model.addAttribute("candidateForm", new CandidateForm());
        if (!model.containsAttribute("schemaForm"))    model.addAttribute("schemaForm", new SchemaFieldForm());
        return "elections/edit";
    }

    @PostMapping("/{code}/meta")
    public String updateMeta(@PathVariable String code,
                             @Valid @ModelAttribute("metaForm") ElectionForm form,
                             BindingResult br,
                             @AuthenticationPrincipal UserDetails principal,
                             RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        if (br.hasErrors()) {
            ra.addFlashAttribute("metaForm", form);
            ra.addFlashAttribute("org.springframework.validation.BindingResult.metaForm", br);
            return "redirect:/elections/" + code + "/edit";
        }
        try {
            electionService.updateMeta(election, form);
            ra.addFlashAttribute("info", "Election details updated.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/edit";
    }

    // ============ Candidates ============
    @PostMapping(value = "/{code}/candidates",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String addCandidate(@PathVariable String code,
                               @Valid @ModelAttribute("candidateForm") CandidateForm form,
                               BindingResult br,
                               @AuthenticationPrincipal UserDetails principal,
                               RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        if (br.hasErrors()) {
            ra.addFlashAttribute("candidateForm", form);
            ra.addFlashAttribute("org.springframework.validation.BindingResult.candidateForm", br);
            return "redirect:/elections/" + code + "/edit";
        }
        String storedName = null;
        MultipartFile photo = form.getPhoto();
        if (photo != null && !photo.isEmpty()) {
            try {
                storedName = candidatePhotoService.store(photo);
            } catch (ValidationException ex) {
                ra.addFlashAttribute("globalError", ex.getMessage());
                return "redirect:/elections/" + code + "/edit";
            }
        }
        try {
            electionService.addCandidate(election, form, storedName);
            ra.addFlashAttribute("info", "Candidate added.");
        } catch (RuntimeException ex) {
            candidatePhotoService.delete(storedName);
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/edit";
    }

    @PostMapping("/{code}/candidates/{id}/delete")
    public String deleteCandidate(@PathVariable String code,
                                  @PathVariable Long id,
                                  @AuthenticationPrincipal UserDetails principal,
                                  RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        try {
            String removedPhoto = electionService.removeCandidate(election, id);
            candidatePhotoService.delete(removedPhoto);
            ra.addFlashAttribute("info", "Candidate removed.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/edit";
    }

    // ============ Schema ============
    @PostMapping("/{code}/schema")
    public String addSchemaField(@PathVariable String code,
                                 @Valid @ModelAttribute("schemaForm") SchemaFieldForm form,
                                 BindingResult br,
                                 @AuthenticationPrincipal UserDetails principal,
                                 RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        if (br.hasErrors()) {
            ra.addFlashAttribute("schemaForm", form);
            ra.addFlashAttribute("org.springframework.validation.BindingResult.schemaForm", br);
            return "redirect:/elections/" + code + "/edit";
        }
        try {
            electionService.addSchemaField(election, form);
            ra.addFlashAttribute("info", "Schema field added.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/edit";
    }

    @PostMapping("/{code}/schema/{id}/delete")
    public String deleteSchemaField(@PathVariable String code,
                                    @PathVariable Long id,
                                    @AuthenticationPrincipal UserDetails principal,
                                    RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        try {
            electionService.removeSchemaField(election, id);
            ra.addFlashAttribute("info", "Schema field removed.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/edit";
    }

    @PostMapping("/{code}/schema/lock")
    public String lockSchema(@PathVariable String code,
                             @AuthenticationPrincipal UserDetails principal,
                             RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        try {
            electionService.lockSchema(election);
            ra.addFlashAttribute("info", "Schema locked. Upload your voter roll next.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/edit";
    }

    // ============ Voter roll ============
    @PostMapping(value = "/{code}/voter-roll",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadVoterRoll(@PathVariable String code,
                                  @RequestPart("file") MultipartFile file,
                                  @AuthenticationPrincipal UserDetails principal,
                                  RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        try {
            var result = voterRollService.upload(election.getElectionCode(), file, owner);
            ra.addFlashAttribute("info",
                    "Voter roll uploaded — " + result.getAcceptedCount() + " accepted, "
                            + result.getRejectedCount() + " rejected.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/edit";
    }

    // ============ Lifecycle ============

    /**
     * Promote DRAFT → SCHEDULED. The scheduler will auto-activate at startTime
     * and auto-close at endTime.
     */
    @PostMapping("/{code}/schedule")
    public String schedule(@PathVariable String code,
                           @AuthenticationPrincipal UserDetails principal,
                           RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        try {
            electionService.promoteToScheduled(election);
            ra.addFlashAttribute("info",
                    "Election scheduled. It will go live automatically at its start time.");
            return "redirect:/elections/" + code;
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
            return "redirect:/elections/" + code + "/edit";
        }
    }

    /**
     * Revert SCHEDULED → DRAFT. Only allowed while startTime is still in the future.
     */
    @PostMapping("/{code}/unschedule")
    public String unschedule(@PathVariable String code,
                             @AuthenticationPrincipal UserDetails principal,
                             RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        try {
            electionService.revertToDraft(election);
            ra.addFlashAttribute("info", "Election reverted to draft.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/edit";
    }

    /**
     * Legacy manual DRAFT → ACTIVE. Prefer {@link #schedule} in production.
     */
    @PostMapping("/{code}/activate")
    public String activate(@PathVariable String code,
                           @AuthenticationPrincipal UserDetails principal,
                           RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        try {
            electionService.activate(election);
            ra.addFlashAttribute("info", "Election activated. Voting is now open in its window.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/edit";
    }

    @PostMapping("/{code}/close")
    public String close(@PathVariable String code,
                        @AuthenticationPrincipal UserDetails principal,
                        RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        try {
            electionService.close(election);
            ra.addFlashAttribute("info", "Election closed.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/edit";
    }

    @PostMapping("/{code}/delete")
    public String delete(@PathVariable String code,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        try {
            electionService.delete(election);
            ra.addFlashAttribute("info", "Draft election deleted.");
            return "redirect:/dashboard#elections";
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
            return "redirect:/elections/" + code + "/edit";
        }
    }

    // ============ Helpers ============
    private User requireUser(UserDetails principal) {
        if (principal == null) throw new AccessDeniedException("Not signed in.");
        return findUser(principal);
    }

    private User findUser(UserDetails principal) {
        String emailHash = principal.getUsername();
        return userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

}