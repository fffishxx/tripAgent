"""完整自然语言 E2E（可选，RUN_E2E=1 时执行）。

复用 tripagent-backend/scripts/verify_stage5_e2e.py，覆盖：
规划 → 保存 → 查询 → 更新 → 删除 → 打车 → 匿名 SSE 2002。
"""

import os
import subprocess
import sys
from pathlib import Path

import httpx
import pytest

from conftest import JAVA_BASE, PY_BASE, SERVICE_TOKEN

pytestmark = [pytest.mark.integration, pytest.mark.e2e]


def test_full_agent_e2e():
    if os.environ.get("RUN_E2E") != "1":
        pytest.skip("设置 RUN_E2E=1 才执行完整自然语言 E2E")

    gateway = os.environ.get("GATEWAY_BASE", "http://127.0.0.1:10086")
    try:
        health = httpx.get(gateway + "/health", timeout=5)
        if health.status_code != 200:
            pytest.skip(f"Gateway 健康检查失败: HTTP {health.status_code}")
    except httpx.HTTPError as exc:
        pytest.skip(f"Gateway 不可用: {exc}")

    script = (Path(__file__).resolve().parents[3]
              / "tripagent-backend" / "scripts" / "verify_stage5_e2e.py")
    env = {
        **os.environ,
        "JAVA_BASE": JAVA_BASE,
        "PY_BASE": PY_BASE,
        "AGENT_SERVICE_TOKEN": SERVICE_TOKEN,
    }
    result = subprocess.run(
        [sys.executable, str(script)],
        cwd=str(script.parent.parent),
        env=env,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=900,
    )
    if result.returncode != 0:
        pytest.fail(
            "E2E 失败\nSTDOUT:\n"
            + result.stdout[-8000:]
            + "\nSTDERR:\n"
            + result.stderr[-4000:]
        )
