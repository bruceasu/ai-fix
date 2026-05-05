package me.asu.ai.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Created by Victor on 2020/8/5.
 * Jackson 工具类
 *  支持Java8 时间类型的序列化和反序列化，统一使用UTC时区
 *
 * <h3>apiObjectMapper：对外 API 使用的 ObjectMapper</h3>
 *  long 型全部序列化为 String，避免 javascript 精度丢失问题
 *  BigDecimal 序列化为非科学计数法的字符串, 避免 javascript 科学计数法精度丢失问题，使用 stripTrailingZeros().toPlainString()
 * <h3>inernalObjectMapper：内部使用的 ObjectMapper</h3>
 * 不做 long 型特殊处理
 * BigDecimal 序列化为非科学计数法的字符串
 * 如果有需要对某个 Long 字段单独处理，可以使用注解：
 * <pre><code>
 * @JsonSerialize(using = ToStringSerializer.class)
 * private Long id;
 * </code></pre>
 */
public class JacksonUtils {
    // Global ObjectMapper for API use
    public static final ObjectMapper apiObjectMapper;
    public static final ObjectMapper inernalObjectMapper;

    static {
        apiObjectMapper = getApiObjectMapper();
        inernalObjectMapper = getInternalMapper();
    }

    private static ObjectMapper getApiObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
                // 序列化：忽略 null
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                // Map/List/JsonNode 中的小数 → BigDecimal
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                // Date/Calendar 的输出格式；对 java.time 不生效（仅作兜底）
                .setDateFormat(new StdDateFormat().withColonInTimeZone(true)) // 只影响 java.util.Date/Calendar
                // 统一设置到 UTC（影响 Date/Calendar；java.time 由各自序列化器控制）
                .setTimeZone(TimeZone.getTimeZone("UTC"))
                // .registerModule(new JavaTimeModule()) // 注册 Java 8 时间模块（如果使用 LocalDateTime 等）
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // 可选：序列化 BigDecimal 不使用科学计数法
        objectMapper.configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);
        // javascript 没有long型，是个坑
        // Long / BigDecimal 全部序列化为 String（这是一个架构级决策）
        SimpleModule m = new SimpleModule();
        m.addSerializer(Long.class, ToStringSerializer.instance);
        m.addSerializer(Long.TYPE, ToStringSerializer.instance);
        m.addSerializer(BigInteger.class, ToStringSerializer.instance);
        m.addSerializer(BigDecimal.class, new JsonSerializer<BigDecimal>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen,
                    SerializerProvider serializers) throws IOException {
                gen.writeString(value.stripTrailingZeros().toPlainString());
            }
        });
        objectMapper.registerModule(m);

        // =============================
        // Java 8 时间类型支持
        // =============================
        // ❗必须禁用时间戳
        addJava8TimeSupport(objectMapper);
        return objectMapper;
    }

    private static ObjectMapper getInternalMapper() {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
                // 序列化：忽略 null
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                // Map/List/JsonNode 中的小数 → BigDecimal
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                // Date/Calendar 的输出格式；对 java.time 不生效（仅作兜底）
                .setDateFormat(new StdDateFormat().withColonInTimeZone(true)) // 只影响 java.util.Date/Calendar
                // 统一设置到 UTC（影响 Date/Calendar；java.time 由各自序列化器控制）
                .setTimeZone(TimeZone.getTimeZone("UTC"))
                // .registerModule(new JavaTimeModule()) // 注册 Java 8 时间模块（如果使用 LocalDateTime 等）
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // 可选：序列化 BigDecimal 不使用科学计数法
        objectMapper.configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);

        addJava8TimeSupport(objectMapper);

        return objectMapper;
    }

    private static void addJava8TimeSupport(ObjectMapper objectMapper) {
        // =============================
        // Java 8 时间类型支持
        // =============================
        // ❗必须禁用时间戳
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 反序列化时，将带 offset / zone 的时间统一换算到 ObjectMapper 时区（UTC）
        objectMapper.enable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

        // Java 8 时间类型支持
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        // 日期时间格式化器
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_DATE_TIME;

        // LocalDate
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormatter));
        javaTimeModule.addDeserializer(LocalDate.class, new JsonDeserializer<LocalDate>() {
            @Override
            public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = p.getText().trim();
                if (isTimestamp13(text)) {
                    // 纯时间戳 → 转 LocalDate
                    long epochMillis = Long.parseLong(text);
                    return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate();
                } else if (isTimestamp10(text)) {
                    long epochSeconds = Long.parseLong(text);
                    return Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate();
                }
                List<String> list = Arrays.asList("yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd", "MM/dd/yyyy");
                for (String pattern : list) {
                    try {
                        return LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern));
                    } catch (DateTimeParseException ignored) {}
                }
                throw ctxt.weirdStringException(text, LocalDate.class, "Unrecognized date format");
            }

        });

        // LocalDateTime
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
        javaTimeModule.addDeserializer(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = p.getText().trim();
                if (text.matches("\\d+")) {
                    long epochMillis = Long.parseLong(text);
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
                }
                List<DateTimeFormatter> fmts = Arrays.asList(
                        dateTimeFormatter,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),

                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),

                        DateTimeFormatter.ofPattern("yyyy/MM/dd'T'HH:mm:ss"),
                        DateTimeFormatter.ofPattern("yyyy/MM/dd'T'HH:mm:ss.SSS"),
                        DateTimeFormatter.ofPattern("yyyy/MM/dd'T'HH:mm"),
                        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS"),
                        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
                );
                for (DateTimeFormatter fmt : fmts) {
                    try {
                        return LocalDateTime.parse(text, fmt);
                    } catch (DateTimeParseException ignored) {}
                }
                throw ctxt.weirdStringException(text, LocalDateTime.class, "Unrecognized datetime format");
            }
        });

        // OffsetDateTime 系统内统一使用 UTC OffsetDateTime（offset 固定为 +00:00）
        DateTimeFormatter offsetFormatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneOffset.UTC);
        javaTimeModule.addSerializer(OffsetDateTime.class, new JsonSerializer<OffsetDateTime>() {
            @Override
            public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                if (value == null) {
                    gen.writeNull();
                    return;
                }
                gen.writeString(offsetFormatter.format(value)); // 例如 2025-10-29T10:36:20.123+00:00
                //                gen.writeString(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)); // 2025-10-29T18:36:20.123+08:00
            }
        });

        javaTimeModule.addDeserializer(OffsetDateTime.class, new JsonDeserializer<OffsetDateTime>() {
            @Override
            public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = p.getText().trim();
                if (text.matches("\\d+")) {
                    return OffsetDateTime.ofInstant(
                            Instant.ofEpochMilli(Long.parseLong(text)), ZoneOffset.UTC);
                }
                for (String fmt : Arrays.asList(
                        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                        "yyyy-MM-dd'T'HH:mm:ssXXX",
                        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                        "yyyy-MM-dd'T'HH:mm:ssZ",
                        "yyyy-MM-dd'T'HH:mm:ss.SSS",
                        "yyyy-MM-dd'T'HH:mm:ss",
                        "yyyy-MM-dd'T'HH:mm",

                        "yyyy/MM/dd'T'HH:mm:ss.SSSXXX",
                        "yyyy/MM/dd'T'HH:mm:ssXXX",
                        "yyyy/MM/dd'T'HH:mm:ss.SSSZ",
                        "yyyy/MM/dd'T'HH:mm:ssZ",
                        "yyyy/MM/dd'T'HH:mm:ss.SSS",
                        "yyyy/MM/dd HH:mm:ss",
                        "yyyy/MM/dd HH:mm"
                )) {
                    try {
                        return OffsetDateTime.parse(text, DateTimeFormatter.ofPattern(fmt).withZone(ZoneOffset.UTC));
                    } catch (DateTimeParseException ignored) {}
                }
                throw ctxt.weirdStringException(text, OffsetDateTime.class, "Unrecognized offset datetime format");
            }
        });
        // ===== Instant =====
        javaTimeModule.addSerializer(Instant.class, new JsonSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                if (value == null) {
                    gen.writeNull();
                } else {
                    gen.writeString(offsetFormatter.format(value));
                }
            }
        });
        javaTimeModule.addDeserializer(Instant.class, new JsonDeserializer<Instant>() {
            @Override
            public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = p.getText().trim();
                if (text.matches("\\d+")) {
                    return Instant.ofEpochMilli(Long.parseLong(text));
                }
                try {
                    return Instant.parse(text);
                } catch (DateTimeParseException e) {
                    for (String fmt : Arrays.asList(
                            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                            "yyyy-MM-dd'T'HH:mm:ss.SSS",
                            "yyyy-MM-dd'T'HH:mm:ssXXX",
                            "yyyy-MM-dd'T'HH:mm:ssZ",
                            "yyyy-MM-dd'T'HH:mm:ss",
                            "yyyy-MM-dd HH:mm:ss.SSSXXX",
                            "yyyy-MM-dd HH:mm:ss.SSSZ",
                            "yyyy-MM-dd HH:mm:ss.SSS",
                            "yyyy-MM-dd HH:mm:ssXXX",
                            "yyyy-MM-dd HH:mm:ssZ",
                            "yyyy-MM-dd HH:mm:ss",

                            "yyyy/MM/dd'T'HH:mm:ss.SSSXXX",
                            "yyyy/MM/dd'T'HH:mm:ss.SSSZ",
                            "yyyy/MM/dd'T'HH:mm:ss.SSS",
                            "yyyy/MM/dd'T'HH:mm:ssXXX",
                            "yyyy/MM/dd'T'HH:mm:ssZ",
                            "yyyy/MM/dd'T'HH:mm:ss",
                            "yyyy/MM/dd HH:mm:ss.SSSXXX",
                            "yyyy/MM/dd HH:mm:ss.SSSZ",
                            "yyyy/MM/dd HH:mm:ss.SSS",
                            "yyyy/MM/dd HH:mm:ssXXX",
                            "yyyy/MM/dd HH:mm:ssZ",
                            "yyyy/MM/dd HH:mm:ss"
                    )) {
                        try {
                            return LocalDateTime.parse(text,
                                    DateTimeFormatter.ofPattern(fmt)).toInstant(ZoneOffset.UTC);
                        } catch (DateTimeParseException ignored) {}
                    }
                    throw ctxt.weirdStringException(text, Instant.class, "Unrecognized instant format");
                }
            }
        });
        objectMapper.registerModule(javaTimeModule);
    }

    static boolean isTimestamp10(String text) {
        return text.matches("\\d{10}");
    }

    static boolean isTimestamp13(String text) {
        return text.matches("\\d{13}");
    }


    public static String serialize(Object data) {
        try {
            return apiObjectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String serializeForPrint(Object data) {
        try {
            return apiObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] serializeToBytes(Object data) {
        try {
            return apiObjectMapper.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode deserialize(String data) {
        try {
            return apiObjectMapper.readTree(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode deserialize(byte[] data) {
        try {
            return apiObjectMapper.readTree(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode deserialize(Reader reader) {
        try {
            return apiObjectMapper.readTree(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode deserialize(InputStream is) {
        try {
            return apiObjectMapper.readTree(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 使用构造的泛型类型进行反序列化
     */
    public static <T> T deserialize(String json, Class<?>... classes) {
        try {
            JavaType javaType = JacksonTypes.createGenericTypeRef(classes);
            return apiObjectMapper.readValue(json, javaType);
        } catch (IOException e) {
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }

    public static <T> T deserialize(String data, Class<T> cls) {
        if (cls == String.class) {
            return (T) data;
        } else {
            try {
                return apiObjectMapper.readValue(data, JacksonTypes.typeOf(cls));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static <T> T deserialize(String data, TypeReference<T> type) {
        try {
            return apiObjectMapper.readValue(data, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserialize(InputStream is, Class<T> cls) {
        try {
            if (cls == String.class) {
                return (T) toString(is);
            } else {
                return apiObjectMapper.readValue(is, JacksonTypes.typeOf(cls));

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 从 InputStream 反序列化对象
     *
     * @param is  输入流
     * @param cls 目标类型
     * @param <T> 目标类型泛型
     * @return 目标类型对象
     */
    public static <T> T deserialize(InputStream is,  TypeReference<T> type) {
        try {
            return apiObjectMapper.readValue(is, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T deserialize(Reader is, Class<T> cls) {
        try {
              return apiObjectMapper.readValue(is, JacksonTypes.typeOf(cls));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 从 InputStream 反序列化对象
     *
     * @param is  输入流
     * @param cls 目标类型
     * @param <T> 目标类型泛型
     * @return 目标类型对象
     */
    public static <T> T deserialize(Reader is,  TypeReference<T> type) {
        try {
            return apiObjectMapper.readValue(is, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

      public static <T> T deserialize(File file, Class<T> cls) {
        try {
            FileInputStream fis = new FileInputStream(file);
            return apiObjectMapper.readValue(fis, JacksonTypes.typeOf(cls));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 从 InputStream 反序列化对象
     *
     * @param is  输入流
     * @param cls 目标类型
     * @param <T> 目标类型泛型
     * @return 目标类型对象
     */
    public static <T> T deserialize(File file,  TypeReference<T> type) {
        try {
             FileInputStream fis = new FileInputStream(file);
            return apiObjectMapper.readValue(fis, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * to Map
     *
     * @param data json String
     * @return a Map
     */
    public static Map<String, Object> deserializeToMap(String data) {
        try {
            return apiObjectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    

    @SuppressWarnings("unchecked")
    public static <T> List<T> deserializeToList(String data, Class<T> cls) {
        CollectionType listType = JacksonTypes.collection(List.class, cls);
        try {
            return apiObjectMapper.readValue(data, listType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static List<Map<String, Object>> deserializeToMapList(String data) {
        try {
            JavaType genericTypeRef = JacksonTypes.createGenericTypeRef(
                    List.class, Map.class, String.class, Object.class);
            return apiObjectMapper.readValue(data, genericTypeRef);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> convertToMap(Object object) {
        //用jackson将bean转换为map
        return apiObjectMapper.convertValue(object, new TypeReference<Map<String, Object>>() {});
    }


    public static <T> List<T> convertToList(Object data, Class<T> cls) {
        CollectionType listType = JacksonTypes.collection(List.class, cls);
        return apiObjectMapper.convertValue(data, listType);
    }

    public static List<Map<String, Object>> convertToMapList(List<Object> list) {
        //用jackson将bean转换为List<Map>
        JavaType genericTypeRef = JacksonTypes.createGenericTypeRef(List.class, Map.class, String.class, Object.class);
        return apiObjectMapper.convertValue(list, genericTypeRef);
    }

    public static <T> List<T> convertToMapList(List<Object> list, Class<T> cls) {
        //用jackson将bean转换为List<Map>
        JavaType genericTypeRef = JacksonTypes.createGenericTypeRef(List.class, cls);
        return apiObjectMapper.convertValue(list, genericTypeRef);
    }

    /**
     * 将一个对象转换为另一个类型的对象
     *
     * @param data 源对象
     * @param cls  目标类型
     * @param <T>  目标类型泛型
     * @return 目标类型对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertToObject(Object data, Class<T> cls) {
        if (data == null) return null;
        if (data instanceof JsonNode && cls == JsonNode.class) {
            return (T) data;
        } else {
            try {
                return apiObjectMapper.convertValue(data, cls);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static <T> T convertToObject(Object data, TypeReference<T> type) {
        return apiObjectMapper.convertValue(data, type);
    }


    public static String asText(JsonNode dtNode, String item) {
        return asText(dtNode, item, null);
    }

    public static String asText(JsonNode dtNode, String item, String defaultValue) {
        if (dtNode == null) {
            return defaultValue;
        }

        JsonNode node = dtNode.get(item);
        if (node == null) {
            return defaultValue;
        }
        return node.asText();
    }

    //        node.isMissingNode() - 节点不存在
    //        node.isNull() - 节点值为 JSON null
    public static JsonNode at(JsonNode dtNode, String path) {
        if (dtNode == null || StringUtils.isEmpty(path)) {
            return null;
        }
        return dtNode.at(path);
    }

    public static String atAsText(JsonNode dtNode, String path) {
        if (dtNode == null || StringUtils.isEmpty(path)) {
            return null;
        }

        return dtNode.at(path).asText();
    }

    public static ObjectNode createObject() {
        return apiObjectMapper.createObjectNode();

    }

    public static ArrayNode createArray() {
        return apiObjectMapper.createArrayNode();
    }


    static String toString(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }


    public static final class JacksonTypes {
        private static final ObjectMapper mapper = new ObjectMapper();
        private static final TypeFactory tf = mapper.getTypeFactory();

        private JacksonTypes() {}

        // 基础
        public static JavaType typeOf(Class<?> raw) {
            return tf.constructType(raw);
        }

        // 通用：raw<T1, T2, ...>
        public static JavaType parametric(Class<?> raw, JavaType... params) {
            return tf.constructParametricType(raw, params);
        }

        // 通用：raw<T1, T2, ...>
        public static JavaType parametric(Class<?> raw, Class<?>... params) {
            return tf.constructParametricType(raw, params);
        }

        // 便捷：Collection<E>
        public static <C extends Collection<?>> CollectionType collection(Class<C> collRaw, JavaType elem) {
            return tf.constructCollectionType(collRaw, elem);
        }

        public static <C extends Collection<E>, E> CollectionType collection(Class<C> collRaw, Class<E> elem) {
            return tf.constructCollectionType(collRaw, elem);
        }

        // 便捷：Map<K,V>
        public static <M extends Map<?, ?>> MapType map(Class<M> mapRaw, JavaType key, JavaType val) {
            return tf.constructMapType(mapRaw, key, val);
        }

        public static <M extends Map<K, V>, K, V> MapType map(Class<M> mapRaw, Class<K> key, Class<V> val) {
            return tf.constructMapType(mapRaw, key, val);
        }

        /**
         * 以“从内到外”的顺序描述类型层级，自动根据原始类的类型参数个数组装 JavaType。
         * 例如要构造 List<Map<String, Integer>>：
         * createGenericTypeRef(List.class, Map.class, String.class, Integer.class)
         * <p>
         * 规则：
         * - 遇到非泛型类（arity==0）：直接入栈（constructType）
         * - 遇到泛型原始类（arity>0）：从栈顶弹出 arity 个 JavaType 作为其类型参数，按顺序组装
         * - 对 Collection/Map 使用 Jackson 专用构造器（更稳）
         * - 支持接口：List/Set/Map 这类接口可直接传，Jackson 会选默认实现
         * <p>
         * 限制：
         * - 仅支持以 Class 描述的层级；不支持通配符 ?、边界、TypeVariable
         * - 需要你保证“外层在前、内层在后”的顺序与 arity 对齐
         */
        public static JavaType createGenericTypeRef(Class<?>... classes) {
            if (classes == null || classes.length == 0) {
                throw new IllegalArgumentException("至少需要一个类型");
            }

            Deque<JavaType> stack = new ArrayDeque<>();

            // 从右到左扫描：右侧是更“内层”的类型
            for (int i = classes.length - 1; i >= 0; i--) {
                Class<?> raw = classes[i];

                // 若是数组类：从栈顶取一个作为 component type
                if (raw.isArray()) {
                    if (stack.isEmpty()) {
                        throw new IllegalArgumentException("数组类型需要一个组件类型：" + raw);
                    }
                    JavaType component = stack.pop();
                    stack.push(tf.constructArrayType(component));
                    continue;
                }

                int arity = raw.getTypeParameters().length;

                if (arity == 0) {
                    // 非泛型，直接构造
                    stack.push(tf.constructType(raw));
                    continue;
                }

                // 泛型：需要 arity 个参数
                if (stack.size() < arity) {
                    throw new IllegalArgumentException("类型参数不足：" + raw.getName() +
                            " 需要 " + arity + " 个，但当前栈只有 " + stack.size());
                }

                // 从栈顶按“内到外”的顺序取出参数；注意要倒回到正确的形参顺序
                JavaType[] args = new JavaType[arity];
                for (int k = 0; k < arity; k++) {
                    args[k] = stack.pop();
                }

                // 对 Map/Collection 使用专用构造器；其他使用 constructParametricType
                if (Map.class.isAssignableFrom(raw)) {
                    if (arity != 2) {
                        throw new IllegalArgumentException("Map 派生类型应有 2 个类型参数：" + raw.getName());
                    }
                    @SuppressWarnings("unchecked")
                    Class<? extends Map<?, ?>> mapRaw = (Class<? extends Map<?, ?>>) raw;
                    MapType mapType = tf.constructMapType(mapRaw, args[0], args[1]);
                    stack.push(mapType);
                } else if (Collection.class.isAssignableFrom(raw)) {
                    if (arity != 1) {
                        throw new IllegalArgumentException("Collection 派生类型应有 1 个类型参数：" + raw.getName());
                    }
                    @SuppressWarnings("unchecked")
                    Class<? extends Collection<?>> collRaw = (Class<? extends Collection<?>>) raw;
                    CollectionType collType = tf.constructCollectionType(collRaw, args[0]);
                    stack.push(collType);
                } else {
                    stack.push(tf.constructParametricType(raw, args));
                }
            }

            if (stack.size() != 1) {
                throw new IllegalArgumentException("多余或缺失的类型参数，最终栈大小为 " + stack.size());
            }
            return stack.pop();
        }

        //        // 示例：List<Map<String, Integer>>
        //        JavaType t1 =  collection(List.class, map(Map.class, String.class, Integer.class));
        //        JavaType t1 = JacksonTypes.createGenericTypeRef(List.class, Map.class, String.class, Integer.class);
        //
        //        // List<Map>
        //        JavaType t1 =  collection(List.class, Map.class);
        //
        //        // Optional<List<String>>
        //        JavaType t2 = JacksonTypes.createGenericTypeRef(Optional.class, List.class, String.class);
        //
        //        // Set<List<Map<String, Integer>>>
        //        JavaType t3 = JacksonTypes.createGenericTypeRef(Set.class, List.class, Map.class, String.class, Integer.class);
        //
        //        // Map<String, List<Integer>>
        //        JavaType t4 = JacksonTypes.createGenericTypeRef(Map.class, String.class, List.class, Integer.class);

    }


}
