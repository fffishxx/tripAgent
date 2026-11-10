package com.tripagent.trip.controller;

import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.exception.GlobalExceptionHandler;
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

class TripControllerTest {

    private TripService tripService;
    private MockMvc mockMvc;

    private final String body = """
            {
              "title": "测试行程",
              "startDate": "2026-11-10",
              "endDate": "2026-11-11",
              "plan": {
                "destination": "杭州",
                "days": [
                  {"dayIndex": 1, "items": [{"name": "西湖", "longitude": 120.1, "latitude": 30.2, "timeSlot": "上午"}]},
                  {"dayIndex": 2, "items": [{"name": "灵隐寺", "longitude": 120.2, "latitude": 30.3, "timeSlot": "下午"}]}
                ]
              }
            }
            """;

    @BeforeEach
    void setUp() {
        tripService = mock(TripService.class);
        TripController controller = new TripController(tripService);
        Validator validator = new SpringValidatorAdapter(
                Validation.buildDefaultValidatorFactory().getValidator());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json().build()))
                .build();
        UserContextHolder.set(new UserContext(1L, null, null));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createWithoutIdempotencyKeyReturnsCreated() throws Exception {
        when(tripService.create(eq(1L), any(TripSaveCommand.class), eq("MANUAL")))
                .thenReturn(detail());

        mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("100"))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false));

        verify(tripService).create(eq(1L), any(TripSaveCommand.class), eq("MANUAL"));
    }

    @Test
    void createWithIdempotencyKeyReturnsCreatedOnFirstRequest() throws Exception {
        when(tripService.createWithIdempotency(
                eq(1L), any(TripSaveCommand.class), eq("MANUAL"), eq("idem-1")))
                .thenReturn(new TripSaveResult(detail(), false));

        mockMvc.perform(post("/api/v1/trips")
                        .header(CommonConstants.IDEMPOTENCY_KEY_HEADER, "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.idempotentReplay").value(false));

        verify(tripService).createWithIdempotency(
                eq(1L), any(TripSaveCommand.class), eq("MANUAL"), eq("idem-1"));
    }

    @Test
    void createWithIdempotencyKeyReturnsOkOnReplay() throws Exception {
        when(tripService.createWithIdempotency(
                eq(1L), any(TripSaveCommand.class), eq("MANUAL"), eq("idem-1")))
                .thenReturn(new TripSaveResult(detail(), true));

        mockMvc.perform(post("/api/v1/trips")
                        .header(CommonConstants.IDEMPOTENCY_KEY_HEADER, "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("100"))
                .andExpect(jsonPath("$.data.idempotentReplay").value(true));
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
        trip.setSource("MANUAL");
        trip.setVersion(0);
        return new TripDetail(trip, List.of(), List.of());
    }
}
