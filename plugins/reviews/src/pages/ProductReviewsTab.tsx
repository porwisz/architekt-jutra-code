import { useEffect, useState } from "react";
import { getSDK } from "../../../sdk";
import { toReview } from "../domain";
import type { Review } from "../domain";

export function ProductReviewsTab() {
  const sdk = getSDK();
  const productId = sdk.thisPlugin.productId ?? "";

  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  const [reviewer, setReviewer] = useState("");
  const [rating, setRating] = useState(5);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");

  useEffect(() => {
    if (!productId) {
      setLoading(false);
      return;
    }

    async function load() {
      try {
        const sdk = getSDK();
        const objects = await sdk.thisPlugin.objects.list("review", {
          entityType: "PRODUCT",
          entityId: productId,
          filter: "status:eq:APPROVED",
        });
        setReviews(objects.map(toReview));
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load reviews");
      } finally {
        setLoading(false);
      }
    }
    void load();
  }, [productId]);

  async function handleSubmit() {
    setError(null);
    try {
      const sdk = getSDK();
      const objects = sdk.thisPlugin.objects;

      await objects.save(
        "review",
        crypto.randomUUID(),
        { rating, title, body, reviewer, status: "PENDING" },
        { entityType: "PRODUCT", entityId: productId },
      );

      const approved = await objects.list("review", {
        entityType: "PRODUCT",
        entityId: productId,
        filter: "status:eq:APPROVED",
      });

      const avg =
        approved.length > 0
          ? Math.round(
              (approved.reduce((s, r) => s + (r.data.rating as number), 0) / approved.length) * 10,
            ) / 10
          : 0;

      await sdk.thisPlugin.setData(productId, { rating: avg, count: approved.length });

      setSubmitted(true);
      setReviews(approved.map(toReview));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to submit review");
    }
  }

  if (loading) return <p>Loading reviews...</p>;

  const labelStyle: React.CSSProperties = { display: "flex", flexDirection: "column", gap: "0.25rem" };
  const labelSpanStyle: React.CSSProperties = { width: 80, fontWeight: 500 };

  return (
    <div className="tc-plugin" style={{ padding: "1rem" }}>
      {error && <p className="tc-error">{error}</p>}

      <section className="tc-section">
        <table className="tc-table">
          <thead>
            <tr>
              <th>Reviewer</th>
              <th>Rating</th>
              <th>Title</th>
              <th>Status</th>
              <th>Date</th>
            </tr>
          </thead>
          <tbody>
            {reviews.length === 0 ? (
              <tr>
                <td colSpan={5}>
                  <p>No reviews yet for this product.</p>
                </td>
              </tr>
            ) : (
              reviews.map((review) => (
                <tr key={review.objectId}>
                  <td>{review.reviewer}</td>
                  <td>★ {review.rating}</td>
                  <td>{review.title}</td>
                  <td>{review.status}</td>
                  <td>{review.createdAt}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </section>

      <section className="tc-section">
        <h3>Submit a Review</h3>
        {submitted ? (
          <p>Review submitted! Pending approval.</p>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "0.625rem" }}>
            <label style={labelStyle}>
              <span style={labelSpanStyle}>Reviewer</span>
              <input
                className="tc-input"
                value={reviewer}
                onChange={(e) => setReviewer(e.target.value)}
              />
            </label>
            <label style={labelStyle}>
              <span style={labelSpanStyle}>Rating</span>
              <select
                className="tc-select"
                value={rating}
                onChange={(e) => setRating(Number(e.target.value))}
              >
                <option value={1}>1</option>
                <option value={2}>2</option>
                <option value={3}>3</option>
                <option value={4}>4</option>
                <option value={5}>5</option>
              </select>
            </label>
            <label style={labelStyle}>
              <span style={labelSpanStyle}>Title</span>
              <input
                className="tc-input"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </label>
            <label style={labelStyle}>
              <span style={labelSpanStyle}>Body</span>
              <textarea
                className="tc-input"
                value={body}
                onChange={(e) => setBody(e.target.value)}
              />
            </label>
            <div className="tc-flex">
              <button className="tc-primary-button" onClick={() => void handleSubmit()}>
                Submit Review
              </button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
