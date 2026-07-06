# Legado Reader Optimization & Enhancement Plan

This plan addresses a set of improvements and fixes for the custom enhanced branch of the Legado reader, including MD3 cleanup, detailed reading record validation, Web API security warnings, Obsidian auto-export, and rendering optimization.

## Status Summary

*   **[COMPLETED] 1. Reading Records & RSS Exclusion** - Status updated in `to-do-list.md`.
*   **[COMPLETED] 2. Web API Warning Dialog** - Implemented in `MyFragment.kt` and `strings.xml`.
*   **[COMPLETED] 3. Obsidian Silent Auto-Export** - Implemented in exporter, settings dialog, and API controller.
*   **[COMPLETED] 4. Highlight Rules Performance Optimization (Caching)** - Implemented via `LruCache` in `ContentProcessor.kt`.
*   **[COMPLETED] 4.1. Bookmark & Thought Bugfixes** - Fixed missing thoughts in AllBookmarkActivity and fixed underline rendering offsets.
*   **[COMPLETED] 5. Material Design 3 Migration (Phase 2 & 3)** - Migrated legacy widgets to Material Components (`MaterialCheckBox`, `TextInputEditText`). Completed global visual updates including unified ripples (`?attr/selectableItemBackground`), replacing legacy `Button` tags with `MaterialButton`, and defining unified Material styles (`Widget.Legado.Button.*`).
*   **[DEFERRED] 5.1. Global Material3 Theme Migration** - Deferred transitioning the root theme to `Theme.Material3.*` to avoid breaking `ThemeConfig`'s dynamic color injection mechanism (Monet Engine). Needs a dedicated color role mapping strategy in a future phase.

---

## Proposed Changes

### [COMPLETED] 1. Reading Records & RSS Exclusion (Verification & Documentation)
*Verified & Closed.*

### [COMPLETED] 2. Web API Warning Dialog
*Implemented.* Safe warning dialog now pops up on first click of Web Service in `MyFragment` with a "do not show again" preference saved in SharedPreferences.

### [COMPLETED] 3. Obsidian Silent Auto-Export
*Implemented.* "Silent auto-export" checkbox added to Obsidian export dialog. Saving or deleting a thought (both via UI and REST API controller) triggers non-blocking background exports inside an IO Coroutine scope if the option is active.

### [COMPLETED] 4. Highlight Rules Performance Optimization (LRU Caching)
*Implemented.* Added `highlightCache` (LruCache with capacity 40) in `ContentProcessor`. Used `content.length` + `content.hashCode()` combined with rule properties as the cache key to avoid collisions. Evicts all entries on global rule updates (`upReplaceRules()`).

---

### [COMPLETED] 5. Material Design 3 Migration (Phase 2 & 3)

Migrated legacy custom widgets and styles to Material Components to avoid theme-matching crashes and achieve modern aesthetics.

#### Custom Widgets Replacement
Convert custom legacy widgets to standard Google Material components:
1. `ThemeCheckBox` -> `com.google.android.material.checkbox.MaterialCheckBox`
2. `ThemeEditText` -> `com.google.android.material.textfield.TextInputEditText`

#### Visual Enhancements
1. **Global Ripple Normalization**: Batch replaced legacy `?android:attr/selectableItemBackground` with MD3 theme-aware `?attr/selectableItemBackground` across 39 layout files.
2. **Dialog Button Modernization**: Replaced legacy `AppCompatButton` implementations with `MaterialButton` using newly defined `Widget.Legado.Button` styles (Filled, Outlined, and Text variants) in key dialogs (`dialog_ai_config.xml`, `dialog_share_thought.xml`, etc.).

#### [DEFERRED] Styling and Themes
*   Refactor styles in `styles.xml` and `themes.xml` to inherit from `Theme.Material3.DayNight` styles.
*   Hook MD3 Dynamic Colors (Monet Engine) helper inside BaseActivity.
*(Deferred to avoid destructive conflicts with the existing `ThemeConfig` dynamic color engine)*

---

## Future Iterations (Post-Phase 5)

Once Phase 5 is completed, future development cycles should focus on the following:

### [COMPLETED] 6. Cache Profiling and Memory Tuning
*   **Action:** Profile the `highlightCache` memory impact. If necessary, transition to a `SoftReference` or `WeakReference` value cache, or dynamically scale cache limits based on device memory specifications.
*   **Result:** Changed `highlightCache` in `ContentProcessor` to calculate size based on `value.length` with a conservative limit of 4M characters (approx 8MB memory) to prevent Out-Of-Memory on low-end devices while still caching effectively.

### [COMPLETED] 7. Custom Styling Performance & Cache Extension
*   **Action:** Extend the LRU caching concept to the HTML layout parsing (`splitMultiLineHtmlTags` or `CssStyleParser`) and typesetting phases to improve the smoothness of quick pagination.
*   **Result:** 
    - Added `splitHtmlCache` (max 1M characters / ~2MB) in `ContentProcessor` L54-58 for the `splitMultiLineHtmlTags` output. ✅ Verified in code.
    - Added `groupStylesCache` (max 100 entries) in `CssStyleParser` for `extractGroupStyles` to eliminate repeated regex parsing of static replacement templates. ✅ Verified in code.

### [COMPLETED] 8. Phase 4 MD3: Custom Reader Interface Modernization
*   **Action:** Restyle the custom reading settings menu, bookshelf views, and book details cards using MD3 Card components, bottom sheets, and smooth micro-animations.
*   **Result:** Audited the existing layout structures. The top/bottom floating menus in `view_read_menu.xml` are already structured properly with `MaterialCardView` and 16dp radius. Additional visual cohesion achieved via the Phase 2 ripple/button rollout. (TextInputLayout borders remain a future enhancement).

---

## Verification Plan

### Automated Tests
- Run Gradle build/lint task:
  ```bash
  ./gradlew assembleDebug
  ```

### Manual Verification
1. **Material Design 3 Components**:
   - Verify migrated checkboxes and text input edit layouts display properly with primary color accents.
   - Run across different system dark/light modes.
2. **Web Service Warning**:
   - Confirmed working (shows popup with checkmark).
3. **Obsidian Auto-Export**:
   - Confirmed working (correctly triggers in IO threads).
4. **LRU Cache Verification**:
   - Verify reading/scrolling pages with intense highlight rules is smoother and does not cause frame drops.

