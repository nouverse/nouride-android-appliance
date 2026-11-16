//! This computer's address on its network, for Connect: the address other devices on the same network open it at.

use std::net::Ipv4Addr;

/// The interfaces that are never the network a tablet or a phone is on: the loopback, VPN tunnels, Apple's peer links,
/// and the bridges of virtual machines and containers (OrbStack, Docker).
const NOT_THE_NETWORK: &[&str] = &["lo", "utun", "awdl", "llw", "bridge", "vmenet", "vnic", "docker", "veth", "anpi", "ap"];

fn private(ip: &Ipv4Addr) -> bool {
  ip.is_private() && !ip.is_link_local() && !ip.is_loopback()
}

/// Of the interfaces' IPv4 addresses (name, address), the one to show: a private address on a real interface, Wi-Fi
/// or Ethernet first (`en0`, then other `en*`, then any other). `None` without one. Pure.
pub fn pick(addresses: &[(String, Ipv4Addr)]) -> Option<Ipv4Addr> {
  let usable: Vec<&(String, Ipv4Addr)> = addresses
    .iter()
    .filter(|(name, ip)| private(ip) && !NOT_THE_NETWORK.iter().any(|p| name.starts_with(p)))
    .collect();
  let rank = |name: &str| match name {
    "en0" => 0,
    n if n.starts_with("en") || n.starts_with("eth") || n.starts_with("wl") => 1,
    _ => 2,
  };
  usable.into_iter().min_by_key(|(name, _)| rank(name)).map(|(_, ip)| *ip)
}

/// This computer's address on its network, now.
pub fn address() -> Option<Ipv4Addr> {
  let found: Vec<(String, Ipv4Addr)> = if_addrs::get_if_addrs()
    .ok()?
    .into_iter()
    .filter_map(|i| match i.ip() {
      std::net::IpAddr::V4(ip) => Some((i.name, ip)),
      _ => None,
    })
    .collect();
  pick(&found)
}

#[cfg(test)]
mod tests {
  use super::*;

  fn a(name: &str, ip: &str) -> (String, Ipv4Addr) {
    (name.to_owned(), ip.parse().unwrap())
  }

  #[test]
  fn wifi_is_shown_and_a_virtual_machines_bridge_or_a_vpn_never() {
    let found = [a("lo0", "127.0.0.1"), a("bridge100", "192.168.139.3"), a("utun4", "10.8.0.2"), a("en0", "192.168.1.20")];
    assert_eq!(pick(&found), Some("192.168.1.20".parse().unwrap()));
  }

  #[test]
  fn ethernet_counts_and_link_local_or_public_addresses_do_not() {
    assert_eq!(pick(&[a("en7", "192.168.0.106")]), Some("192.168.0.106".parse().unwrap()));
    assert_eq!(pick(&[a("en0", "169.254.10.2")]), None);
    assert_eq!(pick(&[a("en0", "203.0.113.5")]), None);
  }

  #[test]
  fn no_network_is_none() {
    assert_eq!(pick(&[a("lo0", "127.0.0.1")]), None);
    assert_eq!(pick(&[]), None);
  }
}
