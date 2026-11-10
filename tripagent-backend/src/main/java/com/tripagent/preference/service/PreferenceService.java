package com.tripagent.preference.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tripagent.preference.dto.PreferenceUpdateRequest;
import com.tripagent.preference.entity.UserPreference;
import com.tripagent.preference.mapper.UserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PreferenceService {

    static final String DEFAULT_CROWD = "general";
    static final String DEFAULT_BUDGET_LEVEL = "economy";
    static final String DEFAULT_TRANSPORT = "transit";
    static final String DEFAULT_PACE = "moderate";

    private final UserPreferenceMapper userPreferenceMapper;

    @Transactional(rollbackFor = Exception.class)
    public void createDefault(Long userId) {
        long count = userPreferenceMapper.selectCount(Wrappers.<UserPreference>lambdaQuery()
                .eq(UserPreference::getUserId, userId));
        if (count > 0) {
            return;
        }
        userPreferenceMapper.insert(newPreference(userId));
    }

    public UserPreference getOrDefault(Long userId) {
        UserPreference preference = userPreferenceMapper.selectOne(
                Wrappers.<UserPreference>lambdaQuery().eq(UserPreference::getUserId, userId));
        return preference == null ? newPreference(userId) : preference;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserPreference update(Long userId, PreferenceUpdateRequest request) {
        UserPreference preference = userPreferenceMapper.selectOne(
                Wrappers.<UserPreference>lambdaQuery().eq(UserPreference::getUserId, userId));
        boolean insert = preference == null;
        if (insert) {
            preference = newPreference(userId);
        }

        preference.setCrowd(defaultIfBlank(request.getCrowd(), DEFAULT_CROWD));
        preference.setBudgetLevel(defaultIfBlank(request.getBudgetLevel(), DEFAULT_BUDGET_LEVEL));
        preference.setPreferredTransport(defaultIfBlank(request.getPreferredTransport(), DEFAULT_TRANSPORT));
        preference.setTravelPace(defaultIfBlank(request.getTravelPace(), DEFAULT_PACE));
        preference.setInterests(sanitize(request.getInterests(), 10));
        preference.setFoodPreferences(sanitize(request.getFoodPreferences(), 10));
        preference.setAccommodationPreference(trimToNull(request.getAccommodationPreference()));
        preference.setNotes(trimToNull(request.getNotes()));

        if (insert) {
            userPreferenceMapper.insert(preference);
        } else {
            userPreferenceMapper.updateById(preference);
        }
        return preference;
    }

    private UserPreference newPreference(Long userId) {
        UserPreference preference = new UserPreference();
        preference.setUserId(userId);
        preference.setCrowd(DEFAULT_CROWD);
        preference.setBudgetLevel(DEFAULT_BUDGET_LEVEL);
        preference.setPreferredTransport(DEFAULT_TRANSPORT);
        preference.setTravelPace(DEFAULT_PACE);
        preference.setInterests(new ArrayList<>());
        preference.setFoodPreferences(new ArrayList<>());
        return preference;
    }

    private List<String> sanitize(List<String> values, int maxSize) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                result.add(value.trim());
            }
            if (result.size() >= maxSize) {
                break;
            }
        }
        return result;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
