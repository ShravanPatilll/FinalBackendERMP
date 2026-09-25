package com.erp.reimbursement.controller;

import com.erp.reimbursement.dto.AdvanceDtos.*;
import com.erp.reimbursement.service.AdvanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/advances")
public class AdvanceController {

    private final AdvanceService s;

    public AdvanceController(AdvanceService s) {
        this.s = s;
    }

    @PostMapping
    public ResponseEntity<AdvanceResponse> create(
            @Valid @RequestBody CreateAdvanceRequest r) {

        return ResponseEntity
                .status(201)
                .body(s.create(r));
    }

    @GetMapping("/mine")
    public List<AdvanceResponse> mine() {
        return s.mine();
    }

    @GetMapping("/manager/all")
    public List<AdvanceResponse> mall() {
        return s.managerAll();
    }

    @GetMapping("/manager/pending")
    public List<AdvanceResponse> mp() {
        return s.managerPending();
    }

    @GetMapping("/finance/pending")
    public List<AdvanceResponse> fp() {
        return s.financePending();
    }

    @GetMapping("/all")
    public List<AdvanceResponse> all() {
        return s.all();
    }

    @GetMapping("/{id}")
    public AdvanceResponse get(
            @PathVariable Long id) {

        return s.get(id);
    }

    @PutMapping("/{id}/manager/approve")
    public AdvanceResponse ma(
            @PathVariable Long id) {

        return s.managerAction(
                id,
                true,
                null
        );
    }

    @PutMapping("/{id}/manager/reject")
    public AdvanceResponse mr(
            @PathVariable Long id,
            @RequestBody @Valid ActionRequest r) {

        return s.managerAction(
                id,
                false,
                r.remark()
        );
    }

    @PutMapping("/{id}/pay")
    public AdvanceResponse pay(
            @PathVariable Long id,
            @RequestBody @Valid AdvancePaymentRequest r) {

        return s.pay(id, r);
    }
}