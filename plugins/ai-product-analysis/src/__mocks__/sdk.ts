import type { PluginSDKType } from "../../../sdk";

export function getSDK(): PluginSDKType {
  return {
    thisPlugin: {
      pluginId: "ai-product-analysis",
      pluginName: "AI Product Analysis",
      productId: "42",
      getContext: () => ({
        extensionPoint: "product.detail.tabs",
        pluginId: "ai-product-analysis",
        pluginName: "AI Product Analysis",
        hostOrigin: "http://localhost:8080",
        productId: "42",
      }),
      getData: jest.fn(),
      setData: jest.fn(),
      removeData: jest.fn(),
      objects: {
        list: jest.fn(),
        listByEntity: jest.fn(),
        get: jest.fn(),
        save: jest.fn(),
        delete: jest.fn(),
      },
    },
    hostApp: {
      getProducts: jest.fn(),
      getProduct: jest.fn(),
      getPlugins: jest.fn(),
      getToken: jest.fn().mockResolvedValue("mock-token"),
      fetch: jest.fn(),
    },
  };
}
