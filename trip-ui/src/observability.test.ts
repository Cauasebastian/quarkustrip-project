import { describe, expect, it } from "vitest";
import { jaegerDependenciesUrl, jaegerTraceUrl } from "./observability";

describe("Jaeger links", () => {
  it("opens an individual trace and the dependency graph", () => {
    expect(jaegerTraceUrl("0123456789abcdef0123456789abcdef"))
      .toBe("http://localhost:16686/trace/0123456789abcdef0123456789abcdef");
    expect(jaegerDependenciesUrl()).toBe("http://localhost:16686/dependencies");
  });
});
