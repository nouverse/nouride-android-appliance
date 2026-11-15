//! What the screens can ask, each the same as a button or a menu item. The rules are the crates'.

use std::sync::Arc;

use place::Read;
use serde::Serialize;
use shared::{Reply, Where};
use tauri::{AppHandle, State};
use tauri_plugin_clipboard_manager::ClipboardExt;

use crate::applog;
use crate::poller;
use crate::state::{App, Appearance, Snapshot};
use crate::{setup, versions, windows};

type AppState<'a> = State<'a, Arc<App>>;

#[tauri::command]
pub fn state(app: AppState) -> Snapshot {
  app.snapshot()
}

/// "Run it on this computer": a Nouride already answering on this build's port is used rather than a second one;
/// otherwise the first setup starts at once (the press is the person asking for it). Either way, from another
/// computer, this computer's own Nouride comes back as it was.
pub fn here_now(handle: &AppHandle, app: &Arc<App>, version: Option<String>) {
  let _ = app.place().here();
  applog::write(&app.layout.app_log(), "place: this computer");
  if app.ours() {
    start_now(handle, app);
  } else if poller::fetch(&app.build.health_url()).nouride_answered() {
    app.set_adopted(true);
    applog::write(&app.layout.app_log(), "a Nouride already answers on this computer: using it");
  } else {
    app.set_adopted(false);
    setup::first(handle.clone(), app.clone(), version);
  }
  poller::tick(handle, app, &mut 0);
}

#[tauri::command]
pub fn choose_here(handle: AppHandle, app: AppState) {
  here_now(&handle, &app, None);
}

#[derive(Serialize)]
#[serde(untagged)]
pub enum AddressAnswer {
  Ok { ok: bool, address: String },
  No { ok: bool, error: &'static str },
}

/// The address card's Connect: read, then checked once, as a typo is the likeliest cause of nothing answering.
pub fn remote_now(handle: &AppHandle, app: &Arc<App>, typed: &str) -> AddressAnswer {
  let no = |error| AddressAnswer::No { ok: false, error };
  let base = match place::remote_host::read(typed) {
    Read::Address(base) => base,
    Read::Empty => return no("where_empty"),
    Read::PlainHttp => return no("where_plain_http"),
    Read::NotAnAddress => return no("where_not_an_address"),
  };
  let reply = place::remote_reply::of(&poller::fetch(&place::remote_reply::health_url(&base)));
  if reply == Reply::Down {
    return no("where_unreachable");
  }
  // Nouride here turns off: its agents stop answering until the person switches back.
  if app.ours() {
    let _ = service::Supervisor::stop(&app.agent);
  }
  let _ = app.place().remote(&base);
  app.inner.lock().unwrap().remote_reply = Some(reply);
  applog::write(&app.layout.app_log(), &format!("place: another computer, {base}"));
  poller::tick(handle, app, &mut crate::applog::now_ms());
  AddressAnswer::Ok { ok: true, address: place::remote_host::shown(&base).to_owned() }
}

#[tauri::command]
pub async fn choose_remote(handle: AppHandle, app: AppState<'_>, typed: String) -> Result<AddressAnswer, ()> {
  let app = app.inner().clone();
  tauri::async_runtime::spawn_blocking(move || remote_now(&handle, &app, &typed)).await.map_err(|_| ())
}

pub fn start_now(handle: &AppHandle, app: &Arc<App>) {
  if app.where_() != Some(Where::Here) {
    return;
  }
  if !app.ours() {
    return here_now(handle, app, None);
  }
  app.inner.lock().unwrap().tracker.restart();
  if let Err(e) = service::Supervisor::start(&app.agent) {
    applog::write(&app.layout.app_log(), &format!("start: {e}"));
  }
  poller::tick(handle, app, &mut 0);
}

#[tauri::command]
pub fn start(handle: AppHandle, app: AppState) {
  start_now(&handle, &app);
}

/// Try again: the setup again when it never finished, the other computer asked again, or Nouride started again.
pub fn retry_now(handle: &AppHandle, app: &Arc<App>) {
  match app.where_() {
    Some(Where::Remote) => {
      app.inner.lock().unwrap().remote_reply = None;
      poller::send(handle, app);
      poller::check_remote(app);
    }
    _ if app.adopted() => {}
    _ if !app.ours() => setup::first(handle.clone(), app.clone(), None),
    // launchd restarts Nouride on its own; the app adds a restart only when no update is proving itself.
    _ if !app.layout.may_restart() => {}
    _ => {
      app.inner.lock().unwrap().tracker.restart();
      let _ = service::Supervisor::restart(&app.agent);
    }
  }
  poller::tick(handle, app, &mut 0);
}

#[tauri::command]
pub async fn retry(handle: AppHandle, app: AppState<'_>) -> Result<(), ()> {
  let app = app.inner().clone();
  tauri::async_runtime::spawn_blocking(move || retry_now(&handle, &app)).await.map_err(|_| ())
}

pub fn turn_off_now(handle: &AppHandle, app: &Arc<App>) {
  if !app.ours() {
    return;
  }
  app.inner.lock().unwrap().stopping = true;
  poller::tick(handle, app, &mut 0);
  if let Err(e) = service::Supervisor::stop(&app.agent) {
    applog::write(&app.layout.app_log(), &format!("turn off: {e}"));
  }
  applog::write(&app.layout.app_log(), "turned off");
  poller::tick(handle, app, &mut 0);
}

#[tauri::command]
pub async fn turn_off(handle: AppHandle, app: AppState<'_>) -> Result<(), ()> {
  let app = app.inner().clone();
  tauri::async_runtime::spawn_blocking(move || turn_off_now(&handle, &app)).await.map_err(|_| ())
}

#[tauri::command]
pub async fn open_dashboard(handle: AppHandle, app: AppState<'_>) -> Result<(), ()> {
  let app = app.inner().clone();
  tauri::async_runtime::spawn_blocking(move || windows::dashboard::open(&handle, &app)).await.map_err(|_| ())
}

/// Connect's switch: `HOST` in the install's `.env`, then Nouride started again, which reads it.
pub fn access_now(handle: &AppHandle, app: &Arc<App>, open: bool) {
  // A restart now would count against an update still proving itself (`Layout::update_pending`).
  if !app.ours() || !app.layout.may_restart() {
    return;
  }
  let changed = daemon::env_file::rewrite(&app.layout.env_file(), |env| daemon::network_access::with_access(env, open));
  applog::write(&app.layout.app_log(), &format!("open to other devices: {open} ({changed:?})"));
  if let Ok(true) = changed {
    let _ = service::Supervisor::restart(&app.agent);
  }
  poller::tick(handle, app, &mut 0);
}

#[tauri::command]
pub async fn set_access(handle: AppHandle, app: AppState<'_>, open: bool) -> Result<(), ()> {
  let app = app.inner().clone();
  tauri::async_runtime::spawn_blocking(move || access_now(&handle, &app, open)).await.map_err(|_| ())
}

#[tauri::command]
pub fn set_appearance(handle: AppHandle, app: AppState, appearance: Appearance) {
  app.set_appearance(appearance);
  windows::apply_appearance(&handle, appearance);
  poller::send(&handle, &app);
}

#[tauri::command]
pub fn check_app(handle: AppHandle, app: AppState) {
  versions::check_app(handle, app.inner().clone());
}

#[tauri::command]
pub fn update_app(handle: AppHandle, app: AppState) {
  versions::update_app(handle, app.inner().clone());
}

#[tauri::command]
pub fn check_nouride(handle: AppHandle, app: AppState) {
  versions::check_nouride(handle, app.inner().clone());
}

#[tauri::command]
pub fn update_nouride(handle: AppHandle, app: AppState) {
  versions::update_nouride(handle, app.inner().clone());
}

#[tauri::command]
pub fn install_nouride(handle: AppHandle, app: AppState) {
  versions::install_nouride(handle, app.inner().clone());
}

#[derive(Serialize)]
pub struct Logs {
  summary: String,
  parts: Vec<Part>,
}

#[derive(Serialize)]
pub struct Part {
  name: String,
  text: String,
}

/// Technical Logs: the technical words the other screens never show, for whoever helps.
#[tauri::command]
pub fn logs(app: AppState) -> Logs {
  let s = app.snapshot();
  let inner = app.inner.lock().unwrap();
  let facts = [
    ("App", format!("{} {} ({})", app.build.name, s.app_version, app.build.id)),
    ("Where", format!("{:?}", s.place)),
    ("Address", match s.place {
      Some(Where::Remote) => app.place().last_address().unwrap_or_default(),
      _ => app.build.origin(),
    }),
    ("Phase", format!("{:?}, answering {}", inner.phase, inner.answering)),
    ("Health", inner.health.as_ref().map(|h| h.summary()).unwrap_or_else(|| "not asked".into())),
    ("Remote", format!("{:?}", inner.remote_reply)),
    ("Installed by the app", format!("{}", s.ours)),
    ("Already on this computer", format!("{}", s.adopted)),
    ("LaunchAgent", format!("{} at {}", app.build.agent_label, app.agent.path.display())),
    ("Job", format!("loaded {}, pid {:?}, last exit {:?}", inner.job.loaded, inner.job.pid, inner.job.last_exit)),
    ("Install folder", app.layout.install().display().to_string()),
    ("Open to other devices", format!("{} (network address {:?})", s.open_to_others, inner.lan)),
    ("Ports", format!("Nouride {}, gateway {}", app.build.port, app.build.gateway_port)),
  ];
  drop(inner);
  let summary = facts.iter().map(|(k, v)| format!("{k}: {v}")).collect::<Vec<_>>().join("\n");
  let read = |path: &std::path::Path| daemon::support::read_tail(path, 400, 64 * 1024);
  Logs {
    summary,
    parts: vec![
      Part { name: "Nouride Home".into(), text: read(&app.layout.app_log()) },
      Part { name: "Installer".into(), text: read(&app.layout.installer_log()) },
      Part { name: "Nouride".into(), text: read(&app.layout.nouride_log()) },
    ],
  }
}

#[tauri::command]
pub fn copy(handle: AppHandle, text: String) {
  let _ = handle.clipboard().write_text(text);
}

/// Connect's code, as SVG, dark on white.
#[tauri::command]
pub fn qr(text: String) -> String {
  qrcode::QrCode::new(text.as_bytes())
    .map(|code| code.render::<qrcode::render::svg::Color>().quiet_zone(false).min_dimensions(180, 180).build())
    .unwrap_or_default()
}
