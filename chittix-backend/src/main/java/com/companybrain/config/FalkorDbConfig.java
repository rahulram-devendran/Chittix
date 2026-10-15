package com.companybrain.config;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FalkorDbConfig {

    /**
     * Creates the FalkorDB driver. The driver does not connect at startup, so the
     * application still boots when the database is down and /api/health reports it.
     */
    @Bean(destroyMethod = "close")
    public Driver falkorDbDriver(@Value("${falkordb.host}") String host,
                                 @Value("${falkordb.port}") int port,
                                 @Value("${falkordb.username:}") String username,
                                 @Value("${falkordb.password:}") String password) {
        if (!password.isBlank()) {
            return FalkorDB.driver(host, port, username.isBlank() ? "default" : username, password);
        }
        return FalkorDB.driver(host, port);
    }
}
