package com.tyin.gateway.filter;

import com.tyin.core.api.DefaultErrorResult;
import com.tyin.core.api.Result;
import com.tyin.core.exception.ApiException;
import com.tyin.core.exception.RequestException;
import com.tyin.core.logs.RequestLog;
import com.tyin.core.modules.bean.MultipartData;
import com.tyin.core.modules.entity.AdminGatewayLog;
import com.tyin.core.utils.JsonUtils;
import com.tyin.core.utils.StringUtils;
import com.tyin.gateway.components.RequestLogComponents;
import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class GatewayGlobalFilter implements GlobalFilter, Ordered {
    private static final RequestLog LOG = new RequestLog();
    private final RequestLogComponents requestLogComponents;

    @Override
    @SuppressWarnings("ALL")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
        String requestId = request.getId() + ":" + System.currentTimeMillis();
        String uuid = StringUtils.uuid();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public @NotNull Mono<Void> writeWith(@Nonnull Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    return super.writeWith(fluxBody.map(dataBuffer -> {
                        byte[] content = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(content);
                        DataBufferUtils.release(dataBuffer);
                        String s = new String(content, StandardCharsets.UTF_8);
                        Result<?> result = JsonUtils.toJavaObject(s, Result.class);
                        DefaultErrorResult defaultErrorResult = JsonUtils.toJavaObject(s, DefaultErrorResult.class);
                        if (Objects.isNull(result.code()) && Objects.isNull(defaultErrorResult.status())) {
                            byte[] uppedContent = new String(content, StandardCharsets.UTF_8).getBytes();
                            return bufferFactory.wrap(uppedContent);
                        }
                        if (Objects.nonNull(result.code())) {
                            String resultStr = JsonUtils.toJSONString(result);
                            LOG.info("-RESULT   " + requestId + "   ===:  " + resultStr);
                            LOG.info("-E N D    " + requestId + "   ================]");
                            requestLogComponents.saveLog(AdminGatewayLog.builder().requestId(uuid + ":" + requestId).field("RESULT").value(resultStr).sort(99).build());
                        } else {
                            throw new RequestException(requestId, uuid, new ApiException(defaultErrorResult.error()));
                        }
                        byte[] uppedContent = new String(content, StandardCharsets.UTF_8).getBytes();
                        return bufferFactory.wrap(uppedContent);
                    }));
                }
                return super.writeWith(body);
            }
        };
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        MultiValueMap<String, String> queryParams = request.getQueryParams();
        HttpHeaders headers = request.getHeaders();
        Map<String, String> stringStringMap = headers.toSingleValueMap();
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
        LOG.info("-START    " + requestId + "   ================]");
        LOG.info("-PATH     " + requestId + "   ===:  " + path);
        requestLogComponents.saveLog(AdminGatewayLog.builder().requestId(uuid + ":" + requestId).field("PATH").value(path).sort(0).build());
        LOG.info("-METHOD   " + requestId + "   ===:  " + method);
        requestLogComponents.saveLog(AdminGatewayLog.builder().requestId(uuid + ":" + requestId).field("METHOD").value(method).sort(1).build());
        String headersStr = JsonUtils.toJSONString(stringStringMap);
        LOG.info("-HEADER   " + requestId + "   ===:  " + headersStr);
        requestLogComponents.saveLog(AdminGatewayLog.builder().requestId(uuid + ":" + requestId).field("HEADER").value(headersStr).sort(2).build());
        String paramsStr = JsonUtils.toJSONString(queryParams);
        LOG.info("-PARAMS   " + requestId + "   ===:  " + paramsStr);
        requestLogComponents.saveLog(AdminGatewayLog.builder().requestId(uuid + ":" + requestId).field("PARAMS").value(paramsStr).sort(3).build());
        if (Objects.nonNull(contentType) && (contentType.includes(MediaType.APPLICATION_JSON))) {
            return DataBufferUtils.join(exchange.getRequest().getBody())
                    .flatMap(dataBuffer -> {
                        AtomicReference<String> bodyRef = new AtomicReference<>();
                        DataBufferUtils.retain(dataBuffer);
                        Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.split(dataBuffer.readableByteCount())));
                        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(dataBuffer.toByteBuffer());
                        bodyRef.set(charBuffer.toString());
                        String data = JsonUtils.toJSONString(JsonUtils.toJavaObject(bodyRef.get(), Object.class));
                        LOG.info("-DATA     " + requestId + "   ===:  " + data);
                        requestLogComponents.saveLog(AdminGatewayLog.builder().requestId(uuid + ":" + requestId).field("DATA").value(data).sort(4).build());
                        ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                return cachedFlux;
                            }
                        };
                        return chain.filter(exchange.mutate().request(mutatedRequest).response(decoratedResponse).build());
                    });
        } else if (Objects.nonNull(contentType) && contentType.includes(MediaType.MULTIPART_FORM_DATA)) {
            return DataBufferUtils.join(exchange.getRequest().getBody())
                    .flatMap(dataBuffer -> {
                        AtomicReference<String> bodyRef = new AtomicReference<>();
                        DataBufferUtils.retain(dataBuffer);
                        Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.split(dataBuffer.readableByteCount())));
                        ByteBuffer byteBuffer = dataBuffer.toByteBuffer();
                        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(byteBuffer);
                        Thread thread = new Thread(){
                            @Override
                            public void run() {
                                Map<String, List<MultipartData>> readline = MultipartData.readline(byteBuffer);
                                bodyRef.set(charBuffer.toString());
                                String data = JsonUtils.toJSONString(readline);
                                LOG.info("-DATA     " + requestId + "   ===:  " + data);
                                requestLogComponents.saveLog(AdminGatewayLog.builder().requestId(uuid + ":" + requestId).field("DATA").value(data).sort(4).build());
                            }
                        };
                        thread.start();
                        ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                return cachedFlux;
                            }
                        };
                        return chain.filter(exchange.mutate().request(mutatedRequest).response(decoratedResponse).build());
                    });
        } else {
            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        }
    }

    @Override
    public int getOrder() {
        return -2;
    }

    private void readline() {

    }
}
