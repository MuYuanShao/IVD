package com.zlth.ivd.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 安全 Long 序列化器
 * <p>
 * 超大 Long（Snowflake ID，超过 JS 安全整数 2^53-1）→ String 防精度丢失；
 * 小数值（分页 total/page/pageSize 等）→ Number，确保前端组件类型校验通过。
 *
 * @author IVD Team
 * @since 2026-07-23
 */
public class SafeLongSerializer extends JsonSerializer<Long> {

    /** JavaScript 最大安全整数：2^53 - 1 */
    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else if (value > JS_MAX_SAFE_INTEGER || value < -JS_MAX_SAFE_INTEGER) {
            gen.writeString(value.toString());
        } else {
            gen.writeNumber(value);
        }
    }
}
