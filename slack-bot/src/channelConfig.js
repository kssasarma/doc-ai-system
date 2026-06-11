/**
 * Per-channel product/version configuration, stored in memory.
 * In production: persist to a database or Redis.
 */
const channelConfig = new Map();

export function getChannelConfig(channelId) {
  return channelConfig.get(channelId) ?? {
    product: process.env.DEFAULT_PRODUCT || null,
    version: process.env.DEFAULT_VERSION || null,
  };
}

export function setChannelConfig(channelId, product, version) {
  channelConfig.set(channelId, { product: product || null, version: version || null });
}
