package com.devteria.gateway.configuration;

import com.devteria.gateway.dto.request.ApiResponse;
import com.devteria.gateway.service.IdentityService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerResponse;

import javax.print.attribute.standard.Media;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationFilter implements GlobalFilter, Ordered {

    IdentityService identityService;
    ObjectMapper objectMapper;

    // reactive core, có hổ trợ httpclient equivalent to feign client k cần thêm dependency của feign client
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("Enter Authentication Filter........");
        // Get token from authorization header
        List<String> authHeader = exchange.getRequest().getHeaders().get("Authorization");
        if(CollectionUtils.isEmpty(authHeader)) {
            return unauthenticated(exchange.getResponse());
        }
        String token = authHeader.getFirst().replace("Bearer ", "");
        log.info("token : {}", token);
     return   identityService.introspect(token).flatMap(introspectResponse ->
                {
                    if(introspectResponse.getResult().isValid()){
                        return chain.filter(exchange);
                    }else {
                        return unauthenticated(exchange.getResponse());
                    }
                }).onErrorResume(throwable -> unauthenticated(exchange.getResponse()));

        //verify token
        //delegate identity service
       // return chain.filter(exchange); //   it's mean enable request (exchange) through pass this filter

    }
    //priority
    @Override
    public int getOrder() {
        return -1;
    }
    //reactive xem lại
    Mono<Void> unauthenticated(ServerHttpResponse response){
        ApiResponse<?> apiResponse =  ApiResponse.builder()
                .code(1401)
                .message("Unauthenticated")
                .build();

        String body = null;
        try {
            body = objectMapper.writeValueAsString(apiResponse);
        }catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }
}
