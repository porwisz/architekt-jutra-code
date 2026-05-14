# UI Mockups: Reviews Plugin

**Generated**: 2026-04-07
**Task Path**: `.maister/tasks/development/2026-04-07-reviews-plugin`
**Feature Type**: New Feature (greenfield plugin)

---

## Overview

### UI Requirements

- **ReviewsAdmin** — full-page admin table of all reviews with client-side status filter and per-row approve/reject actions
- **ProductReviewsTab** — product-scoped tab with review list and a submit form below
- **ProductRatingBadge** — compact (~60px) read-only badge showing average rating and count
- All pages wrapped in `<div className="tc-plugin">`, no custom CSS, only `tc-*` classes

### Integration Strategy

**Decision**: Follow the warehouse plugin aesthetic exactly — `tc-table` for lists, `tc-section` for grouping, `tc-flex` + `tc-input` + `tc-select` for forms, `tc-primary-button` / `tc-ghost-button--danger` for actions, `tc-badge` for the badge component.

**Rationale**: The warehouse and box-size plugins are the canonical templates specified in the task. The platform loads `plugin-ui.css` from the host; using any class not in that sheet would produce unstyled output.

---

## Existing Layout Analysis

### Application Structure

Each plugin is a self-contained iframe. There is no host-side navigation within the iframe — the plugin owns its full viewport. The only "layout" is the `tc-plugin` root wrapper with inline `padding` and optional `maxWidth`.

**Key Reference Files**:

- Full CRUD page: `plugins/warehouse/src/pages/WarehousePage.tsx`
- Product-scoped read-only tab: `plugins/warehouse/src/pages/ProductStockTab.tsx`
- Product-scoped form tab: `plugins/box-size/src/pages/ProductBoxTab.tsx`
- Compact badge: `plugins/warehouse/src/pages/ProductAvailability.tsx`

### Identified Patterns

- **Page chrome**: `<div className="tc-plugin" style={{ padding: "1rem", maxWidth: N }}>` — always present
- **Section grouping**: `<section className="tc-section"><h2>...</h2>...</section>` — used in WarehousePage for visual separation
- **Toolbar row**: `<div className="tc-flex" style={{ marginBottom: "1rem" }}>` — holds form inputs and action buttons in a row
- **Tables**: `<table className="tc-table">` with `<thead>`, `<tbody>`, optional `<tfoot>`; right-aligned numeric columns use `align="right"`
- **Loading state**: `if (loading) return <p>Loading...</p>` (or `return null` for badge)
- **Error state**: `<p className="tc-error">{error}</p>` inline above content
- **Primary action**: `<button className="tc-primary-button">`
- **Destructive action**: `<button className="tc-ghost-button tc-ghost-button--danger">`
- **Badge**: `<span className="tc-badge tc-badge--success|tc-badge--danger">`
- **Label+input row**: flex row with fixed-width label span + `tc-input` / `tc-select` — from ProductBoxTab

---

## Mockups

### Mockup 1: ReviewsAdmin — Default View (all reviews, no filter)

**Context**: `menu.main`, `path="/"`, `RENDER` context. Renders full-page in the host sidebar iframe slot. `maxWidth: 900` keeps the table readable.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  <div className="tc-plugin" style={{ padding:"1rem", maxWidth:900 }}>       │
│                                                                              │
│  <h1>Reviews</h1>                                                            │
│  [tc-error shown here if error state]                                        │
│                                                                              │
│  <section className="tc-section">                                            │
│    <div className="tc-flex" style={{ marginBottom:"1rem" }}>                 │
│      Status: [<select tc-select> All | PENDING | APPROVED | REJECTED ]      │
│    </div>                                                                    │
│                                                                              │
│    <table className="tc-table">                                              │
│    ┌──────────┬──────────┬────────┬─────────────┬──────────┬────────┬──────┐│
│    │ PRODUCT  │ REVIEWER │ RATING │ TITLE       │ STATUS   │ DATE   │      ││
│    ├──────────┼──────────┼────────┼─────────────┼──────────┼────────┼──────┤│
│    │ 42       │ Alice    │ ★★★★☆  │ Great item  │ PENDING  │ 07 Apr │[App] ││
│    │          │          │  (4)   │             │          │        │[Rej] ││
│    ├──────────┼──────────┼────────┼─────────────┼──────────┼────────┼──────┤│
│    │ 42       │ Bob      │ ★★★★★  │ Excellent   │ APPROVED │ 06 Apr │      ││
│    │          │          │  (5)   │             │          │        │[Rej] ││
│    ├──────────┼──────────┼────────┼─────────────┼──────────┼────────┼──────┤│
│    │ 7        │ Carol    │ ★☆☆☆☆  │ Broken      │ REJECTED │ 05 Apr │      ││
│    │          │          │  (1)   │             │          │        │[App] ││
│    └──────────┴──────────┴────────┴─────────────┴──────────┴────────┴──────┘│
│                                                                              │
│    [App] = <button tc-primary-button>Approve</button>                        │
│    [Rej] = <button tc-ghost-button tc-ghost-button--danger>Reject</button>   │
│                                                                              │
│  </section>                                                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Button visibility rules per row** (shown in Actions column):

```
Review status  |  Buttons shown
---------------+----------------------------------------------
PENDING        |  [Approve] (tc-primary-button)
               |  [Reject]  (tc-ghost-button--danger)
APPROVED       |  [Reject]  (tc-ghost-button--danger) only
REJECTED       |  [Approve] (tc-primary-button) only
```

**Integration Points**:
- `tc-section` wraps the filter + table group, matching `WarehousePage.tsx` section pattern
- `tc-flex` toolbar row holds the status `tc-select`, matching warehouse's add-form toolbar
- `tc-table` columns: Product ID, Reviewer, Rating (numeric 1-5 rendered as star glyphs + raw number), Title, Status, Date, Actions
- Actions column has no `<th>` text (empty header), matching warehouse delete column convention
- Rating rendered as Unicode stars (e.g. `★★★★☆`) with raw number in parentheses for accessibility
- After Approve/Reject: recalculate aggregate via `review.entityId` as productId, then call `thisPlugin.setData(productId, { rating: avg, count: n })`

**Component Reuse**:
- `tc-table` — full table with blue uppercase headers
- `tc-select` — status filter dropdown
- `tc-primary-button` — Approve action
- `tc-ghost-button tc-ghost-button--danger` — Reject action
- `tc-error` — error paragraph
- `tc-section` — section wrapper with bottom margin
- `tc-flex` — toolbar flex row


### Mockup 2: ReviewsAdmin — Loading and Empty States

**Loading state** (mirrors WarehousePage loading pattern):

```
┌─────────────────────────────────────────────────────┐
│  <div className="tc-plugin" style={{ padding:1rem }}│
│                                                      │
│  <p>Loading...</p>                                   │
│                                                      │
└─────────────────────────────────────────────────────┘
```

**Empty state** (no reviews match the selected filter):

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  <div className="tc-plugin" style={{ padding:"1rem", maxWidth:900 }}>       │
│                                                                              │
│  <h1>Reviews</h1>                                                            │
│                                                                              │
│  <section className="tc-section">                                            │
│    <div className="tc-flex" ...>                                             │
│      Status: [PENDING ▼]                                                     │
│    </div>                                                                    │
│    <p>No reviews found.</p>                                                  │
│  </section>                                                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Empty state note**: `<p>No reviews found.</p>` mirrors the warehouse "No warehouses yet" pattern — plain paragraph, no special styling.


### Mockup 3: ProductReviewsTab — Default View

**Context**: `product.detail.tabs`, `path="/product-reviews"`, `PRODUCT_DETAIL` context. Renders as a tab inside the host product detail page. `maxWidth: 800`.

```
┌──────────────────────────────────────────────────────────────────────────┐
│  <div className="tc-plugin" style={{ padding:"1rem", maxWidth:800 }}>    │
│                                                                           │
│  <h2>Reviews</h2>                                                         │
│  [tc-error shown here if error state]                                     │
│                                                                           │
│  ── EXISTING REVIEWS ────────────────────────────────────────────────────│
│  <section className="tc-section">                                         │
│    <table className="tc-table">                                           │
│    ┌──────────┬────────┬─────────────┬──────────┬────────┐               │
│    │ REVIEWER │ RATING │ TITLE       │ STATUS   │ DATE   │               │
│    ├──────────┼────────┼─────────────┼──────────┼────────┤               │
│    │ Alice    │ ★★★★☆  │ Great item  │ PENDING  │ 07 Apr │               │
│    │          │  (4)   │             │          │        │               │
│    ├──────────┼────────┼─────────────┼──────────┼────────┤               │
│    │ Bob      │ ★★★★★  │ Excellent   │ APPROVED │ 06 Apr │               │
│    │          │  (5)   │             │          │        │               │
│    └──────────┴────────┴─────────────┴──────────┴────────┘               │
│  </section>                                                               │
│                                                                           │
│  ── SUBMIT A REVIEW ─────────────────────────────────────────────────────│
│  <section className="tc-section">                                         │
│    <h3>Submit a Review</h3>                                               │
│                                                                           │
│    <div style={{ display:"flex", flexDirection:"column", gap:"0.625rem"}}│
│      <label style={{ display:"flex", alignItems:"center", gap:"0.75rem"}│
│        <span style={{ width:80 }}>Reviewer</span>                        │
│        <input className="tc-input" placeholder="Your name" />            │
│      </label>                                                             │
│      <label style={{ display:"flex", alignItems:"center", gap:"0.75rem"}│
│        <span style={{ width:80 }}>Rating</span>                          │
│        <select className="tc-select">                                    │
│          <option>1</option><option>2</option><option>3</option>          │
│          <option>4</option><option>5</option>                            │
│        </select>                                                          │
│      </label>                                                             │
│      <label style={{ display:"flex", alignItems:"center", gap:"0.75rem"}│
│        <span style={{ width:80 }}>Title</span>                           │
│        <input className="tc-input" placeholder="Review title" />         │
│      </label>                                                             │
│      <label style={{ display:"flex", alignItems:"center", gap:"0.75rem"}│
│        <span style={{ width:80 }}>Body</span>                            │
│        <textarea className="tc-input" placeholder="Your review..." />    │
│      </label>                                                             │
│    </div>                                                                 │
│                                                                           │
│    <div className="tc-flex" style={{ marginTop:"1rem" }}>                │
│      <button className="tc-primary-button">Submit Review</button>        │
│    </div>                                                                 │
│  </section>                                                               │
│                                                                           │
└──────────────────────────────────────────────────────────────────────────┘
```

**Form label width**: `width: 80` (wider than box-size's `width: 60` because "Reviewer" is longer than "Length").

**Integration Points**:
- Two `tc-section` blocks: top for existing reviews table, bottom for submit form
- Review table has no Actions column — this is a read-only list for the product customer view
- All reviews shown (PENDING, APPROVED, REJECTED) — Status column lets the submitter track moderation state
- Form uses the label+input pattern from `ProductBoxTab.tsx` (flex column, labeled inputs)
- Body field uses `<textarea className="tc-input">` — `tc-input` applies to textarea as well per plugin-ui.css
- After submit: save review object with `status: "PENDING"` and entityType/entityId binding, then recalculate aggregate via `thisPlugin.setData`

**Component Reuse**:
- `tc-table` — reviews list
- `tc-section` — two sections (list + form)
- `tc-input` — Reviewer, Title, Body textarea inputs
- `tc-select` — Rating 1-5 dropdown
- `tc-primary-button` — Submit Review
- `tc-flex` — button row
- `tc-error` — validation/save error paragraph


### Mockup 4: ProductReviewsTab — Empty State

```
┌──────────────────────────────────────────────────────────────────┐
│  <div className="tc-plugin" style={{ padding:"1rem" }}>          │
│                                                                   │
│  <h2>Reviews</h2>                                                 │
│                                                                   │
│  <section className="tc-section">                                 │
│    <p>No reviews yet for this product.</p>                        │
│  </section>                                                       │
│                                                                   │
│  <section className="tc-section">                                 │
│    <h3>Submit a Review</h3>                                       │
│    [form as above]                                                │
│  </section>                                                       │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

**Note**: Form always shown even when no reviews exist — encourages first submission.


### Mockup 5: ProductRatingBadge — All States

**Context**: `product.detail.info`, `path="/product-rating-badge"`, `PRODUCT_DETAIL` context. Renders in a ~60px iframe slot below the product details card. No padding-heavy wrapper — keep height tight.

```
── Loading state ─────────────────────────────────────
  return null;   (no content, avoids layout shift)

── No data state (no reviews yet) ───────────────────
  return null;   (same — no badge if no rating data)

── Good rating (avg >= 4.0) ──────────────────────────
┌──────────────────────────────────────────────────┐
│  <div className="tc-plugin"                      │
│       style={{ padding:"0.5rem 1rem" }}>         │
│                                                  │
│  <span className="tc-badge tc-badge--success">   │
│    ★ 4.2 (15 reviews)                            │
│  </span>                                         │
│                                                  │
└──────────────────────────────────────────────────┘

── Medium rating (2.0 <= avg < 4.0) ─────────────────
┌──────────────────────────────────────────────────┐
│  <div className="tc-plugin"                      │
│       style={{ padding:"0.5rem 1rem" }}>         │
│                                                  │
│  <span>★ 3.1 (8 reviews)</span>                  │
│  (plain text — no badge class)                   │
│                                                  │
└──────────────────────────────────────────────────┘

── Poor rating (avg < 2.0) ───────────────────────────
┌──────────────────────────────────────────────────┐
│  <div className="tc-plugin"                      │
│       style={{ padding:"0.5rem 1rem" }}>         │
│                                                  │
│  <span className="tc-badge tc-badge--danger">    │
│    ★ 1.3 (4 reviews)                             │
│  </span>                                         │
│                                                  │
└──────────────────────────────────────────────────┘
```

**Badge selection logic**:

```
avg >= 4.0  →  tc-badge tc-badge--success   (green)
avg >= 2.0  →  plain <span> (no badge)      (neutral)
avg <  2.0  →  tc-badge tc-badge--danger    (red)
```

**Padding**: `0.5rem 1rem` instead of the usual `1rem` — keeps the iframe height to ~40-50px, fitting the ~60px slot constraint.

**Integration Points**:
- Mirrors `ProductAvailability.tsx` exactly in structure: load on mount, return null while loading, return null if no data
- Data read from `thisPlugin.getData(productId)` — expects `{ rating: number, count: number }` stored by the reviews plugin's aggregate update
- `rating` is pre-computed average (float, 1 decimal); `count` is total review count
- No actions, no form — purely read-only display

**Component Reuse**:
- `tc-badge tc-badge--success` — good rating
- `tc-badge tc-badge--danger` — poor rating
- plain `<span>` — medium rating (no badge variant exists for neutral)

---

## Reusable Components

### Layout Wrappers

- **`tc-plugin`** — root wrapper; all pages use it; provides base typography and color resets
- **`tc-section`** — section with bottom margin; used to separate the filter+table group in ReviewsAdmin and the list/form groups in ProductReviewsTab

### Form Inputs

- **`tc-input`** — text, number, and textarea inputs (`plugins/warehouse/src/pages/WarehousePage.tsx`, `plugins/box-size/src/pages/ProductBoxTab.tsx`)
  - Use for: Reviewer name, Title, Body in ProductReviewsTab submit form
- **`tc-select`** — dropdown select (`plugins/warehouse/src/pages/WarehousePage.tsx`)
  - Use for: Status filter in ReviewsAdmin; Rating 1-5 in ProductReviewsTab form
- Label+input flex row pattern (inline styles) — from `ProductBoxTab.tsx`
  - `display:"flex"`, `alignItems:"center"`, `gap:"0.75rem"`, fixed-width label span

### Buttons

- **`tc-primary-button`** — primary action (`plugins/warehouse/src/pages/WarehousePage.tsx`)
  - Use for: Approve in ReviewsAdmin, Submit Review in ProductReviewsTab
- **`tc-ghost-button tc-ghost-button--danger`** — destructive action (`plugins/warehouse/src/pages/WarehousePage.tsx`)
  - Use for: Reject in ReviewsAdmin

### Tables

- **`tc-table`** — full table with blue uppercase headers, hover rows, borders (`plugins/warehouse/src/pages/WarehousePage.tsx`, `plugins/warehouse/src/pages/ProductStockTab.tsx`)
  - Use for: All reviews table in ReviewsAdmin, product reviews list in ProductReviewsTab
  - Numeric/action columns: use `align="right"` on `<th>`/`<td>`
  - Empty actions header: omit text from `<th>` (warehouse delete column convention)

### Badges

- **`tc-badge tc-badge--success`** — green badge (`plugins/warehouse/src/pages/ProductAvailability.tsx`)
  - Use for: avg rating >= 4.0 in ProductRatingBadge
- **`tc-badge tc-badge--danger`** — red badge (`plugins/warehouse/src/pages/ProductAvailability.tsx`)
  - Use for: avg rating < 2.0 in ProductRatingBadge

### Feedback

- **`tc-error`** — red error paragraph
  - Use for: all error states in ReviewsAdmin and ProductReviewsTab
- **`tc-flex`** — flex row with gap
  - Use for: toolbar rows (filter row in ReviewsAdmin, button row in ProductReviewsTab)

---

## Implementation Notes

### Consistency Checklist

- All three pages use `<div className="tc-plugin">` as the root element
- Loading states: ReviewsAdmin and ProductReviewsTab use `<p>Loading...</p>`; ProductRatingBadge returns `null`
- Error states: always `<p className="tc-error">{error}</p>` placed directly below `<h1>` / `<h2>`
- No custom CSS classes or inline style for non-layout concerns
- Button labels follow warehouse convention: short imperative verbs ("Approve", "Reject", "Submit Review")
- Empty states use plain `<p>` paragraph, no special styling

### Accessibility Considerations

- Rating stars rendered as `★★★★☆ (4)` — Unicode glyphs plus numeric fallback in parentheses ensures screen reader readability
- Form labels use `<label>` wrapping `<span>` + `<input>` — label element provides implicit association
- Status filter `<select>` should have a visible `<label>` ("Status:") as text before it in the flex row
- Approve/Reject buttons in table rows have unique accessible names because the review title or reviewer name should be included via `aria-label` if needed (e.g. `aria-label="Approve review by Alice"`)
- Disabled button state (`disabled={saving}`) prevents double-submit

### Responsive Behavior

- Plugin iframes are sized by the host; plugins do not control their own viewport width
- `maxWidth: 900` (ReviewsAdmin) and `maxWidth: 800` (ProductReviewsTab) prevent over-wide tables on large screens while remaining usable in typical host sidebar widths
- ProductRatingBadge has no maxWidth — badge is inline and naturally compact
- Table columns may compress on narrow viewports; no responsive table behavior needed for v1 (internal admin tool)

### State Management Notes

- ReviewsAdmin: load all reviews once on mount, filter client-side by selected status. Single `objects.list("review")` call, no re-fetches on filter change.
- ProductReviewsTab: load reviews filtered by `entityType: "PRODUCT", entityId: productId` on mount. Re-fetch after successful submit.
- ProductRatingBadge: read `thisPlugin.getData(productId)` once on mount. No polling.
- Aggregate update pattern (shared by ReviewsAdmin and ProductReviewsTab):
  1. Fetch all APPROVED reviews for the product: `objects.list("review", { entityType:"PRODUCT", entityId: productId, filter: "status:eq:APPROVED" })`
  2. Compute `avg = sum(ratings) / count`, round to 1 decimal
  3. `thisPlugin.setData(productId, { rating: avg, count: reviews.length })`

---

## Alternatives Considered

### Option 1: Server-side status filter (Rejected)

Load reviews with `objects.list("review", { filter: "status:eq:PENDING" })` and re-fetch on dropdown change. Rejected because: adds network round-trips on every filter toggle; total review counts are small (internal tool); client-side filter matches the clarified decision in `clarifications.md`.

### Option 2: Inline editing for Approve/Reject (Rejected)

Replace status text in the table cell with a dropdown. Rejected because: two distinct actions (Approve, Reject) with different semantics map naturally to two buttons; the warehouse plugin's delete-button-in-row is the established pattern.

### Option 3: Separate "PENDING" tab in ReviewsAdmin (Rejected)

Split admin into tabs: "All" / "Pending". Rejected because: no tab component exists in the tc-* system; the filter dropdown achieves the same goal with established components.

### Option 4: Badge showing all three rating states with distinct badge classes (Rejected)

Would require a `tc-badge--warning` class that does not exist in `plugin-ui.css`. Rejected in favour of plain `<span>` for the medium range — consistent with "only use existing classes" constraint.

### Option 5: Chosen Approach (Selected)

All-in-one filter dropdown for ReviewsAdmin, two-section layout for ProductReviewsTab, null-return badge for ProductRatingBadge — directly mirrors warehouse and box-size reference patterns with no invented components.

---

*Generated by ui-mockup-generator subagent*
