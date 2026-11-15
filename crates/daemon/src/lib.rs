//! Nouride on this computer: where the app keeps it, how the official installer is run without touching anything
//! outside the app's folder, the first setup's progress, the install's `.env` and `config.toml`, the first dashboard
//! account, `/health`, and the phase Home shows. No Tauri; tested on Linux.

pub mod config;
pub mod env_file;
pub mod first_admin;
pub mod health;
pub mod installer;
pub mod lan;
pub mod layout;
pub mod network_access;
pub mod phase;
pub mod progress;
pub mod setup;
pub mod support;

pub use layout::Layout;
