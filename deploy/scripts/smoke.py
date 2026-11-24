#!/usr/bin/env python3
import http.cookiejar
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser


class CsrfParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.tokens = {}

    def handle_starttag(self, tag, attrs):
        if tag != "input":
            return
        values = dict(attrs)
        if values.get("name") in ("_csrf", "formToken", "actionId", "expectedVersion"):
            self.tokens[values["name"]] = values.get("value")


def request(opener, url, data=None):
    req = urllib.request.Request(url, data=data)
    return opener.open(req, timeout=10)


def csrf_token(opener, url):
    return form_tokens(opener, url)["_csrf"]


def form_tokens(opener, url):
    with request(opener, url) as response:
        html = response.read().decode("utf-8")
    parser = CsrfParser()
    parser.feed(html)
    if not parser.tokens.get("_csrf"):
        raise RuntimeError(f"No CSRF token found in {url}")
    return parser.tokens


def page(opener, url, values=None):
    data = None if values is None else urllib.parse.urlencode(values, doseq=True).encode("utf-8")
    with request(opener, url, data) as response:
        return response.geturl(), response.read().decode("utf-8")


def command(opener, form_url, route, **fields):
    tokens = form_tokens(opener, form_url)
    envelope = {name: tokens[name] for name in ("_csrf", "actionId", "expectedVersion")}
    return page(opener, route, envelope | fields)


def verify_rests(opener, base_url):
    campaign_list = f"{base_url}/campaigns"
    setup, _ = page(opener, campaign_list, form_tokens(opener, campaign_list) | {"name": "Smoke table"})
    if not setup.endswith("/setup"):
        raise RuntimeError("Campaign setup did not open.")
    campaign = setup.removesuffix("/setup")
    command(opener, setup, setup, step=1, skip="true")
    command(opener, campaign, campaign + "/sessions")
    command(opener, campaign + "/settings", campaign + "/settings/hitDiceMethod/decisions", value="FIXED")
    characters = campaign + "/characters"
    sheet, _ = command(
        opener, characters + "/new", characters,
        step="review", speciesKey="srd-5.1:human", classKey="srd-5.1:fighter",
        fightingStyleKey="srd-5.1:defense", abilityMethod="STANDARD_ARRAY",
        abilityScores=[14, 13, 15, 8, 12, 10], skillChoices=["athletics", "perception"],
        languageChoices="elvish", equipmentChoices=[
            "srd-5.1:fighter-kit-0-0", "srd-5.1:fighter-kit-1-0",
            "srd-5.1:fighter-kit-2-0", "srd-5.1:fighter-kit-3-0",
        ], name="Smoke Fighter",
    )
    character = sheet.rsplit("/", 1)[-1]
    command(opener, sheet, sheet + "/edits", section="hit-points", currentHp=1, temporaryHp=0)

    rests = campaign + "/rests"
    for kind, minutes, expected_hp, clock, event in (
        ("short", 60, 10, 3600, "HitDieSpent"),
        ("long", 480, 13, 32400, "HitDiceRecovered"),
    ):
        new_form = rests + "/new?kind=" + kind
        options = dict(kind=kind, durationMinutes=minutes, participantIds=character)
        if kind == "short":
            options[f"spendCount[{character}]"] = 1
        _, preview = command(opener, new_form, rests + "/previews", **options)
        if 'id="rest-preview"' not in preview:
            raise RuntimeError(f"The {kind} rest preview did not render.")
        _, result = command(opener, new_form, rests, **options)
        if f'data-event="{event}"' not in result or f"{clock} seconds elapsed" not in result:
            raise RuntimeError(f"The {kind} rest result or clock was wrong.")
        _, character_sheet = page(opener, sheet)
        if f"{expected_hp} / 13" not in character_sheet:
            raise RuntimeError(f"The {kind} rest did not restore the expected hit points.")
    print("Short rest, long rest, Hit Dice, and game clock passed.")


def wait_until_ready(opener, base_url):
    deadline = time.monotonic() + 120
    last_error = None
    while time.monotonic() < deadline:
        try:
            with request(opener, f"{base_url}/actuator/health/readiness") as response:
                body = response.read().decode("utf-8")
                if response.status == 200 and '"status":"UP"' in body.replace(" ", ""):
                    return
                last_error = RuntimeError(f"Readiness response was not UP: {body}")
        except (OSError, urllib.error.URLError) as error:
            last_error = error
        time.sleep(2)
    raise RuntimeError(f"Application did not become ready: {last_error}")


def run(base_url):
    cookies = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))
    wait_until_ready(opener, base_url)

    suffix = secrets.token_hex(4)
    username = f"smoke-{suffix}"
    password = f"Walking-Skeleton-{secrets.token_hex(10)}!"
    display_name = "Smoke Test Admin"

    tokens = form_tokens(opener, f"{base_url}/register")
    payload = urllib.parse.urlencode({
        "_csrf": tokens["_csrf"],
        "formToken": tokens["formToken"],
        "username": username,
        "displayName": display_name,
        "password": password,
    }).encode("utf-8")
    with request(opener, f"{base_url}/register", payload) as response:
        if response.status != 200 or not response.geturl().endswith("/campaigns"):
            raise RuntimeError(f"Registration did not reach the campaign list: {response.geturl()}")
    print("Readiness passed and first-account registration completed.")

    # Start a fresh browser session to verify password sign-in independently of registration.
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
    )
    token = csrf_token(opener, f"{base_url}/login")
    payload = urllib.parse.urlencode({
        "_csrf": token,
        "username": username,
        "password": password,
    }).encode("utf-8")
    with request(opener, f"{base_url}/login", payload) as response:
        body = response.read().decode("utf-8")
        if response.status != 200 or not response.geturl().endswith("/campaigns"):
            raise RuntimeError(f"Login did not reach the workspace: {response.geturl()}")
        if "<h1>Campaigns</h1>" not in body:
            raise RuntimeError("The authenticated campaign list did not render.")
    print("Login and authenticated workspace passed.")
    verify_rests(opener, base_url)


if __name__ == "__main__":
    try:
        run(sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://localhost:8080")
    except Exception as error:
        print(f"Smoke test failed: {error}", file=sys.stderr)
        sys.exit(1)
