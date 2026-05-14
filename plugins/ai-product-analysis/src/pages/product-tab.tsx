import { useEffect, useMemo, useState } from "react";
import { getSDK } from "../../../sdk";
import { toProductAnalysis } from "../domain";
import type { DimensionAssessment, ProductAnalysis } from "../domain";
import { getVerdictClassName, PRICE_DISCLAIMER } from "./product-info-badge";

export const ANALYZE_BUTTON_LABEL = "Analyze";
export const REANALYZE_BUTTON_LABEL = "Re-analyze";

function DimensionCard({ title, dimension, disclaimer }: {
  title: string;
  dimension: DimensionAssessment;
  disclaimer?: string;
}) {
  return (
    <div className="tc-card" style={{ padding: "1rem", marginBottom: "1rem" }}>
      <h4 style={{ margin: "0 0 0.5rem" }}>{title}</h4>
      <p style={{ margin: "0 0 0.5rem" }}>
        <span className={getVerdictClassName(dimension.verdict)}>{dimension.verdict}</span>
        {" "}Score: {dimension.score}/10
      </p>
      <p style={{ margin: "0" }}>{dimension.explanation}</p>
      {disclaimer && (
        <p style={{ margin: "0.5rem 0 0", fontSize: "12px", color: "#666" }}>
          {disclaimer}
        </p>
      )}
    </div>
  );
}

export default function ProductTab() {
  const sdk = useMemo(() => (typeof window !== "undefined" ? getSDK() : null), []);
  const productId = sdk?.thisPlugin.productId ?? "";

  const [analysis, setAnalysis] = useState<ProductAnalysis | null>(null);
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [canEdit, setCanEdit] = useState(false);

  useEffect(() => {
    if (!sdk) return;
    if (!productId) {
      setError("Product ID is missing. This tab must be opened from a product detail page.");
      setLoading(false);
      return;
    }

    async function load() {
      try {
        const token = await sdk!.hostApp.getToken();
        if (token) {
          try {
            const payload = JSON.parse(atob(token.split(".")[1]));
            const permissions = (payload.permissions ?? []) as string[];
            setCanEdit(permissions.includes("EDIT"));
          } catch { /* invalid token — leave canEdit false */ }
        }

        const objects = await sdk!.thisPlugin.objects.listByEntity("PRODUCT", productId);
        if (objects.length > 0) {
          setAnalysis(toProductAnalysis(objects[0]));
        }
      } catch {
        setError("Failed to load existing analysis.");
      } finally {
        setLoading(false);
      }
    }
    void load();
  }, [productId, sdk]);

  async function handleAnalyze() {
    if (!sdk) return;
    setError(null);
    setAnalyzing(true);

    try {
      const token = await sdk.hostApp.getToken();
      const response = await fetch("/api/analyze", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ productId }),
      });

      if (!response.ok) {
        const body = (await response.json()) as { error?: string };
        throw new Error(body.error ?? "Failed to analyze product.");
      }

      const result = (await response.json()) as ProductAnalysis;
      setAnalysis({ ...result, objectId: productId });
    } catch (err) {
      if (err instanceof TypeError && err.message === "Failed to fetch") {
        setError("Network error. Please check your connection and try again.");
      } else {
        setError(err instanceof Error ? err.message : "An unexpected error occurred.");
      }
    } finally {
      setAnalyzing(false);
    }
  }

  if (loading) {
    return <div className="tc-plugin" style={{ padding: "1rem" }}>Loading...</div>;
  }

  return (
    <div className="tc-plugin" style={{ padding: "1.5rem" }}>
      <h3 style={{ margin: "0 0 1rem" }}>AI Product Analysis</h3>

      {error && <p className="tc-error">{error}</p>}

      {analysis && (
        <>
          <DimensionCard title="Description Quality" dimension={analysis.descriptionQuality} />
          <DimensionCard title="Category Relevance" dimension={analysis.categoryRelevance} />
          <DimensionCard
            title="Price Assessment"
            dimension={analysis.priceAssessment}
            disclaimer={PRICE_DISCLAIMER}
          />
        </>
      )}

      {canEdit && (
        <button
          className="tc-primary-button"
          onClick={() => void handleAnalyze()}
          disabled={analyzing}
        >
          {analyzing ? "Analyzing..." : analysis ? REANALYZE_BUTTON_LABEL : ANALYZE_BUTTON_LABEL}
        </button>
      )}
    </div>
  );
}
