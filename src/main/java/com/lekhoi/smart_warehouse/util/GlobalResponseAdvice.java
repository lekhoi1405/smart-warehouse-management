package com.lekhoi.smart_warehouse.util;

import java.util.Optional;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.lekhoi.smart_warehouse.domain.dto.response.RestResponse;
import com.lekhoi.smart_warehouse.util.annotation.ApiMessage;

import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object>{

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }
    
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
                HttpServletResponse httpServletResponse = ((ServletServerHttpResponse)response).getServletResponse();
                int status = httpServletResponse.getStatus();
                RestResponse<Object> res = new RestResponse<>();
                res.setStatusCode(status);
                if(body instanceof String || body instanceof byte[]){
                    return body;
                }
                if(status >= 400){
                    return body;
                }else{
                    String message = Optional.ofNullable(returnType.getMethodAnnotation(ApiMessage.class))
                                                                    .map(ApiMessage::value)
                                                                    .orElse("call api success");
                    res.setMessage(message);
                    res.setData(body);
                }
                return res;
    }
}

