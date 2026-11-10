package com.tripagent.preference.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PreferenceUpdateRequest {

    @Pattern(regexp = "general|family|couple|elderly|solo|student",
            message = "crowd 取值不合法")
    private String crowd;

    @Pattern(regexp = "economy|comfort|premium",
            message = "budgetLevel 取值不合法")
    private String budgetLevel;

    @Pattern(regexp = "walking|driving|transit",
            message = "preferredTransport 取值不合法")
    private String preferredTransport;

    @Pattern(regexp = "relaxed|moderate|intensive",
            message = "travelPace 取值不合法")
    private String travelPace;

    @Size(max = 10, message = "interests 最多 10 项")
    private List<@Size(max = 20, message = "interest 每项最多 20 个字符") String> interests;

    @Size(max = 10, message = "foodPreferences 最多 10 项")
    private List<@Size(max = 50, message = "foodPreference 每项最多 50 个字符") String> foodPreferences;

    @Size(max = 255, message = "住宿偏好不能超过255个字符")
    private String accommodationPreference;

    @Size(max = 1000, message = "补充说明不能超过1000个字符")
    private String notes;
}
