package com.docai.bot.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.FaqEntry;
import com.docai.bot.domain.entity.FaqEntry.Status;

@Repository
public interface FaqEntryRepository extends JpaRepository<FaqEntry, UUID> {

    Page<FaqEntry> findByStatus(Status status, Pageable pageable);

    Page<FaqEntry> findByProductAndVersionAndStatus(
        String product, String version, Status status, Pageable pageable);

    Page<FaqEntry> findByProductAndStatus(String product, Status status, Pageable pageable);

    List<FaqEntry> findByStatus(Status status);

    @Modifying
    @Query("UPDATE FaqEntry f SET f.viewCount = f.viewCount + 1 WHERE f.id = :id")
    void incrementViewCount(UUID id);

    @Modifying
    @Query("UPDATE FaqEntry f SET f.helpfulCount = f.helpfulCount + 1 WHERE f.id = :id")
    void incrementHelpfulCount(UUID id);
}
