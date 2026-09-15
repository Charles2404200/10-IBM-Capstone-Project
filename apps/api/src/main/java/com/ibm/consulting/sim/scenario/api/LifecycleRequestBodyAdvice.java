package com.ibm.consulting.sim.scenario.api;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.ibm.consulting.sim.scenario.application.LifecycleDefinitionCodec;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/** Apply resource limits before recursive record deserialization, including chunked requests. */
@ControllerAdvice(assignableTypes = AdminScenarioLifecycleController.class)
public class LifecycleRequestBodyAdvice extends RequestBodyAdviceAdapter {
    public static final int MAX_REQUEST_BYTES = LifecycleDefinitionCodec.MAX_JSON_LENGTH * 4 + 512;
    private static final int MAX_JSON_NESTING = 16;
    private static final JsonFactory JSON = JsonFactory.builder().streamReadConstraints(
            StreamReadConstraints.builder().maxNestingDepth(MAX_JSON_NESTING)
                    .maxStringLength(LifecycleDefinitionCodec.MAX_JSON_LENGTH).build()).build();

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return parameter.getContainingClass() == AdminScenarioLifecycleController.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        byte[] bytes = input.getBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new HttpMessageNotReadableException("Lifecycle request exceeds the size limit", input);
        }
        try (var parser = JSON.createParser(bytes)) {
            while (parser.nextToken() != null) { /* Validate bounds before building the tree. */ }
        } catch (IOException exception) {
            throw new HttpMessageNotReadableException("Lifecycle request exceeds JSON limits or is malformed", exception, input);
        }
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(bytes); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }
}
