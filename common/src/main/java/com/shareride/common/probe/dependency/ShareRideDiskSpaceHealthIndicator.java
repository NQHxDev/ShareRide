package com.shareride.common.probe.dependency;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.io.File;

/**
 * Health indicator verifying storage volume free space against configurable threshold.
 */
public class ShareRideDiskSpaceHealthIndicator implements HealthIndicator {

    private final File path;
    private final long thresholdBytes;

    public ShareRideDiskSpaceHealthIndicator() {
        this(new File("."), 500L * 1024 * 1024); // 500 MB default
    }

    public ShareRideDiskSpaceHealthIndicator(File path, long thresholdBytes) {
        this.path = path;
        this.thresholdBytes = thresholdBytes;
    }

    @Override
    public Health health() {
        long freeBytes = path.getFreeSpace();
        long totalBytes = path.getTotalSpace();
        long usableBytes = path.getUsableSpace();

        double usedPercent = totalBytes > 0 ? (double) (totalBytes - freeBytes) / totalBytes * 100.0 : 0.0;
        long freeMb = freeBytes / (1024 * 1024);
        long thresholdMb = thresholdBytes / (1024 * 1024);

        Health.Builder builder = (usableBytes >= thresholdBytes) ? Health.up() : Health.down();

        return builder
                .withDetail("totalBytes", totalBytes)
                .withDetail("freeBytes", freeBytes)
                .withDetail("freeMb", freeMb)
                .withDetail("thresholdMb", thresholdMb)
                .withDetail("usedPercent", Math.round(usedPercent * 10.0) / 10.0)
                .withDetail("path", path.getAbsolutePath())
                .build();
    }
}
