import os

import requests


def staff_portal_url(path=""):
    base_url = os.environ.get(
        "ROCKET_STAFF_PORTAL_URL",
        "https://rocketpubserver.co.uk/staff",
    ).rstrip("/")

    return f"{base_url}/{path.lstrip('/')}" if path else base_url


def staff_api_url(path=""):
    base_url = os.environ.get(
        "ROCKET_STAFF_API_URL",
        "http://localhost:8080",
    ).rstrip("/")

    return f"{base_url}/{path.lstrip('/')}" if path else base_url


def authenticated_staff(cookie_value):
    """Return the Spring user attached to a JSESSIONID cookie."""
    if not cookie_value:
        return None

    try:
        response = requests.get(
            staff_api_url("/api/auth/me"),
            cookies={"JSESSIONID": cookie_value},
            timeout=3,
        )
    except requests.RequestException:
        return None

    if response.status_code != 200:
        return None

    try:
        user = response.json()
    except ValueError:
        return None

    if not isinstance(user, dict) or not user.get("id") or not user.get("name"):
        return None

    return user


def logout_staff(cookie_value):
    if not cookie_value:
        return

    try:
        requests.post(
            staff_api_url("/api/auth/logout"),
            cookies={"JSESSIONID": cookie_value},
            timeout=3,
        )
    except requests.RequestException:
        return
