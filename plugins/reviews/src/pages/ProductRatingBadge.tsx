import { useEffect, useState } from "react";
import { getSDK } from "../../../sdk";
import { toRatingSummary } from "../domain";
import type { RatingSummary } from "../domain";

export function ProductRatingBadge() {
  const [summary, setSummary] = useState<RatingSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const sdk = getSDK();
  const productId = sdk.thisPlugin.productId ?? "";

  useEffect(() => {
    if (!productId) {
      setLoading(false);
      return;
    }

    async function load() {
      try {
        const data = (await sdk.thisPlugin.getData(productId)) as Record<string, unknown> | null;
        if (!data || Object.keys(data).length === 0) {
          setSummary(null);
        } else {
          setSummary(toRatingSummary(data));
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load rating");
      } finally {
        setLoading(false);
      }
    }
    void load();
  }, [productId]);

  if (loading) return null;
  if (error) return <p className="tc-error">{error}</p>;
  if (!summary || summary.count === 0) return null;

  const avg = summary.rating;
  const count = summary.count;

  return (
    <div className="tc-plugin" style={{ padding: "0.5rem 1rem" }}>
      {avg >= 4.0 ? (
        <span className="tc-badge tc-badge--success">★ {avg} ({count} reviews)</span>
      ) : avg >= 2.0 ? (
        <span>★ {avg} ({count} reviews)</span>
      ) : (
        <span className="tc-badge tc-badge--danger">★ {avg} ({count} reviews)</span>
      )}
    </div>
  );
}
