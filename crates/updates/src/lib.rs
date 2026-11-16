//! Nouride's own update, read the way its dashboard reads it, and the notice that says one is ready. Android 0.2.0's
//! `NourideUpdate`, `NourideUpdates`, `NourideNotice`, `StallWatch` and the Nouride half of `VersionLooks`, with their
//! tests. The app's own update is Tauri's updater, wired in `apps/desktop`.

pub mod client;
pub mod notice;
pub mod nouride;
pub mod stall;

pub use nouride::{Download, DownloadStatus, NourideStatus, Restart};
