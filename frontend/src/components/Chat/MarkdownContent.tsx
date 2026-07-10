import React, { useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeHighlight from 'rehype-highlight';
import { Check, Copy } from 'lucide-react';

// A curated language set relevant to the kind of product/install/config docs this app deals
// with, registered explicitly instead of relying on rehype-highlight's default "common" set —
// this doesn't reduce bundle size (rehype-highlight imports "common" internally regardless of
// what's passed here, a limitation of its API), but it does mean only these languages are ever
// matched/rendered, which is the part actually under this app's control.
import javascript from 'highlight.js/lib/languages/javascript';
import typescript from 'highlight.js/lib/languages/typescript';
import python from 'highlight.js/lib/languages/python';
import java from 'highlight.js/lib/languages/java';
import bash from 'highlight.js/lib/languages/bash';
import json from 'highlight.js/lib/languages/json';
import xml from 'highlight.js/lib/languages/xml';
import yaml from 'highlight.js/lib/languages/yaml';
import sql from 'highlight.js/lib/languages/sql';
import csharp from 'highlight.js/lib/languages/csharp';
import powershell from 'highlight.js/lib/languages/powershell';
import ini from 'highlight.js/lib/languages/ini';
import dockerfile from 'highlight.js/lib/languages/dockerfile';
import diff from 'highlight.js/lib/languages/diff';
import markdown from 'highlight.js/lib/languages/markdown';
import plaintext from 'highlight.js/lib/languages/plaintext';

const HIGHLIGHT_LANGUAGES = {
  javascript, typescript, python, java, bash, json, xml, yaml, sql,
  csharp, powershell, ini, dockerfile, diff, markdown, plaintext,
};

const HIGHLIGHT_ALIASES = {
  bash: ['shell', 'sh', 'zsh'],
  xml: ['html'],
  yaml: ['yml'],
  javascript: ['js', 'jsx'],
  typescript: ['ts', 'tsx'],
  csharp: ['cs'],
  powershell: ['ps1', 'ps'],
  dockerfile: ['docker'],
  ini: ['properties', 'toml', 'conf'],
};

function PreBlock({ children }: { children?: React.ReactNode }) {
  const [copied, setCopied] = useState(false);
  const preRef = useRef<HTMLPreElement>(null);

  const handleCopy = async () => {
    const text = preRef.current?.textContent ?? '';
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch { /* clipboard unavailable */ }
  };

  return (
    <div className="relative group/code">
      <pre ref={preRef}>{children}</pre>
      <button
        onClick={handleCopy}
        className="absolute top-1.5 right-1.5 flex items-center gap-1 px-1.5 py-1 rounded bg-gray-700/80 text-gray-200 text-[10px] opacity-0 group-hover/code:opacity-100 transition-opacity hover:bg-gray-600 z-10"
        title="Copy code"
      >
        {copied ? <Check size={12} /> : <Copy size={12} />}
      </button>
    </div>
  );
}

/**
 * Shared answer/citation renderer: GitHub-flavored Markdown (tables, strikethrough, task lists),
 * syntax-highlighted fenced code blocks, and a per-block copy-to-clipboard button — previously
 * every code block rendered as unstyled `<pre><code>` with no way to copy just that snippet.
 */
export default function MarkdownContent({ content }: { content: string }) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      rehypePlugins={[[rehypeHighlight, { languages: HIGHLIGHT_LANGUAGES, aliases: HIGHLIGHT_ALIASES }]]}
      components={{ pre: PreBlock }}
    >
      {content}
    </ReactMarkdown>
  );
}
