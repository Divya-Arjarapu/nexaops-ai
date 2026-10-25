package com.nexaops.backend.documents;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class DocumentExtractionClientConfiguration {

    @Bean
    RestClient documentExtractionRestClient(
            @Value("${nexaops.ai-service.base-url}") String baseUrl,
            @Value("${nexaops.ai-service.connect-timeout}") Duration connectTimeout,
            @Value("${nexaops.ai-service.document-extraction-read-timeout}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
