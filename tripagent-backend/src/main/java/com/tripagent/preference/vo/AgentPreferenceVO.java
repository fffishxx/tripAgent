package com.tripagent.preference.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.tripagent.preference.entity.UserPreference;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentPreferenceVO {

    private String crowd;
    private String budgetLevel;
    private String preferredTransport;
    private String travelPace;
    private List<String> interests;
    private List<String> foodPreferences;
    private String accommodationPreference;
    private String notes;

    public static AgentPreferenceVO from(UserPreference preference) {
        return AgentPreferenceVO.builder()
                .crowd(preference.getCrowd())
                .budgetLevel(preference.getBudgetLevel())
                .preferredTransport(preference.getPreferredTransport())
                .travelPace(preference.getTravelPace())
                .interests(safeList(preference.getInterests()))
                .foodPreferences(safeList(preference.getFoodPreferences()))
                .accommodationPreference(preference.getAccommodationPreference())
                .notes(preference.getNotes())
                .build();
    }

    private static List<String> safeList(List<String> values) {
        return values == null
                ? List.of()
                : values.stream().filter(Objects::nonNull).toList();
    }
}
