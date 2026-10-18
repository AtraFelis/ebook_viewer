package dev.example.ebookviewer.searchprobe;

import android.os.Bundle;
import android.os.Debug;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/** Aggregate counters only. Never read smaps paths, document text or another app's files. */
final class ProbeMetrics {
    private ProbeMetrics() {}

    static long field(String path, String key) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(key + ":")) {
                    return Long.parseLong(line.substring(line.indexOf(':') + 1).trim().split("\\s+")[0]);
                }
            }
        }
        throw new IOException("Aggregate memory field unavailable");
    }

    static long rssKb() throws IOException {
        return field("/proc/self/status", "VmRSS");
    }

    static long availableKb() throws IOException {
        return field("/proc/meminfo", "MemAvailable");
    }

    static int descriptors() throws IOException {
        String[] entries = new File("/proc/self/fd").list();
        if (entries == null) throw new IOException("Descriptor count unavailable");
        return entries.length;
    }

    static Bundle snapshot() throws IOException {
        Bundle result = new Bundle();
        result.putLong("rssKb", rssKb());
        result.putLong("pssKb", Debug.getPss());
        result.putLong("nativeBytes", Debug.getNativeHeapAllocatedSize());
        Runtime runtime = Runtime.getRuntime();
        result.putLong("javaBytes", runtime.totalMemory() - runtime.freeMemory());
        result.putLong("availableKb", availableKb());
        result.putInt("fds", descriptors());
        return result;
    }
}
