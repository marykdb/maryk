# Maryk website brand assets

`maryk-logo.svg` is the vector master used by the website header and favicon.
Keep this editable source here; do not commit generated image variants.

The website's `yarn dev`, `yarn start`, and `yarn build` commands copy the master
into ignored `website/src/generated/brand/` and `website/public/brand/` paths.
This synchronization uses Node's standard library and needs no desktop build.

The landing-page illustration is a separate, tightly cropped, transparent
873 × 629 WebP at `website/src/assets/maryk-hero.webp` (quality 82).
The SVG and WebP together are about 140 KiB. Social cards are derived from these
sources during the website build as optimized 1200 × 630 JPEGs in ignored
`website/dist/social/`; no generated cards or favicon copies belong in Git.
