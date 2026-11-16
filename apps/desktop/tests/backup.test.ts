import { expect, test } from "bun:test";
import { backupWords, size } from "../src/commons/lib/backup";
import type { BackupLook } from "../src/commons/lib/ipc";

const look: BackupLook = { shown: true, secrets: "backup_unsealed", sizeWords: "backup_size", size: 1_534_000, button: true, bar: false, percent: null, result: null, name: null };

test("a size as Finder says it", () => {
  expect(size(512)).toBe("512 bytes");
  expect(size(1_534_000)).toBe("1.5 MB");
  expect(size(2_000_000)).toBe("2 MB");
  expect(size(158_064_866)).toBe("158 MB");
  expect(size(4_200_000_000)).toBe("4.2 GB");
});

test("its size, then the line under the button, filled", () => {
  expect(backupWords(look)).toEqual({ size: "About 1.5 MB.", result: null });
  expect(backupWords({ ...look, size: null, sizeWords: "backup_counting" }).size).toBe("Working out its size…");
  expect(backupWords({ ...look, bar: true, percent: 37, result: "backup_saving" }).result).toBe("Saving Nouride's files… 37%");
  expect(backupWords({ ...look, result: "backup_saved", name: "nouride-backup-2026-11-17.zip" }).result).toBe("nouride-backup-2026-11-17.zip is in Downloads.");
  expect(backupWords({ ...look, result: "backup_no_room" }).result).toBe("There isn't enough room on this computer. Free about 1.5 MB, then try again.");
});
