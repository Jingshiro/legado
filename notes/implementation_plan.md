# Legado Reader Optimization & Enhancement Plan

This plan addresses a set of improvements and fixes for the custom enhanced branch of the Legado reader, including MD3 cleanup, detailed reading record validation, Web API security warnings, Obsidian auto-export, and rendering optimization.

## Status Summary

*   **[COMPLETED] 1. Reading Records & RSS Exclusion** - Status updated in `to-do-list.md`.
*   **[COMPLETED] 2. Web API Warning Dialog** - Implemented in `MyFragment.kt` and `strings.xml`.
*   **[COMPLETED] 3. Obsidian Silent Auto-Export** - Implemented in exporter, settings dialog, and API controller.
*   **[COMPLETED] 4. Highlight Rules Performance Optimization (Caching)** - Implemented via `LruCache` in `ContentProcessor.kt`.
*   **[COMPLETED] 4.1. Bookmark & Thought Bugfixes** - Fixed missing thoughts in AllBookmarkActivity and fixed underline rendering offsets.
*   **[COMPLETED] 5. Material Design 3 Migration (Phase 2 & 3)** - Refactored custom widgets to Material Design 3.

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

### 5. Material Design 3 Migration (Phase 2 & 3) [CURRENT TARGET]

Migrate legacy custom widgets and styles to Material Components to avoid theme-matching crashes and achieve modern aesthetics.

#### [MODIFY] [material_design_migration_plan.md](file:///D:/myprojects/legado/notes/InProgress/material_design_migration_plan.md)
*   Update migration checklist for Phase 2 & 3.

#### [MODIFY] Custom Widgets Replacement
Convert custom legacy widgets to standard Google Material components:
1. `ThemeCheckBox` -> `com.google.android.material.checkbox.MaterialCheckBox`
2. `ThemeEditText` -> `com.google.android.material.textfield.TextInputEditText`
3. `io.legado.app.ui.widget.text.TextInputLayout` -> `com.google.android.material.textfield.TextInputLayout`

#### [MODIFY] Styling and Themes
*   Refactor styles in `styles.xml` and `themes.xml` to inherit from `Theme.Material3.DayNight` styles.
*   Hook MD3 Dynamic Colors (Monet Engine) helper inside BaseActivity.

---

## Future Iterations (Post-Phase 5)

Once Phase 5 is completed, future development cycles should focus on the following:

### [COMPLETED] 6. Cache Profiling and Memory Tuning
*   **Action:** Profile the `highlightCache` memory impact. If necessary, transition to a `SoftReference` or `WeakReference` value cache, or dynamically scale cache limits based on device memory specifications.
*   **Result:** Changed `highlightCache` in `ContentProcessor` to calculate size based on `value.length` with a conservative limit of 4M characters (approx 8MB memory) to prevent Out-Of-Memory on low-end devices while still caching effectively.

### [COMPLETED] 7. Custom Styling Performance & Cache Extension
*   **Action:** Extend the LRU caching concept to the HTML layout parsing (`splitMultiLineHtmlTags` or `CssStyleParser`) and typesetting phases to improve the smoothness of quick pagination.
*   **Result:** 
    - Added `splitHtmlCache` (max 1M characters / ~2MB) in `ContentProcessor` for the `splitMultiLineHtmlTags` output.
    - Added `groupStylesCache` (max 100 entries) in `CssStyleParser` for `extractGroupStyles` to eliminate repeated regex parsing of static replacement templates.

### 8. Phase 4 MD3: Custom Reader Interface Modernization
*   **Action:** Restyle the custom reading settings menu, bookshelf views, and book details cards using MD3 Card components, bottom sheets, and smooth micro-animations.

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

