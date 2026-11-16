//! The first setup of Nouride on this computer, in order: the official installer downloaded and run in the app's own
//! folder, with a `HOME` and a `PATH` of the app's own (`installer`); the gateway's port for a build off Nouride's
//! default; the first account in `.env` (`first_admin`); Nouride's own plist made with the same `HOME` and handed to
//! the app's LaunchAgent; then waiting for Nouride to answer, and taking the account out of `.env` once it has.
//! Blocking, on a thread of its own; every outside effect goes through [`Effects`], so the order is tested here.

use std::ffi::OsString;
use std::fs;
use std::io::{self, BufRead, BufReader, Write};
use std::path::Path;
use std::process::{Command, Stdio};
use std::time::Duration;

use shared::build::{DEFAULT_GATEWAY_PORT, INSTALL_URL};
use shared::Build;

use crate::progress::SetupProgress;
use crate::{config, env_file, first_admin, installer, Layout};

/// What the setup does to the world, so a test can stand in for it.
pub trait Effects {
  /// Fetches the installer into `to`.
  fn download_installer(&self, to: &Path) -> io::Result<()>;
  /// Runs `argv` with exactly `env`, handing each line it prints to `line`; its exit code.
  fn run(&self, argv: &[OsString], env: &[(String, OsString)], line: &mut dyn FnMut(&str)) -> io::Result<i32>;
  /// Puts the app's LaunchAgent in place from Nouride's generated plist, and starts it.
  fn install_agent(&self, generated_plist: &str) -> io::Result<()>;
  /// Whether Nouride answers `ok` now.
  fn answers(&self) -> bool;
  fn sleep(&self, how_long: Duration);
}

/// Where the setup is, for the screen.
pub trait Report {
  fn progress(&self, progress: &SetupProgress);
  /// A line for the installer's log, kept for Technical Logs.
  fn log(&self, line: &str);
}

/// How long the first boot may take before the setup gives up waiting (launchd keeps trying after).
pub const ANSWER_WAIT: Duration = Duration::from_secs(120);
const POLL: Duration = Duration::from_secs(1);

/// Runs the whole first setup. `inherited` is the app's own environment, of which the installer gets only what
/// [`installer::env`] passes on.
pub fn run(
  build: &Build,
  layout: &Layout,
  version: Option<&str>,
  inherited: &[(String, String)],
  fx: &dyn Effects,
  report: &dyn Report,
) -> io::Result<()> {
  let mut progress = SetupProgress::default();
  report.progress(&progress);
  fs::create_dir_all(layout.installer_dir())?;
  fs::create_dir_all(layout.home())?;
  fs::create_dir_all(layout.logs())?;

  report.log(&format!("downloading {INSTALL_URL}"));
  fx.download_installer(&layout.installer_script())?;
  let shim = installer::make_shim(&layout.shim(), installer::SYSTEM_DIRS)?;
  report.log(&format!("installer PATH: {shim} links, without launchctl"));

  let env = installer::env(layout, inherited);
  let mut argv: Vec<OsString> = vec!["/bin/sh".into(), layout.installer_script().into_os_string()];
  argv.extend(installer::args(build, layout, version));
  let code = fx.run(&argv, &env, &mut |line| {
    report.log(line);
    progress.line(line);
    report.progress(&progress);
  })?;
  if code != 0 || !layout.installed() {
    return Err(io::Error::other(format!("the installer exited {code}")));
  }

  if build.gateway_port != DEFAULT_GATEWAY_PORT {
    let path = layout.config_toml();
    let before = fs::read_to_string(&path).ok();
    fs::write(&path, config::with_gateway_port(before.as_deref(), build.gateway_port))?;
    report.log(&format!("gateway port {} in {}", build.gateway_port, path.display()));
  }

  let password = admin_password(layout)?;
  env_file::rewrite(&layout.env_file(), |env| first_admin::with_account(env, &password))?;

  let user = inherited.iter().find(|(k, _)| k == "USER").map(|(_, v)| v.clone()).unwrap_or_default();
  let service: Vec<OsString> = vec![
    layout.binary().into_os_string(),
    "service".into(),
    "install".into(),
    "--kind".into(),
    "launchd".into(),
    "--user".into(),
    user.into(),
    "--dir".into(),
    layout.install().into_os_string(),
  ];
  let code = fx.run(&service, &env, &mut |line| report.log(line))?;
  let plist = fs::read_to_string(layout.generated_plist())
    .map_err(|e| io::Error::other(format!("nouride service install exited {code} and wrote no plist: {e}")))?;

  progress.starting();
  report.progress(&progress);
  fx.install_agent(&plist)?;

  let mut waited = Duration::ZERO;
  while !fx.answers() {
    if waited >= ANSWER_WAIT {
      return Err(io::Error::other("Nouride did not answer after its first start"));
    }
    fx.sleep(POLL);
    waited += POLL;
    progress.line("waiting");
    report.progress(&progress);
  }
  // The account exists now: the file should not keep a password it no longer needs.
  env_file::rewrite(&layout.env_file(), first_admin::without_account)?;
  report.log("Nouride answers; the first account's lines are out of .env");
  Ok(())
}

/// The first account's password: the one kept from an earlier try, or a new one, kept by the app alone (0600).
pub fn admin_password(layout: &Layout) -> io::Result<String> {
  let path = layout.admin_secret();
  if let Ok(kept) = fs::read_to_string(&path) {
    if !kept.trim().is_empty() {
      return Ok(kept.trim().to_owned());
    }
  }
  let password = first_admin::new_password();
  let mut options = fs::OpenOptions::new();
  options.write(true).create(true).truncate(true);
  #[cfg(unix)]
  std::os::unix::fs::OpenOptionsExt::mode(&mut options, 0o600);
  options.open(&path)?.write_all(password.as_bytes())?;
  Ok(password)
}

/// The app's own password, or `None` once it was forgotten (the person chose their own).
pub fn kept_password(layout: &Layout) -> Option<String> {
  fs::read_to_string(layout.admin_secret()).ok().map(|s| s.trim().to_owned()).filter(|s| !s.is_empty())
}

/// Forgets the app's password: a 401 means the person chose their own.
pub fn forget_password(layout: &Layout) -> io::Result<()> {
  match fs::remove_file(layout.admin_secret()) {
    Err(e) if e.kind() != io::ErrorKind::NotFound => Err(e),
    _ => Ok(()),
  }
}

/// The real world: the installer over HTTPS, processes, the clock.
pub struct System<'a> {
  pub install_agent: &'a dyn Fn(&str) -> io::Result<()>,
  pub health_url: String,
}

impl Effects for System<'_> {
  fn download_installer(&self, to: &Path) -> io::Result<()> {
    let agent: ureq::Agent = ureq::Agent::config_builder().timeout_global(Some(Duration::from_secs(60))).build().into();
    let body = agent
      .get(INSTALL_URL)
      .call()
      .map_err(|e| io::Error::other(format!("downloading the installer: {e}")))?
      .into_body()
      .read_to_string()
      .map_err(io::Error::other)?;
    if !body.starts_with("#!") {
      return Err(io::Error::other("the installer's address answered something that is not a script"));
    }
    fs::write(to, body)
  }

  fn run(&self, argv: &[OsString], env: &[(String, OsString)], line: &mut dyn FnMut(&str)) -> io::Result<i32> {
    let (program, args) = argv.split_first().ok_or_else(|| io::Error::other("an empty command"))?;
    let mut child = Command::new(program)
      .args(args)
      .env_clear()
      .envs(env.iter().map(|(k, v)| (k, v)))
      .stdin(Stdio::null())
      .stdout(Stdio::piped())
      .stderr(Stdio::piped())
      .spawn()?;
    // Both streams into one, in the order they come: the installer warns on stderr.
    let (tx, rx) = std::sync::mpsc::channel::<String>();
    let out = child.stdout.take().map(|s| {
      let tx = tx.clone();
      std::thread::spawn(move || BufReader::new(s).lines().map_while(Result::ok).for_each(|l| drop(tx.send(l))))
    });
    let err = child.stderr.take().map(|s| {
      let tx = tx.clone();
      std::thread::spawn(move || BufReader::new(s).lines().map_while(Result::ok).for_each(|l| drop(tx.send(l))))
    });
    drop(tx);
    for l in rx {
      line(&l);
    }
    out.map(|t| t.join());
    err.map(|t| t.join());
    Ok(child.wait()?.code().unwrap_or(-1))
  }

  fn install_agent(&self, generated_plist: &str) -> io::Result<()> {
    (self.install_agent)(generated_plist)
  }

  fn answers(&self) -> bool {
    crate::health::fetch(&self.health_url, Duration::from_secs(3)).ok()
  }

  fn sleep(&self, how_long: Duration) {
    std::thread::sleep(how_long);
  }
}

#[cfg(test)]
mod tests {
  use super::*;
  use std::cell::{Cell, RefCell};

  /// A world in which the installer writes what the real one writes into the app's folder, and nothing else.
  struct Fake {
    layout: Layout,
    calls: RefCell<Vec<String>>,
    envs: RefCell<Vec<Vec<(String, OsString)>>>,
    answers_after: Cell<u32>,
    installer_exit: i32,
    env_at_agent: RefCell<String>,
  }

  impl Effects for Fake {
    fn download_installer(&self, to: &Path) -> io::Result<()> {
      self.calls.borrow_mut().push("download".into());
      fs::write(to, "#!/bin/sh\n")
    }

    fn run(&self, argv: &[OsString], env: &[(String, OsString)], line: &mut dyn FnMut(&str)) -> io::Result<i32> {
      let words: Vec<String> = argv.iter().map(|a| a.to_string_lossy().into_owned()).collect();
      self.envs.borrow_mut().push(env.to_vec());
      if words[0] == "/bin/sh" {
        self.calls.borrow_mut().push("installer".into());
        let install = self.layout.install();
        fs::create_dir_all(install.join(".nouride")).unwrap();
        fs::write(install.join("nouride"), "").unwrap();
        fs::write(install.join(".env"), "PORT=18264\nHOST=127.0.0.1\n").unwrap();
        fs::write(install.join(".nouride/config.toml"), "[nougate]\nin_process = true\n").unwrap();
        line("\u{1b}[1m▸ Fetching the release\u{1b}[0m");
        line("\u{1b}[1m▸ Installing the binary\u{1b}[0m");
        return Ok(self.installer_exit);
      }
      self.calls.borrow_mut().push(format!("{} {}", words[1], words[2]));
      fs::write(self.layout.generated_plist(), "<key>Label</key><string>tech.nouverse.nouride</string>").unwrap();
      Ok(0)
    }

    fn install_agent(&self, generated_plist: &str) -> io::Result<()> {
      self.calls.borrow_mut().push("agent".into());
      assert!(generated_plist.contains("tech.nouverse.nouride"));
      *self.env_at_agent.borrow_mut() = fs::read_to_string(self.layout.env_file()).unwrap();
      Ok(())
    }

    fn answers(&self) -> bool {
      let left = self.answers_after.get();
      self.answers_after.set(left.saturating_sub(1));
      left == 0
    }

    fn sleep(&self, _: Duration) {}
  }

  struct Quiet(RefCell<Vec<u8>>);

  impl Report for Quiet {
    fn progress(&self, p: &SetupProgress) {
      self.0.borrow_mut().push(p.snapshot().percent);
    }
    fn log(&self, _: &str) {}
  }

  fn fake(name: &str, installer_exit: i32, answers_after: u32) -> Fake {
    let dir = std::env::temp_dir().join(format!("daemon-setup-{}-{name}", std::process::id()));
    let _ = fs::remove_dir_all(&dir);
    Fake {
      layout: Layout::new(&dir),
      calls: RefCell::new(vec![]),
      envs: RefCell::new(vec![]),
      answers_after: Cell::new(answers_after),
      installer_exit,
      env_at_agent: RefCell::new(String::new()),
    }
  }

  fn inherited() -> Vec<(String, String)> {
    vec![("HOME".into(), "/Users/person".into()), ("USER".into(), "person".into()), ("PATH".into(), "/usr/bin:/bin".into())]
  }

  #[test]
  fn the_setup_goes_in_order_and_ends_with_the_account_out_of_env() {
    let fx = fake("order", 0, 3);
    let report = Quiet(RefCell::new(vec![]));
    run(&Build::DEV, &fx.layout, None, &inherited(), &fx, &report).unwrap();
    assert_eq!(*fx.calls.borrow(), ["download", "installer", "service install", "agent"]);
    // The account was there when Nouride first started, and is gone once it answered.
    assert!(fx.env_at_agent.borrow().contains("INITIAL_ADMIN_PASSWORD="));
    let env = fs::read_to_string(fx.layout.env_file()).unwrap();
    assert_eq!(env, "PORT=18264\nHOST=127.0.0.1\n");
    // The Dev build's gateway is on its own port.
    let toml = fs::read_to_string(fx.layout.config_toml()).unwrap();
    assert!(toml.contains("port = 18266"), "{toml}");
    // The bar never went back.
    let seen = report.0.borrow();
    assert!(seen.windows(2).all(|w| w[0] <= w[1]), "{seen:?}");
  }

  #[test]
  fn the_installer_and_nourides_service_install_never_see_the_persons_home_or_path() {
    let fx = fake("env", 0, 0);
    run(&Build::DEV, &fx.layout, None, &inherited(), &fx, &Quiet(RefCell::new(vec![]))).unwrap();
    for env in fx.envs.borrow().iter() {
      let get = |k: &str| env.iter().find(|(key, _)| key == k).map(|(_, v)| v.to_string_lossy().into_owned()).unwrap();
      assert_eq!(get("HOME"), fx.layout.home().display().to_string());
      assert_eq!(get("PATH"), fx.layout.shim().display().to_string());
    }
  }

  #[test]
  fn a_failed_installer_stops_before_anything_is_started() {
    let fx = fake("fail", 1, 0);
    assert!(run(&Build::DEV, &fx.layout, None, &inherited(), &fx, &Quiet(RefCell::new(vec![]))).is_err());
    assert_eq!(*fx.calls.borrow(), ["download", "installer"]);
  }

  #[test]
  fn a_nouride_that_never_answers_ends_the_setup_as_failed() {
    let fx = fake("silent", 0, u32::MAX);
    assert!(run(&Build::DEV, &fx.layout, None, &inherited(), &fx, &Quiet(RefCell::new(vec![]))).is_err());
  }

  #[test]
  fn the_release_leaves_nourides_own_gateway_port_alone() {
    let fx = fake("release", 0, 0);
    run(&Build::RELEASE, &fx.layout, None, &inherited(), &fx, &Quiet(RefCell::new(vec![]))).unwrap();
    assert_eq!(fs::read_to_string(fx.layout.config_toml()).unwrap(), "[nougate]\nin_process = true\n");
  }

  #[test]
  fn the_password_is_kept_across_tries_private_and_forgotten_on_request() {
    let fx = fake("password", 0, 0);
    fs::create_dir_all(&fx.layout.data).unwrap();
    let one = admin_password(&fx.layout).unwrap();
    assert_eq!(admin_password(&fx.layout).unwrap(), one);
    #[cfg(unix)]
    {
      use std::os::unix::fs::PermissionsExt;
      assert_eq!(fs::metadata(fx.layout.admin_secret()).unwrap().permissions().mode() & 0o777, 0o600);
    }
    assert_eq!(kept_password(&fx.layout).as_deref(), Some(one.as_str()));
    forget_password(&fx.layout).unwrap();
    assert_eq!(kept_password(&fx.layout), None);
    forget_password(&fx.layout).unwrap();
  }
}
