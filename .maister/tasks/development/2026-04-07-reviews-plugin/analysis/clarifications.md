# Clarifications

**Date**: 2026-04-07

## Q1: Admin page scope

**Question**: Should ReviewsAdmin show only PENDING reviews, or all reviews with status filtering?

**Answer**: All reviews with status filter — admin page shows all reviews (PENDING/APPROVED/REJECTED) with ability to filter by status.

**Impact**: ReviewsAdmin must support status filter UI (e.g. select dropdown for PENDING/APPROVED/REJECTED/All). Use `objects.list("review", { filter: "status:eq:PENDING" })` etc., or `objects.list("review")` for all.

## Q2: Product list filter semantics

**Question**: filterType "number" uses eq operator (exact match, not min-rating). How to handle?

**Answer**: Accept eq semantics, label it clearly as "Rating" in the manifest.

**Impact**: manifest filterKey="rating", filterType="number", label="Rating". The filter matches products with an exact average rating value stored in pluginData.
