# Tanvrit Accounting — landing

Static marketing page for **https://accounting.tanvrit.com**. Next.js 15 (App
Router) with `output: "export"` — the build emits a plain static site into
`out/`, deployed to Cloudflare Pages by uploading that directory (same
convention as the `developers/` portal, but with a build step).

No client-side JS frameworks at runtime for content: every section is
server-rendered HTML + hand-written CSS/CSS-modules. First-paint critical
bytes (HTML + CSS) are well under the 50 KB gzip budget; note that the
exported `out/` also ships Next's React runtime chunks — they load deferred
and are **not** required for first paint, since all content is static.

## Develop

```bash
npm install
npm run dev      # http://localhost:3000
```

## Build

```bash
npm run build    # static export → out/
```

## Deploy

Deploys the built `out/` directory to the Cloudflare Pages project
`tanvrit-accounting-landing`:

```bash
npm run deploy                 # next build && wrangler pages deploy out
# or explicitly:
npx wrangler pages deploy out
```

`wrangler.toml` sets `name = "tanvrit-accounting-landing"` and
`pages_build_output_dir = "./out"`. `npm run deploy` honors a
`CLOUDFLARE_PAGES_DIR` override; it defaults to `out`.

Production deploys normally run from CI. For a manual deploy, set:

```
CLOUDFLARE_ACCOUNT_ID   # Tanvrit org account (see developers/ portal config)
CLOUDFLARE_API_TOKEN    # token with "Cloudflare Pages: Edit" on this account
```

**Never commit either value.**

## Custom domain checklist — accounting.tanvrit.com

One-time binding (after the first successful deploy):

1. Cloudflare dashboard → **Workers & Pages** → **tanvrit-accounting-landing**
2. **Custom domains** → **Set up a custom domain** (or **Add**)
3. Enter `accounting.tanvrit.com`
4. Because the `tanvrit.com` zone is already on this Cloudflare account,
   Pages adds the CNAME record automatically — accept the prompt.
5. Wait for the certificate to issue (usually under a minute) and verify
   `curl -sI https://accounting.tanvrit.com` returns `200`.
6. No separate DNS step is needed; the zone stays in the existing
   `tanvrit.com` Cloudflare account.

## Stack notes

- `next`, `react`, `react-dom`, `@types/node`, `@types/react`,
  `@types/react-dom`, `typescript` — devDependencies only, nothing else.
- Fonts: system stack (no downloaded webfonts) to stay under budget.
- Icons: inline hand-drawn SVGs (`components/icons.tsx`).
- `app/globals.css` holds tokens + base styles; each section has a small
  colocated `*.module.css`.
