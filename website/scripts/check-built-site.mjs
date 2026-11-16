import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { fromHtml } from 'hast-util-from-html';
import sharp from 'sharp';

const dist = fileURLToPath(new URL('../dist/', import.meta.url));
function files(directory) {
    return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
        const path = resolve(directory, entry.name);
        return entry.isDirectory() ? files(path) : [path];
    });
}
const home = readFileSync(resolve(dist, 'index.html'), 'utf8');
const canonical = home.match(/<link\b[^>]*rel="canonical"[^>]*href="([^"]+)"/);
assert.ok(canonical, 'Homepage must have a canonical URL');
const site = new URL(canonical[1]);
assert.equal(site.protocol, 'https:', 'Published canonical URLs must use HTTPS');
assert.equal(site.pathname, '/', 'The website must be built at the domain root');
const pages = files(dist).filter(path => path.endsWith('.html'));
let checked = 0;
const missing = [];
const find = (node, predicate) => predicate(node) ? node : (node.children ?? []).map(child => find(child, predicate)).find(Boolean);
const checkedImages = new Set();
let exportsChecked = 0;
for (const page of pages) {
    const html = readFileSync(page, 'utf8');
    const pageURL = new URL(relative(dist, page).replaceAll('\\', '/'), site);
    if (!page.endsWith('/404.html')) {
        const tree = fromHtml(html);
        const metadata = key => find(tree, node => node.tagName === 'meta' && (node.properties.property === key || node.properties.name === key))?.properties.content;
        const card = metadata('og:image');
        assert.ok(card, `Missing social preview: ${page}`);
        assert.equal(metadata('twitter:image'), card);
        assert.equal(metadata('twitter:card'), 'summary_large_image');
        assert.ok(metadata('og:image:alt'));
        assert.equal(metadata('og:image:width'), '1200');
        assert.equal(metadata('og:image:height'), '630');
        assert.equal(new URL(card).origin, site.origin);
        assert.equal(new URL(card).protocol, 'https:');
        const cardPath = resolve(dist, `.${new URL(card).pathname}`);
        if (!checkedImages.has(cardPath)) {
            const image = await sharp(cardPath).metadata();
            assert.equal(image.width, 1200);
            assert.equal(image.height, 630);
            assert.equal(image.format, 'jpeg');
            checkedImages.add(cardPath);
        }
        const alternate = find(tree, node => node.tagName === 'link' && node.properties.type === 'text/markdown')?.properties.href;
        assert.ok(alternate, `Missing Markdown discovery link: ${page}`);
        const markdown = readFileSync(resolve(dist, `.${new URL(alternate).pathname}`), 'utf8');
        const canonicalPage = find(tree, node => node.tagName === 'link' && node.properties.rel?.includes('canonical'))?.properties.href;
        assert.ok(markdown.includes(`Canonical: ${canonicalPage}\n`));
        const discovery = find(tree, node => node.tagName === 'link' && node.properties.rel?.includes('describedby'))?.properties.href;
        assert.equal(discovery, `${site.origin}/llms.txt`);
        assert.ok(markdown.includes('Source: https://github.com/marykdb/maryk/blob/main/'));
        // The export links are generated outside code examples; ignore fenced sample Markdown.
        const prose = markdown.replace(/^(`{3,}|~{3,})[^\n]*\n[\s\S]*?^\1\s*$/gm, '');
        for (const link of prose.matchAll(/\]\((https?:\/\/[^)\s]+)\)/g)) {
            const url = new URL(link[1]);
            if (url.origin !== site.origin) continue;
            const target = resolve(dist, `.${decodeURIComponent(url.pathname)}`);
            assert.ok(existsSync(target) && statSync(target).isFile(), `Broken Markdown link in ${alternate}: ${url.href}`);
        }
        exportsChecked++;
    }
    for (const tag of html.matchAll(/<(?:a|link|script|img|source)\b[^>]*>/g)) {
        // Canonicals describe the public route, including the host's special 404 route.
        if (/\brel="canonical"/.test(tag[0])) continue;
        for (const attribute of tag[0].matchAll(/\b(?:href|src)="([^"]+)"/g)) {
            const url = new URL(attribute[1].replaceAll('&amp;', '&'), pageURL);
            if (url.origin !== site.origin) continue;
            assert.ok(!url.pathname.startsWith('/maryk/'), `Old project prefix in ${page}: ${url.pathname}`);
            const target = resolve(dist, `.${decodeURIComponent(url.pathname)}`);
            assert.ok(relative(dist, target) !== '..' && !relative(dist, target).startsWith('../'), 'Local URL escapes the build');
            const exists = existsSync(target) && statSync(target).isFile() || existsSync(resolve(target, 'index.html'));
            if (!exists) missing.push(`${relative(dist, page)} → ${url.pathname}`);
            checked++;
        }
    }
}
assert.deepEqual(missing, [], 'Broken internal links or assets in the built website');
const llms = readFileSync(resolve(dist, 'llms.txt'), 'utf8');
assert.equal((llms.match(/^- \[/gm) ?? []).length, exportsChecked, 'Every exported page must appear in llms.txt');
for (const link of llms.matchAll(/\]\((https?:\/\/[^)\s]+)\)/g)) {
    const url = new URL(link[1]);
    assert.equal(url.origin, site.origin);
    assert.ok(existsSync(resolve(dist, `.${url.pathname}`)), `Broken llms.txt link: ${url.href}`);
}
assert.ok(readFileSync(resolve(dist, 'robots.txt'), 'utf8').includes(`Sitemap: ${site.origin}/sitemap-index.xml`));
console.log(`Verified ${exportsChecked} Markdown exports, social metadata, and agent discovery files.`);
console.log(`Verified ${pages.length} pages and ${checked} local links/assets at ${site.origin}/`);
