# Maryk Website Docs

Starlight docs site for Maryk.

## Important

Part of this site is generated from repository docs before every build. Other pages are hand-authored in `src/content/docs`.

- Source of truth:
  - `core/docs/**`
  - `sql/README.md` and `sql/docs/**`
  - `store/**/README.md` and `store/**/docs/**`
  - `store/rocksdb/documentation/**`
  - `generator/README.md`
  - `cli/docs/commands.md`
  - `app/docs/README.md`
- Sync script: `scripts/sync-core-docs.mjs`
- Generated targets: `src/content/docs/**`

Edit generated pages through their source files and run `yarn sync`.
The mapping in `scripts/sync-core-docs.mjs` identifies which pages are generated.

Hand-authored pages include the landing page, introduction pages, tutorials and tradeoff guidance. Edit those directly.

## Deployment and URLs

Publishing runs only through a manual workflow dispatch or a published GitHub
Release, matching the installer release process. A release publishes its exact
tag; a manual run publishes the selected ref. Pull requests validate the site
without publishing, and ordinary main-branch pushes do not deploy it.

The default site is `https://maryk.io/`, served at the domain root (`base: '/'`).
Local development and preview use the root path too, without a `/maryk/` prefix.
The Pages workflow supplies its configured HTTPS origin and base path, treating
an empty custom-domain base path as `/`. Assets, canonical URLs and the sitemap
are generated from these settings. Every build also checks the root canonical
URL and verifies local page, image, stylesheet and script references against
the output files, rejecting stale `/maryk/` URLs or missing assets.

## Local development

From `website/`:

Requires Node.js 22.23.4 or newer and Yarn 1.22.22.

```bash
yarn install
yarn dev
```

`yarn dev` synchronizes generated documentation before Astro starts.

Build:

```bash
yarn build
```

Preview:

```bash
yarn preview
```

## Generator checks

Run `yarn test` after changing the sync script or its mappings, then `yarn build`.
The tests cover source-relative links, generated routes and code-fence preservation.
The build synchronizes sources and checks that the resulting MDX compiles.

## Diagrams

Use fenced `mermaid` blocks when a relationship, decision, or lifecycle is clearer
as a diagram. Include Mermaid `accTitle` and `accDescr` metadata so the graphic
has an accessible name and description. Edit generated pages through their source
Markdown, as with other documentation.

The MarkdownContent override loads the locally bundled Mermaid renderer only on
pages containing diagrams. It renders flowcharts and sequences in the active site
theme and rerenders when the theme changes. The original code block remains
readable if rendering is unavailable; successfully rendered diagrams show only
the graphic and its caption. Wide diagrams scroll within their own container on
small screens.

## Brand exports

Canonical sources and generation instructions live in [branding](../branding/README.md).
The website uses the vector master. `yarn dev`, `yarn start`, and `yarn build`
copy it into ignored `src/generated/brand/` and `public/brand/` directories before
Astro starts. Do not edit or commit those generated copies. This website step
uses Node's standard library and does not require the desktop build tools.

The homepage's dedicated hero illustration is kept separately as a single
optimized WebP at `src/assets/maryk-hero.webp` (873 × 629px, with transparency). It is
page artwork, not an alternate logo or an input to desktop icon generation.

## Property reference presentation

Reference sections for commands, shortcuts, configuration, encodings and storage
layouts use tables in their source Markdown. Keep procedures numbered and retain
bullets for checklists, feature lists and narrative guidance. Edit generated pages
through their mapped source files so the website and repository docs stay aligned.

The website-only `scripts/rehype-property-reference.mjs` transform turns repeated
property definition/option lists and numeric ranges into semantic tables. Simple two-column references omit repetitive header rows; numeric comparisons
and the support matrix retain column headers. Short usage lists, prose, links
and code examples stay intact. The Properties overview
uses accessible inline SVG status icons: green for supported, red for unsupported,
amber for map keys, purple for type IDs, and blue for nested values. The source
Markdown and its emoji remain unchanged; the transform runs during rendering, so
synchronization never overwrites the presentation. Wide tables scroll in their
own keyboard-focusable region on small screens. Run `yarn test` and `yarn build`
when changing the transform.

## Social sharing and agent exports

Every `yarn build` runs `scripts/generate-site-extras.mjs` after Astro renders
the pages, then validates the resulting artifacts. Use `yarn build` followed by
`yarn preview` to inspect these production outputs; `yarn dev` does not generate
the post-build exports.

- Each content page gets a 1200 × 630 JPEG social card, Open Graph image metadata,
  and a large-image Twitter card. Titles come from the page metadata; descriptions
  use the authored description or a short excerpt of its introduction. The shared
  design uses the existing SVG logo and optimized WebP hero. Content-hashed image
  names update when the rendered image changes.
- Each page has an `index.md` counterpart, for example
  `/data-modeling/properties/index.md`. Exports contain canonical/source links,
  tables, all code tabs and Mermaid source, without navigation or copy controls.
  Internal documentation links lead to the other Markdown exports.
- `/llms.txt` groups all exported pages by subject with generated descriptions.
  HTML pages advertise their Markdown counterpart with `rel="alternate"` and the
  index with `rel="describedby"`.
- `/robots.txt` allows crawling and points to the existing sitemap.

All generated cards, Markdown files, metadata and indexes live in ignored `dist/`.
Do not commit export variants or duplicate the documentation in a separate AI
source file. Only the generator/template and existing brand masters are stored
in Git. The build checks image formats/dimensions, discovery metadata, exports,
source mappings and internal links; `yarn test` checks code/tab preservation,
tables, navigation and card generation. Social platforms need a deployed URL
to fetch the cards, and may cache a previously fetched preview.
