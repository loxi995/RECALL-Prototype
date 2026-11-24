# Hosting the Privacy Policy and Terms

Google Play requires the Privacy Policy to be reachable at a public URL that Google can crawl. The cheapest and easiest way to host it is GitHub Pages, straight from this repo. Here is the exact sequence.

## Option A — host from this repo via GitHub Pages (free, 5 minutes)

1. Open the repo on github.com: https://github.com/loxi995/RECALL-Prototype
2. Click **Settings** → **Pages** (in the left sidebar).
3. Under **Build and deployment**, pick **Source: Deploy from a branch**.
4. Branch: `main`, folder: `/legal`.
5. Click **Save**. GitHub will give you a URL like `https://loxi995.github.io/RECALL-Prototype/` after a minute or two.

The documents will then be reachable as:

- Privacy Policy: `https://loxi995.github.io/RECALL-Prototype/privacy-policy`
- Terms of Service: `https://loxi995.github.io/RECALL-Prototype/terms-of-service`

That first URL is what you paste into Google Play Console → App Content → Privacy Policy.

GitHub Pages renders `.md` files to HTML automatically — no build step needed.

## Option B — host on Vercel (also free, custom domain-friendly)

1. Sign in at vercel.com with GitHub.
2. New project → import `loxi995/RECALL-Prototype`.
3. Root directory: `legal`. Framework preset: **Other**.
4. Deploy. Vercel gives you `https://recall-prototype-<hash>.vercel.app`.
5. (Optional) add your own domain later (e.g. `recallaudio.com`) in Vercel → Domains.

## Option C — host on your own domain later

Once you have `recallaudio.com`:

1. Buy the domain (Namecheap/Porkbun: ~$12/yr).
2. Point its DNS at either GitHub Pages or Vercel.
3. Final URLs become:
   - `https://recallaudio.com/privacy`
   - `https://recallaudio.com/terms`

Having the policy on your own domain looks more professional in the Play listing but is not required by Google.

## After hosting

Update the Play Store listing:

- Play Console → Policy → App content → **Privacy Policy**: paste the live URL.
- In the app itself, when a user opens the disclosure dialog, we show the Terms/Privacy text inline — the link is optional in-app but required on the store page.

## Before public launch

Have a lawyer in your jurisdiction review `terms-of-service.md`. The draft here is a solid starting point but is not legal advice and does not replace counsel. Expect $300–$1,500 for a one-shot review.
