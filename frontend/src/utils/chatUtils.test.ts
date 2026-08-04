import { describe, it, expect } from 'vitest';
import { closeUnterminatedCodeFence } from './chatUtils';

describe('closeUnterminatedCodeFence', () => {
  it('leaves plain prose untouched', () => {
    const text = 'Just an explanation with no code at all.';
    expect(closeUnterminatedCodeFence(text)).toBe(text);
  });

  it('leaves a properly closed code block untouched', () => {
    const text = 'Example:\n```js\nconst x = 1;\n```\nDone.';
    expect(closeUnterminatedCodeFence(text)).toBe(text);
  });

  it('closes a fence still open mid-stream', () => {
    const text = 'Example:\n```js\nconst x = 1;';
    expect(closeUnterminatedCodeFence(text)).toBe('Example:\n```js\nconst x = 1;\n```');
  });

  it('leaves two complete code blocks untouched', () => {
    const text = '```js\na();\n```\ntext\n```py\nb()\n```';
    expect(closeUnterminatedCodeFence(text)).toBe(text);
  });

  it('closes the fence when the second of two blocks is still open', () => {
    const text = '```js\na();\n```\ntext\n```py\nb()';
    expect(closeUnterminatedCodeFence(text)).toBe('```js\na();\n```\ntext\n```py\nb()\n```');
  });
});
