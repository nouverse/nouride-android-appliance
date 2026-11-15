//! The app's one state, and the snapshot the screens and the menu bar are drawn from. Every rule it applies is a
//! crate's; this file only gathers what the crates decided. The snapshot's fields mirror `apps/ui/src/commons/lib/
//! ipc.ts`: a field is renamed in both or in neither.

use std::sync::Mutex;

use daemon::phase::{Seen, Tracker};
use daemon::progress::SetupProgress;
use daemon::Layout;
use looks::{tabs, versions, Look};
use serde::Serialize;
use service::launchd::LaunchAgent;
use service::{Job, System};
use shared::prefs::Prefs;
use shared::{AppStep, Build, Health, NourideStep, Phase, Reply, Where};
use updates::notice::NoticeStore;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, serde::Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Appearance {
  System,
  Light,
  Dark,
}

/// What the Versions screen's Nouride card is doing.
#[derive(Debug, Clone)]
pub struct NourideCard {
  pub step: NourideStep,
  pub ready: Option<String>,
  pub percent: u8,
}

#[derive(Debug, Clone)]
pub struct AppCard {
  pub step: AppStep,
  pub ready: Option<String>,
  pub percent: u8,
}

pub struct Inner {
  pub tracker: Tracker,
  pub phase: Phase,
  pub answering: bool,
  pub installing: bool,
  pub install_failed: bool,
  pub stopping: bool,
  pub progress: Option<SetupProgress>,
  pub job: Job,
  pub health: Option<Health>,
  pub remote_reply: Option<Reply>,
  pub lan: Option<String>,
  pub nouride_card: NourideCard,
  pub app_card: AppCard,
  pub app_update: Option<String>,
  pub last_sent: String,
}

pub struct App {
  pub build: Build,
  pub layout: Layout,
  pub agent: LaunchAgent<System>,
  pub inner: Mutex<Inner>,
}

const ADOPTED: &str = "adopted";
const APPEARANCE: &str = "appearance";

impl App {
  pub fn new(build: Build, layout: Layout, uid: u32, home: &std::path::Path) -> App {
    let agent = LaunchAgent::new(System, uid, build.agent_label, home);
    App {
      build,
      layout,
      agent,
      inner: Mutex::new(Inner {
        tracker: Tracker::default(),
        phase: Phase::Stopped,
        answering: false,
        installing: false,
        install_failed: false,
        stopping: false,
        progress: None,
        job: Job::default(),
        health: None,
        remote_reply: None,
        lan: None,
        nouride_card: NourideCard { step: NourideStep::Checking, ready: None, percent: 0 },
        app_card: AppCard { step: AppStep::Checking, ready: None, percent: 0 },
        app_update: None,
        last_sent: String::new(),
      }),
    }
  }

  pub fn prefs(&self) -> Prefs {
    Prefs::new(self.layout.prefs())
  }

  pub fn place(&self) -> place::Store {
    place::Store::new(self.prefs())
  }

  pub fn notices(&self) -> NoticeStore {
    NoticeStore::new(self.prefs())
  }

  pub fn where_(&self) -> Option<Where> {
    self.place().where_(self.layout.installed())
  }

  /// The app installed Nouride here: the only one it starts, turns off, updates and signs in to.
  pub fn ours(&self) -> bool {
    self.layout.installed()
  }

  /// A Nouride that was answering on this build's port before the app installed one.
  pub fn adopted(&self) -> bool {
    !self.ours() && self.prefs().get(ADOPTED).as_deref() == Some("1")
  }

  pub fn set_adopted(&self, adopted: bool) {
    let _ = self.prefs().set(&[(ADOPTED, if adopted { Some("1") } else { None })]);
  }

  pub fn appearance(&self) -> Appearance {
    match self.prefs().get(APPEARANCE).as_deref() {
      Some("light") => Appearance::Light,
      Some("dark") => Appearance::Dark,
      _ => Appearance::System,
    }
  }

  pub fn set_appearance(&self, appearance: Appearance) {
    let value = match appearance {
      Appearance::System => None,
      Appearance::Light => Some("light"),
      Appearance::Dark => Some("dark"),
    };
    let _ = self.prefs().set(&[(APPEARANCE, value)]);
  }

  pub fn open_to_others(&self) -> bool {
    daemon::env_file::read(&self.layout.env_file()).map(|env| daemon::network_access::open(&env)).unwrap_or(false)
  }

  /// The dashboard this app opens, and the origin its window keeps to.
  pub fn dashboard(&self) -> (String, String) {
    match (self.where_(), self.place().last_address()) {
      (Some(Where::Remote), Some(base)) => (place::remote_host::dashboard(&base), base),
      _ => (self.build.dashboard_url(), self.build.origin()),
    }
  }

  /// Moves the phase on from what was seen. Called by the poller with fresh observations.
  pub fn observe(&self, job: Job, health: Option<Health>, now_ms: u64) {
    let mut guard = self.inner.lock().unwrap();
    let inner = &mut *guard;
    let now = inner.tracker.next(Seen {
      installing: inner.installing,
      install_failed: inner.install_failed,
      loaded: job.loaded,
      stopping: inner.stopping,
      health: health.as_ref(),
      now_ms,
    });
    if inner.stopping && !job.loaded {
      inner.stopping = false;
    }
    inner.phase = now.phase;
    inner.answering = now.answering;
    inner.job = job;
    inner.health = health;
  }

  pub fn look(&self, inner: &Inner) -> Look {
    match self.where_() {
      Some(Where::Remote) => looks::status::remote(inner.remote_reply),
      _ if self.adopted() => looks::status::adopted(inner.health.as_ref().is_some_and(Health::nouride_answered)),
      _ if inner.install_failed => looks::status::setup_failed(),
      _ => looks::status::of(inner.phase, inner.answering),
    }
  }

  pub fn snapshot(&self) -> Snapshot {
    let inner = self.inner.lock().unwrap();
    let place = self.where_();
    let look = self.look(&inner);
    let ours = self.ours();
    let adopted = self.adopted();
    let open_to_others = self.open_to_others();
    let here = place == Some(Where::Here);
    let running_version = inner.health.as_ref().and_then(|h| h.version.clone()).map(|v| v.trim_start_matches('v').to_owned());
    let nouride_update = if here && ours { self.notices().waiting(running_version.as_deref()) } else { None };
    let app_update = if self.build.dev { None } else { inner.app_update.clone() };
    let first_start = inner.progress.is_some();
    let reach = if place == Some(Where::Remote) {
      tabs::Reach::Ready
    } else {
      tabs::reach(&look, inner.lan.as_deref(), open_to_others || adopted)
    };
    let share_url = match place {
      Some(Where::Remote) => self.place().last_address().map(|b| place::remote_host::dashboard(&b)),
      _ if reach == tabs::Reach::Ready => inner.lan.as_ref().map(|ip| format!("http://{ip}:{}/dashboard/", self.build.port)),
      _ => None,
    };
    let app_look = versions::app_card(inner.app_card.step, self.build.dev);
    let nouride_step = if !here {
      NourideStep::Off
    } else if !ours {
      NourideStep::NotOurs
    } else if !(inner.phase == Phase::Running && inner.answering) && matches!(inner.nouride_card.step, NourideStep::Checking | NourideStep::UpToDate | NourideStep::Ready | NourideStep::CheckFailed | NourideStep::Cannot) {
      NourideStep::Off
    } else {
      inner.nouride_card.step
    };
    let nouride_look = versions::nouride(nouride_step);
    Snapshot {
      app_name: self.build.name.to_owned(),
      app_version: env!("CARGO_PKG_VERSION").to_owned(),
      dev: self.build.dev,
      place,
      address: self.place().address(self.layout.installed()).map(|b| place::remote_host::shown(&b).to_owned()),
      last_address: self.place().last_address().map(|b| place::remote_host::shown(&b).to_owned()),
      ours,
      adopted,
      phase: inner.phase,
      first_start,
      progress: inner.progress.as_ref().map(|p| {
        let s = p.snapshot();
        Progress { step: s.step, number: s.step.number(), of: daemon::progress::Step::ALL.len(), percent: s.percent }
      }),
      bar_shown: tabs::bar_shown(&look, first_start),
      can_turn_off: here && ours && looks::status::can_turn_off(inner.phase),
      reach,
      share_url,
      open_to_others,
      access_switch: here && ours && !inner.installing,
      nouride_version: running_version.clone(),
      nouride_update: nouride_update.clone(),
      app_update: app_update.clone(),
      more_dot: looks::notices::more_dot(app_update.is_some(), nouride_update.is_some()),
      versions_row: looks::notices::versions_row(app_update.is_some(), nouride_update.is_some()),
      appearance: self.appearance(),
      versions: Versions {
        app: Card {
          version: Some(env!("CARGO_PKG_VERSION").to_owned()),
          ready: inner.app_card.ready.clone(),
          percent: inner.app_card.percent,
          words: app_look.words,
          button: app_look.button,
          label: app_look.label,
          progress: app_look.progress,
        },
        nouride: Card {
          version: running_version,
          ready: inner.nouride_card.ready.clone(),
          percent: inner.nouride_card.percent,
          words: nouride_look.words,
          button: nouride_look.button,
          label: nouride_look.label,
          progress: nouride_look.progress,
        },
      },
      look,
    }
  }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Progress {
  pub step: daemon::progress::Step,
  pub number: usize,
  pub of: usize,
  pub percent: u8,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Card {
  pub version: Option<String>,
  pub ready: Option<String>,
  pub percent: u8,
  pub words: &'static str,
  pub button: versions::Button,
  pub label: Option<&'static str>,
  pub progress: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct Versions {
  pub app: Card,
  pub nouride: Card,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
  pub app_name: String,
  pub app_version: String,
  pub dev: bool,
  pub place: Option<Where>,
  pub address: Option<String>,
  pub last_address: Option<String>,
  pub ours: bool,
  pub adopted: bool,
  pub phase: Phase,
  pub look: Look,
  pub first_start: bool,
  pub progress: Option<Progress>,
  pub bar_shown: bool,
  pub can_turn_off: bool,
  pub reach: tabs::Reach,
  pub share_url: Option<String>,
  pub open_to_others: bool,
  pub access_switch: bool,
  pub nouride_version: Option<String>,
  pub nouride_update: Option<String>,
  pub app_update: Option<String>,
  pub more_dot: bool,
  pub versions_row: Option<&'static str>,
  pub appearance: Appearance,
  pub versions: Versions,
}
