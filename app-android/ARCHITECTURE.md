# Vendistri Android Architecture

This app is native Android built with Kotlin and Jetpack Compose. The iOS app is the behavior and product reference, but Android code should stay Android-native.

## Core Rules

- Screens are thin: read state, render components, dispatch actions.
- Business rules live in stores/ViewModels and shared helpers.
- `AppState` is the top-level source of truth for user-scoped stores.
- Realtime events update stores, not individual screens.
- Shared UI belongs in `components/`; feature-specific UI belongs under its feature.
- Avoid duplicating task, inventory, route, or permission logic inside composables.

## Package Shape

```text
com.vendistri.operations/
  app/          app root, lifecycle, app-level state
  components/   reusable UI primitives and domain components
  design/       tokens for spacing, colors, typography, shapes
  features/     auth, tasks, work, map, navigation, inventory, settings
  network/      API client, HTTP errors, websocket client
  realtime/     websocket event handling and store updates
  storage/      secure session/cookies, DataStore preferences, restore state
  utils/        focused helpers only
```

Task feature packages should mirror iOS feature ownership:

```text
features/tasks/
  add_stop/     add stop panel, models, API, store
  cancel/       cancel flow store
  delete/       delete flow store
  reassign/     reassign flow store
  reschedule/   reschedule flow store and date helpers
  actions/      Android-native shared task action coordinator/sheet
  detail/       Android-native task detail sheet
  overview/     overview panel parity with iOS activity/overview
```

## Network Boundary

All backend calls go through `network/ApiClient` or feature API classes such as `AuthApi`. Composables must not build backend URLs or manage cookies/CSRF directly.

## Porting Order

1. Auth/session and secure cookie storage
2. App shell and user session sync
3. Tasks list/detail and task action sheets
4. Locations and contact locations
5. Work/Go flow restore state
6. Refill inventory and pickup inventory
7. Mapbox map/navigation
8. Websocket realtime updates
9. Notifications and settings

## Sheet Behavior

Use compact modal sheets by default. Reassign, reschedule, claim, cancel, and delete flows should open at partial height when content fits, expand only when needed, and account for keyboard/IME padding.

Use `VendistriActionSheet` for action flows unless a feature has a specific reason to own a custom modal. Notes and form-heavy sheets must use scrollable content and keep `imePadding()` behavior.
