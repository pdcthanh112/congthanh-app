package com.congthanh.commonservice.web;

import com.congthanh.commonservice.error.DomainException;
import com.congthanh.commonservice.error.ErrorKind;
import com.congthanh.commonservice.exception.NotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemHandlersTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Problems problems = new Problems("https://example.test/problems");
        mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new ResilienceProblemHandler(problems), new PersistenceProblemHandler(problems),
                        new CommonProblemHandler(problems))
                .build();
    }

    @Test
    void domainExceptionBecomesProblemWithKindStatusAndCode() throws Exception {
        mvc.perform(get("/out-of-stock"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.type").value("https://example.test/problems/out-of-stock"))
                .andExpect(jsonPath("$.detail").value("sku-1 is out of stock"));
    }

    @Test
    void readyMadeExceptionUsesKindNameAsCode() throws Exception {
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("product 42 not found"));
    }

    @Test
    void invalidBodyListsFieldErrors() throws Exception {
        mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void springMvcErrorsAlsoCarryACode() throws Exception {
        mvc.perform(post("/missing"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unexpectedExceptionDoesNotLeakItsMessage() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(jsonPath("$.detail").value("an unexpected error occurred"));
    }

    @Test
    void accessDeniedBecomes403() throws Exception {
        mvc.perform(get("/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void openCircuitBecomes503WithRetryAfter() throws Exception {
        mvc.perform(get("/circuit-open"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void optimisticLockBecomes409() throws Exception {
        mvc.perform(get("/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    record Item(@NotBlank String name) {
    }

    static class OutOfStock extends DomainException {
        OutOfStock(String message) {
            super(message);
        }

        @Override
        public ErrorKind errorKind() {
            return ErrorKind.REJECTED;
        }

        @Override
        public String code() {
            return "OUT_OF_STOCK";
        }
    }

    @RestController
    static class FailingController {

        @GetMapping("/out-of-stock")
        String outOfStock() {
            throw new OutOfStock("sku-1 is out of stock");
        }

        @GetMapping("/missing")
        String missing() {
            throw new NotFoundException("product 42 not found");
        }

        @PostMapping("/items")
        String create(@Valid @RequestBody Item item) {
            return item.name();
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("jdbc:postgresql://secret-host/db is down");
        }

        @GetMapping("/denied")
        String denied() {
            throw new AccessDeniedException("no");
        }

        @GetMapping("/circuit-open")
        String circuitOpen() {
            CircuitBreaker breaker = CircuitBreaker.ofDefaults("inventory");
            breaker.transitionToOpenState();
            throw CallNotPermittedException.createCallNotPermittedException(breaker);
        }

        @GetMapping("/stale")
        String stale() {
            throw new OptimisticLockingFailureException("row version mismatch");
        }
    }
}
