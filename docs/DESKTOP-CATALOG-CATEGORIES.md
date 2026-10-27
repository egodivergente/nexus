# Desktop catalogue categories

The default Image and Video catalogue shows general generation and image-editing models.
Specialized operations remain accessible through the Image/Video tools filters; 3D stays in its own intent.
A text search also includes specialized operations, so searching for a tool does not require finding its category first.

`CatalogParity.categoryOf` defines presentation categories. `displayModalities` corrects the displayed modality
of Crun Kling V3 Turbo and Atlas Video Analysis. Neither helper rewrites provider contracts, request fields or payloads.
Family hiding uses the displayed modality, including these corrected entries.

Personal model selections are stored in the user's existing `NexusDesktop/state.json` hidden-route list.
They are recoverable from the catalogue's hidden-model panel and remain outside version control.
Changing visibility does not delete generation history, media, conversations or credentials.

Validation: `node desktop/tools/test-catalog-parity.js` and `python desktop/tools/test-nexus-ten.py`.
The browser suite uses an isolated offline profile and does not submit provider generations.
