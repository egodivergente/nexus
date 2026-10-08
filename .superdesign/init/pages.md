# Key page dependency trees

## canvas (existing desktop creation flow)

Entry: `desktop/ui/app.js` → `renderCanvas`

Dependencies:

- `desktop/ui/index.html`
  - `desktop/ui/style.css`
  - `desktop/ui/studio.css`
  - `desktop/ui/desktop-design.css`
  - `desktop/ui/engine.js`
  - `desktop/ui/local-bridge.js`
  - `desktop/ui/app.js`
    - `desktop/ui/focus.js`
    - `desktop/ui/polish.js`
    - `desktop/ui/workspace.js`
    - `desktop/ui/studio.js`
    - `desktop/ui/flow-assets.js`
    - `desktop/ui/flow-ux.js`
    - `desktop/ui/desktop-design.js`

The rendering branch is selected when `view === 'canvas'`. It shows either the model-free starter state or the selected-model editor with prompt, media fields, regular and advanced parameters, verified quote, readiness feedback, generation action, and result preview.

## catalog (existing desktop model browser)

Entry: `desktop/ui/app.js` → `renderCatalog`

Dependencies:

- Shared shell listed above.
- Model catalog provided by `desktop/ui/local-bridge.js` and normalized by `desktop/ui/engine.js`.
- `desktop/ui/studio.js` adds the modal picker and route details.

## library and history (existing desktop outputs)

Entry: `desktop/ui/workspace.js` → `renderPC`

Dependencies:

- Shared shell listed above.
- `desktop/ui/studio.js` adds output detail, comparison and reuse actions.
- `desktop/ui/workspace.css` and `desktop/ui/desktop-design.css` style the grid and filters.
