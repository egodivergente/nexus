# Theme source

## Compact token summary

- Background: `#14171c`; sidebar: `#1b1e25`; panel: `#20242c`; raised: `#272c36`; border: `#353b48`.
- Main text: `#f1f3f7`; muted text: `#a9b1c0`; soft text: `#818b9e`.
- Base accent: teal `#63d4c6`; OLED overrides the dark surfaces; optional amber and violet accents exist in base CSS.
- Danger: `#ffab9a`; focus ring is the accent.
- Typography: Segoe UI Variable Text / Display, with Segoe UI fallback. Base size: `13px` in the desktop enhancement.
- Rhythm: 4, 8, 12, 16, 24, 32 px. Radii: 8, 12, 14, 16 px. Primary desktop button: 36 px minimum height.
- Motion: 120 ms interaction, 180 ms standard; all motion disabled under `prefers-reduced-motion`.

## Source files

The desktop theme is layered in this order: `style.css`, `assistant.css`, `workspace.css`, `studio.css`, `flow-ux.css`, and the final desktop refinement `desktop-design.css`. The focused creation screen must preserve these tokens and never introduce a new font, palette, or decorative gradient.

### `desktop/ui/desktop-design.css` token block

```css
:root{--nx-bg:#14171c;--nx-sidebar:#1b1e25;--nx-panel:#20242c;--nx-raised:#272c36;--nx-border:#353b48;--nx-text:#f1f3f7;--nx-muted:#a9b1c0;--nx-soft:#818b9e;--nx-radius:14px;--nx-shadow:0 18px 56px #0006}
body.nx-desktop{--bg:var(--nx-bg);--panel:var(--nx-panel);--surface:var(--nx-raised);--line:var(--nx-border);--text:var(--nx-text);--muted:var(--nx-muted);--surface-0:var(--nx-bg);--surface-1:var(--nx-panel);--surface-2:var(--nx-raised);--surface-3:#303744;--surface-4:#3a4352;--focus-ring:var(--accent);font-family:'Segoe UI Variable Text','Segoe UI',sans-serif;font-size:13px;background:var(--bg);color:var(--text);height:100dvh;-webkit-font-smoothing:antialiased}
body.nx-desktop.oled{--nx-bg:#090b0f;--nx-sidebar:#12151b;--nx-panel:#191d25;--nx-raised:#232833}
```

### `desktop/ui/studio.css` token block

```css
:root{--surface-0:#0c1014;--surface-1:#11171d;--surface-2:#172028;--surface-3:#202b34;--surface-4:#293641;--space-1:4px;--space-2:8px;--space-3:12px;--space-4:16px;--space-5:24px;--space-6:32px;--radius-sm:8px;--radius-md:12px;--radius-lg:16px;--shadow-pop:0 12px 40px #0009;--duration-fast:120ms;--duration-standard:180ms;--ease-out:cubic-bezier(.2,.8,.2,1);--focus-ring:#83e0d4}
```
