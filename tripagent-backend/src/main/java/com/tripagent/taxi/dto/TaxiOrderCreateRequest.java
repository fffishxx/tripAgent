package com.tripagent.taxi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TaxiOrderCreateRequest {

    @Valid
    @NotNull(message = "pickup 不能为空")
    private TaxiPointRequest pickup;

    @Valid
    @NotNull(message = "destination 不能为空")
    private TaxiPointRequest destination;

    @Size(max = 64, message = "城市不能超过64个字符")
    private String city;

    @NotBlank(message = "车型不能为空")
    @Size(max = 64, message = "车型不能超过64个字符")
    private String vehicleType;
}
