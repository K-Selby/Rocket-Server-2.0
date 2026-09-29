# Shared portal assets

This directory contains files used by more than one Rocket Pub portal.

- `images/` holds the Rocket Pub logos, favicon, and wallpaper.
- `menus/` holds the customer-facing food and Christmas menu files.

The Next.js Rocket Portal publishes this directory at `http://localhost:8000/assets/`.
All three portals use relative `/assets/...` URLs. A different asset host can be
set with `NEXT_PUBLIC_SHARED_ASSETS_URL` when required.

Keep application CSS and JavaScript inside the portal that owns it. Keep runtime
data such as `rocket_integration.db` directly in `data/`.
