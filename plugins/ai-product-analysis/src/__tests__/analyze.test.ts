import { createMocks } from "node-mocks-http";
import type { NextApiRequest, NextApiResponse } from "next";

const mockAnalyzeProduct = jest.fn();

jest.mock("../../baml_client", () => ({
  b: {
    AnalyzeProduct: (...args: unknown[]) => mockAnalyzeProduct(...args),
  },
}));

const mockGetProduct = jest.fn();
const mockSaveObject = jest.fn();

jest.mock("../../../server-sdk", () => ({
  createServerSDK: () => ({
    hostApp: {
      getProduct: (...args: unknown[]) => mockGetProduct(...args),
    },
    thisPlugin: {
      objects: {
        save: (...args: unknown[]) => mockSaveObject(...args),
      },
    },
  }),
}));

import handler from "../pages/api/analyze";

describe("POST /api/analyze", () => {
  beforeEach(() => {
    mockAnalyzeProduct.mockReset();
    mockGetProduct.mockReset();
    mockSaveObject.mockReset();
    mockSaveObject.mockResolvedValue({});
  });

  test("analyze_nonPostMethod_returns405", async () => {
    const { req, res } = createMocks<NextApiRequest, NextApiResponse>({
      method: "GET",
      body: {},
    });

    await handler(req, res);

    expect(res._getStatusCode()).toBe(405);
    const body = JSON.parse(res._getData());
    expect(body.error).toContain("Method not allowed");
  });

  test("analyze_bamlClientError_returns500", async () => {
    mockGetProduct.mockResolvedValue({
      name: "Widget",
      description: "A small widget",
      categoryName: "Widgets",
      price: "9.99",
    });
    mockAnalyzeProduct.mockRejectedValue(new Error("BAML service unavailable"));

    const { req, res } = createMocks<NextApiRequest, NextApiResponse>({
      method: "POST",
      body: { productId: "42" },
    });

    await handler(req, res);

    expect(res._getStatusCode()).toBe(500);
    const body = JSON.parse(res._getData());
    expect(body.error).toContain("BAML service unavailable");
  });

  test("analyze_productWithNullDescription_stillCallsBAML", async () => {
    mockGetProduct.mockResolvedValue({
      name: "Mystery Product",
      description: null,
      categoryName: "General",
      price: null,
    });
    mockAnalyzeProduct.mockResolvedValue({
      overallVerdict: "WARN",
      descriptionQuality: { verdict: "WARN", score: 4, explanation: "No description provided" },
      categoryRelevance: { verdict: "PASS", score: 7, explanation: "Category is reasonable" },
      priceAssessment: { verdict: "WARN", score: 5, explanation: "No price data" },
      summary: "Incomplete product listing",
    });

    const { req, res } = createMocks<NextApiRequest, NextApiResponse>({
      method: "POST",
      body: { productId: "99" },
    });

    await handler(req, res);

    expect(res._getStatusCode()).toBe(200);
    expect(mockAnalyzeProduct).toHaveBeenCalledTimes(1);
    const [, description, , price] = mockAnalyzeProduct.mock.calls[0] as [string, string, string, string];
    expect(description).toBeNull();
    expect(price).toBe("");
  });

  test("analyze_savesObjectBeforeReturning", async () => {
    mockGetProduct.mockResolvedValue({
      name: "Running Shoes",
      description: "Great for running",
      categoryName: "Footwear",
      price: "99.99",
    });
    mockAnalyzeProduct.mockResolvedValue({
      overallVerdict: "PASS",
      descriptionQuality: { verdict: "PASS", score: 9, explanation: "Excellent" },
      categoryRelevance: { verdict: "PASS", score: 9, explanation: "Perfect fit" },
      priceAssessment: { verdict: "PASS", score: 8, explanation: "Reasonable price" },
      summary: "Great product",
    });

    const { req, res } = createMocks<NextApiRequest, NextApiResponse>({
      method: "POST",
      body: { productId: "55" },
    });

    await handler(req, res);

    expect(res._getStatusCode()).toBe(200);
    expect(mockSaveObject).toHaveBeenCalledTimes(1);
    const [objectType, objectId] = mockSaveObject.mock.calls[0] as [string, string, unknown, unknown];
    expect(objectType).toBe("analysis");
    expect(objectId).toBe("55");
  });

  test("analyze_missingProductId_returns400", async () => {
    const { req, res } = createMocks<NextApiRequest, NextApiResponse>({
      method: "POST",
      body: {},
    });

    await handler(req, res);

    expect(res._getStatusCode()).toBe(400);
    const body = JSON.parse(res._getData());
    expect(body.details).toContain("productId");
  });

  test("analyze_hostApiError_propagatesStatusCode", async () => {
    mockGetProduct.mockRejectedValue(new Error("Host API error 404"));

    const { req, res } = createMocks<NextApiRequest, NextApiResponse>({
      method: "POST",
      body: { productId: "42" },
    });

    await handler(req, res);

    expect(res._getStatusCode()).toBe(404);
  });

  test("analyze_validRequest_returnsThreeDimensionShape", async () => {
    mockGetProduct.mockResolvedValue({
      name: "Running Shoes",
      description: "Lightweight shoes for marathon training",
      categoryName: "Footwear",
      price: "129.99",
    });
    mockAnalyzeProduct.mockResolvedValue({
      overallVerdict: "PASS",
      descriptionQuality: {
        verdict: "PASS",
        score: 8,
        explanation: "Description is clear and accurate",
      },
      categoryRelevance: {
        verdict: "PASS",
        score: 9,
        explanation: "Category matches the product type",
      },
      priceAssessment: {
        verdict: "WARN",
        score: 6,
        explanation: "Price is on the higher end for this category",
      },
      summary: "Good product with accurate description",
    });

    const { req, res } = createMocks<NextApiRequest, NextApiResponse>({
      method: "POST",
      body: { productId: "42" },
    });

    await handler(req, res);

    expect(res._getStatusCode()).toBe(200);
    const body = JSON.parse(res._getData());

    expect(body.descriptionQuality).toHaveProperty("verdict");
    expect(body.descriptionQuality).toHaveProperty("score");
    expect(body.descriptionQuality).toHaveProperty("explanation");

    expect(body.categoryRelevance).toHaveProperty("verdict");
    expect(body.categoryRelevance).toHaveProperty("score");
    expect(body.categoryRelevance).toHaveProperty("explanation");

    expect(body.priceAssessment).toHaveProperty("verdict");
    expect(body.priceAssessment).toHaveProperty("score");
    expect(body.priceAssessment).toHaveProperty("explanation");
  });
});
