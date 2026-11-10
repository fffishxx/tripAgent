package com.tripagent.taxi.controller;

import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.exception.GlobalExceptionHandler;
import com.tripagent.context.UserContext;
import com.tripagent.context.UserContextHolder;
import com.tripagent.taxi.entity.TaxiOrder;
import com.tripagent.taxi.model.TaxiOrderSaveResult;
import com.tripagent.taxi.service.TaxiService;
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

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaxiControllerTest {

    private TaxiService taxiService;
    private MockMvc mockMvc;

    private final String body = """
            {
              "pickup": {"name": "西湖", "longitude": 120.1, "latitude": 30.2},
              "destination": {"name": "灵隐寺", "longitude": 120.2, "latitude": 30.3},
              "city": "杭州",
              "vehicleType": "快车"
            }
            """;

    @BeforeEach
    void setUp() {
        taxiService = mock(TaxiService.class);
        TaxiController controller = new TaxiController(taxiService);
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
        when(taxiService.create(eq(1L), any())).thenReturn(order(77L));

        mockMvc.perform(post("/api/v1/taxi/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("77"))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false));

        verify(taxiService).create(eq(1L), any());
    }

    @Test
    void createWithIdempotencyKeyReturnsCreatedOnFirstRequest() throws Exception {
        when(taxiService.createWithIdempotency(eq(1L), any(), eq("idem-1")))
                .thenReturn(new TaxiOrderSaveResult(order(77L), false));

        mockMvc.perform(post("/api/v1/taxi/orders")
                        .header(CommonConstants.IDEMPOTENCY_KEY_HEADER, "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.idempotentReplay").value(false));

        verify(taxiService).createWithIdempotency(eq(1L), any(), eq("idem-1"));
    }

    @Test
    void createWithIdempotencyKeyReturnsOkOnReplay() throws Exception {
        when(taxiService.createWithIdempotency(eq(1L), any(), eq("idem-1")))
                .thenReturn(new TaxiOrderSaveResult(order(77L), true));

        mockMvc.perform(post("/api/v1/taxi/orders")
                        .header(CommonConstants.IDEMPOTENCY_KEY_HEADER, "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("77"))
                .andExpect(jsonPath("$.data.idempotentReplay").value(true));
    }

    private TaxiOrder order(Long id) {
        TaxiOrder order = new TaxiOrder();
        order.setId(id);
        order.setOrderNo("TX" + id);
        order.setUserId(1L);
        order.setStatus("CREATED");
        order.setPickupName("西湖");
        order.setPickupLongitude(new BigDecimal("120.100000"));
        order.setPickupLatitude(new BigDecimal("30.200000"));
        order.setDestinationName("灵隐寺");
        order.setDestinationLongitude(new BigDecimal("120.200000"));
        order.setDestinationLatitude(new BigDecimal("30.300000"));
        order.setCity("杭州");
        order.setVehicleType("快车");
        order.setCurrency("CNY");
        order.setProvider("MOCK");
        order.setVersion(0);
        return order;
    }
}
