package com.zilai.zilaibuy.config;

import com.zilai.zilaibuy.rakuten.RakutenProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(RakutenProperties.class)
public class WebClientConfig {

    @Bean
    WebClient rakutenWebClient(RakutenProperties props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
    }
}
