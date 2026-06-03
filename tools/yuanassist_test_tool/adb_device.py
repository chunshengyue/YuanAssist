from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any


DEFAULT_ADB_BIN = Path(r"C:\Users\17525\AppData\Local\Android\Sdk\platform-tools\adb.exe")
DEFAULT_EMULATOR_SERIAL = "emulator-5554"


class AdbError(RuntimeError):
    pass


@dataclass
class AdbDevice:
    serial: str
    state: str
    kind: str
    model: str = ""
    product: str = ""
    device: str = ""
    transport_id: str = ""
    raw: str = ""

    @property
    def online(self) -> bool:
        return self.state == "device"

    def to_dict(self) -> dict[str, Any]:
        return {
            "serial": self.serial,
            "state": self.state,
            "kind": self.kind,
            "online": self.online,
            "model": self.model,
            "product": self.product,
            "device": self.device,
            "transportId": self.transport_id,
            "raw": self.raw,
        }


@dataclass
class AdbDevicesReport:
    adb_bin: Path
    devices: list[AdbDevice]
    default_serial: str = DEFAULT_EMULATOR_SERIAL

    @property
    def default_device(self) -> AdbDevice | None:
        return next((item for item in self.devices if item.serial == self.default_serial), None)

    def summary(self) -> dict[str, int]:
        return {
            "devices": len(self.devices),
            "online": sum(1 for item in self.devices if item.online),
            "emulators": sum(1 for item in self.devices if item.kind == "emulator"),
            "usb": sum(1 for item in self.devices if item.kind == "usb"),
            "offline": sum(1 for item in self.devices if item.state == "offline"),
            "unauthorized": sum(1 for item in self.devices if item.state == "unauthorized"),
        }

    def to_dict(self) -> dict[str, Any]:
        default_device = self.default_device
        return {
            "adbBin": str(self.adb_bin),
            "defaultTarget": {
                "serial": self.default_serial,
                "kind": "emulator" if self.default_serial.startswith("emulator-") else "usb",
                "present": default_device is not None,
                "state": default_device.state if default_device else "missing",
                "online": default_device.online if default_device else False,
            },
            "summary": self.summary(),
            "devices": [item.to_dict() for item in self.devices],
        }

    def to_text(self) -> str:
        summary = self.summary()
        lines = [
            "ADB 设备："
            f"{summary['devices']} 台，"
            f"{summary['online']} 在线，"
            f"{summary['emulators']} 模拟器，"
            f"{summary['usb']} USB，"
            f"{summary['offline']} offline，"
            f"{summary['unauthorized']} unauthorized",
            f"adb：{self.adb_bin}",
            f"默认目标：{self.default_serial} ({'已发现' if self.default_device else '未发现'})",
        ]
        if not self.devices:
            lines.append("- 未发现设备")
        for device in self.devices:
            name = device.model or device.device or device.product or "-"
            lines.append(f"- {device.serial} [{device.kind}] state={device.state} model={name}")
        return "\n".join(lines)


def list_adb_devices(adb_bin: Path | None = None) -> AdbDevicesReport:
    resolved_adb = resolve_adb_bin(adb_bin)
    result = run_adb(resolved_adb, ["devices", "-l"])
    devices = parse_adb_devices(result.stdout)
    return AdbDevicesReport(adb_bin=resolved_adb, devices=devices)


def resolve_adb_bin(adb_bin: Path | None = None) -> Path:
    if adb_bin is not None:
        candidate = adb_bin.expanduser()
        if candidate.exists():
            return candidate.resolve()
        raise AdbError(f"指定的 adb 不存在：{adb_bin}")

    if DEFAULT_ADB_BIN.exists():
        return DEFAULT_ADB_BIN.resolve()

    found = shutil.which("adb.exe") or shutil.which("adb")
    if found:
        return Path(found).resolve()

    raise AdbError(f"找不到 adb，可通过 --adb-bin 指定路径，默认路径：{DEFAULT_ADB_BIN}")


def parse_adb_devices(raw: str) -> list[AdbDevice]:
    devices: list[AdbDevice] = []
    for line in raw.splitlines():
        text = line.strip()
        if not text or text.startswith("List of devices"):
            continue
        parts = text.split()
        if len(parts) < 2:
            continue
        serial = parts[0]
        state = parts[1]
        attrs = parse_device_attrs(parts[2:])
        kind = "emulator" if serial.startswith("emulator-") else "usb"
        devices.append(
            AdbDevice(
                serial=serial,
                state=state,
                kind=kind,
                model=attrs.get("model", ""),
                product=attrs.get("product", ""),
                device=attrs.get("device", ""),
                transport_id=attrs.get("transport_id", ""),
                raw=text,
            )
        )
    return devices


def parse_device_attrs(parts: list[str]) -> dict[str, str]:
    attrs: dict[str, str] = {}
    for part in parts:
        if ":" not in part:
            continue
        key, value = part.split(":", 1)
        attrs[key] = value
    return attrs


def run_adb(adb_bin: Path, args: list[str], *, serial: str | None = None, timeout: int = 15) -> subprocess.CompletedProcess[str]:
    command = [str(adb_bin)]
    if serial:
        command.extend(["-s", serial])
    command.extend(args)
    try:
        result = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as exc:
        raise AdbError(f"adb 执行超时：{' '.join(command)}") from exc
    except OSError as exc:
        raise AdbError(f"adb 执行失败：{exc}") from exc
    if result.returncode != 0:
        detail = (result.stderr or result.stdout or "").strip()
        raise AdbError(f"adb 返回失败 code={result.returncode}：{detail}")
    return result


def capture_screenshot(adb_bin: Path, serial: str, output_path: Path) -> Path:
    command = [str(adb_bin), "-s", serial, "exec-out", "screencap", "-p"]
    try:
        result = subprocess.run(command, check=False, capture_output=True, timeout=20)
    except subprocess.TimeoutExpired as exc:
        raise AdbError(f"截图超时：{serial}") from exc
    except OSError as exc:
        raise AdbError(f"截图失败：{exc}") from exc
    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise AdbError(f"截图失败 code={result.returncode}：{detail}")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(result.stdout)
    return output_path
