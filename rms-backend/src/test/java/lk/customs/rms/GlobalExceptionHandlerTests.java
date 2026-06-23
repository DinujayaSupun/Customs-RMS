package lk.customs.rms;

import lk.customs.rms.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTests {

    @RestController
    static class OptimisticLockThrowingController {
        @GetMapping("/test/optimistic-lock")
        String boom() {
            throw new OptimisticLockingFailureException("stale document");
        }
    }

    @Test
    void optimisticLockingFailureIsMappedToConflictWithFriendlyMessage() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new OptimisticLockThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("This document was changed by someone else. Please reload and try again."));
    }
}
