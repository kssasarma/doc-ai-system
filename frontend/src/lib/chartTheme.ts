import { useTheme } from '../context/ThemeContext';

/**
 * Default dataviz palette (see the `dataviz` skill's reference/palette.md), re-validated against
 * this app's own chart surfaces (`#ffffff` light / `#18191e` dark from index.css) rather than the
 * skill's generic defaults — both pass the CVD-separation/contrast checks at those surfaces.
 * Categorical order is the CVD-safety mechanism (don't reorder): blue, aqua, yellow, green,
 * violet, red, magenta, orange.
 */
const LIGHT = {
  categorical: ['#2a78d6', '#1baf7a', '#eda100', '#008300', '#4a3aa7', '#e34948', '#e87ba4', '#eb6834'],
  /** Single-hue default for magnitude encoding (one series, or one metric per category). */
  sequential: '#2a78d6',
  grid: '#e1e0d9',
  axis: '#c3c2b7',
  mutedText: '#898781',
  text: '#0b0b0b',
  tooltipBg: '#fcfcfb',
};

const DARK = {
  categorical: ['#3987e5', '#199e70', '#c98500', '#008300', '#9085e9', '#e66767', '#d55181', '#d95926'],
  sequential: '#3987e5',
  grid: '#2c2c2a',
  axis: '#383835',
  mutedText: '#898781',
  text: '#ffffff',
  tooltipBg: '#1a1a19',
};

export type ChartTheme = typeof LIGHT;

/** Resolves the validated chart palette for the viewer's current theme — see chartTheme.ts doc
 * comment for where these values came from and why they're safe to use unchanged. */
export function useChartTheme(): ChartTheme {
  const { resolvedTheme } = useTheme();
  return resolvedTheme === 'dark' ? DARK : LIGHT;
}
