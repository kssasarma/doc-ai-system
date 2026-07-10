package com.docai.bot.application.service;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.model.VersionComparator;
import com.docai.bot.domain.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryAnalyzerService {

	private final ChatClient.Builder chatClientBuilder;
	private final DocumentRepository documentRepository;

	/**
	 * Extract product and version from user query using AI
	 */
	public QueryContext analyzeQuery(String query, String chatId) {
		log.info("Analyzing query: {}", query);

		QueryContext context = new QueryContext();
		context.setQuery(query);

		// Use LLM to extract both product and version
		extractProductAndVersionWithLLM(query, context);

		if (context.getProduct() != null) {
			// Verify product exists in database
			List<String> availableProducts = documentRepository.findDistinctProducts();
			String matchedProduct = findBestMatch(context.getProduct(), availableProducts);
			if (matchedProduct != null) {
				context.setProduct(matchedProduct);
				log.info("Detected product: {}", context.getProduct());

				// If version not found, try to get latest version for this product
				if (context.getVersion() == null) {
					context.setVersion(getLatestVersion(matchedProduct));
					log.info("Using latest version: {}", context.getVersion());
				} else {
					log.info("Detected version: {}", context.getVersion());
				}
			}
		}

		return context;
	}

	private static final int MAX_ATTEMPTS = 3;
	private static final long INITIAL_BACKOFF_MS = 500L;

	private void extractProductAndVersionWithLLM(String query, QueryContext context) {
		String prompt = """
				Extract the product name and version from this user question.
				The product name might be: Case360, Case 360, VRD, Vignette Record and Documents,
				IWSTU, IWST, WebService ToolKit  or similar variations.
				The version might be in formats like: 14.1, 14.1.0, 15.0, etc.

				Return your response in this exact format:
				Product: <product_name>
				Version: <version_number>

				If no product is mentioned, use "NONE" for Product.
				If no version is mentioned, use "NONE" for Version.

				Question: %s

				Your response:""".formatted(query);

		Exception lastException = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				String response = chatClientBuilder.build().prompt().user(prompt).call().content();
				if (response != null) {
					parseProductAndVersion(response, context);
				}
				return;
			} catch (Exception e) {
				lastException = e;
				log.warn("Query analysis attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
				if (attempt < MAX_ATTEMPTS) {
					try {
						Thread.sleep(INITIAL_BACKOFF_MS * (1L << (attempt - 1)));
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		}
		log.warn("All {} query analysis attempts failed, proceeding without extraction", MAX_ATTEMPTS, lastException);
	}

	private void parseProductAndVersion(String response, QueryContext context) {
		String[] lines = response.split("\n");
		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.startsWith("Product:")) {
				String product = trimmed.substring("Product:".length()).trim();
				if (!product.equalsIgnoreCase("NONE") && !product.isEmpty()) {
					context.setProduct(product);
				}
			} else if (trimmed.startsWith("Version:")) {
				String version = trimmed.substring("Version:".length()).trim();
				if (!version.equalsIgnoreCase("NONE") && !version.isEmpty()) {
					context.setVersion(version);
				}
			}
		}
	}

	private String findBestMatch(String extracted, List<String> available) {
		String extractedLower = extracted.toLowerCase().replaceAll("[^a-z0-9]", "");

		for (String product : available) {
			String productLower = product.toLowerCase().replaceAll("[^a-z0-9]", "");
			if (productLower.contains(extractedLower) || extractedLower.contains(productLower)) {
				return product;
			}
		}
		return available.isEmpty() ? null : available.get(0);
	}

	private String getLatestVersion(String product) {
		List<String> versions = documentRepository.findVersionsByProduct(product);
		return versions.stream().max(VersionComparator.INSTANCE).orElse(null);
	}

	@lombok.Data
	public static class QueryContext {
		private String query;
		private String product;
		private String version;

		public boolean hasProductAndVersion() {
			return product != null && version != null;
		}

		public boolean hasProduct() {
			return product != null;
		}
	}
}
