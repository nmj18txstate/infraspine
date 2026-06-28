package com.infraspine.api;

import com.infraspine.service.IncidentService;
import com.infraspine.service.TopologyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {IncidentController.class, TopologyController.class})
@Import({IncidentService.class, TopologyRepository.class})
class IncidentControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void exposesTopology() throws Exception {
        mockMvc.perform(get("/api/topology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cluster.name").value("lab-primary"))
                .andExpect(jsonPath("$.workloads[*].name", hasItem("orders-db")));
    }

    @Test
    void exposesIncidentsAndRemediationPlan() throws Exception {
        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem("backup-target-wl-orders")));

        mockMvc.perform(get("/api/incidents/backup-target-wl-orders/remediation-plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0]").value("Identify whether the workload is stateful and what consistency guarantees it needs."));
    }

    @Test
    void returnsNotFoundForUnknownIncident() throws Exception {
        mockMvc.perform(get("/api/incidents/unknown"))
                .andExpect(status().isNotFound());
    }
}
