//! A file the dashboard hands its window to keep (System → Files, a chat's attachment, "Download all"): whether it is
//! kept, the name it is saved under in Downloads, and the number a second copy gets. `spec/downloads.md`, whose cases
//! the Android app's `download/Downloads` passes too.

use std::path::{Path, PathBuf};

use url::Url;

mod pending;

pub use pending::{Ended, Part, Pending};

/// What happens to a download the dashboard asks for.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Source {
  /// Nouride's own file, or a `blob:` the dashboard made: saved in Downloads.
  Save,
  /// Another machine's: the browser's to fetch, so the dashboard's session never goes there.
  Outside,
  /// Nothing to keep: a `blob:` of another origin, `data:`, `file:`, `javascript:`.
  Refused,
}

/// Run in the dashboard's window before its own scripts: it gives the page a way to show a short line at its foot,
/// which [`line_call`] uses (`apps/desktop/tests/download-line.test.ts` runs it).
pub const LINE_SCRIPT: &str = include_str!("line.js");

/// The script that shows `text` as that line: the text as a JSON string, so nothing in it runs as code.
pub fn line_call(text: &str) -> String {
  format!("window.__nourideHomeLine && window.__nourideHomeLine({})", serde_json::Value::from(text))
}

/// The name WebKit suggested, before wry numbered it: wry (0.57) picks `base (n).rest`, `base` cut at the first dot,
/// when the name is taken in Downloads, and only then tells the app. Taken back when `base.rest` is there, so the spec's
/// own numbering ([`free`]) is the one a person sees.
pub fn before_webkit_numbering(name: &str, exists: impl Fn(&str) -> bool) -> String {
  let (base, rest) = name.find('.').map_or((name, ""), |i| name.split_at(i));
  let Some(open) = base.strip_suffix(')').and_then(|b| b.rfind(" (").map(|i| (i, b))) else { return name.to_owned() };
  let (i, inner) = open;
  let digits = &inner[i + 2..];
  if digits.is_empty() || !digits.bytes().all(|b| b.is_ascii_digit()) {
    return name.to_owned();
  }
  let original = format!("{}{rest}", &base[..i]);
  if exists(&original) { original } else { name.to_owned() }
}

/// The name of a file when nothing else names it.
pub const FALLBACK: &str = "download";

/// Long enough for any name a person gives a file; a server's longer one is cut before its extension.
const MAX_CHARS: usize = 200;

/// Refused in a name by a phone's shared storage and other systems: each becomes `_`.
const UNSAFE: &[char] = &['/', '\\', ':', '*', '?', '"', '<', '>', '|'];

/// The compressions a `.tar` is wrapped in: `logs.tar.gz` is numbered as `logs (1).tar.gz`.
const TAR_WRAPS: &[&str] = &["gz", "bz2", "xz", "zst"];

/// Whether `url` is kept, sent to the browser or refused, for the dashboard at `origin`.
pub fn source(url: &str, origin: &str) -> Source {
  let (Ok(url), Ok(origin)) = (Url::parse(url), Url::parse(origin)) else { return Source::Refused };
  match url.scheme() {
    "http" | "https" if url.origin() == origin.origin() => Source::Save,
    "http" | "https" => Source::Outside,
    // A blob's address is the origin that made it, then its id: `url` reads that origin as a page's would.
    "blob" if url.origin() == origin.origin() => Source::Save,
    _ => Source::Refused,
  }
}

/// The name a download is saved under: the page's own name for it, the server's, the URL's, or [`FALLBACK`], made safe.
pub fn name(given: Option<&str>, disposition: Option<&str>, url: &str) -> String {
  let picked = given
    .filter(|g| !g.is_empty())
    .map(str::to_owned)
    .or_else(|| disposition.and_then(from_disposition))
    .or_else(|| from_url(url))
    .unwrap_or_default();
  safe(&picked)
}

/// The file name in a `Content-Disposition`: its `filename*` (RFC 5987, UTF-8) first, then its `filename`.
pub fn from_disposition(value: &str) -> Option<String> {
  let params = split_params(value);
  let star = params.iter().find(|(k, _)| k == "filename*").and_then(|(_, v)| {
    let (charset, rest) = v.split_once('\'')?;
    let (_lang, encoded) = rest.split_once('\'')?;
    charset.eq_ignore_ascii_case("utf-8").then(|| percent_decode(encoded))
  });
  star.or_else(|| params.into_iter().find(|(k, _)| k == "filename").map(|(_, v)| v)).filter(|n| !n.is_empty())
}

/// The last part of an http(s) URL's path, percent-decoded. A `blob:`'s is a random id, never a name.
fn from_url(url: &str) -> Option<String> {
  let url = Url::parse(url).ok()?;
  if !matches!(url.scheme(), "http" | "https") {
    return None;
  }
  let last = url.path_segments()?.next_back()?;
  (!last.is_empty()).then(|| percent_decode(last))
}

/// `name` as a file of its own in Downloads: never a path, never hidden, nothing a file system refuses.
pub fn safe(name: &str) -> String {
  let cleaned: String = name
    .chars()
    .filter(|c| !c.is_control())
    .map(|c| if UNSAFE.contains(&c) { '_' } else { c })
    .collect();
  let trimmed = cleaned.trim_matches(|c: char| c == '.' || c.is_whitespace());
  if trimmed.is_empty() {
    return FALLBACK.to_owned();
  }
  if trimmed.chars().count() <= MAX_CHARS {
    return trimmed.to_owned();
  }
  let (stem, ext) = split_extension(trimmed);
  let keep = MAX_CHARS.saturating_sub(ext.chars().count());
  format!("{}{ext}", stem.chars().take(keep).collect::<String>())
}

/// The `n`th copy of `name`: `report (1).pdf`.
pub fn numbered(name: &str, n: u32) -> String {
  let (stem, ext) = split_extension(name);
  format!("{stem} ({n}){ext}")
}

/// `name`, or its first numbered copy that `taken` says is free.
pub fn free(name: &str, taken: impl Fn(&str) -> bool) -> String {
  if !taken(name) {
    return name.to_owned();
  }
  (1..).map(|n| numbered(name, n)).find(|candidate| !taken(candidate)).expect("an unbounded range")
}

/// Where `name` is saved in `dir` without writing over a file there.
pub fn free_path(dir: &Path, name: &str) -> PathBuf {
  dir.join(free(name, |candidate| dir.join(candidate).exists()))
}

/// The name before its extension, and the extension with its dot (`.tar.gz` as one), or empty.
fn split_extension(name: &str) -> (&str, &str) {
  let Some(dot) = name.rfind('.').filter(|&i| i > 0) else { return (name, "") };
  let (stem, ext) = name.split_at(dot);
  if TAR_WRAPS.contains(&&ext[1..]) {
    if let Some(tar) = stem.strip_suffix(".tar").filter(|s| !s.is_empty()) {
      return (tar, &name[tar.len()..]);
    }
  }
  (stem, ext)
}

/// A header's `; key=value` parameters, keys lower-cased, a quoted value read with its `\` escapes and its `;`.
fn split_params(value: &str) -> Vec<(String, String)> {
  let mut params = Vec::new();
  let mut chars = value.chars().peekable();
  // The first part is the type (`attachment`), not a parameter.
  for c in chars.by_ref() {
    if c == ';' {
      break;
    }
  }
  loop {
    let key: String = chars.by_ref().take_while(|&c| c != '=' && c != ';').collect();
    let key = key.trim().to_ascii_lowercase();
    while chars.peek().is_some_and(|c| c.is_whitespace()) {
      chars.next();
    }
    let mut val = String::new();
    if chars.peek() == Some(&'"') {
      chars.next();
      while let Some(c) = chars.next() {
        match c {
          '\\' => val.extend(chars.next()),
          '"' => break,
          c => val.push(c),
        }
      }
      for c in chars.by_ref() {
        if c == ';' {
          break;
        }
      }
    } else {
      val = chars.by_ref().take_while(|&c| c != ';').collect::<String>().trim().to_owned();
    }
    if !key.is_empty() {
      params.push((key, val));
    }
    if chars.peek().is_none() {
      return params;
    }
  }
}

/// `%xx` read as UTF-8 bytes; a `%` not followed by two hex digits, or bytes that are not UTF-8, kept as written.
fn percent_decode(text: &str) -> String {
  let bytes = text.as_bytes();
  let mut out = Vec::with_capacity(bytes.len());
  let mut i = 0;
  while i < bytes.len() {
    let hex = |b: u8| (b as char).to_digit(16);
    if bytes[i] == b'%' && i + 2 < bytes.len() {
      if let (Some(h), Some(l)) = (hex(bytes[i + 1]), hex(bytes[i + 2])) {
        out.push((h * 16 + l) as u8);
        i += 3;
        continue;
      }
    }
    out.push(bytes[i]);
    i += 1;
  }
  String::from_utf8(out).unwrap_or_else(|_| text.to_owned())
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn a_percent_without_two_hex_digits_is_kept() {
    assert_eq!(percent_decode("100%"), "100%");
    assert_eq!(percent_decode("a%2"), "a%2");
    assert_eq!(percent_decode("a%zz"), "a%zz");
    assert_eq!(percent_decode("a%20b"), "a b");
  }

  #[test]
  fn bytes_that_are_not_utf8_leave_the_name_as_written() {
    assert_eq!(percent_decode("%FF.txt"), "%FF.txt");
  }

  #[test]
  fn webkit_s_own_number_is_taken_back_only_when_the_first_copy_is_there() {
    let there = |n: &str| ["report.pdf", "logs.tar.gz", "a (1).txt"].contains(&n);
    assert_eq!(before_webkit_numbering("report (1).pdf", there), "report.pdf");
    assert_eq!(before_webkit_numbering("report (12).pdf", there), "report.pdf");
    assert_eq!(before_webkit_numbering("logs (1).tar.gz", there), "logs.tar.gz");
    assert_eq!(before_webkit_numbering("a (1) (1).txt", there), "a (1).txt");
    assert_eq!(before_webkit_numbering("other (1).pdf", there), "other (1).pdf");
    assert_eq!(before_webkit_numbering("report (x).pdf", there), "report (x).pdf");
    assert_eq!(before_webkit_numbering("report ().pdf", there), "report ().pdf");
    assert_eq!(before_webkit_numbering("report.pdf", there), "report.pdf");
  }

  #[test]
  fn the_line_s_text_never_runs_as_code() {
    assert_eq!(line_call("a\"); alert(1); (\"b.txt is in Downloads."), r#"window.__nourideHomeLine && window.__nourideHomeLine("a\"); alert(1); (\"b.txt is in Downloads.")"#);
  }

  #[test]
  fn a_second_copy_never_writes_over_a_file() {
    let dir = std::env::temp_dir().join(format!("downloads-free-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    std::fs::write(dir.join("a.txt"), "1").unwrap();
    std::fs::write(dir.join("a (1).txt"), "2").unwrap();
    assert_eq!(free_path(&dir, "a.txt"), dir.join("a (2).txt"));
    assert_eq!(free_path(&dir, "b.txt"), dir.join("b.txt"));
    std::fs::remove_dir_all(&dir).unwrap();
  }
}
