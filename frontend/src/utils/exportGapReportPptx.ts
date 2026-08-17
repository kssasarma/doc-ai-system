import pptxgen from 'pptxgenjs';
import { type GapReport } from '../services/gapReportService';

// A4 dimensions in inches
const W = 8.27;
const H = 11.69;
const MX = 0.55; // margin x
const CW = W - 2 * MX; // content width = 7.17

// Colour palette — all 6-digit hex, no '#', no alpha
const C = {
  navy:    '1E2761',
  blue:    '1D4ED8',
  iceBlue: 'CADCFC',
  light:   'EFF6FF',
  white:   'FFFFFF',
  slate:   '334155',
  muted:   '64748B',
  amber:   'D97706',
  amberBg: 'FEF3C7',
  rose:    'BE123C',
  roseBg:  'FEF2F2',
  green:   '059669',
  greenBg: 'ECFDF5',
  teal:    '0D9488',
  purple:  '7C3AED',
};

interface GapTopic {
  topic: string;
  queryCount: number;
  uniqueUsers: number;
  exampleQuestions: string[];
  suggestedDocStub: string;
}

function parseTopics(raw: string): GapTopic[] {
  try { return JSON.parse(raw); } catch { return []; }
}

function formatDate(d: string): string {
  return new Date(d).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

function card(
  slide: pptxgen.Slide,
  x: number, y: number, w: number, h: number,
  fillColor: string,
  opts: { shadow?: boolean; border?: string; borderWidth?: number } = {},
) {
  const shape = slide.addShape('roundRect' as pptxgen.ShapeType, {
    x, y, w, h,
    fill:       { color: fillColor },
    line:       { color: opts.border ?? fillColor, width: opts.borderWidth ?? 0 },
    rectRadius: 0.1,
  });
  if (opts.shadow) {
    // pptxgenjs shadow — must not share the object between calls
    (shape as unknown as { shadow: object }).shadow = {
      type: 'outer', color: 'BBBBBB', blur: 6, offset: 3, angle: 90,
    };
  }
  return shape;
}

function dot(slide: pptxgen.Slide, x: number, y: number, r: number, color: string) {
  slide.addShape('ellipse' as pptxgen.ShapeType, {
    x, y, w: r * 2, h: r * 2,
    fill: { color },
    line: { color, width: 0 },
  });
}

export async function exportGapReportAsPptx(report: GapReport): Promise<void> {
  const pres = new pptxgen();
  pres.defineLayout({ name: 'A4', width: W, height: H });
  pres.layout = 'A4';

  const topics = parseTopics(report.gapTopics);
  const productLabel = [report.product, report.version].filter(Boolean).join(' ') || 'All Products';

  // ── Slide 1: Title ──────────────────────────────────────────────────────
  {
    const s = pres.addSlide();
    s.background = { color: C.navy };

    // Accent block top-right
    s.addShape('rect' as pptxgen.ShapeType, {
      x: 5.2, y: 0, w: 3.07, h: 3.8,
      fill: { color: C.blue },
      line: { color: C.blue, width: 0 },
    });

    s.addText('Documentation', {
      x: MX, y: 3.0, w: 5.0, h: 0.8,
      fontSize: 38, bold: true, color: C.white,
      fontFace: 'Calibri', align: 'left', margin: 0,
    });
    s.addText('Gap Report', {
      x: MX, y: 3.8, w: 5.0, h: 0.8,
      fontSize: 38, bold: true, color: C.iceBlue,
      fontFace: 'Calibri', align: 'left', margin: 0,
    });

    s.addText(productLabel, {
      x: MX, y: 4.9, w: CW, h: 0.42,
      fontSize: 16, color: C.iceBlue,
      fontFace: 'Calibri', align: 'left', margin: 0,
    });
    s.addText(`Period: ${formatDate(report.reportPeriodStart)} → ${formatDate(report.reportPeriodEnd)}`, {
      x: MX, y: 5.4, w: CW, h: 0.38,
      fontSize: 13, color: 'A0B4D6',
      fontFace: 'Calibri', align: 'left', margin: 0,
    });
    s.addText(`Generated: ${formatDate(report.generatedAt)}`, {
      x: MX, y: 5.82, w: CW, h: 0.34,
      fontSize: 12, color: '6680A8',
      fontFace: 'Calibri', align: 'left', margin: 0,
    });

    // Footer line
    s.addShape('line' as pptxgen.ShapeType, {
      x: MX, y: 10.7, w: CW, h: 0,
      line: { color: '2D3E7A', width: 1 },
    });
    s.addText('Docs-inator · AI Documentation Intelligence', {
      x: MX, y: 10.9, w: CW, h: 0.3,
      fontSize: 9, color: '4A5899',
      fontFace: 'Calibri', align: 'left', margin: 0,
    });
  }

  // ── Slide 2: Executive Summary ──────────────────────────────────────────
  {
    const s = pres.addSlide();
    s.background = { color: C.white };

    s.addShape('rect' as pptxgen.ShapeType, {
      x: 0, y: 0, w: W, h: 0.1,
      fill: { color: C.navy },
      line: { color: C.navy, width: 0 },
    });

    s.addText('Executive Summary', {
      x: MX, y: 0.3, w: CW, h: 0.65,
      fontSize: 28, bold: true, color: C.navy,
      fontFace: 'Calibri', align: 'left', margin: 0,
    });

    // 3 stat boxes
    const stats = [
      { label: 'Low-confidence Queries', value: String(report.totalLowConfidenceQueries), color: C.rose, bg: C.roseBg },
      { label: 'Documentation Gaps', value: String(topics.length), color: C.amber, bg: C.amberBg },
      { label: 'Analysis Period (days)', value: String(Math.ceil((new Date(report.reportPeriodEnd).getTime() - new Date(report.reportPeriodStart).getTime()) / 86400000)), color: C.blue, bg: C.light },
    ];

    stats.forEach((st, i) => {
      const bx = MX + i * 2.42;
      card(s, bx, 1.2, 2.2, 2.1, st.bg, { border: st.color, borderWidth: 1, shadow: true });
      s.addText(st.value, {
        x: bx + 0.1, y: 1.4, w: 2.0, h: 0.9,
        fontSize: 44, bold: true, color: st.color,
        fontFace: 'Calibri', align: 'center', margin: 0,
      });
      s.addText(st.label, {
        x: bx + 0.1, y: 2.38, w: 2.0, h: 0.62,
        fontSize: 11, color: C.slate,
        fontFace: 'Calibri', align: 'center', margin: 0,
      });
    });

    // Period & product detail
    card(s, MX, 3.6, CW, 1.5, C.light);
    s.addText('Report Details', {
      x: MX + 0.25, y: 3.75, w: CW - 0.4, h: 0.38,
      fontSize: 13, bold: true, color: C.navy,
      fontFace: 'Calibri', align: 'left', margin: 0,
    });

    const details = [
      `Product: ${productLabel}`,
      `Period: ${formatDate(report.reportPeriodStart)} → ${formatDate(report.reportPeriodEnd)}`,
      `Generated: ${formatDate(report.generatedAt)}`,
    ];
    details.forEach((d, j) => {
      s.addText(d, {
        x: MX + 0.25, y: 4.2 + j * 0.35, w: CW - 0.4, h: 0.32,
        fontSize: 12, color: C.slate,
        fontFace: 'Calibri', align: 'left', margin: 0,
      });
    });

    // Guidance note
    if (topics.length > 0) {
      card(s, MX, 5.4, CW, 1.6, C.amberBg, { border: C.amber, borderWidth: 1 });
      dot(s, MX + 0.2, 5.55, 0.12, C.amber);
      s.addText('What this means', {
        x: MX + 0.57, y: 5.48, w: CW - 0.7, h: 0.38,
        fontSize: 13, bold: true, color: C.amber,
        fontFace: 'Calibri', align: 'left', margin: 0,
      });
      s.addText(
        `${topics.length} topic${topics.length === 1 ? '' : 's'} were identified where users consistently received low-confidence answers. ` +
        'Each topic represents a documentation gap that, when addressed, will improve user satisfaction and reduce support burden.',
        {
          x: MX + 0.25, y: 5.9, w: CW - 0.4, h: 1.0,
          fontSize: 11, color: C.slate,
          fontFace: 'Calibri', align: 'left', margin: 0,
        },
      );
    }
  }

  // ── Slides 3+: One slide per gap topic ─────────────────────────────────
  const topicColors = [C.rose, C.amber, C.blue, C.teal, C.purple, C.green];

  topics.forEach((topic, idx) => {
    const accent = topicColors[idx % topicColors.length];
    const s = pres.addSlide();
    s.background = { color: C.white };

    // Header band
    s.addShape('rect' as pptxgen.ShapeType, {
      x: 0, y: 0, w: W, h: 1.4,
      fill: { color: accent },
      line: { color: accent, width: 0 },
    });

    s.addText(`Gap ${idx + 1} of ${topics.length}`, {
      x: MX, y: 0.1, w: CW, h: 0.3,
      fontSize: 11, color: C.white,
      fontFace: 'Calibri', align: 'left', margin: 0, bold: false,
    });
    s.addText(topic.topic, {
      x: MX, y: 0.4, w: CW, h: 0.85,
      fontSize: 22, bold: true, color: C.white,
      fontFace: 'Calibri', align: 'left', margin: 0,
    });

    // Metrics row
    const metrics = [
      { label: 'Queries', value: String(topic.queryCount) },
      { label: 'Unique Users', value: String(topic.uniqueUsers) },
    ];
    metrics.forEach((m, mi) => {
      const mx2 = MX + mi * 2.5;
      card(s, mx2, 1.6, 2.2, 1.4, C.light, { border: accent, borderWidth: 1 });
      s.addText(m.value, {
        x: mx2 + 0.1, y: 1.75, w: 2.0, h: 0.65,
        fontSize: 36, bold: true, color: accent,
        fontFace: 'Calibri', align: 'center', margin: 0,
      });
      s.addText(m.label, {
        x: mx2 + 0.1, y: 2.44, w: 2.0, h: 0.38,
        fontSize: 11, color: C.slate,
        fontFace: 'Calibri', align: 'center', margin: 0,
      });
    });

    // Example questions
    if (topic.exampleQuestions?.length) {
      s.addText('Example Questions', {
        x: MX, y: 3.25, w: CW, h: 0.38,
        fontSize: 13, bold: true, color: C.navy,
        fontFace: 'Calibri', align: 'left', margin: 0,
      });
      topic.exampleQuestions.slice(0, 4).forEach((q, qi) => {
        dot(s, MX + 0.1, 3.78 + qi * 0.72, 0.09, accent);
        s.addText(`"${q}"`, {
          x: MX + 0.35, y: 3.68 + qi * 0.72, w: CW - 0.45, h: 0.6,
          fontSize: 11, color: C.slate,
          fontFace: 'Calibri', align: 'left', margin: 0, italic: true,
        });
      });
    }

    // Suggested doc stub
    const stubY = Math.min(7.0, 3.25 + 0.5 + (topic.exampleQuestions?.length || 0) * 0.72 + 0.3);
    if (topic.suggestedDocStub) {
      s.addText('Suggested Documentation Stub', {
        x: MX, y: stubY, w: CW, h: 0.38,
        fontSize: 13, bold: true, color: C.navy,
        fontFace: 'Calibri', align: 'left', margin: 0,
      });
      card(s, MX, stubY + 0.45, CW, H - stubY - 0.75, C.amberBg, { border: C.amber, borderWidth: 1 });
      s.addText(topic.suggestedDocStub, {
        x: MX + 0.2, y: stubY + 0.6, w: CW - 0.4, h: H - stubY - 1.05,
        fontSize: 11, color: C.slate,
        fontFace: 'Calibri', align: 'left', margin: 0,
      });
    }
  });

  // ── Final slide: Next Steps ─────────────────────────────────────────────
  {
    const s = pres.addSlide();
    s.background = { color: C.navy };

    s.addShape('rect' as pptxgen.ShapeType, {
      x: 0, y: 7.0, w: W, h: H - 7.0,
      fill: { color: C.blue },
      line: { color: C.blue, width: 0 },
    });

    s.addText('Recommended Next Steps', {
      x: MX, y: 1.5, w: CW, h: 0.8,
      fontSize: 32, bold: true, color: C.white,
      fontFace: 'Calibri', align: 'center', margin: 0,
    });
    s.addText(`${topics.length} topic${topics.length === 1 ? '' : 's'} need${topics.length === 1 ? 's' : ''} documentation attention`, {
      x: MX, y: 2.4, w: CW, h: 0.4,
      fontSize: 14, color: C.iceBlue,
      fontFace: 'Calibri', align: 'center', margin: 0,
    });

    const actions = [
      `Review the ${topics.length} gap topic${topics.length === 1 ? '' : 's'} identified in this report`,
      'Use the suggested documentation stubs as a starting point',
      'Prioritise topics with the highest query count and unique user count',
      'Re-run this report after publishing new docs to measure improvement',
    ];

    actions.forEach((a, i) => {
      card(s, MX, 3.2 + i * 0.88, CW, 0.72, '243580');
      dot(s, MX + 0.18, 3.33 + i * 0.88, 0.1, C.iceBlue);
      s.addText(a, {
        x: MX + 0.5, y: 3.24 + i * 0.88, w: CW - 0.65, h: 0.62,
        fontSize: 12, color: C.white,
        fontFace: 'Calibri', align: 'left', margin: 0,
      });
    });

    s.addText('Docs-inator · AI Documentation Intelligence', {
      x: MX, y: 10.9, w: CW, h: 0.3,
      fontSize: 9, color: '4A5899',
      fontFace: 'Calibri', align: 'center', margin: 0,
    });
  }

  // ── Download ────────────────────────────────────────────────────────────
  const safeName = productLabel.replace(/[^a-z0-9]+/gi, '-').toLowerCase();
  const filename = `gap-report-${safeName}-${report.reportPeriodStart.slice(0, 10)}.pptx`;
  await pres.writeFile({ fileName: filename });
}
