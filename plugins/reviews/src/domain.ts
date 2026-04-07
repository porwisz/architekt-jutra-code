import type { PluginObject } from "../../sdk";

export type ReviewStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface Review {
  objectId: string;
  entityId: string;
  rating: number;
  title: string;
  body: string;
  reviewer: string;
  status: ReviewStatus;
  createdAt: string;
  updatedAt: string;
}

export interface RatingSummary {
  rating: number;
  count: number;
}

export function toReview(obj: PluginObject): Review {
  return {
    objectId: obj.objectId,
    entityId: obj.entityId ?? "",
    rating: obj.data.rating as number,
    title: obj.data.title as string,
    body: obj.data.body as string,
    reviewer: obj.data.reviewer as string,
    status: obj.data.status as ReviewStatus,
    createdAt: obj.data.createdAt as string ?? "",
    updatedAt: obj.data.updatedAt as string ?? "",
  };
}

export function toRatingSummary(data: Record<string, unknown>): RatingSummary {
  return {
    rating: (data.rating as number) ?? 0,
    count: (data.count as number) ?? 0,
  };
}
