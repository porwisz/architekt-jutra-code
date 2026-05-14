import { useEffect, useState } from "react";
import { getSDK } from "../../../sdk";
import { toReview } from "../domain";
import type { Review, ReviewStatus } from "../domain";

type StatusFilter = "All" | ReviewStatus;

export function ReviewsAdmin() {
  const [allReviews, setAllReviews] = useState<Review[]>([]);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("All");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        const sdk = getSDK();
        const objects = await sdk.thisPlugin.objects.list("review");
        setAllReviews(objects.map(toReview));
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load reviews");
      } finally {
        setLoading(false);
      }
    }
    void load();
  }, []);

  async function handleAction(review: Review, newStatus: ReviewStatus) {
    setError(null);
    try {
      const sdk = getSDK();
      const objects = sdk.thisPlugin.objects;

      await objects.save(
        "review",
        review.objectId,
        { rating: review.rating, title: review.title, body: review.body, reviewer: review.reviewer, status: newStatus },
        { entityType: "PRODUCT", entityId: review.entityId },
      );

      const approved = await objects.list("review", {
        entityType: "PRODUCT",
        entityId: review.entityId,
        filter: "status:eq:APPROVED",
      });

      const avg =
        approved.length > 0
          ? Math.round(
              (approved.reduce((s, r) => s + (r.data.rating as number), 0) / approved.length) * 10,
            ) / 10
          : 0;

      await sdk.thisPlugin.setData(review.entityId, { rating: avg, count: approved.length });

      setAllReviews((prev) =>
        prev.map((r) => (r.objectId === review.objectId ? { ...r, status: newStatus } : r)),
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update review");
    }
  }

  const filteredReviews =
    statusFilter === "All" ? allReviews : allReviews.filter((r) => r.status === statusFilter);

  if (loading) return <p>Loading...</p>;

  return (
    <div className="tc-plugin" style={{ padding: "1rem", maxWidth: 900 }}>
      <h1>Reviews</h1>
      {error && <p className="tc-error">{error}</p>}

      <div className="tc-flex" style={{ marginBottom: "1rem" }}>
        <span>Status:</span>
        <select
          className="tc-select"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}
        >
          <option value="All">All</option>
          <option value="PENDING">PENDING</option>
          <option value="APPROVED">APPROVED</option>
          <option value="REJECTED">REJECTED</option>
        </select>
      </div>

      {filteredReviews.length === 0 ? (
        <p>No reviews found.</p>
      ) : (
        <table className="tc-table">
          <thead>
            <tr>
              <th>Product ID</th>
              <th>Reviewer</th>
              <th>Rating</th>
              <th>Title</th>
              <th>Status</th>
              <th>Date</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredReviews.map((review) => (
              <tr key={review.objectId}>
                <td>{review.entityId}</td>
                <td>{review.reviewer}</td>
                <td>★ {review.rating}</td>
                <td>{review.title}</td>
                <td>{review.status}</td>
                <td>{review.createdAt}</td>
                <td>
                  <div className="tc-flex">
                    {(review.status === "PENDING" || review.status === "REJECTED") && (
                      <button
                        className="tc-primary-button"
                        onClick={() => void handleAction(review, "APPROVED")}
                      >
                        Approve
                      </button>
                    )}
                    {(review.status === "PENDING" || review.status === "APPROVED") && (
                      <button
                        className="tc-ghost-button tc-ghost-button--danger"
                        onClick={() => void handleAction(review, "REJECTED")}
                      >
                        Reject
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
