//! Release versions, `x.y.z`, as Android's `UpdateFeed.parseVersion` and `newer` read them.

/// `x.y.z` (a leading `v` allowed) as three numbers; `None` for anything else, a build of no release included.
pub fn parse(version: &str) -> Option<[u64; 3]> {
  let text = version.strip_prefix('v').unwrap_or(version);
  let parts: Vec<&str> = text.split('.').collect();
  if parts.len() != 3 {
    return None;
  }
  let mut out = [0u64; 3];
  for (i, part) in parts.iter().enumerate() {
    if part.is_empty() || !part.chars().all(|c| c.is_ascii_digit()) {
      return None;
    }
    out[i] = part.parse().ok()?;
  }
  Some(out)
}

/// Whether `candidate` is a later release than `installed`. Never for a build that is not a release.
pub fn newer(candidate: &str, installed: &str) -> bool {
  match (parse(candidate), parse(installed)) {
    (Some(a), Some(b)) => a > b,
    _ => false,
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn only_a_later_release_is_an_update_and_a_developers_build_is_never_offered_one() {
    assert!(newer("0.1.1", "0.1.0"));
    assert!(newer("0.2.0", "0.1.9"));
    assert!(newer("1.0.0", "0.10.3"));
    assert!(newer("0.10.0", "0.9.0"));
    assert!(!newer("0.1.0", "0.1.0"));
    assert!(!newer("0.0.9", "0.1.0"));
    assert!(!newer("0.1.1", "0.0.0-dev"));
    assert!(!newer("0.1.1-beta", "0.1.0"));
    assert!(!newer("0.1.+1", "0.1.0"));
  }

  #[test]
  fn a_leading_v_is_read() {
    assert_eq!(parse("v0.8.39"), Some([0, 8, 39]));
    assert!(newer("v0.8.40", "0.8.39"));
    assert_eq!(parse("0.8"), None);
    assert_eq!(parse("nightly"), None);
  }
}
