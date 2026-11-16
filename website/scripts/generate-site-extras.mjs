import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { fromHtml } from 'hast-util-from-html';
import { toMdast } from 'hast-util-to-mdast';
import { toMarkdown } from 'mdast-util-to-markdown';
import { gfmToMarkdown } from 'mdast-util-gfm';
import sharp from 'sharp';
import { map } from './sync-core-docs.mjs';

const siteRoot = fileURLToPath(new URL('../', import.meta.url));
const text = value => ({ type: 'text', value });
const element = (tagName, properties = {}, children = []) => ({ type: 'element', tagName, properties, children });
export const plainText = node => node.value ?? (node.children ?? []).map(plainText).join('');
const classes = node => node.properties?.className ?? [];
const find = (node, predicate) => predicate(node) ? node : (node.children ?? []).map(child => find(child, predicate)).find(Boolean);
function collect(node, predicate, result = []) {
    if (predicate(node)) result.push(node);
    for (const child of node.children ?? []) collect(child, predicate, result);
    return result;
}
const meta = (tree, key) => find(tree, node => node.tagName === 'meta' && (node.properties.name === key || node.properties.property === key))?.properties.content;
const compact = value => value.replace(/\s+/g, ' ').trim();
const excerpt = (value, length = 210) => value.length <= length ? value : `${value.slice(0, length - 1).replace(/\s+\S*$/, '')}…`;
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
export const markdownURL = canonical => new URL('index.md', canonical).href;

/** Export content, not site navigation or client-side controls. Keep every code tab. */
export function pageMarkdown(page, pageURLs) {
    const body = structuredClone(page.body);
    const tabs = new Map(collect(body, node => node.properties?.role === 'tab').map(node => [node.properties.id, compact(plainText(node))]));
    function clean(node) {
        if (node.type !== 'element' && node.type !== 'root') return node;
        const props = node.properties ?? {};
        if (['script', 'style', 'link', 'button', 'svg'].includes(node.tagName) || classes(node).includes('sl-anchor-link') ||
            classes(node).includes('tablist-wrapper') || props.role === 'tablist' || props.dataPagefindIgnore !== undefined) return null;
        if (props.role === 'img' && props.ariaLabel) return text(` ${props.ariaLabel} `);
        if (node.tagName === 'pre') {
            const lines = collect(node, child => classes(child).includes('ec-line'));
            const source = (lines.length ? lines.map(plainText).join('\n') : plainText(node)).replaceAll('\u00a0', ' ');
            const language = props.dataLanguage;
            return element('pre', {}, [element('code', language ? { className: [`language-${language}`] } : {}, [text(source.endsWith('\n') ? source : `${source}\n`)])]);
        }
        for (const key of ['href', 'src']) {
            if (!props[key]) continue;
            const url = new URL(props[key], page.canonical);
            const hash = url.hash;
            url.hash = '';
            if (key === 'href' && pageURLs.has(url.href)) props[key] = `${markdownURL(url)}${hash}`;
            else props[key] = new URL(props[key], page.canonical).href;
        }
        node.children = (node.children ?? []).map(clean).filter(Boolean);
        if (node.tagName === 'table' && !node.children.some(child => child.tagName === 'thead')) {
            const firstCell = find(node, child => child.tagName === 'th');
            const label = firstCell && plainText(firstCell).includes('Kotlin') ? ['Representation', 'Definition'] : ['Property / option', 'Details'];
            node.children.unshift(element('thead', {}, [element('tr', {}, label.map(value => element('th', {}, [text(value)])))]));
        }
        if (props.role === 'tabpanel') {
            const label = tabs.get(Array.isArray(props.ariaLabelledBy) ? props.ariaLabelledBy[0] : props.ariaLabelledBy);
            if (label) node.children.unshift(element('h3', {}, [text(label)]));
        }
        return node;
    }
    const cleaned = clean(body);
    const mdast = toMdast({ type: 'root', children: cleaned.children });
    const markdown = toMarkdown(mdast, { extensions: [gfmToMarkdown()], fences: true, bullet: '-', emphasis: '_', rule: '-' });
    return `# ${page.title}\n\nCanonical: ${page.canonical}\nSource: ${page.source}\n\n${page.isHome ? `${page.description}\n\n` : ''}${markdown}`;
}

function files(directory) {
    return readdirSync(directory, { withFileTypes: true }).flatMap(entry => entry.isDirectory() ? files(resolve(directory, entry.name)) : [resolve(directory, entry.name)]);
}

export function readPages(dist) {
    const sources = new Map(map.map(([target, source]) => [
        target.replace(/^src\/content\/docs\//, '').replace(/(?:^|\/)index\.mdx$/, '/').replace(/\.mdx$/, '/').replace(/^\//, ''),
        source.startsWith('../') ? source.slice(3) : `core/docs/${source}`,
    ]));
    return files(dist).filter(path => path.endsWith('.html') && !path.endsWith('/404.html')).sort().map(path => {
        const html = readFileSync(path, 'utf8');
        const tree = fromHtml(html);
        const canonical = find(tree, node => node.tagName === 'link' && node.properties.rel?.includes('canonical'))?.properties.href;
        const body = find(tree, node => classes(node).includes('sl-markdown-content'));
        if (!canonical || !body) throw new Error(`Missing canonical or documentation content in ${path}`);
        const title = meta(tree, 'og:title');
        const introductionBlocks = [];
        for (const block of body.children ?? []) {
            if (find(block, node => /^h[1-6]$/.test(node.tagName))) break;
            introductionBlocks.push(...collect(block, node => node.tagName === 'p' && compact(plainText(node)).length > 10));
        }
        const introductions = introductionBlocks.slice(0, 2);
        const intro = introductions[0];
        const authored = meta(tree, 'description');
        const description = authored && authored !== 'Kotlin Multiplatform data modeling, versioned storage, and querying.'
            ? authored : excerpt(compact(intro ? plainText(intro) : `${title} — Maryk documentation.`));
        const route = new URL(canonical).pathname.slice(1);
        const indexSource = `website/src/content/docs/${route}index.mdx`;
        const source = sources.get(route) ?? (existsSync(resolve(siteRoot, '..', indexSource)) ? indexSource : `website/src/content/docs/${route.slice(0, -1)}.mdx`);
        if (!existsSync(resolve(siteRoot, '..', source))) throw new Error(`Missing source for ${canonical}: ${source}`);
        return { path, html, tree, canonical, body, title, description, socialDescription: excerpt(introductions.map(node => compact(plainText(node))).join(' ') || description, 320), isHome: route === '', route,
            source: `https://github.com/marykdb/maryk/blob/main/${source}` };
    });
}

export function wrapText(value, width, maxLines) {
    const words = compact(value).split(' ');
    const lines = [];
    let line = '';
    for (const word of words) {
        if (line && `${line} ${word}`.length > width) { lines.push(line); line = word; }
        else line = line ? `${line} ${word}` : word;
    }
    if (line) lines.push(line);
    if (lines.length > maxLines) {
        lines.length = maxLines;
        lines[maxLines - 1] = `${lines[maxLines - 1].replace(/[.,:;]$/, '')}…`;
    }
    return lines;
}

export async function socialCard(page, hero, logo) {
    const titleLines = page.isHome ? ['One model.', 'Every layer.'] : wrapText(page.title, 23, 4);
    const size = page.isHome ? 68 : 52;
    const start = page.isHome || titleLines.length < 3 ? 245 : 225;
    const lines = titleLines.map((line, index) => `<text x="56" y="${start + index * (size + 8)}" font-size="${size}" font-weight="700" fill="${page.isHome && index === 1 ? '#a4dce9' : '#f3f8fc'}">${escape(line)}</text>`).join('');
    const descriptionStart = page.isHome ? 450 : Math.max(300, start + (titleLines.length - 1) * (size + 8) + 52);
    const descriptionSize = page.isHome ? 22 : 24;
    const descriptionStep = page.isHome ? 30 : 32;
    const descriptionLines = page.isHome ? 3 : Math.min(5, Math.floor((555 - descriptionStart) / descriptionStep) + 1);
    const description = wrapText(page.isHome ? 'Kotlin Multiplatform data modeling, querying, and versioned storage.' : (page.socialDescription || page.description), page.isHome ? 45 : 43, descriptionLines)
        .map((line, index) => `<text x="56" y="${descriptionStart + index * descriptionStep}" font-size="${descriptionSize}" fill="#c2d4e5">${escape(line)}</text>`).join('');
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630"><defs><linearGradient id="bg" x2="1" y2=".7"><stop stop-color="#123847"/><stop offset=".55" stop-color="#1e2b43"/><stop offset="1" stop-color="#38315e"/></linearGradient><radialGradient id="glow"><stop stop-color="#76c4d8" stop-opacity=".2"/><stop offset="1" stop-color="#76c4d8" stop-opacity="0"/></radialGradient></defs><rect width="1200" height="630" fill="url(#bg)"/><ellipse cx="980" cy="300" rx="360" ry="300" fill="url(#glow)"/><g font-family="DejaVu Sans, Arial, sans-serif"><text x="160" y="99" font-size="56" font-weight="700" fill="#f3f8fc">Maryk</text>${lines}${description}<text x="56" y="588" font-size="21" fill="#a4dce9">maryk.io</text></g></svg>`;
    return sharp(Buffer.from(svg)).composite([{ input: logo, left: 46, top: 24 }, { input: hero, left: 700, top: 175 }]).jpeg({ quality: 84, mozjpeg: true }).toBuffer();
}

export function llmsIndex(pages) {
    const groups = [
        ['Start here', p => ['', 'what/', 'getting-started/', 'why/', 'tradeoffs/', 'in-practice/'].includes(p.route) || p.route.startsWith('tutorials/')],
        ['Practical guides', p => p.route.startsWith('guides/')],
        ['Data modeling and property types', p => p.route.startsWith('data-modeling/')],
        ['Queries, history, and live updates', p => p.route.startsWith('querying/')],
        ['Storage engines and operations', p => p.route.startsWith('stores/')],
        ['Serialization', p => p.route.startsWith('serialization/')],
        ['Tools', () => true],
    ];
    const remaining = new Set(pages);
    let out = '# Maryk\n\n> Kotlin Multiplatform data modeling, serialization, typed querying, and versioned storage.\n\n';
    out += 'Start with What is Maryk, Getting Started, and the First Store Tutorial. For implementation details, follow the relevant property, request, or store reference. Platform support and capabilities vary by storage engine.\n\n';
    out += 'All links below point to Markdown generated from the same documentation build as the website. Each page includes its canonical website URL and repository source. Code examples, constraints, and migration guidance are preserved. Confirm compatibility with the Maryk release used by your project.\n\n';
    for (const [title, match] of groups) {
        const selected = [...remaining].filter(match);
        if (!selected.length) continue;
        out += `## ${title}\n\n`;
        for (const page of selected) {
            remaining.delete(page);
            out += `- [${page.title.replaceAll('[', '').replaceAll(']', '')}](${markdownURL(page.canonical)}): ${excerpt(page.description, 125)}\n`;
        }
        out += '\n';
    }
    return out;
}

export async function generateSiteExtras(dist = resolve(siteRoot, 'dist')) {
    const pages = readPages(dist);
    const pageURLs = new Set(pages.map(page => page.canonical));
    const heroBytes = readFileSync(resolve(siteRoot, 'src/assets/maryk-hero.webp'));
    const logoBytes = readFileSync(resolve(siteRoot, '../branding/maryk-logo.svg'));
    const hero = await sharp(heroBytes).resize(460).png().toBuffer();
    const logo = await sharp(logoBytes).resize(96).png().toBuffer();
    let imageBytes = 0;
    rmSync(resolve(dist, 'social'), { recursive: true, force: true });
    mkdirSync(resolve(dist, 'social'), { recursive: true });
    for (const page of pages) {
        const image = await socialCard(page, hero, logo);
        const hash = createHash('sha256').update(image).digest('hex').slice(0, 16);
        const imagePath = `/social/${hash}.jpg`;
        writeFileSync(resolve(dist, `.${imagePath}`), image);
        imageBytes += image.length;
        const imageURL = new URL(imagePath, page.canonical).href;
        const imageAlt = page.isHome ? 'Maryk — One model. Every layer. Kotlin Multiplatform data toolkit.' : `${page.title} — Maryk documentation`;
        let html = page.html.replace(/<!-- maryk-generated:start -->[\s\S]*?<!-- maryk-generated:end -->\n?/g, '');
        // Replace existing generic descriptions/types rather than adding conflicting duplicate tags.
        for (const [key, value] of [['description', page.description], ['og:description', page.description], ['og:type', page.isHome ? 'website' : 'article'], ['og:locale', 'en_US']]) {
            html = html.replace(new RegExp(`<meta\\b[^>]*(?:name|property)="${key}"[^>]*>`, 'g'), `<meta ${key.startsWith('og:') ? 'property' : 'name'}="${key}" content="${escape(value)}"/>`);
        }
        const tags = [
            ['property', 'og:image', imageURL], ['property', 'og:image:secure_url', imageURL],
            ['property', 'og:image:type', 'image/jpeg'], ['property', 'og:image:width', '1200'], ['property', 'og:image:height', '630'], ['property', 'og:image:alt', imageAlt],
            ['name', 'twitter:title', page.title], ['name', 'twitter:description', page.description], ['name', 'twitter:image', imageURL], ['name', 'twitter:image:alt', imageAlt],
        ].map(([attribute, key, value]) => `<meta ${attribute}="${key}" content="${escape(value)}"/>`).join('\n');
        html = html.replace('</head>', `<!-- maryk-generated:start -->${tags}\n<link rel="alternate" type="text/markdown" href="${escape(markdownURL(page.canonical))}" title="Markdown"/>\n<link rel="describedby" href="${new URL('/llms.txt', page.canonical).href}" type="text/plain"/>\n<!-- maryk-generated:end -->\n</head>`);
        writeFileSync(page.path, html);
        const output = resolve(dirname(page.path), 'index.md');
        writeFileSync(output, pageMarkdown(page, pageURLs));
    }
    writeFileSync(resolve(dist, 'llms.txt'), llmsIndex(pages));
    const origin = new URL(pages.find(page => page.isHome).canonical).origin;
    writeFileSync(resolve(dist, 'robots.txt'), `User-agent: *\nAllow: /\n\nSitemap: ${origin}/sitemap-index.xml\n`);
    console.log(`Generated ${pages.length} Markdown pages and social cards (${(imageBytes / 1024 / 1024).toFixed(1)} MiB), llms.txt, and robots.txt.`);
    return pages;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await generateSiteExtras();
