package com.company.chatassistant.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import okhttp3.OkHttpClient;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient(AppProperties props) {
        URI uri = URI.create(props.getElasticsearch().getHosts().split(",")[0].trim());
        RestClient restClient = RestClient
                .builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()))
                .build();
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer(AppProperties props) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/chat/**")
                        .allowedOrigins(props.getCors().getAllowedOrigins().split(","))
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("*")
                        .maxAge(3600);
                registry.addMapping("/widget/**")
                        .allowedOrigins("*");
            }
        };
    }
}
