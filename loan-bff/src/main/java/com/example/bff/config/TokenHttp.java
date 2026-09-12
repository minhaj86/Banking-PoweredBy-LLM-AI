package com.example.bff.config;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;

/** Bounded standard token exchange: a stalled provider cannot outlive the shared refresh lease. */
final class TokenHttp {
    private TokenHttp() {}
    static JdkClientHttpRequestFactory factory(){var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());factory.setReadTimeout(Duration.ofSeconds(10));return factory;}
    static RestClient tokens(){return RestClient.builder().requestFactory(factory()).messageConverters(converters->{converters.clear();converters.add(new FormHttpMessageConverter());converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());}).defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build();}
}
