import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import test from 'node:test';
import { createMarkdownProcessor } from '@astrojs/markdown-remark';
import propertyReference from './rehype-property-reference.mjs';

const processor = await createMarkdownProcessor({ rehypePlugins: [propertyReference], syntaxHighlight: false, smartypants: false });
const baseline = await createMarkdownProcessor({ syntaxHighlight: false, smartypants: false });
const sourceRoot = new URL('../../core/docs/properties/', import.meta.url);
const render = (md, route) => processor.render(md, { fileURL: new URL(`/website/src/content/docs/data-modeling/properties/${route}.mdx`, 'file:') });

// Exercise real source documents so new/multiline definitions and examples cannot silently disappear.
test('all property types get definition and option tables, preserving examples and links', async () => {
    for (const name of readdirSync(new URL('types/', sourceRoot))) {
        const md = readFileSync(new URL(`types/${name}`, sourceRoot), 'utf8');
        const original = (await baseline.render(md)).code;
        const result = (await render(md, `types/${name.slice(0, -3)}`)).code;
        assert.ok(result.includes('property-reference-table'), name);
        assert.ok(result.includes('<th scope="row"><code>required</code>'), name);
        assert.ok(!result.includes('<th scope="col">Option</th>'), name);
        assert.deepEqual(result.match(/<pre>[\s\S]*?<\/pre>/g), original.match(/<pre>[\s\S]*?<\/pre>/g), `Examples: ${name}`);
        assert.deepEqual(result.match(/href="[^"]*"/g), original.match(/href="[^"]*"/g), `Links: ${name}`);
        // Every source inline code value, including generic types, defaults, and limits, survives.
        assert.deepEqual(result.match(/<code>[\s\S]*?<\/code>/g), original.match(/<code>[\s\S]*?<\/code>/g), `Inline values: ${name}`);
    }
});

test('numeric ranges become comparison columns and nested generic explanations survive', async () => {
    const number = (await render(readFileSync(new URL('types/number.md', sourceRoot), 'utf8'), 'types/number')).code;
    assert.equal((number.match(/<th scope="col">Range<\/th>/g) ?? []).length, 3);
    assert.ok(number.includes('18446744073709551615'));
    const map = (await render(readFileSync(new URL('types/map.md', sourceRoot), 'utf8'), 'types/map')).code;
    assert.ok(map.includes('K for type of key definition'));
    assert.ok(map.includes('V for type of value definition'));
    assert.match(map, /<ul>\s*<li>Value<\/li>\s*<\/ul>/); // Short usage list remains a list.
});

test('overview replaces all six status symbols with accessible SVGs and keeps conditional meanings', async () => {
    const md = readFileSync(new URL('README.md', sourceRoot), 'utf8');
    const result = (await render(md, 'index')).code;
    assert.equal((result.match(/class="property-support /g) ?? []).length, (md.match(/✅|❌|🆔|🔑|⤵️|🔢/gu) ?? []).length);
    assert.doesNotMatch(result, /✅|❌|🆔|🔑|⤵️|🔢/u);
    for (const label of ['Supported', 'Not supported', 'Type ID only', 'Map key only', 'Nested values only', 'Numeric types']) {
        assert.ok(result.includes(`role="img" aria-label="${label}"`), label);
    }
    assert.equal((result.match(/class="property-table-scroll"/g) ?? []).length, 3);
    assert.ok(result.includes('<th scope="row"><strong>Keyable</strong>'));
    assert.ok(md.includes('✅')); // The source stays ordinary Markdown with its original symbols.
});

test('unrelated pages, examples, and unfamiliar lists remain as authored', async () => {
    const md = '## Validation Options\n\n- `required` - default true\n\n```text\n✅ - sample\n```\n\n- Unpaired item\n';
    const unrelated = await processor.render(md, { fileURL: new URL('file:///website/src/content/docs/stores/index.mdx') });
    assert.equal(unrelated.code, (await baseline.render(md)).code);
    const result = (await render(md, 'types/example')).code;
    assert.ok(result.includes('<pre><code class="language-text">✅ - sample'));
    assert.ok(result.includes('<li>Unpaired item</li>'));
});
