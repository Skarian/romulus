# Aged Brass UI

## Goal

Romulus should feel warm, tactile, and slightly luxurious. The app icon already suggests Roman brass, parchment, and clay. The in-app UI should follow that mood without becoming theatrical or heavy.

This is not a full design system. It is a short visual-direction note for making the current app more coherent and more beautiful.

## Core Mood

- Warm, natural, and refined.
- Slightly game-adjacent in spirit, but not neon, sci-fi, or arcade.
- Closer to an artifact table, old cartridge label, or brass instrument panel than a finance product.

## Palette Model

Keep the palette small:

- `paper`: the main light surface color.
- `ink`: the main light text and icon color.
- `stone`: muted surfaces, dividers, and borders in light mode.
- `brass`: the hero accent for selected states and important actions.
- `clay`: a warm secondary accent.
- `olive`: a quiet contrast accent used sparingly.
- `night`: the main dark background and dark surfaces.
- `sand`: the main dark text and icon color.

## Color Usage

### Light mode

- Backgrounds should feel like pale limestone or parchment.
- The page background should stay airy and clean; it must not drift into muddy sepia.
- Cards should feel slightly lifted from the background, not identical to it.
- Primary text and most icons should use a dark ink tone.
- Borders should use a softened stone tone.
- Brass should be saved for call-to-action buttons, selected nav/items, and important highlights.

### Dark mode

- Backgrounds should feel like smoky night stone with a mineral cast, not flat brown.
- Cards should be a little lighter and warmer than the page background.
- Background, row, header, and shell layers must stay clearly separated at a glance.
- Primary text and most icons should use a soft sand tone.
- Borders should stay visible but subdued.
- Brass should brighten the UI, not flood it.

## Text Rules

- Most text should be neutral, not colored.
- Primary text uses the main ink/sand role.
- Secondary text, helper text, and quiet icons should use a softened neutral.
- Brass is not for paragraphs.

## Border Rules

- Cards, table headers, rows, and contained icon buttons should all have a quiet border.
- Borders are part of the tactile look. Without them, warm colors flatten too quickly.
- Selected states can tint the border slightly toward brass.

## Typography

- Use a warm serif feeling for screen headings and major section titles.
- Keep body copy and controls readable and clean with a sans style.
- The typography should add character without becoming costume.
- Screen titles should stay compact enough that toolbar actions still fit comfortably on phone-sized layouts.

## Component Direction

### Shell chrome

- Bottom navigation should feel contained and branded, not like a stock bar.
- The selected item should clearly pick up the brass accent.
- In dark mode, the selected tab label should use the same soft sand text tone as page headers instead of the darker on-brass text color.
- The nav bar should sit on its own distinct surface instead of blending into the page background.
- That nav surface should stay quiet and neutral; it should not compete with the brass action language.
- In dark mode, the nav slab should be darker than the table/header slabs so shell chrome does not feel washed out.

### Cards

- Section cards should share one visual treatment across Setup, Files, and Settings.
- They should feel inset and tactile, with soft corners and quiet borders.
- Normal structural cards should stay neutral. They should not look selected or highlighted just because they exist.
- Settings cards should keep the same quiet outline language as the rest of the app instead of going borderless in dark mode.

### Buttons and toggles

- Standard in-page utility buttons should use the softer secondary container treatment.
- The single most important call to action on a screen can take the brass primary treatment.
- Disabled buttons still need to read as buttons in both themes.
- Switch off-states should stay clearly visible and should use warm-neutral tracks instead of disappearing into surrounding surfaces.

### Icon buttons

- Small action icons should live in subtle containers rather than floating bare whenever possible.
- Important actions should use a clearly brighter brass treatment so they read as actions, not just another icon.
- Informational icons can use a lighter, borderless treatment so they do not outweigh the content they annotate.

### Tables and rows

- Table headers and rows should feel like part of the same family as the cards.
- Table headers should be visibly distinct from rows, but they should stay neutral and understated rather than reading like pills or highlighted chips.
- Rows should be easier to scan and have more depth than flat surfaces.
- Primary row text should use an explicit title style instead of falling back to default body text.
- In dark mode, background, row, and header surfaces should each occupy their own clear layer.
- In dark mode, headers should read as the darkest slab above the page background, and row surfaces should feel clean and neutral rather than mossy or muddy.
- Shared row content should default to vertical centering unless a specific screen truly needs top alignment.
- If a screen only has one meaningful column, it should prefer a page title and clean rows over a decorative header bar.

### Page headers

- Toolbar titles must never crowd core actions on phone-sized screens.
- Long titles should be capped to a fixed share of the row and ellipsized before they can push actions off screen.

### Dialog text

- Dialog titles should use the same authored title voice as other important in-app labels.
- Dialog body text should be styled explicitly instead of relying on default Material text.
- Dialog and menu containers should stay on the same warm house surface family as the rest of the app, not slip toward lavender Material defaults.
- In dark mode, dialogs should lift far enough from the page that they remain obvious even with a subtle scrim.
- If a screen exposes informative empty-state or option-label text outside dialogs, it should also use explicit styling instead of raw defaults.
- Dialog action labels should follow the same explicit hierarchy rather than using raw default button text.
- Search-field labels, common button labels, and other repeated affordance text should use explicit typography often enough that default Text styling no longer feels like a separate visual voice.

### Status pills

- Status pills should feel intentionally designed, not like default chips.
- Running and completed pills must keep strong foreground/background contrast in both themes.
- Neutral fallback states should still read as part of the warm palette, not fall back to cool blue-gray pills in dark mode.
- They should still preserve clear semantic differences between running, done, failed, and cancelled.

## Anti-goals

- No neon gamer HUD.
- No flat yellow overload.
- No giant custom component framework.
- No decorative Roman cosplay typography all over the app.
- No brass or gold outlines on ordinary structural containers.

## Rollout Order

1. Theme colors, typography, and shapes.
2. Shared panel, table, and icon-button primitives.
3. Shell chrome.
4. Setup, Home, Files, Downloads, and Settings cleanup.
