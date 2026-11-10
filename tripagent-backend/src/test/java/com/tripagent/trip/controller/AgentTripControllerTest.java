package com.tripagent.trip.controller;

import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.exception.GlobalExceptionHandler;
import com.tripagent.common.result.ResultCode;
import com.tripagent.context.UserContext;
import com.tripagent.context.UserContextHolder;
import com.tripagent.trip.entity.Trip;
import com.tripagent.trip.model.TripDetail;
import com.tripagent.trip.model.TripSaveCommand;
import com.tripagent.trip.model.TripSaveResult;
import com.tripagent.trip.service.TripService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentTripControllerTest {

    private TripService tripService;
    private MockMvc mockMvc;

    private final String body = """
            {
              "title": "测试行程",
              "start_date": "2026-11-10",
              "end_date": "2026-11-11",
              "plan": {
                "destination": "杭州",
                "days": [
                  {"day_index": 1, "items": [{"poi": "西湖", "coords": [120.1, 30.2], "time_slot": "上午"}]},
                  {"day_index": 2, "items": [{"poi": "灵隐寺", "coords": [120.2, 30.3], "time_slot": "下午"}]}
                ]
              }
            }
            """;

    @BeforeEach
    void setUp() {
        tripService = mock(TripService.class);
        AgentTripController controller = new AgentTripController(tripService);
        Validator validator = new SpringValidatorAdapter(
                Validation.buildDefaultValidatorFactory().getValidator());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json().build()))
                .build();
        MDC.put(CommonConstants.MDC_REQUEST_ID, "test-request");
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        MDC.clear();
    }

    @Test
    void missingIdempotencyKeyIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/agent/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultCode.BAD_REQUEST.getCode()));
    }

    @Test
    void missingUserContextIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/agent/v1/trips")
                        .header(CommonConstants.IDEMPOTENCY_KEY_HEADER, "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void createReturnsCreated() throws Exception {
        UserContextHolder.set(new UserContext(1L, null, null));
        when(tripService.createWithIdempotency(eq(1L), any(TripSaveCommand.class), eq("AGENT"), eq("idem-1")))
                .thenReturn(new TripSaveResult(detail(), false));

        mockMvc.perform(post("/api/agent/v1/trips")
                        .header(CommonConstants.IDEMPOTENCY_KEY_HEADER, "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").value("test-request"))
                .andExpect(jsonPath("$.data.id").value("100"))
                .andExpect(jsonPath("$.data.idempotent_replay").value(false));

        verify(tripService).createWithIdempotency(
                eq(1L), any(TripSaveCommand.class), eq("AGENT"), eq("idem-1"));
    }

    @Test
    void replayReturnsOk() throws Exception {
        UserContextHolder.set(new UserContext(1L, null, null));
        when(tripService.createWithIdempotency(eq(1L), any(TripSaveCommand.class), eq("AGENT"), eq("idem-1")))
                .thenReturn(new TripSaveResult(detail(), true));

        mockMvc.perform(post("/api/agent/v1/trips")
                        .header(CommonConstants.IDEMPOTENCY_KEY_HEADER, "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.idempotent_replay").value(true));
    }

    @Test
    void invalidBodyIsValidationError() throws Exception {
        UserContextHolder.set(new UserContext(1L, null, null));

        mockMvc.perform(post("/api/agent/v1/trips")
                        .header(CommonConstants.IDEMPOTENCY_KEY_HEADER, "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"缺少 plan\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultCode.VALIDATION_ERROR.getCode()));
    }

    private TripDetail detail() {
        Trip trip = new Trip();
        trip.setId(100L);
        trip.setUserId(1L);
        trip.setTitle("测试行程");
        trip.setDestination("杭州");
        trip.setStartDate(LocalDate.of(2026, 11, 10));
        trip.setEndDate(LocalDate.of(2026, 11, 11));
        trip.setDaysCount(2);
        trip.setSource("AGENT");
        trip.setVersion(0);
        return new TripDetail(trip, List.of(), List.of());
    }
}
