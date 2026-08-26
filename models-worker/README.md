# maimaid vision models

`scripts/build-model-assets.mjs` builds this Worker's `public/` tree from the
Android ONNX resources and the iOS CoreML packages. The generated tree is
deployed to the `models.rhythmeta.org` custom domain.

The manifest is a backwards-compatible JSON array. Each entry contains a
`filename`, a SHA-256 digest, and the exact byte `size`. Model filenames are
versioned so immutable edge caching remains safe for future releases.
