# NEXUS Studio design system

## Product and audience

NEXUS Studio is a local-first creative workspace in Spanish. It lets an experienced creator choose a media model, describe an intent, add compatible files, configure the model, see a source-aware tariff, and explicitly generate. It must feel calm, precise and trustworthy; it should never make an unverified price or an external generation look confirmed.

## Visual foundations

- Use only Segoe UI Variable Text / Display with Segoe UI fallbacks.
- Use the existing charcoal surfaces: background `#14171c`, sidebar `#1b1e25`, panel `#20242c`, raised `#272c36`, border `#353b48`.
- Use off-white `#f1f3f7` for primary text, `#a9b1c0` for secondary, and `#818b9e` for tertiary text.
- Use teal `#63d4c6` only for the current primary action, selected state and input focus. Preserve the dark, restrained appearance; do not introduce gradients beyond the existing quiet accent tint.
- Keep radii between 8 and 16 px and spacing on the 4 px rhythm. Favour flat, quiet panels with a thin border over glass effects or oversized shadows.
- Motion is short and optional: 120–180 ms, with a reduced-motion equivalent.

## Creation-flow rules

- The model, prompt, inputs, configuration, price status and generate button must read in that order.
- Price language remains source-aware: “coste pendiente” until a verified quote exists; the generation CTA must state its blocked reason in plain Spanish.
- Keep a useful preview or recent-results column on wide desktop, but the working form remains the visual priority.
- Preserve the inline NEXUS mark in the sidebar; use no replacement logo, initials or generic mark.
- Improve density through hierarchy and grouping rather than shrinking type below 11 px or hiding necessary state.
- Every control remains keyboard reachable with an obvious focus indicator.

## Design target for this round

Faithfully reproduce the existing desktop creation screen first. Any subsequent redesign should retain the above identity while making readiness, price review and the creation path more immediately legible.
