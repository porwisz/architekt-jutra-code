/** @type {import('jest').Config} */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>/src"],
  moduleFileExtensions: ["ts", "tsx", "js", "json"],
  transform: {
    "^.+\\.tsx?$": ["ts-jest", { tsconfig: { jsx: "react" } }],
  },
  moduleNameMapper: {
    "^../../sdk$": "<rootDir>/src/__mocks__/sdk.ts",
  },
};
