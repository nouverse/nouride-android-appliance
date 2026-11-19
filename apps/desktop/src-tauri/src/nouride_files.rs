//! Settings' "Download Nouride's files": the Nouride this app installed, as one zip in Downloads (`spec/backup.md`).
//! What goes in, the snapshot's answer and the zip are the `backup` crate's; the words `looks::backup`'s. This counts,
//! has Nouride take its database's snapshot, and writes the zip on a thread of its own.

use std::fs::{self, File};
use std::io::BufWriter;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use looks::backup::{Backup, Step};
use tauri::{AppHandle, Manager};

use crate::applog;
use crate::poller;
use crate::state::App;

/// The `.nouride` folder of the app's install.
fn folder(app: &App) -> PathBuf {
  app.layout.install().join(".nouride")
}

fn set(handle: &AppHandle, app: &App, change: impl FnOnce(&mut Backup)) {
  change(&mut app.inner.lock().unwrap().backup);
  poller::send(handle, app);
}

/// What the zip may weigh: the files that go in and the database as it is now, counted when Settings is shown.
pub fn count(handle: &AppHandle, app: &Arc<App>) {
  if !app.ours() || app.inner.lock().unwrap().backup.step.busy() {
    return;
  }
  let (handle, app) = (handle.clone(), app.clone());
  std::thread::spawn(move || {
    let root = folder(&app);
    let Ok(plan) = backup::plan(&root) else { return };
    let database: u64 = ["data/nouride.db", "data/nouride.db-wal"].iter().filter_map(|p| fs::metadata(root.join(p)).ok()).map(|m| m.len()).sum();
    let sealed = root.join("data/secrets.sealed").is_file();
    set(&handle, &app, |b| {
      b.size = Some(plan.bytes + database);
      b.sealed = sealed;
    });
  });
}

/// The zip, from the snapshot to the line that says where it is.
pub fn start(handle: &AppHandle, app: &Arc<App>) {
  if !app.ours() || app.inner.lock().unwrap().backup.step.busy() {
    return;
  }
  set(handle, app, |b| {
    b.step = Step::Snapshot;
    b.done = 0;
    b.total = 0;
  });
  let (handle, app) = (handle.clone(), app.clone());
  std::thread::spawn(move || {
    let step = make(&handle, &app);
    applog::write(&app.layout.app_log(), &format!("nouride's files: {step:?}"));
    set(&handle, &app, |b| b.step = step);
  });
}

fn make(handle: &AppHandle, app: &App) -> Step {
  let Some(snapshot) = snapshot(app) else { return Step::NoSnapshot };
  let root = folder(app);
  let Ok(plan) = backup::plan(&root) else { return Step::Failed };
  let total = plan.bytes + fs::metadata(&snapshot).map(|m| m.len()).unwrap_or(0);
  let Ok(downloads) = handle.path().download_dir() else { return Step::Failed };
  set(handle, app, |b| b.size = Some(total));
  if !backup::fits(total, free_bytes(&downloads)) {
    return Step::NoRoom;
  }
  set(handle, app, |b| {
    b.step = Step::Saving;
    b.total = total;
    b.sealed = root.join("data/secrets.sealed").is_file();
  });
  let path = downloads::free_path(&downloads, &backup::zip_name_today());
  let mut heard = 0;
  let written = File::create(&path).and_then(|file| {
    backup::write(&plan, &snapshot, BufWriter::new(file), |done| {
      // The screen hears every 4 MiB, not every read.
      if done - heard >= 4 * 1024 * 1024 || done == total {
        heard = done;
        set(handle, app, |b| b.done = done);
      }
    })
  });
  match written.and_then(|out| out.into_inner().map_err(|e| e.into_error())?.sync_all()) {
    Ok(()) => {
      let name = path.file_name().and_then(|n| n.to_str()).map(str::to_owned);
      set(handle, app, |b| {
        b.saved = name;
        b.done = total;
      });
      Step::Saved
    }
    Err(e) => {
      applog::write(&app.layout.app_log(), &format!("nouride's files: the zip failed: {e}"));
      let _ = fs::remove_file(&path);
      if backup::fits(0, free_bytes(&downloads)) { Step::Failed } else { Step::NoRoom }
    }
  }
}

/// `nouride db backup --json`, run as the installer runs, with the app's own `HOME` and `PATH`: the snapshot's path.
fn snapshot(app: &App) -> Option<PathBuf> {
  let inherited: Vec<(String, String)> = std::env::vars().collect();
  let output = std::process::Command::new(app.layout.binary())
    .args(["db", "backup", "--json"])
    .current_dir(app.layout.install())
    .env_clear()
    .envs(daemon::installer::env(&app.layout, &inherited))
    .output()
    .ok()?;
  let text = String::from_utf8_lossy(&output.stdout);
  applog::write(&app.layout.app_log(), &format!("nouride db backup: exit {:?}", output.status.code()));
  backup::snapshot(&text).map(PathBuf::from).filter(|p| p.is_file())
}

/// Bytes free where `dir` is.
fn free_bytes(dir: &Path) -> u64 {
  use std::os::unix::ffi::OsStrExt;
  let Ok(path) = std::ffi::CString::new(dir.as_os_str().as_bytes()) else { return 0 };
  let mut stat: libc::statvfs = unsafe { std::mem::zeroed() };
  // SAFETY: `path` is a valid C string and `stat` a zeroed struct statvfs writes into.
  if unsafe { libc::statvfs(path.as_ptr(), &mut stat) } != 0 {
    return 0;
  }
  (stat.f_bavail as u64).saturating_mul(stat.f_frsize as u64)
}
