# Just Cal architecture

Just Cal is a single-module, offline Android application. Compose renders lifecycle-aware StateFlow screen state owned by Hilt ViewModels. Room implementation details stay behind DiaryRepository; deterministic nutrition calculation and input validation belong to domain code.

## Navigation and the global action

Home, Diary and Settings each have a saved Navigation 3 back stack, including entry-scoped ViewModels and saveable UI state. Switching destinations preserves the other stacks. Back pops the current stack; Back at a secondary root returns to Home. NavDisplay handles transitions and predictive back.

Diary history rows and the direct “Choose a day” calendar both open the same Day Detail destination. That destination uses the selected local calendar date and the same diary presentation and repository as Home. Day Detail is retained when switching tabs.

The separate floating FAB is an action, not a fourth destination. It is visible on Home, Diary root and Day Detail, and hidden in Settings and the food editor. It opens manual Add Food with today's date from Home/Diary root or the viewed date from Day Detail. This date-carrying action is the boundary for a future Add Food launcher; scans, camera and AI are not implemented.

The navigation uses Just Cal's own restrained Compose styling. Telegram is only a reference for geometry and interaction; no Telegram source or assets are included.

## Diary and calculations

Room persists individual food entries: original nutrition per 100 g and consumed weight in integer hundredths, source, recorded local epoch day and creation timestamp. Recorded days do not change when the device time zone changes. Home refreshes the active local date on resume and across midnight.

Portions and daily totals are derived with exact BigDecimal arithmetic, rounded only for presentation. There is no persisted Day entity or mutable daily-total cache. Adding, editing or deleting an entry updates Room flows and the affected summaries.

## Settings and local profile

GoalPreferences owns the existing DataStore preferences file. It atomically saves optional local display name, calorie target, independently optional protein/fat/carbohydrate targets and appearance preference. There are no accounts, authentication or cloud profiles.

The calorie target is a required positive whole number, initially an editable 2000 kcal placeholder. Macro targets are independently nullable positive values in integer hundredths of grams. A blank field removes that target; zero is rejected, never used as an absence sentinel. Existing preferences without macro keys mean no macro targets.

Home, Diary and Day Detail observe the same saved settings reactively. Configured macros display consumed / target; unconfigured macros display consumed grams alone. Unsaved Settings drafts survive tab switches and configuration changes but do not alter displayed targets or appearance until saved.
