import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { ReviewsAdmin } from "./pages/ReviewsAdmin";
import { ProductReviewsTab } from "./pages/ProductReviewsTab";
import { ProductRatingBadge } from "./pages/ProductRatingBadge";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ReviewsAdmin />} />
        <Route path="/product-reviews" element={<ProductReviewsTab />} />
        <Route path="/product-rating-badge" element={<ProductRatingBadge />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
