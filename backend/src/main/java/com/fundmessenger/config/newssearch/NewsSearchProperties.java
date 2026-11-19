package com.fundmessenger.config.newssearch;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "news-search")
public class NewsSearchProperties {

    private ProviderConfig tavily = new ProviderConfig();
    private ProviderConfig serpapi = new ProviderConfig();
    private ProviderConfig marketaux = new ProviderConfig();

    @Getter
    @Setter
    public static class ProviderConfig {
        private String apiKeys = "";
        private int dailyLimitPerKey = 10;

        public List<String> getKeyList() {
            if (apiKeys == null || apiKeys.isBlank()) return List.of();
            return Arrays.stream(apiKeys.split(","))
                    .map(String::trim)
                    .filter(k -> !k.isBlank())
                    .toList();
        }

        public int keyCount() {
            return getKeyList().size();
        }
    }
}
