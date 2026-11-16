/** Website-only presentation of the canonical property documentation. */
const text = value => ({ type: 'text', value });
const el = (tagName, properties = {}, children = []) => ({ type: 'element', tagName, properties, children });
const content = node => node.value ?? (node.children ?? []).map(content).join('');
const elements = node => (node.children ?? []).filter(child => child.type === 'element');
const findHeading = node => /^h[1-6]$/.test(node.tagName) ? node :
    (node.children ?? []).map(findHeading).find(Boolean);

// Tight Markdown lists have direct inline children; loose lists have paragraphs.
function itemContent(item) {
    const blocks = elements(item);
    if (blocks[0]?.tagName === 'p') return { inline: blocks[0].children, rest: blocks.slice(1) };
    const blockIndex = item.children.findIndex(node => ['ul', 'ol'].includes(node.tagName));
    return blockIndex < 0 ? { inline: item.children, rest: [] } :
        { inline: item.children.slice(0, blockIndex), rest: item.children.slice(blockIndex).filter(node => node.type === 'element') };
}

// Split only prose text nodes; punctuation inside code or links is left intact.
function splitInline(children, delimiter) {
    for (let i = 0; i < children.length; i++) {
        const child = children[i];
        if (child.type !== 'text') continue;
        const match = delimiter.exec(child.value);
        if (!match) continue;
        const before = [...children.slice(0, i), text(child.value.slice(0, match.index))];
        const after = [text(child.value.slice(match.index + match[0].length)), ...children.slice(i + 1)];
        if (before.map(content).join('').trim() && after.map(content).join('').trim()) return [before, after];
    }
}

function referenceTable(list, delimiter, ranges = false) {
    const rows = [];
    for (const item of elements(list)) {
        const { inline, rest } = itemContent(item);
        const pair = splitInline(inline, delimiter);
        if (!pair) return; // Keep unfamiliar structures as authored instead of losing content.
        const cells = [el('th', { scope: 'row' }, pair[0]), el('td', {}, pair[1])];
        if (ranges) {
            const range = rest[0];
            if (rest.length !== 1 || range.tagName !== 'ul' || elements(range).length !== 1) return;
            const rangeInline = itemContent(elements(range)[0]).inline;
            if (!/^Range:\s*/.test(rangeInline.map(content).join(''))) return;
            const rangeContent = rangeInline.map((node, index) => index === 0 && node.type === 'text'
                ? text(node.value.replace(/^Range:\s*/, '')) : node);
            cells.push(el('td', {}, rangeContent));
        } else {
            // Retain generic-parameter explanations and any other nested details.
            cells[1].children.push(...rest);
        }
        rows.push(el('tr', {}, cells));
    }
    if (!rows.length) return;
    return el('table', { className: ['property-reference-table'] }, [
        ...(ranges ? [el('thead', {}, [el('tr', {}, ['Type', 'Format', 'Range'].map(header => el('th', { scope: 'col' }, [text(header)])))])] : []),
        el('tbody', {}, rows),
    ]);
}

const symbols = {
    '✅': ['yes', 'Supported', [el('circle', { cx: 12, cy: 12, r: 9 }), el('path', { d: 'm8 12 2.5 2.5L16 9' })]],
    '❌': ['no', 'Not supported', [el('circle', { cx: 12, cy: 12, r: 9 }), el('path', { d: 'M8 12h8' })]],
    '🆔': ['type-id', 'Type ID only', [el('path', { d: 'M4 4h10l6 6-10 10-6-6Z' }), el('circle', { cx: 9, cy: 9, r: 1 })]],
    '🔑': ['map-key', 'Map key only', [el('circle', { cx: 8, cy: 9, r: 4 }), el('path', { d: 'm11 12 8 8m-3-3 3-3m-6 0 3-3' })]],
    '⤵️': ['nested', 'Nested values only', [el('path', { d: 'M6 4v7a5 5 0 0 0 5 5h8m-4-4 4 4-4 4' })]],
    '🔢': ['note', 'Numeric types', [el('path', { d: 'M5 19v-5m7 5V9m7 10V4' })]],
};

function supportIcon(symbol) {
    const [kind, label, paths] = symbols[symbol];
    return el('span', { className: ['property-support', `property-support--${kind}`], role: 'img', ariaLabel: label, title: label }, [
        el('svg', { xmlns: 'http://www.w3.org/2000/svg', viewBox: '0 0 24 24', width: 20, height: 20,
            fill: 'none', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round', strokeLinejoin: 'round', ariaHidden: 'true' }, paths),
    ]);
}

function replaceSymbols(node) {
    if (!node.children || ['code', 'pre', 'svg'].includes(node.tagName)) return;
    node.children = node.children.flatMap(child => {
        if (child.type !== 'text') { replaceSymbols(child); return [child]; }
        return child.value.split(/(✅|❌|🆔|🔑|⤵️|🔢)/u).filter(Boolean).map(part => symbols[part] ? supportIcon(part) : text(part));
    });
}

export default function propertyReference() {
    return (tree, file) => {
        const path = String(file.path ?? '').replaceAll('\\', '/');
        if (!/\/data-modeling\/properties\/(?:index\.mdx|types\/[^/]+\.mdx)$/.test(path)) return;
        const overview = path.endsWith('/properties/index.mdx');
        let section = '';
        tree.children = tree.children.map(node => {
            const heading = findHeading(node);
            if (heading) section = content(heading).trim().toLowerCase();
            if (node.tagName !== 'ul') return node;
            const first = content(elements(node)[0] ?? {});
            if (/^Kotlin Definition\s*:/.test(first)) return referenceTable(node, /\s*:\s*/) ?? node;
            if (['validation options', 'other options'].includes(section)) return referenceTable(node, /\s+[-–]\s+/) ?? node;
            if (overview && ['characteristic meanings', 'validation'].includes(section)) return referenceTable(node, /\s+[-–]\s+/) ?? node;
            if (['unsigned integers', 'signed integers', 'signed floats'].includes(section)) return referenceTable(node, /\s+-\s+/, true) ?? node;
            return node;
        });
        if (overview) {
            replaceSymbols(tree);
            for (const node of tree.children) {
                if (node.tagName !== 'table') continue;
                for (const group of elements(node)) {
                    for (const row of elements(group)) {
                        elements(row).forEach((cell, index) => {
                            if (group.tagName === 'thead') cell.properties.scope = 'col';
                            else if (index === 0) { cell.tagName = 'th'; cell.properties.scope = 'row'; }
                        });
                    }
                }
            }
        }
        let title = 'Property reference';
        tree.children = tree.children.map(node => {
            const heading = findHeading(node);
            if (heading) title = content(heading).trim();
            if (node.tagName !== 'table') return node;
            return el('div', { className: ['property-table-scroll'], role: 'region', ariaLabel: title, tabIndex: 0 }, [node]);
        });
    };
}
