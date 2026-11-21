# Universe bridge contract (v0)

Andromeda is fully functional without Universe. Nothing about Universe is hardcoded: no package names,
endpoints, tokens or credentials are in the source.

## Implemented
- `UniverseBridge` interface (`isAvailable()`, `open(path)`), implemented by `IntentUniverseBridge`.
- Settings > Universe: you supply the Universe package name and (optionally) a URI scheme.
- Detection: Universe counts as available if the package has a launcher activity visible to Andromeda.
- Open: with a path and scheme, fires `ACTION_VIEW` on `<scheme>://<path>` restricted to that package;
  otherwise launches the app normally (subject to Focus blocking).

## Contract Universe apps should honour
- Expose a launcher activity.
- Handle `<scheme>://<path>` deep links via an exported activity with a VIEW/BROWSABLE intent filter.

## Not implemented (future, needs design with Universe)
Authentication, shared theme/settings, widgets, search integration, inter-app messaging.
Any of these should be added behind new interfaces in the bridge, using documented intents or a
signature-protected ContentProvider, never raw tokens in shared storage.
