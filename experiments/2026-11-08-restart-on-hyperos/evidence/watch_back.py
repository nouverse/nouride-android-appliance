import socket, time, json, urllib.request, concurrent.futures as cf, datetime

t0 = time.time()


def now():
    return datetime.datetime.utcnow().strftime("%H:%M:%SZ")


def probe(i, port=18254):
    ip = "10.18.1.%d" % i
    s = socket.socket()
    s.settimeout(0.5)
    try:
        return ip if s.connect_ex((ip, port)) == 0 else None
    finally:
        s.close()


def health(ip, port):
    try:
        return json.load(urllib.request.urlopen("http://%s:%d/health" % (ip, port), timeout=3))
    except Exception:
        return None


hits = []
while time.time() - t0 < 900:
    with cf.ThreadPoolExecutor(128) as ex:
        hits = [r for r in ex.map(probe, [i for i in range(1, 255) if i != 102]) if r]
    for ip in hits:
        h = health(ip, 18254)
        if h and h.get("uptime", 10 ** 9) < 900:
            d = health(ip, 18264)
            print("%s PROD BACK on %s: status=%s uptime=%ss version=%s edition=%s; dev (no Autostart): %s; %ds after the watch began"
                  % (now(), ip, h.get("status"), h.get("uptime"), h.get("version"), h.get("edition"),
                     d.get("status") if d else "not answering", int(time.time() - t0)), flush=True)
            raise SystemExit(0)
    time.sleep(4)
print(now(), "prod not back within 15 min; hits:", hits, flush=True)
