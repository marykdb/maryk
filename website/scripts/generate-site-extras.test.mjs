import assert from 'node:assert/strict';
import test from 'node:test';
import { fromHtml } from 'hast-util-from-html';
import sharp from 'sharp';
import { llmsIndex, markdownURL, pageMarkdown, socialCard, wrapText } from './generate-site-extras.mjs';

const page = html => ({ title: 'Reference', description: 'A typed API reference.', canonical: 'https://maryk.io/example/',
    source: 'https://github.com/marykdb/maryk/blob/main/core/docs/example.md', route: 'example/',
    body: fromHtml(html, { fragment: true }), isHome: false });

test('Markdown preserves code, all tab panels, and diagram source without interface controls', () => {
    const input = page(`<div><div class="tablist-wrapper"><a id="one" role="tab">Person.kt</a><a id="two" role="tab">Store.kt</a></div>
      <div role="tabpanel" aria-labelledby="one"><pre data-language="kotlin"><code><div class="ec-line">val name = &quot;&lt;T&gt;{value}&quot;</div><div class="ec-line"></div><div class="ec-line">println(name)</div></code></pre><button>Copy to clipboard</button></div>
      <div role="tabpanel" aria-labelledby="two" hidden><pre data-language="kotlin"><code><div class="ec-line">execute(Person.get(key))</div></code></pre></div>
      <pre data-language="mermaid"><code><div class="ec-line">flowchart TB</div><div class="ec-line">Model --&gt; Store</div></code></pre></div>`);
    const output = pageMarkdown(input, new Set([input.canonical]));
    assert.ok(output.includes('### Person.kt'));
    assert.ok(output.includes('### Store.kt'));
    assert.ok(output.includes('```kotlin\nval name = "<T>{value}"\n\nprintln(name)\n```'));
    assert.ok(output.includes('execute(Person.get(key))'));
    assert.ok(output.includes('```mermaid\nflowchart TB\nModel --> Store\n```'));
    assert.ok(!output.includes('Copy to clipboard'));
    assert.ok(!output.includes('<div'));
});

test('Markdown retains every table row and readable support states', () => {
    const output = pageMarkdown(page('<table><tbody><tr><th scope="row"><code>required</code></th><td>default true</td></tr><tr><th scope="row"><code>final</code></th><td>default false</td></tr></tbody></table><span role="img" aria-label="Map key only"><svg></svg></span>'), new Set());
    assert.match(output, /Property \/ option.*Details/);
    assert.ok(output.includes('`required`'));
    assert.ok(output.includes('`final`'));
    assert.ok(output.includes('Map key only'));
    assert.ok(!output.includes('<svg'));
});

test('internal links point to Markdown counterparts with fragments and sources stay discoverable', () => {
    const input = page('<p><a href="../other/#types">Types</a> <a href="#local">Local</a> <a href="https://example.org/">External</a></p>');
    const output = pageMarkdown(input, new Set([input.canonical, 'https://maryk.io/other/']));
    assert.ok(output.includes('https://maryk.io/other/index.md#types'));
    assert.ok(output.includes('https://maryk.io/example/index.md#local'));
    assert.ok(output.includes('https://example.org/'));
    assert.ok(output.includes(`Canonical: ${input.canonical}`));
    assert.ok(output.includes(`Source: ${input.source}`));
    assert.equal(markdownURL('https://maryk.io/'), 'https://maryk.io/index.md');
});

test('llms index covers every page once under the relevant documentation group', () => {
    const pages = ['', 'what/', 'data-modeling/properties/', 'stores/rocksdb/', 'guides/test/', 'querying/', 'serialization/', 'app/'].map(route => ({ ...page(''), title: route || 'Maryk', route, canonical: `https://maryk.io/${route}` }));
    const index = llmsIndex(pages);
    for (const item of pages) assert.equal(index.split(`](${markdownURL(item.canonical)})`).length, 2);
    assert.ok(index.includes('## Data modeling and property types'));
    assert.ok(index.includes('## Storage engines and operations'));
    assert.equal(index, llmsIndex(pages));
});

test('social cards are compact opaque 1200 × 630 JPEGs with safely escaped titles', async () => {
    const pixel = await sharp({ create: { width: 1, height: 1, channels: 4, background: '#123847' } }).png().toBuffer();
    const image = await socialCard({ ...page(''), title: 'Queries <T> & "History"' }, pixel, pixel);
    const metadata = await sharp(image).metadata();
    assert.equal(metadata.format, 'jpeg');
    assert.equal(metadata.width, 1200);
    assert.equal(metadata.height, 630);
    assert.equal(metadata.hasAlpha, false);
    assert.ok(image.length < 160_000);
    assert.equal(wrapText('One two three four five six seven eight', 10, 2).length, 2);
    assert.ok(wrapText('One two three four five six seven eight', 10, 2)[1].endsWith('…'));
});
