# Shared portal assets

This directory contains files used by more than one Rocket Pub portal.

- `images/` holds the Rocket Pub logos, favicon, and wallpaper.
- `menus/` holds the customer-facing food menu PDF.

The combined Flask server publishes this directory at `http://localhost:8000/assets/`.
The Booking and Customer portals use relative `/assets/...` URLs. The Staff Portal
uses the same address and can override it with `NEXT_PUBLIC_SHARED_ASSETS_URL` when
the Flask server is hosted somewhere else.

Keep application CSS and JavaScript inside the portal that owns it. Keep runtime
data such as `rocket_integration.db` directly in `data/`.
