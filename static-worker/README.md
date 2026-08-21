# maimaid static assets

`backend/scripts/build-static-bundle.ts` generates this Worker's `public/` tree.
The deployment is atomic: clients read `manifest.json`, then download its
content-addressed bundle and image assets from the same Worker origin.

Required build environment:

- `MAIMAID_API_URL`
- `MAIMAID_INTERNAL_JOB_TOKEN`
- `MAIMAID_STATIC_ASSETS_URL`

Required GitHub Actions secrets:

- `MAIMAID_API_URL`
- `MAIMAID_INTERNAL_JOB_TOKEN`
- `MAIMAID_STATIC_ASSETS_URL`
- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_ACCOUNT_ID`

Configure the `MAIMAID_STATIC_ASSETS_URL` hostname as this Worker's custom
domain. Run the repository workflow to generate, deploy, verify, and notify the
API.
