# Maryk brand assets

Keep the editable source artwork here, rather than a collection of sizes and
numbered iterations:

- `maryk-logo.svg`: vector master for the documentation website and SVG favicon.
- `maryk-logo.webp`: optimized 1024px version of the generated raster artwork,
  used for the desktop data-browser app icon. Preserve this source's dimensional shading; do not
  replace it with a rasterized copy of the SVG.

The raster source is about 79 KiB (1024 × 1024, quality 94 WebP with alpha),
reduced from the 1254px, roughly 1 MiB generated PNG. Keep only this optimized
raster and the vector source in Git. All PNG/ICO/ICNS sizes are derived exports.
The 1024px source covers the largest app icon without upscaling the artwork.

## Desktop icons and image exports

From the repository root:

```sh
./gradlew :app:generateBrandAssets
```

`GenerateBrandAssets.java` renders the vector with the Skia renderer already
provided by Compose. It decodes WebP through Skia and places the raster artwork on a padded rounded
gradient background for the desktop app. No Python, Node, image editor, or additional
rendering dependency is needed for this task.

All outputs go into ignored `build/generated/brand/`:

- `app/maryk-app.icns`: macOS, including Retina sizes up to 1024px.
- `app/maryk-app.ico`: Windows, with 16–256px entries.
- `app/maryk-app.png`: 512px Linux/package icon.
- `resources/maryk/app-icon.png`: the app's runtime window/Dock icon.
- `logo/`: transparent PNG exports and browser favicon sizes from the vector.

App resource processing and native packaging generate their icons automatically.
Changing either source or the generator invalidates the Gradle task. Generated
icons are not checked in; edit the background/padding recipe in the generator
when changing the desktop icon treatment.

## Website

The website's `yarn dev`, `yarn start`, and `yarn build` commands copy the vector
into ignored `website/src/generated/brand/` and `website/public/brand/` paths.
This copy step uses only Node's standard library. Astro uses the copied SVG for
its header logo, with `/brand/favicon.svg` as the favicon.

The landing-page illustration is stored separately at
`website/src/assets/maryk-hero.webp`: a tightly cropped, transparent 873 × 629
WebP (quality 82). Keep this one optimized source in Git; Astro generates the
website delivery assets during the build.

The WebP and SVG are distinct source artworks, not interchangeable exports. All
resized or platform-specific files must be generated from the appropriate source.
