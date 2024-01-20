package org.droolsassert.util;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static java.lang.String.format;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;

public final class JsonUtils {

	private static final ObjectMapper DEFAULT_MAPPER = newFieldMapper(new JsonFactory(), NON_NULL);
	private static final ObjectMapper YAML_MAPPER = newFieldMapper(new YAMLFactory(), NON_NULL);
	static {
		DEFAULT_MAPPER.registerModule(new JavaTimeModule());
		DEFAULT_MAPPER.configure(WRITE_DATES_AS_TIMESTAMPS, false);
		DEFAULT_MAPPER.activateDefaultTyping(DEFAULT_MAPPER.getPolymorphicTypeValidator(), OBJECT_AND_NON_CONCRETE);

		YAML_MAPPER.registerModule(new JavaTimeModule());
		YAML_MAPPER.configure(WRITE_DATES_AS_TIMESTAMPS, false);
	}

	private JsonUtils() {
	}

	public static String toJson(Object obj) {
		return toJson(obj, false);
	}

	public static String toJson(Object obj, boolean prettyPrint) {
		try {
			return prettyPrint
					? DEFAULT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj)
					: DEFAULT_MAPPER.writeValueAsString(obj);
		} catch (IOException e) {
			throw new IllegalArgumentException("Cannot convert to JSON.", e);
		}
	}
	
	public static String toYaml(Object obj) {
		try {
			return YAML_MAPPER.writeValueAsString(obj);
		} catch (IOException e) {
			throw new IllegalArgumentException("Cannot convert to YAML.", e);
		}
	}

	public static <T> T fromJson(String json, Class<T> clazz) {
		try {
			return DEFAULT_MAPPER.readValue(json, clazz);
		} catch (IOException e) {
			throw new IllegalArgumentException(format("Cannot read %s from JSON:%n%s", clazz.getSimpleName(), json), e);
		}
	}
	
	public static <T> T fromYaml(String yaml, Class<T> clazz) {
		try {
			return YAML_MAPPER.readValue(yaml, clazz);
		} catch (IOException e) {
			throw new IllegalArgumentException(format("Cannot read %s from YAML:%n%s", clazz.getSimpleName(), yaml), e);
		}
	}

	public static ObjectMapper getObjectMapper() {
		return DEFAULT_MAPPER;
	}

	private static ObjectMapper newFieldMapper(JsonFactory factory, Include inclusion) {
		ObjectMapper mapper = new ObjectMapper(factory);
		mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
				.withFieldVisibility(ANY)
				.withGetterVisibility(NONE)
				.withSetterVisibility(NONE)
				.withCreatorVisibility(NONE));

		mapper.setSerializationInclusion(inclusion);
		mapper.configure(FAIL_ON_EMPTY_BEANS, false);
		return mapper;
	}
}
