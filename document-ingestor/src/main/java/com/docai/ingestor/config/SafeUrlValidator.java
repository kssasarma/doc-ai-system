package com.docai.ingestor.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Guards every server-side fetch of a caller-supplied URL (webhook download URLs, Confluence
 * site URLs) against SSRF: fetching internal services or cloud metadata (169.254.169.254) using
 * this server's network position and, for Confluence, its stored bearer token.
 *
 * <p>Callers must re-validate after following each redirect — this class only checks the URL
 * it's given, it does not follow redirects itself. {@code java.net.http.HttpClient} must be built
 * with {@code Redirect.NEVER} so the caller can do that manually.
 *
 * <p><b>Known residual gap (DNS rebinding):</b> this validates the hostname by resolving it once,
 * here; the actual connection is a second, independent resolution performed later by
 * {@code java.net.http.HttpClient}. An attacker controlling authoritative DNS for the target
 * hostname could in principle answer the two lookups differently (a public address for this
 * check, a private/link-local one — e.g. cloud metadata — for the real connection), a small
 * TOCTOU window this class does not close. Doing so correctly requires pinning the connection to
 * the specific resolved address (e.g. via a custom resolver/socket layer), which
 * {@code java.net.http.HttpClient}'s public API does not support cleanly without either a
 * restricted-header JVM flag (to override the Host header) or hand-rolled TLS/SNI handling —
 * both risk introducing worse, harder-to-test bugs than the gap they'd close. Left open
 * deliberately rather than shipped half-fixed; closing it needs a dedicated pinned-DNS HTTP
 * client, not a patch to this class.
 */
@Component
public class SafeUrlValidator {

    private final List<String> httpAllowedHosts;
    private final List<String> confluenceHostAllowlistSuffixes;

    public SafeUrlValidator(
            @Value("${ingestor.security.http-allowed-hosts:localhost,127.0.0.1}") List<String> httpAllowedHosts,
            @Value("${ingestor.security.confluence-host-allowlist:.atlassian.net}") List<String> confluenceHostAllowlistSuffixes) {
        this.httpAllowedHosts = httpAllowedHosts;
        this.confluenceHostAllowlistSuffixes = confluenceHostAllowlistSuffixes;
    }

    /** Validates a general external fetch target (webhook download URLs, Confluence API calls). */
    public void validateExternalUrl(String urlString) {
        URI uri = parse(urlString);
        validateScheme(uri);
        validateHostIsNotInternal(uri.getHost());
    }

    /** Additionally restricts the host to the Confluence allowlist — applied once, at the time a
     * site URL is registered, so a compromised/malicious admin can't point sync at an arbitrary
     * internal host even though the URL otherwise passes {@link #validateExternalUrl}. */
    public void validateConfluenceSiteUrl(String urlString) {
        URI uri = parse(urlString);
        validateScheme(uri);
        validateHostIsNotInternal(uri.getHost());
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = confluenceHostAllowlistSuffixes.stream()
            .anyMatch(suffix -> host.endsWith(suffix.toLowerCase(Locale.ROOT)));
        if (!allowed) {
            throw new IllegalArgumentException(
                "Confluence site URL host '" + host + "' is not in the allowlist (" + confluenceHostAllowlistSuffixes + ")");
        }
    }

    private URI parse(String urlString) {
        URI uri;
        try {
            uri = URI.create(urlString);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed URL: " + urlString);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL has no host: " + urlString);
        }
        return uri;
    }

    private void validateScheme(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) return;
        if ("http".equals(scheme) && httpAllowedHosts.stream().anyMatch(h -> h.equalsIgnoreCase(uri.getHost()))) {
            return;
        }
        throw new IllegalArgumentException(
            "Refusing non-HTTPS URL (scheme=" + scheme + ") for host " + uri.getHost());
    }

    private void validateHostIsNotInternal(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve host: " + host);
        }
        for (InetAddress addr : addresses) {
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                    || addr.isMulticastAddress() || addr.isAnyLocalAddress() || isUniqueLocalIpv6(addr)) {
                throw new IllegalArgumentException(
                    "Refusing to fetch " + host + ": resolves to a private/internal address (" + addr.getHostAddress() + ")");
            }
        }
    }

    /** Java's InetAddress#isSiteLocalAddress only recognizes the deprecated IPv6 fec0::/10 site-
     * local range, not the modern fc00::/7 unique-local range — check that separately. */
    private boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
