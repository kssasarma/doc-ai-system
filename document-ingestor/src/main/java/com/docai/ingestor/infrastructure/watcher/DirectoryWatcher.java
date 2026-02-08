package com.docai.ingestor.infrastructure.watcher;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.docai.ingestor.application.service.IngestionService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectoryWatcher {

    private final IngestionService ingestionService;

    @Value("${ingestor.watch-directory}")
    private String watchDirectory;

    private WatchService watchService;
    private ExecutorService executorService;
    private volatile boolean running = false;

    @PostConstruct
    public void init() throws IOException {
        File dir = new File(watchDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("Created watch directory: {}", watchDirectory);
        }

        this.watchService = FileSystems.getDefault().newWatchService();
        Path path = Paths.get(watchDirectory);
        path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

        this.executorService = Executors.newSingleThreadExecutor();
        this.running = true;

        executorService.submit(this::watchDirectory);
        
        // Initial scan of existing files
        scanExistingFiles();
        
        log.info("Directory watcher started for: {}", watchDirectory);
    }

    private void watchDirectory() {
        while (running) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path filePath = Paths.get(watchDirectory).resolve(filename);
                    File file = filePath.toFile();

                    if (kind == ENTRY_CREATE || kind == ENTRY_MODIFY) {
                        handleFileChange(file);
                    } else if (kind == ENTRY_DELETE) {
                        handleFileDelete(file);
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    log.warn("Watch key no longer valid");
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Directory watcher interrupted");
                break;
            }
        }
    }

    private void handleFileChange(File file) {
        if (!isSupportedFile(file)) {
            return;
        }

        log.info("File change detected: {}", file.getName());
        
        // Wait a bit to ensure file is fully written
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        ingestionService.ingestDocument(file);
    }

    private void handleFileDelete(File file) {
        log.info("File deleted: {}", file.getName());
        // Could implement deletion logic here if needed
    }

    private void scanExistingFiles() {
        File dir = new File(watchDirectory);
        File[] files = dir.listFiles(this::isSupportedFile);
        
        if (files != null) {
            log.info("Scanning {} existing files in watch directory", files.length);
            for (File file : files) {
                ingestionService.ingestDocument(file);
            }
        }
    }

    private boolean isSupportedFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        
        String name = file.getName().toLowerCase();
        return name.endsWith(".pdf") || name.endsWith(".chm");
    }

    @PreDestroy
    public void destroy() {
        running = false;
        
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("Error closing watch service", e);
            }
        }
        
        log.info("Directory watcher stopped");
    }
}
