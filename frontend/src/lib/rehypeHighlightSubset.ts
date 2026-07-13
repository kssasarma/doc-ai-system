import { toText } from 'hast-util-to-text';
import { createLowlight, type LanguageFn } from 'lowlight';
import { visit } from 'unist-util-visit';
import type { Element, ElementContent, Root } from 'hast';

/**
 * A trimmed reimplementation of `rehype-highlight`'s transform, built directly on `lowlight`
 * instead. `rehype-highlight` unconditionally imports lowlight's ~35-language "common" bundle at
 * module scope (`import {common, createLowlight} from 'lowlight'`, then `settings.languages ||
 * common`) — since that binding is referenced in its own source, bundlers can't tree-shake it out
 * even when a `languages` option is always passed, so using it here would ship every common
 * language's grammar regardless of the curated subset actually registered below. Importing
 * `createLowlight` ourselves and never referencing `common`/`all` lets the bundler drop both.
 */
export function rehypeHighlightSubset(languages: Record<string, LanguageFn>, aliases: Record<string, string[]>) {
  const lowlight = createLowlight(languages);
  lowlight.registerAlias(aliases);

  return function transform(tree: Root) {
    visit(tree, 'element', (node: Element, _index, parent) => {
      if (node.tagName !== 'code' || !parent || parent.type !== 'element' || parent.tagName !== 'pre') {
        return;
      }

      const lang = languageOf(node);
      if (lang === false || !lang) return;

      if (!Array.isArray(node.properties.className)) {
        node.properties.className = [];
      }
      if (!node.properties.className.includes('hljs')) {
        node.properties.className.unshift('hljs');
      }

      const text = toText(node, { whitespace: 'pre' });
      try {
        const result = lowlight.highlight(lang, text, { prefix: 'hljs-' });
        if (result.children.length > 0) {
          node.children = result.children as ElementContent[];
        }
      } catch {
        // Unregistered language — leave the block as plain, unhighlighted text.
      }
    });
  };
}

function languageOf(node: Element): false | string | undefined {
  const list = node.properties.className;
  if (!Array.isArray(list)) return undefined;

  for (const raw of list) {
    const value = String(raw);
    if (value === 'no-highlight' || value === 'nohighlight') return false;
    if (value.startsWith('lang-')) return value.slice(5);
    if (value.startsWith('language-')) return value.slice(9);
  }
  return undefined;
}
