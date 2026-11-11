"""ConfigManager 环境变量占位符与类型读取测试。"""

import pytest


@pytest.fixture
def config_manager(load_module):
    module = load_module("config_manager_under_test", "config/ConfigManager.py")
    return module.config_manager


def test_environment_overrides_inline_default(config_manager, monkeypatch):
    monkeypatch.setenv("TEST_URL", "http://env:8080/x")
    assert config_manager._resolve_value("${TEST_URL:http://default:1/x}") == "http://env:8080/x"


def test_inline_default_is_used_when_env_missing(config_manager, monkeypatch):
    monkeypatch.delenv("TEST_MISSING_URL", raising=False)
    assert config_manager._resolve_value("${TEST_MISSING_URL:http://default:1/x}") == "http://default:1/x"


def test_fallback_is_used_without_inline_default(config_manager, monkeypatch):
    monkeypatch.delenv("TEST_NO_DEFAULT", raising=False)
    assert config_manager._resolve_value("${TEST_NO_DEFAULT}", "fallback") == "fallback"


def test_ip_and_numeric_password_stay_strings(config_manager, monkeypatch):
    monkeypatch.setattr(config_manager, "_config", {
        "net": {
            "ip": "${TEST_IP:192.168.150.1}",
            "password": "${TEST_PASSWORD:123321}",
        }
    })
    monkeypatch.delenv("TEST_IP", raising=False)
    monkeypatch.delenv("TEST_PASSWORD", raising=False)

    assert config_manager.get("net.ip") == "192.168.150.1"
    assert config_manager.get("net.password") == "123321"


def test_precedence_env_inline_and_fallback(config_manager, monkeypatch):
    monkeypatch.setattr(config_manager, "_config", {"value": "${TEST_PRECEDENCE:inline}"})
    monkeypatch.delenv("TEST_PRECEDENCE", raising=False)
    assert config_manager.get("value", "fallback") == "inline"

    monkeypatch.setenv("TEST_PRECEDENCE", "env")
    assert config_manager.get("value", "fallback") == "env"


def test_empty_environment_value_wins(config_manager, monkeypatch):
    monkeypatch.setattr(config_manager, "_config", {"value": "${TEST_EMPTY:inline}"})
    monkeypatch.setenv("TEST_EMPTY", "")
    assert config_manager.get("value") == ""


def test_typed_getters(config_manager, monkeypatch):
    monkeypatch.setattr(config_manager, "_config", {
        "typed": {
            "bool": "${TEST_BOOL:true}",
            "int": "${TEST_INT:3}",
            "float": "${TEST_FLOAT:1.5}",
        }
    })
    monkeypatch.setenv("TEST_BOOL", "false")
    monkeypatch.setenv("TEST_INT", "7")
    monkeypatch.setenv("TEST_FLOAT", "2.5")

    assert config_manager.get_bool("typed.bool") is False
    assert config_manager.get_int("typed.int") == 7
    assert config_manager.get_float("typed.float") == 2.5


def test_typed_getters_accept_yaml_literals(config_manager, monkeypatch):
    monkeypatch.setattr(config_manager, "_config", {
        "typed": {"enabled": True, "disabled": False, "count": 5, "timeout": 10.5}
    })

    assert config_manager.get_bool("typed.enabled") is True
    assert config_manager.get_bool("typed.disabled") is False
    assert config_manager.get_int("typed.count") == 5
    assert config_manager.get_float("typed.timeout") == 10.5


def test_invalid_typed_values_raise(config_manager, monkeypatch):
    monkeypatch.setattr(config_manager, "_config", {
        "bad": {"bool": "${TEST_BAD_BOOL:true}", "int": "${TEST_BAD_INT:1}"}
    })
    monkeypatch.setenv("TEST_BAD_BOOL", "maybe")
    monkeypatch.setenv("TEST_BAD_INT", "not-a-number")

    with pytest.raises(ValueError):
        config_manager.get_bool("bad.bool")
    with pytest.raises(ValueError):
        config_manager.get_int("bad.int")


def test_missing_key_returns_fallback(config_manager):
    assert config_manager.get("not.exists", "fallback") == "fallback"
