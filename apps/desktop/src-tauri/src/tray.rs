//! The menu bar's icon and menu. Which items show is `looks::tray`; their words are the app's words file.

use std::sync::{Arc, Mutex};

use looks::tray::{self as menu_rules, Item};
use shared::Where;
use tauri::image::Image;
use tauri::menu::{Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::{TrayIcon, TrayIconBuilder};
use tauri::AppHandle;

use crate::state::{App, Snapshot};
use crate::words::t;
use crate::{commands, windows};

pub const ID: &str = "nouride-home";

/// The items last drawn, for the e2e dump.
pub static LAST: Mutex<Vec<(String, String, bool)>> = Mutex::new(Vec::new());

pub fn create(handle: &AppHandle, app: &Arc<App>) -> tauri::Result<TrayIcon> {
  let icon = Image::from_bytes(include_bytes!("../icons/tray.png"))?;
  let menu = build_menu(handle, &items(app, &app.snapshot()))?;
  let app = app.clone();
  TrayIconBuilder::with_id(ID)
    .icon(icon)
    .icon_as_template(true)
    .tooltip(app.build.name)
    .menu(&menu)
    .show_menu_on_left_click(true)
    .on_menu_event(move |handle, event| press(handle, &app, event.id().as_ref()))
    .build(handle)
}

pub fn items(app: &App, s: &Snapshot) -> Vec<Item> {
  let inner = app.inner.lock().unwrap();
  menu_rules::menu(menu_rules::Input {
    place: s.place,
    look: &s.look,
    ours: s.ours && s.place == Some(Where::Here),
    phase: inner.phase,
    app_update: s.app_update.is_some(),
    nouride_update: s.nouride_update.is_some(),
  })
}

fn build_menu(handle: &AppHandle, items: &[Item]) -> tauri::Result<Menu<tauri::Wry>> {
  let menu = Menu::new(handle)?;
  let mut last = vec![];
  for (i, item) in items.iter().enumerate() {
    // A line under the state, and one above Quit.
    if item.id == menu_rules::QUIT || i == 1 {
      menu.append(&PredefinedMenuItem::separator(handle)?)?;
    }
    let words = t(&item.words);
    menu.append(&MenuItem::with_id(handle, item.id, &words, item.enabled, None::<&str>)?)?;
    last.push((item.id.to_owned(), words, item.enabled));
  }
  *LAST.lock().unwrap() = last;
  Ok(menu)
}

pub fn redraw(handle: &AppHandle, app: &App, s: &Snapshot) {
  let Some(tray) = handle.tray_by_id(ID) else { return };
  if let Ok(menu) = build_menu(handle, &items(app, s)) {
    let _ = tray.set_menu(Some(menu));
  }
}

/// What a menu item does: the same as the button it stands for.
pub fn press(handle: &AppHandle, app: &Arc<App>, id: &str) {
  match id {
    menu_rules::OPEN_HOME => windows::main::show(handle),
    menu_rules::UPDATES => windows::main::show_at(handle, "versions"),
    menu_rules::OPEN_DASHBOARD => windows::dashboard::open(handle, app),
    menu_rules::START => commands::start_now(handle, app),
    // Turning off asks first, in the window, as More does.
    menu_rules::TURN_OFF => windows::main::show_at(handle, "more"),
    menu_rules::QUIT => handle.exit(0),
    _ => {}
  }
}
