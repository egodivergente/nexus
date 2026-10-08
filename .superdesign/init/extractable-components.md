# Extractable interface components

The current desktop shell is a single local HTML document coordinated by monolithic render functions. It is not a portable component yet, so it is deliberately not extracted into the Superdesign component library for this first faithful baseline. That avoids replacing the inline NEXUS mark with an approximation.

## CreateWelcome

- Source: `desktop/ui/desktop-design.js`
- Category: basic
- Description: first-use creation and assistant welcome panel.
- Extractable props: `kind`, `onIdeaSelect`.
- Hardcoded: NEXUS spark illustration and three Spanish starting ideas.

## ModelCard

- Source: `desktop/ui/app.js` inside `renderCatalog`
- Category: basic
- Description: model family or variant selection card with source-aware tariff label.
- Extractable props: `name`, `provider`, `description`, `price`, `isFavorite`, `onOpen`.
- Hardcoded: existing action labels and local price disclosure language.

## CreationSendbar

- Source: `desktop/ui/app.js` inside `renderCanvas`
- Category: basic
- Description: quote/review/generate confirmation area.
- Extractable props: `quoteStatus`, `quoteAmount`, `isReady`, `onCheckPrice`, `onGenerate`.
- Hardcoded: prior-to-send cost disclosure and Flow account phrasing.
