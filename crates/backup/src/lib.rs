//! Nouride's files as one zip (`spec/backup.md`): what under `.nouride` stays out of it, the database snapshot it
//! carries in place of the open database, its name, and the zip itself, written as it is read. The Android app's
//! `backup/Backup` holds the same list, tested against the same fixture.

use std::fs::{self, File};
use std::io::{self, Read, Seek, Write};
use std::path::{Path, PathBuf};

use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipWriter};

/// Where the folder's files go in the zip: it unpacks where the folder was.
pub const FOLDER_ENTRY: &str = ".nouride/";

/// The database snapshot's place in the zip: where the open database is in the folder.
pub const DATABASE_ENTRY: &str = ".nouride/data/nouride.db";

/// What stays out, as path patterns under `.nouride/`: a pattern ending in `/` is a folder and everything in it, `*`
/// any part of one name, `**/` any number of folders. Why each is out is in `spec/fixtures/backup.json`.
pub const OUT: &[&str] = &[
  // Secrets in the clear, and what opens them. Only `data/secrets.sealed` goes in.
  "data/secrets.enc",
  "data/secrets.json",
  "encryption.key",
  "data/.control-token",
  "credentials/",
  "home/",
  "workspace/private/*/.cli/",
  "data/whatsapp/",
  "data/appliance/",
  "data/cli-accounts.json",
  "nougate/",
  // The open database and what comes with it: the snapshot goes in instead.
  "data/nouride.*",
  "data/backups/",
  // What the installer or Nouride puts back.
  "bin/",
  "lib/",
  "opt/",
  "share/",
  "system-packages/",
  ".crates.toml",
  ".crates2.json",
  "**/node_modules/",
  "**/.venv/",
  "**/__pycache__/",
  // Logs and caches.
  "jobs/",
  "tool-output/",
  "data/knowledge/",
  "data/clean-shutdown.json",
  "workspace/private/*/.scratchpad/",
];

/// Whether `path` (under `.nouride/`, `/` between names, ending in `/` for a folder) goes in the zip.
pub fn kept(path: &str) -> bool {
  !OUT.iter().any(|pattern| matches(pattern, path))
}

/// The zip's name on `date` (`YYYY-MM-DD`, the device's own).
pub fn zip_name(date: &str) -> String {
  format!("nouride-backup-{date}.zip")
}

/// Left free for the device itself: a zip that would fill it is said not to fit (Android's `DownloadsFolder.HEADROOM`).
pub const HEADROOM: u64 = 64 * 1024 * 1024;

/// Whether a zip of `bytes` fits where `free` bytes are left, with [`HEADROOM`] to spare.
pub fn fits(bytes: u64, free: u64) -> bool {
  free.checked_sub(HEADROOM).is_some_and(|room| bytes <= room)
}

/// The zip's name today, by this computer's clock.
pub fn zip_name_today() -> String {
  zip_name(&chrono::Local::now().format("%Y-%m-%d").to_string())
}

/// A file's own time in the zip, in this computer's time zone as zip tools read it; none when it cannot be said.
fn modified(path: &Path) -> Option<zip::DateTime> {
  let time: chrono::DateTime<chrono::Local> = fs::metadata(path).and_then(|m| m.modified()).ok()?.into();
  zip::DateTime::try_from(time.naive_local()).ok()
}

/// The snapshot `nouride db backup --json` took, from what it printed: `data.path` of its JSON line when `ok`.
pub fn snapshot(output: &str) -> Option<String> {
  output.lines().rev().find_map(|line| {
    let answer: serde_json::Value = serde_json::from_str(line.trim()).ok()?;
    if answer["ok"] != serde_json::Value::Bool(true) {
      return None;
    }
    answer["data"]["path"].as_str().filter(|p| !p.is_empty()).map(str::to_owned)
  })
}

fn matches(pattern: &str, path: &str) -> bool {
  let folder_pattern = pattern.ends_with('/');
  let is_folder = path.ends_with('/');
  let pat: Vec<&str> = pattern.trim_end_matches('/').split('/').collect();
  let names: Vec<&str> = path.trim_end_matches('/').split('/').collect();
  if !folder_pattern {
    return !is_folder && segments_match(&pat, &names);
  }
  // A folder pattern takes the folder and everything in it: any leading run of names that is a folder.
  let last = if is_folder { names.len() } else { names.len() - 1 };
  (1..=last).any(|n| segments_match(&pat, &names[..n]))
}

fn segments_match(pat: &[&str], names: &[&str]) -> bool {
  match pat.split_first() {
    None => names.is_empty(),
    Some((&"**", rest)) => (0..=names.len()).any(|skip| segments_match(rest, &names[skip..])),
    Some((first, rest)) => names.split_first().is_some_and(|(name, names)| name_matches(first, name) && segments_match(rest, names)),
  }
}

/// One name against one part of a pattern, `*` standing for any run of characters.
fn name_matches(pattern: &str, name: &str) -> bool {
  match pattern.split_once('*') {
    None => pattern == name,
    Some((head, tail)) => {
      let Some(rest) = name.strip_prefix(head) else { return false };
      (0..=rest.len()).filter(|&i| rest.is_char_boundary(i)).any(|i| name_matches(tail, &rest[i..]))
    }
  }
}

/// A file that goes in: its path under `.nouride/`, where it is, its size when it was counted.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Entry {
  pub name: String,
  pub path: PathBuf,
  pub bytes: u64,
}

/// What the zip will hold of the folder, counted before it starts.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Plan {
  pub files: Vec<Entry>,
  pub bytes: u64,
}

/// Every file under `root` (the `.nouride` folder) that goes in, links never followed. A file or folder that cannot
/// be read is left out rather than stopping the count; the open database's size is the caller's to add.
pub fn plan(root: &Path) -> io::Result<Plan> {
  let mut plan = Plan::default();
  walk(root, "", &mut plan)?;
  plan.files.sort_by(|a, b| a.name.cmp(&b.name));
  Ok(plan)
}

fn walk(dir: &Path, under: &str, plan: &mut Plan) -> io::Result<()> {
  let mut entries: Vec<_> = fs::read_dir(dir)?.filter_map(Result::ok).collect();
  entries.sort_by_key(|e| e.file_name());
  for entry in entries {
    let Some(name) = entry.file_name().to_str().map(str::to_owned) else { continue };
    let Ok(kind) = entry.file_type() else { continue };
    if kind.is_symlink() {
      continue;
    }
    if kind.is_dir() {
      let folder = format!("{under}{name}/");
      if kept(&folder) {
        let _ = walk(&entry.path(), &folder, plan);
      }
    } else if kind.is_file() {
      let file = format!("{under}{name}");
      if kept(&file) {
        let bytes = entry.metadata().map(|m| m.len()).unwrap_or(0);
        plan.bytes += bytes;
        plan.files.push(Entry { name: file, path: entry.path(), bytes });
      }
    }
  }
  Ok(())
}

/// The zip: the plan's files under [`FOLDER_ENTRY`], then the database snapshot as [`DATABASE_ENTRY`], each streamed
/// in. `progress` hears the bytes read so far. A file gone since it was counted is skipped; any other error stops it.
pub fn write<W: Write + Seek>(plan: &Plan, database: &Path, out: W, mut progress: impl FnMut(u64)) -> io::Result<W> {
  let mut zip = ZipWriter::new(out);
  // Fast over small: the database and text compress well at the lowest level, and a phone is slow at the highest.
  let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated).compression_level(Some(1)).large_file(true);
  let mut done = 0u64;
  let mut buf = vec![0u8; 256 * 1024];
  let mut copy = |zip: &mut ZipWriter<W>, mut from: File, done: &mut u64| -> io::Result<()> {
    loop {
      let n = from.read(&mut buf)?;
      if n == 0 {
        return Ok(());
      }
      zip.write_all(&buf[..n])?;
      *done += n as u64;
      progress(*done);
    }
  };
  let dated = |path: &Path| match modified(path) {
    Some(time) => options.last_modified_time(time),
    None => options,
  };
  for entry in &plan.files {
    let Ok(file) = File::open(&entry.path) else { continue };
    zip.start_file(format!("{FOLDER_ENTRY}{}", entry.name), dated(&entry.path)).map_err(io::Error::other)?;
    copy(&mut zip, file, &mut done)?;
  }
  zip.start_file(DATABASE_ENTRY, dated(database)).map_err(io::Error::other)?;
  copy(&mut zip, File::open(database)?, &mut done)?;
  zip.finish().map_err(io::Error::other)
}

#[cfg(test)]
mod tests {
  use super::*;

  fn tree(name: &str) -> PathBuf {
    let root = std::env::temp_dir().join(format!("backup-{name}-{}", std::process::id()));
    let _ = fs::remove_dir_all(&root);
    for (path, text) in [
      ("config.toml", "[daemon]\n"),
      ("data/nouride.db", "open"),
      ("data/secrets.enc", "sealed in the clear"),
      ("data/secrets.sealed", "sealed"),
      ("workspace/shared/app/index.ts", "x"),
      ("workspace/shared/app/node_modules/a/index.js", "y"),
    ] {
      let file = root.join(path);
      fs::create_dir_all(file.parent().unwrap()).unwrap();
      fs::write(file, text).unwrap();
    }
    root
  }

  #[test]
  fn a_zip_fits_only_with_room_to_spare() {
    assert!(fits(10, 10 + HEADROOM));
    assert!(!fits(11, 10 + HEADROOM));
    assert!(!fits(u64::MAX, u64::MAX));
  }

  #[test]
  fn the_plan_takes_what_goes_in_and_never_follows_a_link() {
    let root = tree("plan");
    let outside = root.with_extension("outside");
    fs::create_dir_all(&outside).unwrap();
    fs::write(outside.join("private.txt"), "not Nouride's").unwrap();
    std::os::unix::fs::symlink(&outside, root.join("workspace/shared/elsewhere")).unwrap();
    let plan = plan(&root).unwrap();
    let names: Vec<&str> = plan.files.iter().map(|e| e.name.as_str()).collect();
    assert_eq!(names, ["config.toml", "data/secrets.sealed", "workspace/shared/app/index.ts"]);
    assert_eq!(plan.bytes, 9 + 6 + 1);
    fs::remove_dir_all(&root).unwrap();
    fs::remove_dir_all(&outside).unwrap();
  }

  #[test]
  fn the_zip_holds_the_plan_and_the_snapshot_as_the_database() {
    let root = tree("zip");
    let snapshot = root.with_extension("db");
    fs::write(&snapshot, "snapshot").unwrap();
    let mut heard = 0;
    let out = write(&plan(&root).unwrap(), &snapshot, io::Cursor::new(Vec::new()), |n| heard = n).unwrap();
    let mut zip = zip::ZipArchive::new(io::Cursor::new(out.into_inner())).unwrap();
    let names: Vec<String> = (0..zip.len()).map(|i| zip.by_index(i).unwrap().name().unwrap().into_owned()).collect();
    assert_eq!(names, [".nouride/config.toml", ".nouride/data/secrets.sealed", ".nouride/workspace/shared/app/index.ts", ".nouride/data/nouride.db"]);
    let mut db = String::new();
    zip.by_name(DATABASE_ENTRY).unwrap().read_to_string(&mut db).unwrap();
    assert_eq!(db, "snapshot");
    assert_eq!(heard, 16 + 8);
    // Each file keeps its own time, not the zip's.
    let old = std::time::SystemTime::UNIX_EPOCH + std::time::Duration::from_secs(1_700_000_000);
    File::options().write(true).open(root.join("config.toml")).unwrap().set_modified(old).unwrap();
    let out = write(&plan(&root).unwrap(), &snapshot, io::Cursor::new(Vec::new()), |_| {}).unwrap();
    let mut zip = zip::ZipArchive::new(io::Cursor::new(out.into_inner())).unwrap();
    assert_eq!(zip.by_name(".nouride/config.toml").unwrap().last_modified().unwrap().year(), 2023);
    fs::remove_dir_all(&root).unwrap();
    fs::remove_file(&snapshot).unwrap();
  }
}
