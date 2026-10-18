package com.crane.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * 时间序列化契约（03 §1.4）：ISO 8601 带时区 +08:00（服务器时区 Asia/Shanghai）。
 * 输出形如 2026-10-17T14:00:00+08:00；输入兼容带/不带偏移量的 ISO 字符串。
 */
@Configuration
public class JacksonConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter IN =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                    .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
                    .toFormatter();

    @Bean
    public SimpleModule localDateTimeModule() {
        SimpleModule module = new SimpleModule("crane-local-datetime");
        module.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.atZone(ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
        });
        module.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = p.getText();
                if (text == null || text.isBlank()) {
                    return null;
                }
                // 去掉尾部偏移量（+08:00 / Z / +0800），统一按服务器时区解析
                int t = text.indexOf('T');
                String datePart = t > 0 ? text.substring(0, t) : text.substring(0, Math.min(10, text.length()));
                String timePart = t > 0 ? text.substring(t + 1) : "00:00:00";
                int idx = timePart.length();
                for (int i = 0; i < timePart.length(); i++) {
                    char c = timePart.charAt(i);
                    if (c == '+' || c == 'Z') {
                        idx = i;
                        break;
                    }
                }
                if (timePart.contains("-") && idx == timePart.length()) {
                    int lastDash = timePart.lastIndexOf('-');
                    if (lastDash > 8) {
                        idx = lastDash;
                    }
                }
                timePart = timePart.substring(0, Math.min(idx, timePart.length()));
                if (timePart.endsWith(".")) {
                    timePart = timePart.substring(0, timePart.length() - 1);
                }
                String normalized = timePart.isEmpty() ? "00:00:00"
                        : (timePart.length() == 5 ? timePart + ":00" : timePart);
                return LocalDateTime.parse(datePart + "T" + normalized, IN);
            }
        });
        return module;
    }
}
