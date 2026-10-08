# Desktop destinations

NEXUS Studio is a local single-document application. `desktop/ui/index.html` is the only URL entry point; `view` in `desktop/ui/app.js` selects the rendered destination.

| View key | Sidebar label | Renderer | Purpose |
| --- | --- | --- | --- |
| `canvas` | Lienzo / Crear | `renderCanvas` | Prompt, model configuration, media, price check and generation confirmation. |
| `catalog` | Modelos | `renderCatalog` | Browse families and variants, inspect tariffs and choose a model. |
| `history` | Historial | `renderHistory` | Generation activity and refreshable states. |
| `library` | Biblioteca | `renderLibrary` / `renderPC` | Results, filters, detail and comparison. |
| `settings` | Ajustes | `renderSettings` | Appearance, accounts and local preferences. |
| `assistant` | Asistente | `renderAssistant` | Guided prompting and creative assistance, mounted by `assistant.js`. |

The application starts on `catalog`. Selecting a model transitions to `canvas` through `openModel(id)`.
