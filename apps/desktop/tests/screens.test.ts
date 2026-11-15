import { expect, test } from "bun:test";
import { report } from "../src/commons/lib/report";
import { back, tabOf } from "../src/commons/lib/screens";

test("Back from Connect or More goes Home, from Versions or Technical Logs to More, from Home nowhere", () => {
  expect(back("connect")).toBe("home");
  expect(back("more")).toBe("home");
  expect(back("versions")).toBe("more");
  expect(back("logs")).toBe("more");
  expect(back("home")).toBeNull();
});

test("Versions and Technical Logs light More's tab", () => {
  expect(tabOf("versions")).toBe("more");
  expect(tabOf("logs")).toBe("more");
  expect(tabOf("connect")).toBe("connect");
});

test("the copied report has each part under its heading, an empty one said as such", () => {
  expect(report({ summary: "Where: here", parts: [{ name: "Nouride", text: "ready" }, { name: "Installer", text: "  " }] })).toBe(
    "Where: here\n\n== Nouride ==\nready\n\n== Installer ==\n(empty)",
  );
});
