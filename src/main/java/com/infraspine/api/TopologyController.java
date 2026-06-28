package com.infraspine.api;

import com.infraspine.domain.Models.*;
import com.infraspine.service.TopologyRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topology")
public class TopologyController {
    private final TopologyRepository repository;

    public TopologyController(TopologyRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Topology topology() {
        return repository.topology();
    }
}
