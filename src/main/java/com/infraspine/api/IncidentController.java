package com.infraspine.api;

import com.infraspine.domain.Models.*;
import com.infraspine.service.IncidentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public List<Incident> incidents() {
        return incidentService.incidents();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Incident> incident(@PathVariable String id) {
        return ResponseEntity.of(incidentService.incident(id));
    }

    @GetMapping("/{id}/blast-radius")
    public ResponseEntity<BlastRadiusReport> blastRadius(@PathVariable String id) {
        return ResponseEntity.of(incidentService.blastRadius(id));
    }

    @GetMapping("/{id}/remediation-plan")
    public ResponseEntity<RemediationPlan> remediationPlan(@PathVariable String id) {
        return ResponseEntity.of(incidentService.remediationPlan(id));
    }
}
