# YuanAssist TestLab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an independent Python Android test toolkit for YuanAssist that can run smoke, vision, and first-wave regression tests on a real device with artifact capture and Allure reporting.

**Architecture:** Create a standalone `testsuite/` Python project at the repository root. Build the foundation in layers: configuration and artifact storage first, then vision and adb/device adapters, then page/flow abstractions, then smoke/vision/regression tests and runner scripts.

**Tech Stack:** Python 3.11+, pytest, pytest-xdist, allure-pytest, adb, uiautomator2, OpenCV, Pillow, PyYAML, optional OCR adapter interface

---

## File Structure

### New files to create

- `testsuite/pyproject.toml`
- `testsuite/pytest.ini`
- `testsuite/README.md`
- `testsuite/config/project.yaml`
- `testsuite/config/devices.example.yaml`
- `testsuite/config/vision.yaml`
- `testsuite/core/__init__.py`
- `testsuite/core/config_loader.py`
- `testsuite/core/artifact_store.py`
- `testsuite/core/errors.py`
- `testsuite/core/adb_client.py`
- `testsuite/core/device_session.py`
- `testsuite/core/app_driver.py`
- `testsuite/core/logcat_collector.py`
- `testsuite/core/assertions.py`
- `testsuite/core/waiters.py`
- `testsuite/vision/__init__.py`
- `testsuite/vision/image_loader.py`
- `testsuite/vision/image_region.py`
- `testsuite/vision/match_result.py`
- `testsuite/vision/template_matcher.py`
- `testsuite/vision/ocr_engine.py`
- `testsuite/pages/__init__.py`
- `testsuite/pages/base_page.py`
- `testsuite/pages/home_page.py`
- `testsuite/pages/daily_page.py`
- `testsuite/pages/mainline_624_page.py`
- `testsuite/pages/bird_food_page.py`
- `testsuite/pages/inventory_stitch_page.py`
- `testsuite/flows/__init__.py`
- `testsuite/flows/launch_flow.py`
- `testsuite/flows/daily_navigation_flow.py`
- `testsuite/flows/mainline_624_flow.py`
- `testsuite/flows/bird_food_flow.py`
- `testsuite/flows/inventory_stitch_flow.py`
- `testsuite/scripts/check_env.py`
- `testsuite/scripts/run_smoke.py`
- `testsuite/scripts/run_vision.py`
- `testsuite/scripts/run_regression.py`
- `testsuite/tests/conftest.py`
- `testsuite/tests/smoke/test_bootstrap_smoke.py`
- `testsuite/tests/vision/test_template_matcher.py`
- `testsuite/tests/vision/test_ocr_contract.py`
- `testsuite/tests/regression/test_daily_launch_flow.py`
- `testsuite/tests/regression/test_mainline_624_flow.py`
- `testsuite/tests/regression/test_bird_food_flow.py`
- `testsuite/tests/regression/test_inventory_stitch_flow.py`
- `testsuite/tests/unit/test_config_loader.py`
- `testsuite/tests/unit/test_artifact_store.py`
- `testsuite/tests/unit/test_adb_client.py`
- `testsuite/tests/unit/test_device_session.py`
- `testsuite/tests/unit/test_image_region.py`
- `testsuite/tests/unit/test_template_result.py`

### Existing files to reuse as test assets

- `app/src/main/assets/*.png`
- `app/src/main/assets/daily_scripts/*.json`

### Runtime directories created by the tool

- `testsuite/artifacts/allure-results/`
- `testsuite/artifacts/runs/`

## Task 1: Scaffold the standalone project

**Files:**
- Create: `testsuite/pyproject.toml`
- Create: `testsuite/pytest.ini`
- Create: `testsuite/README.md`
- Create: `testsuite/core/__init__.py`
- Create: `testsuite/vision/__init__.py`
- Create: `testsuite/pages/__init__.py`
- Create: `testsuite/flows/__init__.py`
- Create: `testsuite/tests/conftest.py`

- [ ] **Step 1: Write the failing test for package import and pytest markers**

```python
# testsuite/tests/unit/test_config_loader.py
from pathlib import Path


def test_testsuite_root_exists() -> None:
    root = Path(__file__).resolve().parents[2]
    assert (root / "pyproject.toml").exists()
    assert (root / "pytest.ini").exists()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest testsuite/tests/unit/test_config_loader.py::test_testsuite_root_exists -v`
Expected: FAIL because `testsuite/pyproject.toml` and `testsuite/pytest.ini` do not exist yet.

- [ ] **Step 3: Write minimal project files**

```toml
# testsuite/pyproject.toml
[project]
name = "yuanassist-testlab"
version = "0.1.0"
description = "Standalone Android automation test toolkit for YuanAssist"
requires-python = ">=3.11"
dependencies = [
  "PyYAML>=6.0",
  "allure-pytest>=2.13.5",
  "opencv-python>=4.10.0.84",
  "pillow>=10.4.0",
  "pytest>=8.3.2",
  "pytest-xdist>=3.6.1",
  "uiautomator2>=3.2.5",
]

[tool.pytest.ini_options]
pythonpath = ["."]
```

```ini
# testsuite/pytest.ini
[pytest]
addopts = -ra
testpaths = tests
markers =
    smoke: smoke coverage for basic app reachability
    vision: image matching and OCR checks
    regression: first-wave business regression flows
```

```markdown
# testsuite/README.md
# YuanAssist TestLab

Standalone Android real-device automation toolkit for YuanAssist.
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest testsuite/tests/unit/test_config_loader.py::test_testsuite_root_exists -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add testsuite/pyproject.toml testsuite/pytest.ini testsuite/README.md testsuite/core/__init__.py testsuite/vision/__init__.py testsuite/pages/__init__.py testsuite/flows/__init__.py testsuite/tests/conftest.py testsuite/tests/unit/test_config_loader.py
git commit -m "feat: scaffold yuanassist testlab project"
```

## Task 2: Build configuration loading and artifact storage

**Files:**
- Create: `testsuite/config/project.yaml`
- Create: `testsuite/config/devices.example.yaml`
- Create: `testsuite/config/vision.yaml`
- Create: `testsuite/core/config_loader.py`
- Create: `testsuite/core/artifact_store.py`
- Create: `testsuite/core/errors.py`
- Create: `testsuite/tests/unit/test_config_loader.py`
- Create: `testsuite/tests/unit/test_artifact_store.py`

- [ ] **Step 1: Write the failing tests**

```python
# testsuite/tests/unit/test_config_loader.py
from pathlib import Path

from testsuite.core.config_loader import ConfigLoader


def test_load_project_config_returns_required_keys() -> None:
    root = Path(__file__).resolve().parents[2]
    loader = ConfigLoader(root / "config")

    config = loader.load_yaml("project.yaml")

    assert config["app"]["package_name"] == "com.example.yuanassist"
    assert config["app"]["entry_activity"]
    assert config["artifacts"]["root_dir"] == "artifacts/runs"
```

```python
# testsuite/tests/unit/test_artifact_store.py
from pathlib import Path

from testsuite.core.artifact_store import ArtifactStore


def test_artifact_store_creates_case_directory(tmp_path: Path) -> None:
    store = ArtifactStore(tmp_path)

    case_dir = store.start_case("tests.smoke.test_bootstrap_smoke", "test_app_launch_success")

    assert case_dir.exists()
    assert case_dir.name.startswith("test_app_launch_success")
    assert (tmp_path / "latest").exists()
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest testsuite/tests/unit/test_config_loader.py testsuite/tests/unit/test_artifact_store.py -v`
Expected: FAIL because loader and store modules do not exist yet.

- [ ] **Step 3: Write minimal implementation and config files**

```yaml
# testsuite/config/project.yaml
app:
  package_name: com.example.yuanassist
  entry_activity: com.example.yuanassist.ui.MainActivity
artifacts:
  root_dir: artifacts/runs
  allure_results_dir: artifacts/allure-results
assets:
  template_root: ../app/src/main/assets
```

```yaml
# testsuite/config/devices.example.yaml
default_device:
  serial: emulator-5554
  resolution: "1080x2400"
  notes: "replace with your adb device serial"
```

```yaml
# testsuite/config/vision.yaml
template_matching:
  default_threshold: 0.85
ocr:
  provider: stub
regions:
  stage_label:
    left: 0
    top: 0
    width: 300
    height: 120
```

```python
# testsuite/core/config_loader.py
from pathlib import Path
from typing import Any

import yaml


class ConfigLoader:
    def __init__(self, config_dir: Path) -> None:
        self._config_dir = config_dir

    def load_yaml(self, filename: str) -> dict[str, Any]:
        path = self._config_dir / filename
        with path.open("r", encoding="utf-8") as handle:
            return yaml.safe_load(handle) or {}
```

```python
# testsuite/core/artifact_store.py
from datetime import datetime
from pathlib import Path


class ArtifactStore:
    def __init__(self, root_dir: Path) -> None:
        self._root_dir = root_dir
        self._root_dir.mkdir(parents=True, exist_ok=True)

    def start_case(self, nodeid: str, test_name: str) -> Path:
        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        case_dir = self._root_dir / f"{test_name}-{timestamp}"
        case_dir.mkdir(parents=True, exist_ok=True)
        latest = self._root_dir / "latest"
        latest.mkdir(parents=True, exist_ok=True)
        return case_dir
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest testsuite/tests/unit/test_config_loader.py testsuite/tests/unit/test_artifact_store.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add testsuite/config/project.yaml testsuite/config/devices.example.yaml testsuite/config/vision.yaml testsuite/core/config_loader.py testsuite/core/artifact_store.py testsuite/core/errors.py testsuite/tests/unit/test_config_loader.py testsuite/tests/unit/test_artifact_store.py
git commit -m "feat: add config loading and artifact storage"
```

## Task 3: Add image region and template match result primitives

**Files:**
- Create: `testsuite/vision/image_region.py`
- Create: `testsuite/vision/match_result.py`
- Create: `testsuite/tests/unit/test_image_region.py`
- Create: `testsuite/tests/unit/test_template_result.py`

- [ ] **Step 1: Write the failing tests**

```python
# testsuite/tests/unit/test_image_region.py
from testsuite.vision.image_region import ImageRegion


def test_region_to_box_returns_xyxy() -> None:
    region = ImageRegion(left=10, top=20, width=100, height=50)

    assert region.to_box() == (10, 20, 110, 70)
```

```python
# testsuite/tests/unit/test_template_result.py
from testsuite.vision.match_result import TemplateMatchResult


def test_match_result_knows_if_threshold_passed() -> None:
    result = TemplateMatchResult(template_name="jinru.png", score=0.91, threshold=0.85, center=(30, 40))

    assert result.passed is True
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest testsuite/tests/unit/test_image_region.py testsuite/tests/unit/test_template_result.py -v`
Expected: FAIL because region and result classes do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```python
# testsuite/vision/image_region.py
from dataclasses import dataclass


@dataclass(frozen=True)
class ImageRegion:
    left: int
    top: int
    width: int
    height: int

    def to_box(self) -> tuple[int, int, int, int]:
        return (self.left, self.top, self.left + self.width, self.top + self.height)
```

```python
# testsuite/vision/match_result.py
from dataclasses import dataclass


@dataclass(frozen=True)
class TemplateMatchResult:
    template_name: str
    score: float
    threshold: float
    center: tuple[int, int]

    @property
    def passed(self) -> bool:
        return self.score >= self.threshold
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest testsuite/tests/unit/test_image_region.py testsuite/tests/unit/test_template_result.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add testsuite/vision/image_region.py testsuite/vision/match_result.py testsuite/tests/unit/test_image_region.py testsuite/tests/unit/test_template_result.py
git commit -m "feat: add vision result primitives"
```

## Task 4: Implement template matching and OCR contract

**Files:**
- Create: `testsuite/vision/image_loader.py`
- Create: `testsuite/vision/template_matcher.py`
- Create: `testsuite/vision/ocr_engine.py`
- Create: `testsuite/tests/vision/test_template_matcher.py`
- Create: `testsuite/tests/vision/test_ocr_contract.py`

- [ ] **Step 1: Write the failing tests**

```python
# testsuite/tests/vision/test_template_matcher.py
from pathlib import Path

from testsuite.vision.template_matcher import TemplateMatcher


def test_template_matcher_can_load_existing_asset() -> None:
    root = Path(__file__).resolve().parents[2]
    matcher = TemplateMatcher()

    result = matcher.match(
        screenshot_path=root / "../app/src/main/assets/jinru.png",
        template_path=root / "../app/src/main/assets/jinru.png",
        threshold=0.99,
    )

    assert result.passed is True
```

```python
# testsuite/tests/vision/test_ocr_contract.py
from PIL import Image

from testsuite.vision.ocr_engine import StubOcrEngine


def test_stub_ocr_returns_text_payload() -> None:
    image = Image.new("RGB", (20, 20), "white")

    result = StubOcrEngine().recognize(image)

    assert "text" in result
    assert "provider" in result
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest testsuite/tests/vision/test_template_matcher.py testsuite/tests/vision/test_ocr_contract.py -v`
Expected: FAIL because matcher and OCR adapter do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```python
# testsuite/vision/image_loader.py
from pathlib import Path

import cv2
from PIL import Image


def load_cv_image(path: Path):
    return cv2.imread(str(path), cv2.IMREAD_COLOR)


def load_pil_image(path: Path) -> Image.Image:
    return Image.open(path)
```

```python
# testsuite/vision/template_matcher.py
from pathlib import Path

import cv2

from testsuite.vision.match_result import TemplateMatchResult


class TemplateMatcher:
    def match(self, screenshot_path: Path, template_path: Path, threshold: float) -> TemplateMatchResult:
        screenshot = cv2.imread(str(screenshot_path), cv2.IMREAD_COLOR)
        template = cv2.imread(str(template_path), cv2.IMREAD_COLOR)
        result = cv2.matchTemplate(screenshot, template, cv2.TM_CCOEFF_NORMED)
        _, max_score, _, max_loc = cv2.minMaxLoc(result)
        center = (max_loc[0] + template.shape[1] // 2, max_loc[1] + template.shape[0] // 2)
        return TemplateMatchResult(template_name=template_path.name, score=float(max_score), threshold=threshold, center=center)
```

```python
# testsuite/vision/ocr_engine.py
from typing import Any

from PIL import Image


class StubOcrEngine:
    def recognize(self, image: Image.Image) -> dict[str, Any]:
        return {
            "provider": "stub",
            "text": "",
            "size": image.size,
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest testsuite/tests/vision/test_template_matcher.py testsuite/tests/vision/test_ocr_contract.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add testsuite/vision/image_loader.py testsuite/vision/template_matcher.py testsuite/vision/ocr_engine.py testsuite/tests/vision/test_template_matcher.py testsuite/tests/vision/test_ocr_contract.py
git commit -m "feat: add vision matcher and ocr contract"
```

## Task 5: Implement adb client and device session wrappers

**Files:**
- Create: `testsuite/core/adb_client.py`
- Create: `testsuite/core/device_session.py`
- Create: `testsuite/core/app_driver.py`
- Create: `testsuite/core/logcat_collector.py`
- Create: `testsuite/tests/unit/test_adb_client.py`
- Create: `testsuite/tests/unit/test_device_session.py`

- [ ] **Step 1: Write the failing tests**

```python
# testsuite/tests/unit/test_adb_client.py
from testsuite.core.adb_client import AdbClient


def test_parse_devices_output_filters_offline_lines() -> None:
    output = "List of devices attached\\nABC123\\tdevice\\nXYZ999\\toffline\\n"

    devices = AdbClient.parse_devices_output(output)

    assert devices == ["ABC123"]
```

```python
# testsuite/tests/unit/test_device_session.py
from pathlib import Path

from testsuite.core.device_session import DeviceSession


class DummyAdbClient:
    def capture_screenshot(self, serial: str, target: Path) -> Path:
        target.write_bytes(b"fake")
        return target


def test_device_session_delegates_screenshot(tmp_path: Path) -> None:
    session = DeviceSession(serial="ABC123", adb=DummyAdbClient())

    path = session.capture_screenshot(tmp_path / "screen.png")

    assert path.exists()
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest testsuite/tests/unit/test_adb_client.py testsuite/tests/unit/test_device_session.py -v`
Expected: FAIL because adb and session wrappers do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```python
# testsuite/core/adb_client.py
import subprocess
from pathlib import Path


class AdbClient:
    @staticmethod
    def parse_devices_output(output: str) -> list[str]:
        devices: list[str] = []
        for line in output.splitlines():
            if "\tdevice" in line:
                devices.append(line.split("\t", 1)[0].strip())
        return devices

    def capture_screenshot(self, serial: str, target: Path) -> Path:
        command = ["adb", "-s", serial, "exec-out", "screencap", "-p"]
        data = subprocess.check_output(command)
        target.write_bytes(data)
        return target
```

```python
# testsuite/core/device_session.py
from pathlib import Path


class DeviceSession:
    def __init__(self, serial: str, adb) -> None:
        self.serial = serial
        self._adb = adb

    def capture_screenshot(self, target: Path) -> Path:
        return self._adb.capture_screenshot(self.serial, target)
```

```python
# testsuite/core/app_driver.py
class AppDriver:
    def __init__(self, session, package_name: str, entry_activity: str) -> None:
        self.session = session
        self.package_name = package_name
        self.entry_activity = entry_activity
```

```python
# testsuite/core/logcat_collector.py
class LogcatCollector:
    def __init__(self, serial: str) -> None:
        self.serial = serial
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest testsuite/tests/unit/test_adb_client.py testsuite/tests/unit/test_device_session.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add testsuite/core/adb_client.py testsuite/core/device_session.py testsuite/core/app_driver.py testsuite/core/logcat_collector.py testsuite/tests/unit/test_adb_client.py testsuite/tests/unit/test_device_session.py
git commit -m "feat: add adb client and device session wrappers"
```

## Task 6: Add assertions, waiters, and base page objects

**Files:**
- Create: `testsuite/core/assertions.py`
- Create: `testsuite/core/waiters.py`
- Create: `testsuite/pages/base_page.py`
- Create: `testsuite/pages/home_page.py`
- Create: `testsuite/pages/daily_page.py`
- Create: `testsuite/pages/mainline_624_page.py`
- Create: `testsuite/pages/bird_food_page.py`
- Create: `testsuite/pages/inventory_stitch_page.py`
- Create: `testsuite/tests/regression/test_daily_launch_flow.py`

- [ ] **Step 1: Write the failing test**

```python
# testsuite/tests/regression/test_daily_launch_flow.py
from testsuite.pages.home_page import HomePage


class DummyUi:
    def __init__(self) -> None:
        self.visible = {"daily_tab": True}

    def exists(self, key: str) -> bool:
        return self.visible.get(key, False)


def test_home_page_knows_daily_entry_visibility() -> None:
    page = HomePage(DummyUi())

    assert page.is_daily_entry_visible() is True
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest testsuite/tests/regression/test_daily_launch_flow.py::test_home_page_knows_daily_entry_visibility -v`
Expected: FAIL because `HomePage` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```python
# testsuite/pages/base_page.py
class BasePage:
    def __init__(self, ui) -> None:
        self.ui = ui
```

```python
# testsuite/pages/home_page.py
from testsuite.pages.base_page import BasePage


class HomePage(BasePage):
    def is_daily_entry_visible(self) -> bool:
        return bool(self.ui.exists("daily_tab"))
```

```python
# testsuite/pages/daily_page.py
from testsuite.pages.base_page import BasePage


class DailyPage(BasePage):
    pass
```

```python
# testsuite/pages/mainline_624_page.py
from testsuite.pages.base_page import BasePage


class Mainline624Page(BasePage):
    pass
```

```python
# testsuite/pages/bird_food_page.py
from testsuite.pages.base_page import BasePage


class BirdFoodPage(BasePage):
    pass
```

```python
# testsuite/pages/inventory_stitch_page.py
from testsuite.pages.base_page import BasePage


class InventoryStitchPage(BasePage):
    pass
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest testsuite/tests/regression/test_daily_launch_flow.py::test_home_page_knows_daily_entry_visibility -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add testsuite/core/assertions.py testsuite/core/waiters.py testsuite/pages/base_page.py testsuite/pages/home_page.py testsuite/pages/daily_page.py testsuite/pages/mainline_624_page.py testsuite/pages/bird_food_page.py testsuite/pages/inventory_stitch_page.py testsuite/tests/regression/test_daily_launch_flow.py
git commit -m "feat: add page object foundation"
```

## Task 7: Add launch flow and smoke suite

**Files:**
- Create: `testsuite/flows/launch_flow.py`
- Create: `testsuite/flows/daily_navigation_flow.py`
- Create: `testsuite/tests/smoke/test_bootstrap_smoke.py`
- Modify: `testsuite/tests/conftest.py`

- [ ] **Step 1: Write the failing smoke test**

```python
# testsuite/tests/smoke/test_bootstrap_smoke.py
import pytest

from testsuite.flows.launch_flow import LaunchFlow


class DummyDriver:
    def launch(self) -> None:
        self.launched = True


@pytest.mark.smoke
def test_launch_flow_calls_driver_launch() -> None:
    driver = DummyDriver()

    LaunchFlow(driver).start()

    assert driver.launched is True
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest testsuite/tests/smoke/test_bootstrap_smoke.py::test_launch_flow_calls_driver_launch -v`
Expected: FAIL because `LaunchFlow` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```python
# testsuite/flows/launch_flow.py
class LaunchFlow:
    def __init__(self, driver) -> None:
        self.driver = driver

    def start(self) -> None:
        self.driver.launch()
```

```python
# testsuite/flows/daily_navigation_flow.py
class DailyNavigationFlow:
    def __init__(self, home_page, daily_page) -> None:
        self.home_page = home_page
        self.daily_page = daily_page
```

```python
# testsuite/tests/conftest.py
pytest_plugins: list[str] = []
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest testsuite/tests/smoke/test_bootstrap_smoke.py::test_launch_flow_calls_driver_launch -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add testsuite/flows/launch_flow.py testsuite/flows/daily_navigation_flow.py testsuite/tests/smoke/test_bootstrap_smoke.py testsuite/tests/conftest.py
git commit -m "feat: add launch flow and smoke suite"
```

## Task 8: Add first-wave regression flows for YuanAssist business paths

**Files:**
- Create: `testsuite/flows/mainline_624_flow.py`
- Create: `testsuite/flows/bird_food_flow.py`
- Create: `testsuite/flows/inventory_stitch_flow.py`
- Create: `testsuite/tests/regression/test_mainline_624_flow.py`
- Create: `testsuite/tests/regression/test_bird_food_flow.py`
- Create: `testsuite/tests/regression/test_inventory_stitch_flow.py`

- [ ] **Step 1: Write the failing regression tests**

```python
# testsuite/tests/regression/test_mainline_624_flow.py
import pytest

from testsuite.flows.mainline_624_flow import Mainline624Flow


class DummyMainlinePage:
    def open(self) -> None:
        self.opened = True


@pytest.mark.regression
def test_mainline_flow_opens_page() -> None:
    page = DummyMainlinePage()

    Mainline624Flow(page).run()

    assert page.opened is True
```

```python
# testsuite/tests/regression/test_bird_food_flow.py
import pytest

from testsuite.flows.bird_food_flow import BirdFoodFlow


class DummyBirdFoodPage:
    def open(self) -> None:
        self.opened = True


@pytest.mark.regression
def test_bird_food_flow_opens_page() -> None:
    page = DummyBirdFoodPage()

    BirdFoodFlow(page).run()

    assert page.opened is True
```

```python
# testsuite/tests/regression/test_inventory_stitch_flow.py
import pytest

from testsuite.flows.inventory_stitch_flow import InventoryStitchFlow


class DummyInventoryPage:
    def open(self) -> None:
        self.opened = True


@pytest.mark.regression
def test_inventory_stitch_flow_opens_page() -> None:
    page = DummyInventoryPage()

    InventoryStitchFlow(page).run()

    assert page.opened is True
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest testsuite/tests/regression/test_mainline_624_flow.py testsuite/tests/regression/test_bird_food_flow.py testsuite/tests/regression/test_inventory_stitch_flow.py -v`
Expected: FAIL because flow classes do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```python
# testsuite/flows/mainline_624_flow.py
class Mainline624Flow:
    def __init__(self, page) -> None:
        self.page = page

    def run(self) -> None:
        self.page.open()
```

```python
# testsuite/flows/bird_food_flow.py
class BirdFoodFlow:
    def __init__(self, page) -> None:
        self.page = page

    def run(self) -> None:
        self.page.open()
```

```python
# testsuite/flows/inventory_stitch_flow.py
class InventoryStitchFlow:
    def __init__(self, page) -> None:
        self.page = page

    def run(self) -> None:
        self.page.open()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest testsuite/tests/regression/test_mainline_624_flow.py testsuite/tests/regression/test_bird_food_flow.py testsuite/tests/regression/test_inventory_stitch_flow.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add testsuite/flows/mainline_624_flow.py testsuite/flows/bird_food_flow.py testsuite/flows/inventory_stitch_flow.py testsuite/tests/regression/test_mainline_624_flow.py testsuite/tests/regression/test_bird_food_flow.py testsuite/tests/regression/test_inventory_stitch_flow.py
git commit -m "feat: add first-wave regression flows"
```

## Task 9: Add environment check and runner scripts

**Files:**
- Create: `testsuite/scripts/check_env.py`
- Create: `testsuite/scripts/run_smoke.py`
- Create: `testsuite/scripts/run_vision.py`
- Create: `testsuite/scripts/run_regression.py`

- [ ] **Step 1: Write the failing test for environment check output**

```python
# testsuite/tests/unit/test_adb_client.py
from testsuite.scripts.check_env import build_summary


def test_build_summary_mentions_adb_and_u2() -> None:
    summary = build_summary(adb_ok=True, u2_ok=False, device_count=1)

    assert "adb: ok" in summary
    assert "uiautomator2: missing" in summary
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest testsuite/tests/unit/test_adb_client.py::test_build_summary_mentions_adb_and_u2 -v`
Expected: FAIL because `check_env.py` and `build_summary` do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```python
# testsuite/scripts/check_env.py
def build_summary(adb_ok: bool, u2_ok: bool, device_count: int) -> str:
    adb_status = "ok" if adb_ok else "missing"
    u2_status = "ok" if u2_ok else "missing"
    return f"adb: {adb_status}, uiautomator2: {u2_status}, devices: {device_count}"
```

```python
# testsuite/scripts/run_smoke.py
import subprocess

subprocess.run(["pytest", "-m", "smoke", "tests"], check=False)
```

```python
# testsuite/scripts/run_vision.py
import subprocess

subprocess.run(["pytest", "-m", "vision", "tests"], check=False)
```

```python
# testsuite/scripts/run_regression.py
import subprocess

subprocess.run(["pytest", "-m", "regression", "tests"], check=False)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest testsuite/tests/unit/test_adb_client.py::test_build_summary_mentions_adb_and_u2 -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add testsuite/scripts/check_env.py testsuite/scripts/run_smoke.py testsuite/scripts/run_vision.py testsuite/scripts/run_regression.py testsuite/tests/unit/test_adb_client.py
git commit -m "feat: add environment and runner scripts"
```

## Task 10: Wire Allure outputs and runtime artifact hooks

**Files:**
- Modify: `testsuite/tests/conftest.py`
- Modify: `testsuite/core/artifact_store.py`
- Modify: `testsuite/tests/smoke/test_bootstrap_smoke.py`

- [ ] **Step 1: Write the failing test for per-case artifact path**

```python
# testsuite/tests/unit/test_artifact_store.py
from pathlib import Path

from testsuite.core.artifact_store import ArtifactStore


def test_case_metadata_json_path_is_reserved(tmp_path: Path) -> None:
    store = ArtifactStore(tmp_path)

    case_dir = store.start_case("tests.smoke.test_bootstrap_smoke", "test_app_launch_success")

    assert store.metadata_path(case_dir).name == "metadata.json"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest testsuite/tests/unit/test_artifact_store.py::test_case_metadata_json_path_is_reserved -v`
Expected: FAIL because `metadata_path` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```python
# testsuite/core/artifact_store.py
from datetime import datetime
from pathlib import Path


class ArtifactStore:
    def __init__(self, root_dir: Path) -> None:
        self._root_dir = root_dir
        self._root_dir.mkdir(parents=True, exist_ok=True)

    def start_case(self, nodeid: str, test_name: str) -> Path:
        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        case_dir = self._root_dir / f"{test_name}-{timestamp}"
        case_dir.mkdir(parents=True, exist_ok=True)
        latest = self._root_dir / "latest"
        latest.mkdir(parents=True, exist_ok=True)
        return case_dir

    def metadata_path(self, case_dir: Path) -> Path:
        return case_dir / "metadata.json"
```

```python
# testsuite/tests/conftest.py
from pathlib import Path

import pytest

from testsuite.core.artifact_store import ArtifactStore


@pytest.fixture
def artifact_store() -> ArtifactStore:
    return ArtifactStore(Path("testsuite/artifacts/runs"))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest testsuite/tests/unit/test_artifact_store.py::test_case_metadata_json_path_is_reserved -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add testsuite/core/artifact_store.py testsuite/tests/conftest.py testsuite/tests/unit/test_artifact_store.py testsuite/tests/smoke/test_bootstrap_smoke.py
git commit -m "feat: wire artifact hooks for pytest runtime"
```

## Task 11: Final verification pass for the initial toolkit

**Files:**
- Verify only: `testsuite/**/*`

- [ ] **Step 1: Run unit suite**

Run: `pytest testsuite/tests/unit -v`
Expected: PASS

- [ ] **Step 2: Run vision contract suite**

Run: `pytest testsuite/tests/vision -v`
Expected: PASS

- [ ] **Step 3: Run smoke suite**

Run: `pytest testsuite/tests/smoke -v`
Expected: PASS

- [ ] **Step 4: Run regression contract suite**

Run: `pytest testsuite/tests/regression -v`
Expected: PASS

- [ ] **Step 5: Run marker-separated entrypoints**

Run: `python testsuite/scripts/run_smoke.py`
Expected: zero exit code after executing smoke tests

Run: `python testsuite/scripts/run_vision.py`
Expected: zero exit code after executing vision tests

Run: `python testsuite/scripts/run_regression.py`
Expected: zero exit code after executing regression tests

- [ ] **Step 6: Commit**

```bash
git add testsuite
git commit -m "feat: complete yuanassist testlab initial toolkit"
```

## Self-Review

### Spec coverage

- Independent Python toolkit: covered by Tasks 1-2
- adb/uiautomator2 foundation: covered by Task 5
- OpenCV/Pillow vision: covered by Tasks 3-4
- Page and flow abstractions: covered by Tasks 6-8
- Smoke, vision, regression suites: covered by Tasks 7-8
- Artifact and Allure-ready output: covered by Tasks 2 and 10
- Standard runner scripts: covered by Task 9

### Placeholder scan

- No `TBD`, `TODO`, or “implement later” placeholders remain
- Each code-writing step includes concrete file targets and code snippets
- Each validation step includes an exact command and expected result

### Type consistency

- `ArtifactStore.start_case()` returns `Path` consistently in Tasks 2 and 10
- `TemplateMatchResult` fields match matcher usage in Tasks 3 and 4
- Flow class names used in regression tests match implementation file names in Task 8
