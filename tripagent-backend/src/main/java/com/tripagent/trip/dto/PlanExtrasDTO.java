package com.tripagent.trip.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 前端行程扩展字段（camelCase）。
 *
 * <p>仅作为嵌套类型的命名空间，请求/响应直接使用内部静态类。</p>
 */
public final class PlanExtrasDTO {

    private PlanExtrasDTO() {
    }

    @Data
    public static class LodgingAreaDTO {
        @Size(max = 100, message = "住宿区域名称不能超过100个字符")
        private String name;
        @Size(max = 500, message = "住宿区域理由不能超过500个字符")
        private String reason;
    }

    @Data
    public static class DiningSuggestionDTO {
        @Size(max = 100, message = "餐饮区域不能超过100个字符")
        private String area;
        @Size(max = 50, message = "菜系不能超过50个字符")
        private String cuisine;
        @Size(max = 500, message = "餐饮说明不能超过500个字符")
        private String note;
    }

    @Data
    public static class BudgetEstimateDTO {
        @Size(max = 100, message = "预算总价不能超过100个字符")
        private String total;
        @Size(max = 500, message = "预算构成不能超过500个字符")
        private String breakdown;
    }

    @Data
    public static class AssumptionsDTO {
        private Integer days;
        @Pattern(regexp = "general|family|couple|elderly|solo|student",
                message = "assumptions.crowd 取值不合法")
        private String crowd;
        @Pattern(regexp = "economy|comfort|premium",
                message = "assumptions.budgetLevel 取值不合法")
        private String budgetLevel;
        @Size(max = 50, message = "assumptions.notes 最多50项")
        private List<@Size(max = 100, message = "assumption note 每项最多100个字符") String> notes;
    }

    @Data
    public static class WarningDTO {
        private Integer dayIndex;
        @Size(max = 128, message = "warning.from 不能超过128个字符")
        private String from;
        @Size(max = 128, message = "warning.to 不能超过128个字符")
        private String to;
        private Integer walkMin;
        private BigDecimal distanceKm;
        @Size(max = 500, message = "warning.message 不能超过500个字符")
        private String message;
    }
}
