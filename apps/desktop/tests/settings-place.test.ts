import { expect, test } from "bun:test";
import { pressPlace, shownPlace } from "../src/modules/settings/placeChoice";

test("the choice shows the place in use, and Another computer while its address is typed", () => {
  expect(shownPlace("here", false)).toBe("here");
  expect(shownPlace("remote", false)).toBe("remote");
  expect(shownPlace(null, false)).toBe("here");
  expect(shownPlace("here", true)).toBe("remote");
});

test("Another computer opens the address section and changes nothing yet", () => {
  expect(pressPlace("here", "remote")).toEqual({ asking: true, chooseHere: false });
  expect(pressPlace("remote", "remote")).toEqual({ asking: true, chooseHere: false });
});

test("This computer closes the section, and moves Nouride back only when it runs elsewhere", () => {
  // Cancel by another route: the address was being typed, Nouride still runs here, nothing to switch.
  expect(pressPlace("here", "here")).toEqual({ asking: false, chooseHere: false });
  expect(pressPlace("remote", "here")).toEqual({ asking: false, chooseHere: true });
});
