# maimaid static assets

`backend/scripts/build-static-bundle.ts` generates this Worker's `public/` tree.
The deployment is atomic: clients read `manifest.json`, then download its
content-addressed bundle and image assets from the same Worker origin.
Image URLs use Cloudflare's `/cdn-cgi/image/f=auto/` transformation and retain
the original Worker paths as fallbacks. Native clients advertise only formats
their platform image decoder supports; browsers provide their own image Accept
header.

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
