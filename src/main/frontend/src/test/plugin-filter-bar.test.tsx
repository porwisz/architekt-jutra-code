import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ChakraProvider } from "@chakra-ui/react";
import { system } from "../theme";
import { PluginFilterBar } from "../plugins/PluginFilterBar";
import type { ResolvedExtensionPoint } from "../plugins/PluginContext";
import { PRODUCT_LIST_FILTERS } from "../plugins/extensionPoints";

function renderWithProviders(ui: React.ReactElement) {
  return render(<ChakraProvider value={system}>{ui}</ChakraProvider>);
}

function makeNumberFilter(overrides: Partial<ResolvedExtensionPoint> = {}): ResolvedExtensionPoint {
  return {
    type: PRODUCT_LIST_FILTERS,
    label: "Rating",
    filterKey: "rating",
    filterType: "number",
    priority: 20,
    pluginId: "reviews",
    pluginName: "Product Reviews",
    pluginUrl: "http://localhost:3010",
    ...overrides,
  };
}

describe("PluginFilterBar — buildFilterString", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("uses filterOperator from extension point when provided (gte)", () => {
    const onFilterChange = vi.fn();
    const filter = makeNumberFilter({ filterOperator: "gte" });

    renderWithProviders(
      <PluginFilterBar filters={[filter]} onFilterChange={onFilterChange} />,
    );

    const input = screen.getByRole("spinbutton");
    fireEvent.change(input, { target: { value: "4" } });

    expect(onFilterChange).toHaveBeenLastCalledWith(["reviews:rating:gte:4"]);
  });

  it("defaults operator to eq when filterOperator is absent", () => {
    const onFilterChange = vi.fn();
    const filter = makeNumberFilter();

    renderWithProviders(
      <PluginFilterBar filters={[filter]} onFilterChange={onFilterChange} />,
    );

    const input = screen.getByRole("spinbutton");
    fireEvent.change(input, { target: { value: "4" } });

    expect(onFilterChange).toHaveBeenLastCalledWith(["reviews:rating:eq:4"]);
  });
});
