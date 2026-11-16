"""Small pure helpers shared by the widgets (and unit tested)."""

from __future__ import annotations

from datetime import datetime, timezone

STATUS_COLORS = {
    "pending": "#9B6DDB",
    "active": "#3FB950",
    "expired": "#E3B341",
    "blocked": "#E5484D",
    "cancelled": "#8B8D98",
}


def status_label(status: str) -> str:
    return {
        "pending": "Pending",
        "active": "Active",
        "expired": "Expired",
        "blocked": "Blocked",
        "cancelled": "Cancelled",
    }.get(status, status.title() if status else "—")


def status_color(status: str) -> str:
    return STATUS_COLORS.get(status, "#8B8D98")


def format_ts(millis: int | None, fallback: str = "—") -> str:
    if not millis:
        return fallback
    moment = datetime.fromtimestamp(millis / 1000, tz=timezone.utc).astimezone()
    return moment.strftime("%d %b %Y")


def format_datetime(millis: int | None, fallback: str = "—") -> str:
    if not millis:
        return fallback
    moment = datetime.fromtimestamp(millis / 1000, tz=timezone.utc).astimezone()
    return moment.strftime("%d %b %Y %H:%M")


def relative_time(millis: int | None, now_millis: int | None = None) -> str:
    if not millis:
        return "never"
    now = now_millis if now_millis is not None else int(datetime.now(tz=timezone.utc).timestamp() * 1000)
    delta = max(0, now - millis) / 1000
    if delta < 90:
        return "just now"
    minutes = delta / 60
    if minutes < 60:
        return f"{int(minutes)} min ago"
    hours = minutes / 60
    if hours < 24:
        return f"{int(hours)} h ago"
    days = hours / 24
    if days < 30:
        return f"{int(days)} d ago"
    return format_ts(millis)


def end_date_label(license_row: dict) -> str:
    expires = license_row.get("expiresAt")
    if expires is None:
        return "Lifetime"
    days = license_row.get("daysLeft")
    if days is None:
        return format_ts(expires)
    if days <= 0:
        return f"{format_ts(expires)} (ended)"
    if days == 1:
        return f"{format_ts(expires)} (last day)"
    return f"{format_ts(expires)} ({days} days left)"


def plan_label(license_row: dict) -> str:
    mode = license_row.get("quotaMode")
    limit = license_row.get("quotaLimit")
    if mode == "daily":
        return f"{limit}/day"
    if mode == "total":
        return f"{limit} total"
    return "Unlimited"


def plan_summary(duration_days: int | None, quota_mode: str, quota_limit: int | None) -> str:
    duration = "Lifetime" if duration_days is None else f"{duration_days} days"
    if quota_mode == "daily":
        usage = f"max {quota_limit} images per day"
    elif quota_mode == "total":
        usage = f"{quota_limit} images in total"
    else:
        usage = "unlimited images"
    return f"{duration} · {usage}"


def remaining_label(license_row: dict) -> str:
    remaining = license_row.get("remaining")
    if remaining is None:
        return "—"
    return str(remaining)


def activation_message(key: str, name: str) -> str:
    who = f", {name}" if name else ""
    return (
        f"Your TextPic activation key{who}: {key}\n\n"
        "Open TextPic Studio, paste the key on the Activation screen and tap Activate. "
        "Typing works without it; the key unlocks making images."
    )


def mask_key(key: str) -> str:
    if len(key) < 4:
        return "••••"
    return "••••-••••-" + key[-4:]
