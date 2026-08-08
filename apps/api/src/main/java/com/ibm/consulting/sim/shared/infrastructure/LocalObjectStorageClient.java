package com.ibm.consulting.sim.shared.infrastructure;

import com.ibm.consulting.sim.shared.domain.ObjectStorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Local-filesystem fallback storage for development, tests and the offline
 * demo path (§8 Phase 5). Enabled when app.cos.mock-mode=true (default).
 */
@Component
@ConditionalOnProperty(name = "app.cos.mock-mode", havingValue = "true", matchIfMissing = true)
public class LocalObjectStorageClient implements ObjectStorageClient {

    private static final Logger log = LoggerFactory.getLogger(LocalObjectStorageClient.class);
    private static final Path ROOT = Paths.get(System.getProperty("java.io.tmpdir"), "consulting-sim-storage");

    @Override
    public String upload(String key, byte[] content, String contentType) {
        try {
            Path target = ROOT.resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            log.debug("Stored object locally at {}", target);
            return "local://" + target;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write local object storage entry: " + key, e);
        }
    }
}
