//! The LLM gateway's port in Nouride's `config.toml`, for a build whose port is not Nouride's default (the Dev
//! build), whatever the installer left there. Android's `Nouride.gatewayConfig`, as text rather than a shell line:
//! missing, the file is made; without `[nougate]`, the section is added; with it but no `port`, the port goes under
//! its heading. A port already there is kept, so running it again changes nothing.

/// `toml` with the gateway's port in it; `None` for a file that is not there yet.
pub fn with_gateway_port(toml: Option<&str>, port: u16) -> String {
  let Some(text) = toml else { return format!("[nougate]\nport = {port}\n") };
  let lines: Vec<&str> = text.split('\n').collect();
  let Some(start) = lines.iter().position(|l| l.trim() == "[nougate]") else {
    let mut out = text.to_owned();
    if !out.is_empty() && !out.ends_with('\n') {
      out.push('\n');
    }
    out.push_str(&format!("\n[nougate]\nport = {port}\n"));
    return out;
  };
  let end = lines.iter().skip(start + 1).position(|l| l.trim_start().starts_with('[')).map_or(lines.len(), |i| start + 1 + i);
  let has_port = lines[start + 1..end].iter().any(|l| {
    let t = l.trim_start();
    t.starts_with("port") && t["port".len()..].trim_start().starts_with('=')
  });
  if has_port {
    return text.to_owned();
  }
  let mut out: Vec<String> = lines.iter().map(|l| (*l).to_owned()).collect();
  out.insert(start + 1, format!("port = {port}"));
  out.join("\n")
}

#[cfg(test)]
mod tests {
  use super::*;

  #[test]
  fn a_missing_file_is_made_with_the_section() {
    assert_eq!(with_gateway_port(None, 18266), "[nougate]\nport = 18266\n");
  }

  #[test]
  fn the_router_installers_section_gets_the_port_under_its_heading() {
    let installed = "[nougate]\nin_process = true\n\n[agents]\nx = 1\n";
    assert_eq!(with_gateway_port(Some(installed), 18266), "[nougate]\nport = 18266\nin_process = true\n\n[agents]\nx = 1\n");
  }

  #[test]
  fn a_file_without_the_section_gets_it_added() {
    assert_eq!(with_gateway_port(Some("[agents]\nx = 1"), 18266), "[agents]\nx = 1\n\n[nougate]\nport = 18266\n");
  }

  #[test]
  fn a_port_already_there_is_kept_so_running_it_again_changes_nothing() {
    let once = with_gateway_port(Some("[nougate]\nin_process = true\n"), 18266);
    assert_eq!(with_gateway_port(Some(&once), 18266), once);
    let theirs = "[nougate]\nport = 19000\n";
    assert_eq!(with_gateway_port(Some(theirs), 18266), theirs);
  }

  #[test]
  fn a_port_in_another_section_is_not_the_gateways() {
    let text = "[dashboard]\nport = 1\n[nougate]\nin_process = true\n";
    assert_eq!(with_gateway_port(Some(text), 18266), "[dashboard]\nport = 1\n[nougate]\nport = 18266\nin_process = true\n");
  }
}
