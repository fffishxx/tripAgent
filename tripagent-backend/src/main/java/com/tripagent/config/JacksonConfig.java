package com.tripagent.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * JSON 时间格式统一为 yyyy-MM-dd / yyyy-MM-dd HH:mm:ss，时区 Asia/Shanghai。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.serializers(
                    new LocalDateSerializer(DATE_FORMATTER),
                    new LocalDateTimeSerializer(DATE_TIME_FORMATTER)
            );
            builder.deserializers(
                    new LocalDateDeserializer(DATE_FORMATTER),
                    new LocalDateTimeDeserializer(DATE_TIME_FORMATTER)
            );
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            builder.timeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        };
    }
}
