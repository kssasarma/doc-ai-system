package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.docai.bot.application.service.ProductCatalogService;
import com.docai.bot.application.service.ProductCatalogService.ProductEntry;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.DocumentRepository.ProductVersion;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTest {

    @Mock DocumentRepository documentRepository;

    private ProductCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ProductCatalogService(documentRepository);
    }

    private record Row(String getProduct, String getVersion) implements ProductVersion {}

    @Test
    void emptyScope_shortCircuitsWithoutQuerying() {
        SearchScope empty = new SearchScope(UUID.randomUUID(), Set.of());

        List<ProductEntry> result = service.listAccessibleProducts(empty);

        assertThat(result).isEmpty();
        verify(documentRepository, never()).findDistinctProductVersionsAccessible(any(), any());
    }

    @Test
    void groupsVersionsByProduct_sortedNumerically() {
        SearchScope scope = new SearchScope(UUID.randomUUID(), Set.of(UUID.randomUUID()));
        when(documentRepository.findDistinctProductVersionsAccessible(any(), any())).thenReturn(List.of(
            new Row("case360", "14.9"),
            new Row("case360", "14.10"),
            new Row("case360", "14.2"),
            new Row("iwst", "3.0")
        ));

        List<ProductEntry> result = service.listAccessibleProducts(scope);

        assertThat(result).hasSize(2);
        ProductEntry case360 = result.stream().filter(p -> p.product().equals("case360")).findFirst().orElseThrow();
        // Numeric-segment order, not lexicographic — 14.10 must sort after 14.9, not before.
        assertThat(case360.versions()).containsExactly("14.2", "14.9", "14.10");
    }

    @Test
    void productsSortedAlphabetically() {
        SearchScope scope = new SearchScope(UUID.randomUUID(), Set.of(UUID.randomUUID()));
        when(documentRepository.findDistinctProductVersionsAccessible(any(), any())).thenReturn(List.of(
            new Row("zeta", "1.0"),
            new Row("alpha", "1.0")
        ));

        List<ProductEntry> result = service.listAccessibleProducts(scope);

        assertThat(result).extracting(ProductEntry::product).containsExactly("alpha", "zeta");
    }

    @Test
    void nullProductRow_isSkipped() {
        SearchScope scope = new SearchScope(UUID.randomUUID(), Set.of(UUID.randomUUID()));
        when(documentRepository.findDistinctProductVersionsAccessible(any(), any())).thenReturn(List.of(
            new Row(null, "1.0"),
            new Row("case360", "1.0")
        ));

        List<ProductEntry> result = service.listAccessibleProducts(scope);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).product()).isEqualTo("case360");
    }
}
