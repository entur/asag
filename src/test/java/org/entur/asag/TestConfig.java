package org.entur.asag;

import org.entur.asag.service.BlobStoreService;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Profile("test")
@Configuration
public class TestConfig {
    @Bean
    @Primary
    public BlobStoreService blobStoreService() {
        return Mockito.mock(BlobStoreService.class);
    }
}

