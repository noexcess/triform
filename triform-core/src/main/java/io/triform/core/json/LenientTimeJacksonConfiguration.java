package io.triform.core.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 入参时间宽松反序列化。
 *
 * <p>背景：Jackson 默认只接受 ISO-8601（{@code 2026-10-30T00:00:00}），而前端日期控件
 * （Element Plus {@code value-format="YYYY-MM-DD HH:mm:ss"}）与多数人写接口文档的习惯都是
 * 空格分隔的 {@code 2026-10-30 00:00:00}。默认行为下这类入参会抛
 * {@code JSON parse error: Cannot deserialize value of type java.time.LocalDateTime}，
 * 报错信息只说"格式不对"，调用方很难自己定位，表现为"新建某个带截止时间的授权就报系统异常"。
 *
 * <p>处理：只在**反序列化**（入参）方向放宽，接受空格/ISO/纯日期/无秒 四种写法；
 * 序列化（出参）仍由 Boot 默认按 ISO-8601 输出，不改动既有出参口径。
 */
@Configuration
public class LenientTimeJacksonConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer lenientTimeDeserializerCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("ticket-lenient-time");
            module.addDeserializer(LocalDateTime.class, new LenientLocalDateTimeDeserializer());
            module.addDeserializer(LocalDate.class, new LenientLocalDateDeserializer());
            builder.modulesToInstall(module);
        };
    }

    /** 接受 yyyy-MM-dd HH:mm:ss / yyyy-MM-dd HH:mm / yyyy-MM-dd / ISO-8601（含 T、含毫秒）。 */
    static class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        private static final DateTimeFormatter WITH_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private static final DateTimeFormatter WITHOUT_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String raw = p.getText();
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            String text = raw.trim();
            LocalDateTime parsed = parseDateTime(text);
            if (parsed != null) {
                return parsed;
            }
            LocalDate date = LenientLocalDateDeserializer.parseDate(text);
            if (date != null) {
                // 只给了日期：按当天 00:00:00 处理（"截止时间 2026-10-30" 的直觉含义）
                return date.atStartOfDay();
            }
            throw new IOException("时间格式无法识别：" + raw
                    + "（支持 yyyy-MM-dd HH:mm:ss、yyyy-MM-dd HH:mm、yyyy-MM-dd、yyyy-MM-ddTHH:mm:ss）");
        }

        private LocalDateTime parseDateTime(String text) {
            try {
                return LocalDateTime.parse(text, WITH_SECONDS);
            } catch (DateTimeParseException ignore) {
                // 继续尝试其他模式
            }
            try {
                return LocalDateTime.parse(text, WITHOUT_SECONDS);
            } catch (DateTimeParseException ignore) {
                // 继续尝试其他模式
            }
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ignore) {
                return null;
            }
        }
    }

    /** 接受 yyyy-MM-dd 与 ISO-8601；带时间的入参按日期部分截断。 */
    static class LenientLocalDateDeserializer extends JsonDeserializer<LocalDate> {

        private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String raw = p.getText();
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            LocalDate parsed = parseDate(raw.trim());
            if (parsed == null) {
                throw new IOException("日期格式无法识别：" + raw + "（支持 yyyy-MM-dd）");
            }
            return parsed;
        }

        static LocalDate parseDate(String text) {
            try {
                return LocalDate.parse(text, DATE_ONLY);
            } catch (DateTimeParseException ignore) {
                // 继续尝试其他模式
            }
            try {
                return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignore) {
                // 可能是带时间的写法，截取日期部分
            }
            if (text.length() > 10 && text.charAt(10) == ' ') {
                try {
                    return LocalDate.parse(text.substring(0, 10), DATE_ONLY);
                } catch (DateTimeParseException ignore) {
                    return null;
                }
            }
            return null;
        }
    }
}
