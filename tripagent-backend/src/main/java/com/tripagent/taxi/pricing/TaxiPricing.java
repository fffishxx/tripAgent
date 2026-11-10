package com.tripagent.taxi.pricing;

import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.taxi.model.TaxiEstimateResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Java 模拟打车计价。
 *
 * <p>球面距离 * 1.3 作为道路里程；平均车速 30km/h；
 * 基础价 10 元 + 2.5 元/公里 + 0.5 元/分钟，再乘车型系数，最低 10 元。</p>
 */
@Component
public class TaxiPricing {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double ROAD_FACTOR = 1.3;
    private static final double AVG_SPEED_KMH = 30.0;
    private static final int MIN_DURATION_MIN = 5;

    private static final BigDecimal BASE_FARE = new BigDecimal("10.00");
    private static final BigDecimal PER_KM = new BigDecimal("2.50");
    private static final BigDecimal PER_MIN = new BigDecimal("0.50");
    private static final BigDecimal MIN_FARE = new BigDecimal("10.00");

    private static final List<VehicleType> VEHICLES = List.of(
            new VehicleType("特惠快车", new BigDecimal("0.90")),
            new VehicleType("快车", new BigDecimal("1.00")),
            new VehicleType("专车", new BigDecimal("1.50")),
            new VehicleType("豪华车", new BigDecimal("2.00"))
    );

    public TaxiEstimateResult estimate(BigDecimal pickupLongitude,
                                       BigDecimal pickupLatitude,
                                       BigDecimal destinationLongitude,
                                       BigDecimal destinationLatitude) {
        double directKm = haversineKm(
                pickupLatitude.doubleValue(), pickupLongitude.doubleValue(),
                destinationLatitude.doubleValue(), destinationLongitude.doubleValue());
        if (directKm < 0.01) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "起点和终点不能相同");
        }

        double roadKm = directKm * ROAD_FACTOR;
        int distanceM = (int) Math.round(roadKm * 1000);
        int durationMin = Math.max(MIN_DURATION_MIN,
                (int) Math.ceil(roadKm / AVG_SPEED_KMH * 60));

        List<TaxiEstimateResult.VehicleOption> options = VEHICLES.stream()
                .map(vehicle -> new TaxiEstimateResult.VehicleOption(
                        vehicle.name(),
                        calculateFare(roadKm, durationMin, vehicle.multiplier())))
                .toList();

        return new TaxiEstimateResult(distanceM, durationMin, "CNY", true, options);
    }

    public BigDecimal selectedFare(TaxiEstimateResult estimate, String vehicleType) {
        return estimate.options().stream()
                .filter(option -> option.vehicleType().equals(vehicleType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ResultCode.VALIDATION_ERROR, "vehicleType 取值不合法"))
                .estimatedFare();
    }

    private BigDecimal calculateFare(double roadKm, int durationMin, BigDecimal multiplier) {
        return BASE_FARE
                .add(PER_KM.multiply(BigDecimal.valueOf(roadKm)))
                .add(PER_MIN.multiply(BigDecimal.valueOf(durationMin)))
                .multiply(multiplier)
                .max(MIN_FARE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double deltaLat = radLat2 - radLat1;
        double deltaLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    private record VehicleType(String name, BigDecimal multiplier) {
    }
}
