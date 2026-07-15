package com.docai.ingestor.application.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Aborts with an {@link IOException} once more than {@code maxBytes} have been read, so a
 * caller-supplied download URL can't exhaust memory/disk via an oversized or decompression-bomb
 * response. Wrap the raw network stream with this before any hashing/storage wrapper. */
public class LimitedInputStream extends FilterInputStream {

    private final long maxBytes;
    private long readSoFar = 0;

    public LimitedInputStream(InputStream in, long maxBytes) {
        super(in);
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) checkLimit(1);
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) checkLimit(n);
        return n;
    }

    private void checkLimit(int n) throws IOException {
        readSoFar += n;
        if (readSoFar > maxBytes) {
            throw new IOException("Download exceeded the maximum allowed size of " + maxBytes + " bytes");
        }
    }
}
