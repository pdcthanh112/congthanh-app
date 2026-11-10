package com.congthanh.commonservice.utils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@UtilityClass
@Slf4j
public class JsonUtils {

    public static final ObjectMapper objectMapper = new ObjectMapper();

    public String toJson(Object obj) {
        String json = null;
        try {
            json = objectMapper.writeValueAsString(obj);
            return json;
        } catch (JacksonException e) {
            log.error("[JsonUtils][toJson] exception {}", ExceptionUtils.getStackTrace(e));
            return json;
        }
    }
}
