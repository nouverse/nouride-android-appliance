//! The Versions screen's two cards: this app's version and Nouride's, each with one sentence and one button.
//! Android's `VersionLooks`, with its tests.

use serde::Serialize;
use shared::{AppStep, NourideStep};

/// What a card's one button does: ask again, fetch and put in the new version, put in the one already fetched.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Button {
  Check,
  Update,
  Install,
  None,
}

/// A card's sentence and button. `words` and `label` may hold `{version}` and `{percent}`; `label` is `None` with no
/// button.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct VersionLook {
  pub words: &'static str,
  pub button: Button,
  pub label: Option<&'static str>,
  pub progress: bool,
}

const fn v(words: &'static str, button: Button, label: Option<&'static str>, progress: bool) -> VersionLook {
  VersionLook { words, button, label, progress }
}

pub fn app(step: AppStep) -> VersionLook {
  use Button as B;
  match step {
    AppStep::Checking => v("checking", B::None, None, false),
    AppStep::UpToDate => v("up_to_date", B::Check, Some("check_updates"), false),
    AppStep::Ready => v("versions_app_ready", B::Update, Some("versions_update_to"), false),
    AppStep::CheckFailed => v("check_failed", B::Check, Some("update_retry"), false),
    AppStep::Downloading => v("versions_downloading", B::None, None, true),
    AppStep::Installing => v("update_installing", B::None, None, false),
    AppStep::DownloadFailed => v("update_failed", B::Update, Some("update_retry"), false),
    AppStep::InstallFailed => v("update_install_failed", B::Update, Some("update_retry"), false),
  }
}

/// The app's card in a build: the Dev build is never offered an update, as on Android, so its card says so instead.
pub fn app_card(step: AppStep, dev: bool) -> VersionLook {
  if dev {
    v("versions_app_dev", Button::None, None, false)
  } else {
    app(step)
  }
}

pub fn nouride(step: NourideStep) -> VersionLook {
  use Button as B;
  match step {
    NourideStep::Off => v("versions_nouride_off", B::None, None, false),
    NourideStep::Checking => v("checking", B::None, None, false),
    NourideStep::UpToDate => v("up_to_date", B::Check, Some("check_updates"), false),
    NourideStep::Ready => v("versions_nouride_ready", B::Update, Some("versions_update_to"), false),
    NourideStep::Cannot => v("versions_nouride_cannot", B::Check, Some("check_updates"), false),
    NourideStep::CheckFailed => v("check_failed", B::Check, Some("update_retry"), false),
    NourideStep::Downloading => v("versions_downloading", B::None, None, true),
    NourideStep::Downloaded => v("versions_nouride_downloaded", B::Install, Some("versions_install"), false),
    NourideStep::Installing => v("versions_nouride_installing", B::None, None, true),
    NourideStep::RestartingNow => v("versions_nouride_restarting", B::None, None, true),
    NourideStep::RestartingIdle => v("versions_nouride_restarts_idle", B::None, None, false),
    NourideStep::RestartingJobs => v("versions_nouride_restarts_jobs", B::None, None, false),
    NourideStep::DownloadFailed => v("update_failed", B::Update, Some("update_retry"), false),
    NourideStep::InstallFailed => v("update_install_failed", B::Install, Some("update_retry"), false),
    NourideStep::NotOurs => v("versions_nouride_not_ours", B::None, None, false),
  }
}

pub const APP_STEPS: [AppStep; 8] = [
  AppStep::Checking,
  AppStep::UpToDate,
  AppStep::Ready,
  AppStep::CheckFailed,
  AppStep::Downloading,
  AppStep::Installing,
  AppStep::DownloadFailed,
  AppStep::InstallFailed,
];

pub const NOURIDE_STEPS: [NourideStep; 15] = [
  NourideStep::Off,
  NourideStep::Checking,
  NourideStep::UpToDate,
  NourideStep::Ready,
  NourideStep::Cannot,
  NourideStep::CheckFailed,
  NourideStep::Downloading,
  NourideStep::Downloaded,
  NourideStep::Installing,
  NourideStep::RestartingNow,
  NourideStep::RestartingIdle,
  NourideStep::RestartingJobs,
  NourideStep::DownloadFailed,
  NourideStep::InstallFailed,
  NourideStep::NotOurs,
];

/// Every look of both cards.
pub fn all() -> Vec<VersionLook> {
  APP_STEPS.iter().map(|s| app(*s)).chain([app_card(AppStep::Ready, true)]).chain(NOURIDE_STEPS.iter().map(|s| nouride(*s))).collect()
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn every_step_has_words_and_a_button_has_a_label() {
    for look in all() {
      assert!(!look.words.is_empty());
      assert_eq!(look.button == Button::None, look.label.is_none(), "{look:?}");
    }
  }

  #[test]
  fn nothing_to_press_while_something_moves() {
    for look in all().into_iter().filter(|l| l.progress) {
      assert_eq!(look.button, Button::None);
    }
  }

  #[test]
  fn a_newer_nouride_is_offered_as_update_and_a_fetched_one_as_install() {
    assert_eq!(nouride(NourideStep::Ready).button, Button::Update);
    assert_eq!(nouride(NourideStep::Downloaded).button, Button::Install);
  }

  #[test]
  fn a_failed_download_tries_the_download_again_a_failed_install_the_install() {
    assert_eq!(nouride(NourideStep::DownloadFailed).button, Button::Update);
    assert_eq!(nouride(NourideStep::InstallFailed).button, Button::Install);
  }

  #[test]
  fn the_dev_build_is_never_offered_an_update() {
    for step in APP_STEPS {
      assert_eq!(app_card(step, true).button, Button::None);
    }
    assert_eq!(app_card(AppStep::Ready, false).button, Button::Update);
  }

  #[test]
  fn nouride_off_or_not_the_apps_offers_nothing_to_press() {
    assert_eq!(nouride(NourideStep::Off).button, Button::None);
    assert_eq!(nouride(NourideStep::NotOurs).button, Button::None);
  }
}
