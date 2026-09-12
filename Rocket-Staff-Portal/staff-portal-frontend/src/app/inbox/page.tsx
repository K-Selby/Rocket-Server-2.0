"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

import { CurrentUser, useCurrentUser } from "../context/CurrentUserContext";
import { DayOffRequest, isRecent, useInbox } from "../context/InboxContext";

import styles from "./Inbox.module.css";

type DayOffStatus = DayOffRequest["status"];

type DiaryEntry = {
  id: number;
  entryDate: string;
  type: string;
  status: string;
  staffMember: CurrentUser | null;
  note: string | null;
  availableFrom: string | null;
  availableTo: string | null;
  availableUntilFinish: boolean;
};

type PubEvent = {
  id: number;
  eventDate: string;
  eventTime: string | null;
  title: string;
  eventType: string;
};

type LargeParty = {
  id: number;
  eventDate: string;
  startTime: string | null;
  name: string;
  partySize: number;
};

type DayContext = {
  month: string;
  version: number;
  changes: string;
  diary: DiaryEntry[];
  events: PubEvent[];
  parties: LargeParty[];
};

type RotaShift = {
  id: number;
  shiftDate: string;
  startTime: string | null;
  endTime: string | null;
  finishShift: boolean;
};

type ShiftSwapStatus =
  | "PENDING"
  | "AWAITING_MANAGER_APPROVAL"
  | "APPROVED"
  | "REJECTED"
  | "CANCELLED";

type ShiftSwap = {
  id: number;
  requester: CurrentUser;
  targetStaffMember: CurrentUser;
  requesterShift: RotaShift;
  targetShift: RotaShift | null;
  status: ShiftSwapStatus;
  note: string | null;
  managerActedBy?: CurrentUser | null;
  managerActedAt?: string | null;
};

type StaffSettings = {
  id: number;
  requireManagerShiftSwapApproval: boolean;
};

const API = "";

const DAY_OFF_STATUSES: DayOffStatus[] = [
  "PENDING",
  "APPROVED",
  "DECLINED",
];

/* Converts a Date into YYYY-MM-DD. */
function localDate(date: Date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}

/* Gives API statuses readable labels. */
function statusLabel(status: string) {
  if (status === "REJECTED") return "Declined";
  if (status === "AWAITING_MANAGER_APPROVAL") return "Manager approval";

  return status.charAt(0) + status.slice(1).toLowerCase();
}

/* Formats a normal request date. */
function dateLabel(date: string) {
  return new Date(`${date}T12:00:00`).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

/* Formats a shift date including the weekday. */
function shiftDateLabel(date: string) {
  return new Date(`${date}T12:00:00`).toLocaleDateString("en-GB", {
    weekday: "long",
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

/* Converts 24-hour API times back into pub-style times. */
function pubTime(time: string | null) {
  if (!time) return "";

  const [hourText, minute] = time.split(":");
  const hour = Number(hourText);
  const displayHour = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour;

  return minute === "00" ? String(displayHour) : `${displayHour}:${minute}`;
}

/* Shows a shift as 5-F, 12-4 etc. */
function shiftTimeLabel(shift: RotaShift) {
  const start = pubTime(shift.startTime);
  return shift.finishShift ? `${start}-F` : `${start}-${pubTime(shift.endTime)}`;
}

/* Shows all dates belonging to a grouped day-off request. */
function formatRequestDates(dates: string[]) {
  return [...dates].sort().map(dateLabel).join(", ");
}

/* Formats the calendar month heading. */
function monthLabel(month: string) {
  return new Date(`${month}-01T12:00:00`).toLocaleDateString("en-GB", {
    month: "long",
    year: "numeric",
  });
}

/* Moves the review calendar backwards or forwards. */
function moveMonth(month: string, amount: number) {
  const date = new Date(`${month}-01T12:00:00`);
  date.setMonth(date.getMonth() + amount);
  return localDate(date).slice(0, 7);
}

/* Builds complete Sunday-Saturday rows for a month. */
function monthDays(month: string) {
  const start = new Date(`${month}-01T12:00:00`);
  const end = new Date(start.getFullYear(), start.getMonth() + 1, 0, 12);

  start.setDate(start.getDate() - start.getDay());
  end.setDate(end.getDate() + 6 - end.getDay());

  const days: string[] = [];

  for (
    const day = new Date(start);
    day <= end;
    day.setDate(day.getDate() + 1)
  ) {
    days.push(localDate(day));
  }

  return days;
}

/* Finds the first date belonging to a grouped request. */
function firstRequestDate(request?: DayOffRequest) {
  return request ? [...request.dates].sort()[0] : undefined;
}

/* Keeps Manager requests in submission order. */
function oldestRequestFirst(first: DayOffRequest, second: DayOffRequest) {
  return first.createdAt.localeCompare(second.createdAt) || first.id - second.id;
}

/* Shared helper for Spring API requests. */
async function api<T>(path: string, method = "GET"): Promise<T> {
  const response = await fetch(`${API}${path}`, { method });
  const data = await response.json().catch(() => null);

  if (!response.ok) {
    throw new Error(data?.message || `Request failed (${response.status})`);
  }

  if (data === null) {
    throw new Error("The server returned an empty response.");
  }

  return data as T;
}

/* Converts network errors into something useful. */
function errorMessage(error: unknown) {
  if (error instanceof TypeError) {
    return "Could not reach the server. Check the backend is running.";
  }

  return error instanceof Error ? error.message : "Could not load requests.";
}

export default function InboxPage() {
  const { currentUser, loadingUser, userError } = useCurrentUser();

  if (loadingUser) {
    return (
      <main>
        <h1>Inbox</h1>
        <p>Loading...</p>
      </main>
    );
  }

  if (!currentUser) {
    return (
      <main>
        <h1>Inbox</h1>
        <p role="alert">{userError || "No current user is available."}</p>
        <button onClick={() => window.location.reload()}>Retry</button>
      </main>
    );
  }

  return (
    <Inbox
      key={`${currentUser.id}-${currentUser.role}`}
      user={currentUser}
    />
  );
}

/* Controls Team/My requests and Days off/Shift swaps. */
function Inbox({ user }: { user: CurrentUser }) {
  const {
    pendingCount,
    unreadCount,
    shiftPendingCount,
    managerShiftApprovalCount,
  } = useInbox();

  const manager = user.role === "MANAGER" || user.role === "ADMIN";

  const [scope, setScope] = useState<"team" | "mine">(
    manager ? "team" : "mine"
  );
  const [kind, setKind] = useState<"days" | "swaps">("days");

  const dayBadge = scope === "team" ? pendingCount : unreadCount;
  const shiftBadge =
    scope === "team" ? managerShiftApprovalCount : shiftPendingCount;

  return (
    <main className="inbox-workspace">
      <div className="page-header">
        <h1>Inbox</h1>
      </div>

      {manager && (
        <nav className="inbox-scope" aria-label="Whose requests">
          <button
            aria-pressed={scope === "team"}
            onClick={() => {
              setScope("team");
              setKind("days");
            }}
          >
            Team requests
            {pendingCount + managerShiftApprovalCount > 0 && (
              <span className="inbox-count">
                {pendingCount + managerShiftApprovalCount}
              </span>
            )}
          </button>

          <button
            aria-pressed={scope === "mine"}
            onClick={() => {
              setScope("mine");
              setKind("days");
            }}
          >
            My requests
            {unreadCount + shiftPendingCount > 0 && (
              <span className="inbox-count">
                {unreadCount + shiftPendingCount}
              </span>
            )}
          </button>
        </nav>
      )}

      <nav className="inbox-kinds" aria-label="Request type">
        <button
          aria-pressed={kind === "days"}
          onClick={() => setKind("days")}
        >
          Days off
          {dayBadge > 0 && <span className="inbox-count">{dayBadge}</span>}
        </button>

        <button
          aria-pressed={kind === "swaps"}
          onClick={() => setKind("swaps")}
        >
          Shift swaps
          {shiftBadge > 0 && <span className="inbox-count">{shiftBadge}</span>}
        </button>
      </nav>

      {scope === "team" && kind === "days" && <TeamDayOffReview user={user} />}
      {scope === "team" && kind === "swaps" && (
        <ManagerShiftSwapInbox user={user} />
      )}
      {scope === "mine" && kind === "days" && (
        <PersonalDayOffInbox user={user} />
      )}
      {scope === "mine" && kind === "swaps" && <ShiftSwapInbox user={user} />}
    </main>
  );
}

/* Shows one staff member's own day-off requests. */
function PersonalDayOffInbox({ user }: { user: CurrentUser }) {
  const {
    requests,
    loading,
    error: loadError,
    isUnread,
    markRead,
    updateRequest,
    refresh,
  } = useInbox();

  const [status, setStatus] = useState<DayOffStatus>("PENDING");
  const [history, setHistory] = useState(false);
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const personalRequests = requests.filter(
    request => request.requester.id === user.id
  );

  const inView = personalRequests
    .filter(request => {
      if (request.status === "PENDING") return !history;
      return history ? !isRecent(request) : isRecent(request);
    })
    .sort(
      (first, second) =>
        second.createdAt.localeCompare(first.createdAt) || second.id - first.id
    );

  const visible = inView.filter(request => request.status === status);
  const cancelled = personalRequests.filter(
    request => request.status === "CANCELLED"
  );

  async function cancelRequest(request: DayOffRequest) {
    if (
      busy !== null ||
      request.status !== "PENDING" ||
      request.requester.id !== user.id
    ) {
      return;
    }

    if (!window.confirm("Cancel this request?")) return;

    setBusy(request.id);
    setNotice("");

    try {
      const updated = await api<DayOffRequest>(
        `/api/day-off-requests/${request.id}?currentUserId=${user.id}`,
        "DELETE"
      );

      updateRequest(updated);
      setError("");
      setNotice(`Request ${updated.status.toLowerCase()}.`);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setBusy(null);
    }
  }

  function requestCard(request: DayOffRequest) {
    const unread = isUnread(request);

    return (
      <article
        key={request.id}
        className="inbox-detail"
        style={{
          marginTop: "0.75rem",
          border: unread ? "2px solid #176b3a" : undefined,
          background: unread ? "#eef8f0" : undefined,
        }}
      >
        {unread && (
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              marginBottom: ".75rem",
            }}
          >
            <strong style={{ color: "#176b3a" }}>New update</strong>
          </div>
        )}

        <div className="inbox-detail-header">
          <p style={{ margin: 0 }}>{formatRequestDates(request.dates)}</p>

          <span
            className={`request-status request-${request.status.toLowerCase()}`}
          >
            {statusLabel(request.status)}
          </span>
        </div>

        {request.note && <p>{request.note}</p>}

        {request.actedBy && (
          <p className="muted-text">
            {statusLabel(request.status)} by {request.actedBy.name}
          </p>
        )}

        {request.status === "PENDING" && (
          <div className="request-actions">
            <button
              className="danger-button"
              disabled={busy !== null}
              onClick={() => cancelRequest(request)}
            >
              Cancel request
            </button>
          </div>
        )}
      </article>
    );
  }

  return (
    <section aria-label="My day-off requests">
      <div className="inbox-filter-bar">
        <nav className="inbox-status-tabs" aria-label="Request status">
          {DAY_OFF_STATUSES.map(value => (
            <button
              key={value}
              aria-pressed={status === value}
              onClick={() => {
                setStatus(value);
                setNotice("");

                if (value === "APPROVED" || value === "DECLINED") {
                  markRead(
                    inView.filter(request => request.status === value)
                  );
                }
              }}
            >
              {statusLabel(value)}
              <span className="inbox-count">
                {inView.filter(request => request.status === value).length}
              </span>
            </button>
          ))}
        </nav>

        <button
          className="inbox-history-toggle"
          aria-pressed={history}
          onClick={() => {
            setHistory(current => !current);

            if (!history && status === "PENDING") {
              setStatus("APPROVED");
            }

            setNotice("");
          }}
        >
          {history ? "Back to recent" : "History"}
        </button>
      </div>

      <p className="muted-text">
        {history
          ? "Decisions older than two weeks."
          : status === "PENDING"
            ? "All outstanding requests."
            : "Decisions from the last two weeks."}
      </p>

      {(error || loadError) && (
        <div className="page-error" role="alert">
          {error || loadError} <button onClick={refresh}>Retry</button>
        </div>
      )}

      {notice && (
        <p className="inbox-notice" role="status">
          {notice}
        </p>
      )}

      {loading ? (
        <p>Loading requests...</p>
      ) : visible.length ? (
        visible.map(requestCard)
      ) : (
        <p className="inbox-empty">No {status.toLowerCase()} requests here.</p>
      )}

      {history && !loading && cancelled.length > 0 && (
        <details style={{ marginTop: "1rem" }}>
          <summary>Cancelled requests ({cancelled.length})</summary>
          {cancelled.map(requestCard)}
        </details>
      )}
    </section>
  );
}

/* Shared Manager/Admin day-off review screen. */
function TeamDayOffReview({ user }: { user: CurrentUser }) {
  const { requests, loading, error, refresh } = useInbox();

  if (user.role !== "MANAGER" && user.role !== "ADMIN") return null;
  if (loading) return <p>Loading team requests...</p>;

  if (error && !requests.length) {
    return (
      <p role="alert">
        {error} <button onClick={refresh}>Retry</button>
      </p>
    );
  }

  const firstPending = requests
    .filter(request => request.status === "PENDING")
    .sort(oldestRequestFirst)[0];

  return <TeamDayOffWorkspace user={user} initial={firstPending} />;
}

function TeamDayOffWorkspace({
  user,
  initial,
}: {
  user: CurrentUser;
  initial?: DayOffRequest;
}) {
  const {
    requests,
    updateRequest,
    error: inboxError,
    refresh,
  } = useInbox();

  const today = localDate(new Date());
  const initialDay = firstRequestDate(initial) || today;

  const [status, setStatus] = useState<DayOffStatus>("PENDING");
  const [history, setHistory] = useState(false);
  const [month, setMonth] = useState(initialDay.slice(0, 7));
  const [day, setDay] = useState(initialDay);
  const [selectedId, setSelectedId] = useState<number | null>(
    initial?.id ?? null
  );
  const [context, setContext] = useState<DayContext | null>(null);
  const [contextError, setContextError] = useState("");
  const [version, setVersion] = useState(0);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");

  function requestIsInView(request: DayOffRequest, value: DayOffStatus) {
    return (
      request.status === value &&
      (value === "PENDING" ||
        (history ? !isRecent(request) : isRecent(request)))
    );
  }

  const queue = requests
    .filter(request => requestIsInView(request, status))
    .sort(oldestRequestFirst);

  const selected =
    queue.find(request => request.id === selectedId) ?? null;

  const selectedIndex = queue.findIndex(request => request.id === selectedId);

  const requestsForDay = queue.filter(request => request.dates.includes(day));
  const calendar = monthDays(month);

  const changes = requests
    .map(request => `${request.id}:${request.status}:${request.actedAt}`)
    .sort()
    .join("|");

  const contextReady =
    context?.month === month &&
    context.version === version &&
    context.changes === changes;

  const diary = contextReady
    ? context.diary
        .filter(entry => entry.entryDate === day)
        .sort((first, second) => first.id - second.id)
    : [];

  const daysOff = diary.filter(
    entry => entry.type === "DAY_OFF" && entry.status !== "REJECTED"
  );

  const available = diary.filter(entry => entry.type === "AVAILABLE");

  const events = contextReady
    ? context.events.filter(event => event.eventDate === day)
    : [];

  const parties = contextReady
    ? context.parties.filter(party => party.eventDate === day)
    : [];

  /* Loads Diary, events and parties for the visible month. */
  useEffect(() => {
    let active = true;

    async function loadContext() {
      setContextError("");

      try {
        const dates = monthDays(month);
        const range = `from=${dates[0]}&to=${dates[dates.length - 1]}`;

        const [diaryData, eventData, partyData] = await Promise.all([
          api<DiaryEntry[]>(`/api/diary/range?${range}`),
          api<PubEvent[]>(`/api/events?${range}`),
          api<LargeParty[]>(`/api/large-parties?${range}`),
        ]);

        if (active) {
          setContext({
            month,
            version,
            changes,
            diary: diaryData,
            events: eventData,
            parties: partyData,
          });
        }
      } catch (loadError) {
        if (active) {
          setContextError(
            loadError instanceof Error
              ? loadError.message
              : "Could not load day details."
          );
        }
      }
    }

    loadContext();

    return () => {
      active = false;
    };
  }, [month, version, changes]);

  function selectRequest(request: DayOffRequest) {
    const date = firstRequestDate(request) || today;

    setSelectedId(request.id);
    setDay(date);
    setMonth(date.slice(0, 7));
    setNotice("");
    setActionError("");
  }

  function changeStatus(value: DayOffStatus, past = history) {
    setStatus(value);
    setHistory(past);
    setNotice("");
    setActionError("");

    const next = requests
      .filter(request => {
        return (
          request.status === value &&
          (value === "PENDING" || (past ? !isRecent(request) : isRecent(request)))
        );
      })
      .sort(oldestRequestFirst)[0];

    if (next) selectRequest(next);
    else setSelectedId(null);
  }

  function selectDate(date: string) {
    setDay(date);
    setMonth(date.slice(0, 7));

    if (!selected?.dates.includes(date)) {
      setSelectedId(
        queue.find(request => request.dates.includes(date))?.id ?? null
      );
    }

    setNotice("");
    setActionError("");
  }

  function navigateMonth(amount: number) {
    const next = moveMonth(month, amount);

    setMonth(next);
    setDay(`${next}-01`);
    setSelectedId(null);
    setNotice("");
    setActionError("");
  }

  async function decide(action: "approve" | "decline") {
    if (!selected || selected.status !== "PENDING" || busy) return;

    setBusy(true);
    setActionError("");

    try {
      const updated = await api<DayOffRequest>(
        `/api/day-off-requests/${selected.id}/${action}?currentUserId=${user.id}`,
        "PUT"
      );

      updateRequest(updated);
      setStatus(action === "approve" ? "APPROVED" : "DECLINED");
      setHistory(false);
      setVersion(current => current + 1);

      setNotice(
        `${updated.requester.name}’s request ${
          action === "approve" ? "approved" : "declined"
        }.`
      );
    } catch (decisionError) {
      setActionError(
        decisionError instanceof Error
          ? decisionError.message
          : "Could not update request."
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className={styles.review} aria-label="Team request review">
      <div className={styles.toolbar}>
        <nav className="inbox-status-tabs" aria-label="Request status">
          {DAY_OFF_STATUSES.map(value => (
            <button
              key={value}
              aria-pressed={status === value}
              disabled={busy}
              onClick={() => changeStatus(value)}
            >
              {statusLabel(value)}

              <span className="inbox-count">
                {
                  requests.filter(request =>
                    requestIsInView(request, value)
                  ).length
                }
              </span>
            </button>
          ))}
        </nav>

        <button
          className="inbox-history-toggle"
          disabled={busy}
          onClick={() =>
            changeStatus(
              status === "PENDING" ? "APPROVED" : status,
              !history
            )
          }
        >
          {history ? "Recent decisions" : "History"}
        </button>
      </div>

      <div className={styles.queue}>
        <div>
          <strong>
            {status === "PENDING"
              ? "Needs a decision"
              : `${statusLabel(status)} requests`}{" "}
            · {queue.length}
          </strong>

          <small>
            {status === "PENDING"
              ? "Oldest request first · all months"
              : history
                ? "Decisions older than two weeks"
                : "Decisions from the last two weeks"}
          </small>
        </div>

        <div className={styles.queueButtons}>
          <button
            disabled={busy || selectedIndex <= 0}
            onClick={() => selectRequest(queue[selectedIndex - 1])}
          >
            ← Previous request
          </button>

          <span>
            {selectedIndex >= 0
              ? `${selectedIndex + 1} / ${queue.length}`
              : "—"}
          </span>

          <button
            disabled={
              busy || !queue.length || selectedIndex >= queue.length - 1
            }
            onClick={() => selectRequest(queue[selectedIndex + 1])}
          >
            Next request →
          </button>
        </div>
      </div>

      {inboxError && (
        <p role="alert" className="page-error">
          {inboxError} <button onClick={refresh}>Retry</button>
        </p>
      )}

      {notice && (
        <p role="status" className="inbox-notice">
          {notice}
        </p>
      )}

      <div className={styles.layout}>
        <aside className={styles.calendar}>
          <div className={styles.monthControls}>
            <button
              aria-label="Previous month"
              disabled={busy}
              onClick={() => navigateMonth(-1)}
            >
              ←
            </button>

            <strong>{monthLabel(month)}</strong>

            <button
              aria-label="Next month"
              disabled={busy}
              onClick={() => navigateMonth(1)}
            >
              →
            </button>
          </div>

          <div className={styles.grid}>
            {["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map(name => (
              <span className={styles.weekday} key={name}>
                {name}
              </span>
            ))}

            {calendar.map(date => {
              const requestCount = queue.filter(request =>
                request.dates.includes(date)
              ).length;

              const approvedCount = contextReady
                ? context.diary.filter(
                    entry =>
                      entry.entryDate === date &&
                      entry.type === "DAY_OFF" &&
                      entry.status === "APPROVED"
                  ).length
                : 0;

              const happening =
                contextReady &&
                (context.events.some(event => event.eventDate === date) ||
                  context.parties.some(party => party.eventDate === date));

              const className = [
                styles.date,
                happening ? styles.eventDate : "",
                date === day ? styles.selected : "",
                selected?.dates.includes(date) ? styles.range : "",
                !date.startsWith(month) ? styles.outside : "",
              ].join(" ");

              const ariaLabel =
                `${dateLabel(date)}, ${requestCount} ${status.toLowerCase()} requests` +
                (contextReady
                  ? `, ${approvedCount} already off${
                      happening ? ", events or parties" : ""
                    }`
                  : ", context loading");

              return (
                <button
                  key={date}
                  disabled={busy}
                  aria-pressed={date === day}
                  aria-label={ariaLabel}
                  className={className}
                  onClick={() => selectDate(date)}
                >
                  <span className={date === today ? styles.today : ""}>
                    {Number(date.slice(-2))}
                  </span>

                  <span className={styles.markers}>
                    {requestCount > 0 && (
                      <span className={`request-${status.toLowerCase()}`}>
                        {requestCount}
                      </span>
                    )}

                    {approvedCount > 0 && status !== "APPROVED" && (
                      <span className="request-approved">{approvedCount}</span>
                    )}
                  </span>
                </button>
              );
            })}
          </div>

          <p className={styles.legend}>
            <span>Yellow · pending</span>
            <span>Green · approved</span>
            <span>Purple · events</span>
            {status === "DECLINED" && <span>Red · declined</span>}
          </p>

          <p className={styles.hint}>
            Select a date to check events and staffing. Highlighted dates belong
            to the open request.
          </p>

          <a href="/diary">Open Staff Diary →</a>
        </aside>

        <div className={styles.panel}>
          <header className={styles.dayHeading}>
            <h2>{dateLabel(day)}</h2>

            <span>
              {new Date(`${day}T12:00:00`).toLocaleDateString("en-GB", {
                weekday: "long",
              })}
            </span>
          </header>

          {requestsForDay.length > 1 && (
            <label className={styles.requestPicker}>
              Requests on this date

              <select
                disabled={busy}
                value={selectedId ?? ""}
                onChange={event => setSelectedId(Number(event.target.value))}
              >
                {requestsForDay.map(request => (
                  <option key={request.id} value={request.id}>
                    {request.requester.name} · {request.dates.length} days
                  </option>
                ))}
              </select>
            </label>
          )}

          {selected ? (
            <section className={styles.request}>
              <div className={styles.requestHeading}>
                <h3>{selected.requester.name}</h3>

                <span
                  className={`request-status request-${selected.status.toLowerCase()}`}
                >
                  {statusLabel(selected.status)}
                </span>
              </div>

              <div className={styles.requestDates}>
                {[...selected.dates].sort().map(date => (
                  <button
                    key={date}
                    disabled={busy}
                    aria-pressed={date === day}
                    onClick={() => selectDate(date)}
                  >
                    {dateLabel(date)}
                  </button>
                ))}
              </div>

              {selected.note && <p>{selected.note}</p>}

              {selected.actedBy && (
                <p className={styles.decision}>
                  {statusLabel(selected.status)} by {selected.actedBy.name}
                </p>
              )}
            </section>
          ) : (
            <p className={styles.hint}>
              {queue.length
                ? `No ${status.toLowerCase()} request selected. Choose a marked date or use Next request.`
                : `No ${status.toLowerCase()} requests in this view.`}
            </p>
          )}

          {contextError ? (
            <p role="alert" className="page-error">
              {contextError}{" "}
              <button onClick={() => setVersion(current => current + 1)}>
                Retry
              </button>
            </p>
          ) : !contextReady ? (
            <p>Loading day details...</p>
          ) : (
            <>
              <section className={styles.section}>
                <h3>What’s happening</h3>

                {!events.length && !parties.length && (
                  <p className={styles.hint}>
                    No events or large parties noted.
                  </p>
                )}

                <div className={styles.chips}>
                  {events.map(event => (
                    <span
                      className={styles.event}
                      key={`event-${event.id}`}
                    >
                      {event.eventType === "FOOTBALL" ? "⚽ " : ""}
                      {event.title}
                      {event.eventTime
                        ? ` · ${event.eventTime.slice(0, 5)}`
                        : ""}
                    </span>
                  ))}

                  {parties.map(party => (
                    <span
                      className={styles.event}
                      key={`party-${party.id}`}
                    >
                      {party.name} · {party.partySize} guests
                      {party.startTime
                        ? ` · ${party.startTime.slice(0, 5)}`
                        : ""}
                    </span>
                  ))}
                </div>
              </section>

              <section className={styles.section}>
                <h3>Staffing</h3>

                {diary
                  .filter(entry => entry.type === "NO_ONE_OFF")
                  .map(entry => (
                    <p className={styles.warning} key={entry.id}>
                      No one off{entry.note ? ` · ${entry.note}` : ""}
                    </p>
                  ))}

                <div className={styles.chips}>
                  {daysOff.map((entry, index) => (
                    <span
                      key={entry.id}
                      className={
                        entry.status === "APPROVED"
                          ? styles.off
                          : styles.pending
                      }
                    >
                      #{index + 1} {entry.staffMember?.name} ·{" "}
                      {entry.status === "APPROVED" ? "Off" : "Requested"}
                    </span>
                  ))}
                </div>

                {!daysOff.length && (
                  <p className={styles.hint}>No days off noted.</p>
                )}

                <h4>Available</h4>

                <div className={styles.chips}>
                  {available.map(entry => (
                    <span className={styles.available} key={entry.id}>
                      {entry.staffMember?.name} ·{" "}
                      {entry.availableFrom?.slice(0, 5) || "?"}–
                      {entry.availableUntilFinish
                        ? "Finish"
                        : entry.availableTo?.slice(0, 5) || "?"}
                    </span>
                  ))}
                </div>

                {!available.length && (
                  <p className={styles.hint}>No extra availability offered.</p>
                )}

                {diary
                  .filter(
                    entry =>
                      entry.type === "UNAVAILABLE" || entry.type === "NOTE"
                  )
                  .map(entry => (
                    <p className={styles.hint} key={entry.id}>
                      {entry.staffMember?.name
                        ? `${entry.staffMember.name} · `
                        : ""}
                      {entry.type === "UNAVAILABLE" ? "Unavailable" : "Note"}
                      {entry.note ? ` · ${entry.note}` : ""}
                    </p>
                  ))}

                <small className={styles.hint}>
                  Queue numbers follow the Staff Diary order for this date.
                </small>
              </section>
            </>
          )}

          {actionError && (
            <p role="alert" className="page-error">
              {actionError}
            </p>
          )}

          {selected?.status === "PENDING" && (
            <footer className={styles.actions}>
              <span>
                Decision applies to all {selected.dates.length} requested{" "}
                {selected.dates.length === 1 ? "day" : "days"}.
              </span>

              <div>
                <button
                  className="primary-button"
                  disabled={busy || !contextReady || !!contextError}
                  onClick={() => decide("approve")}
                >
                  {busy ? "Saving…" : "Approve"}
                </button>

                <button
                  className="danger-button"
                  disabled={busy}
                  onClick={() => decide("decline")}
                >
                  Decline
                </button>
              </div>
            </footer>
          )}
        </div>
      </div>
    </section>
  );
}

/* Personal shift-cover Inbox. */
function ShiftSwapInbox({ user }: { user: CurrentUser }) {
  const { refresh: refreshInbox } = useInbox();

  const [incoming, setIncoming] = useState<ShiftSwap[]>([]);
  const [outgoing, setOutgoing] = useState<ShiftSwap[]>([]);
  const [direction, setDirection] = useState<"received" | "sent">("received");
  const [history, setHistory] = useState(false);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [retry, setRetry] = useState(0);

  const today = localDate(new Date());

  /* Loads incoming and outgoing cover requests together. */
  useEffect(() => {
    let active = true;

    async function loadSwaps() {
      setLoading(true);

      try {
        const [received, sent] = await Promise.all([
          api<ShiftSwap[]>(`/api/shift-swaps/incoming/${user.id}`),
          api<ShiftSwap[]>(`/api/shift-swaps/outgoing/${user.id}`),
        ]);

        if (!active) return;

        setIncoming(received);
        setOutgoing(sent);
        setError("");
      } catch (loadError) {
        if (active) setError(errorMessage(loadError));
      } finally {
        if (active) setLoading(false);
      }
    }

    loadSwaps();

    return () => {
      active = false;
    };
  }, [user.id, retry]);

  const requests = (direction === "received" ? incoming : outgoing)
    .filter(request =>
      direction === "received"
        ? request.targetStaffMember.id === user.id
        : request.requester.id === user.id
    )
    .filter(request =>
      history ? request.status !== "PENDING" : request.status === "PENDING"
    )
    .sort(
      (first, second) =>
        first.requesterShift.shiftDate.localeCompare(
          second.requesterShift.shiftDate
        ) || first.id - second.id
    );

  const months = [
    ...new Set(
      requests.map(request => request.requesterShift.shiftDate.slice(0, 7))
    ),
  ];

  const selected =
    requests.find(request => request.id === selectedId) ?? null;

  const receivedPending = incoming.filter(
    request => request.status === "PENDING"
  ).length;

  const sentPending = outgoing.filter(
    request =>
      request.status === "PENDING" ||
      request.status === "AWAITING_MANAGER_APPROVAL"
  ).length;

  async function actOnSwap(
    request: ShiftSwap,
    action: "approve" | "reject" | "cancel"
  ) {
    if (busy) return;
    if (action !== "cancel" && request.status !== "PENDING") return;

    setBusy(true);
    setError("");

    try {
      const updated = await api<ShiftSwap>(
        `/api/shift-swaps/${request.id}/${action}?currentUserId=${user.id}`,
        "PUT"
      );

      const replaceRequest = (items: ShiftSwap[]) =>
        items.map(item => (item.id === updated.id ? updated : item));

      setIncoming(replaceRequest);
      setOutgoing(replaceRequest);
      setSelectedId(null);

      if (updated.status === "AWAITING_MANAGER_APPROVAL") {
        setHistory(true);
      }

      refreshInbox();
    } catch (swapError) {
      setError(
        swapError instanceof Error
          ? swapError.message
          : "Could not update shift request."
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-label="My shift swaps">
      <div className="inbox-filter-bar">
        <nav className="inbox-status-tabs" aria-label="Swap direction">
          <button
            aria-pressed={direction === "received"}
            onClick={() => {
              setDirection("received");
              setSelectedId(null);
            }}
          >
            Received
            <span className="inbox-count">{receivedPending}</span>
          </button>

          <button
            aria-pressed={direction === "sent"}
            onClick={() => {
              setDirection("sent");
              setSelectedId(null);
            }}
          >
            Sent
            <span className="inbox-count">{sentPending}</span>
          </button>
        </nav>

        <button
          className="inbox-history-toggle"
          aria-pressed={history}
          onClick={() => {
            setHistory(current => !current);
            setSelectedId(null);
          }}
        >
          {history ? "Back to pending" : "View history"}
        </button>
      </div>

      {error && (
        <div className="page-error" role="alert">
          {error}{" "}
          <button onClick={() => setRetry(current => current + 1)}>
            Retry
          </button>
        </div>
      )}

      {loading ? (
        <p>Loading shift swaps...</p>
      ) : (
        <div className="inbox-layout">
          <section className="inbox-request-list">
            <h2>
              {direction === "received"
                ? "Received requests"
                : "Sent requests"}
            </h2>

            {!requests.length && (
              <div className="inbox-empty">
                <strong>All clear here</strong>
                <p>No {history ? "previous" : "pending"} shift requests.</p>
              </div>
            )}

            {months.map(value => (
              <details
                key={`${history}-${direction}-${value}`}
                className="inbox-month"
                open
              >
                <summary>
                  {monthLabel(value)}

                  <span className="inbox-count">
                    {
                      requests.filter(request =>
                        request.requesterShift.shiftDate.startsWith(value)
                      ).length
                    }
                  </span>
                </summary>

                <div className="inbox-month-items">
                  {requests
                    .filter(request =>
                      request.requesterShift.shiftDate.startsWith(value)
                    )
                    .map(request => (
                      <button
                        key={request.id}
                        className={`inbox-request-card ${
                          selectedId === request.id ? "selected" : ""
                        }`}
                        aria-pressed={selectedId === request.id}
                        onClick={() => setSelectedId(request.id)}
                      >
                        <div className="inbox-card-top">
                          <strong>
                            {direction === "received"
                              ? request.requester.name
                              : request.targetStaffMember.name}
                          </strong>

                          <span
                            className={`request-status request-${
                              request.status === "REJECTED"
                                ? "declined"
                                : request.status ===
                                    "AWAITING_MANAGER_APPROVAL"
                                  ? "pending"
                                  : request.status.toLowerCase()
                            }`}
                          >
                            {statusLabel(request.status)}
                          </span>
                        </div>

                        <small>
                          {shiftDateLabel(request.requesterShift.shiftDate)}
                        </small>

                        <strong>{shiftTimeLabel(request.requesterShift)}</strong>

                        {request.status === "PENDING" &&
                          request.requesterShift.shiftDate < today && (
                            <small>Past date — unresolved</small>
                          )}
                      </button>
                    ))}
                </div>
              </details>
            ))}
          </section>

          <section className="inbox-detail">
            {!selected ? (
              <div className="inbox-empty">
                <strong>Select a request</strong>
                <p>Choose a shift request to view it.</p>
              </div>
            ) : (
              <>
                <div className="inbox-detail-header">
                  <h2>
                    {direction === "received" ? "Shift cover" : "Your request"}
                  </h2>

                  <span
                    className={`request-status request-${
                      selected.status === "REJECTED"
                        ? "declined"
                        : selected.status === "AWAITING_MANAGER_APPROVAL"
                          ? "pending"
                          : selected.status.toLowerCase()
                    }`}
                  >
                    {statusLabel(selected.status)}
                  </span>
                </div>

                <div className="request-note">
                  <strong>
                    {shiftDateLabel(selected.requesterShift.shiftDate)}
                  </strong>

                  <p>
                    {selected.requester.name} ·{" "}
                    <strong>
                      {shiftTimeLabel(selected.requesterShift)}
                    </strong>
                  </p>
                </div>

                {selected.note && (
                  <div className="request-note">
                    <strong>Note</strong>
                    <p>{selected.note}</p>
                  </div>
                )}

                {selected.status === "AWAITING_MANAGER_APPROVAL" && (
                  <p className="inbox-notice">
                    Both staff members have agreed. This request is waiting for
                    manager approval.
                  </p>
                )}

                {selected.status === "PENDING" && (
                  <div className="request-actions">
                    {direction === "received" ? (
                      <>
                        <button
                          className="primary-button"
                          disabled={
                            busy ||
                            selected.requesterShift.shiftDate < today
                          }
                          onClick={() => actOnSwap(selected, "approve")}
                        >
                          Accept
                        </button>

                        <button
                          className="danger-button"
                          disabled={busy}
                          onClick={() => actOnSwap(selected, "reject")}
                        >
                          Decline
                        </button>
                      </>
                    ) : (
                      <button
                        className="danger-button"
                        disabled={busy}
                        onClick={() => actOnSwap(selected, "cancel")}
                      >
                        Cancel request
                      </button>
                    )}
                  </div>
                )}

                {direction === "sent" &&
                  selected.status === "AWAITING_MANAGER_APPROVAL" && (
                    <div className="request-actions">
                      <button
                        className="danger-button"
                        disabled={busy}
                        onClick={() => actOnSwap(selected, "cancel")}
                      >
                        Cancel request
                      </button>
                    </div>
                  )}
              </>
            )}
          </section>
        </div>
      )}
    </section>
  );
}

/* Manager/Admin final approval for accepted shift covers. */
function ManagerShiftSwapInbox({ user }: { user: CurrentUser }) {
  const { refresh: refreshInbox } = useInbox();

  const [settings, setSettings] = useState<StaffSettings | null>(null);
  const [requests, setRequests] = useState<ShiftSwap[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);

      try {
        const settingsResponse =
          await api<StaffSettings>("/api/staff-settings");

        if (!active) return;

        setSettings(settingsResponse);

        if (!settingsResponse.requireManagerShiftSwapApproval) {
          setRequests([]);
          setSelectedId(null);
          setError("");
          return;
        }

        const pending = await api<ShiftSwap[]>(
          `/api/shift-swaps/manager/pending?currentUserId=${user.id}`
        );

        if (!active) return;

        setRequests(pending);

        setSelectedId(current => {
          if (current && pending.some(request => request.id === current)) {
            return current;
          }

          return pending[0]?.id ?? null;
        });

        setError("");
      } catch (loadError) {
        if (active) setError(errorMessage(loadError));
      } finally {
        if (active) setLoading(false);
      }
    }

    load();

    return () => {
      active = false;
    };
  }, [user.id, revision]);

  const selected =
    requests.find(request => request.id === selectedId) ?? null;

  async function decide(
    request: ShiftSwap,
    action: "manager-approve" | "manager-reject"
  ) {
    if (busy) return;

    setBusy(true);
    setError("");
    setNotice("");

    try {
      const updated = await api<ShiftSwap>(
        `/api/shift-swaps/${request.id}/${action}?currentUserId=${user.id}`,
        "PUT"
      );

      setRequests(current =>
        current.filter(item => item.id !== updated.id)
      );
      setSelectedId(null);

      setNotice(
        action === "manager-approve"
          ? "Shift cover approved and rota updated."
          : "Shift cover declined."
      );

      refreshInbox();
      setRevision(current => current + 1);
    } catch (decisionError) {
      setError(errorMessage(decisionError));
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <p>Loading shift swap settings...</p>;

  if (error) {
    return (
      <div className="page-error" role="alert">
        {error}{" "}
        <button onClick={() => setRevision(current => current + 1)}>
          Retry
        </button>
      </div>
    );
  }

  /*
   * Managers can still open this tab when approval is disabled.
   */
  if (settings && !settings.requireManagerShiftSwapApproval) {
    return (
      <section className="manager-section">
        <div className="manager-section-header">
          <h2>Shift swap approval is off</h2>

          <p>
            Shift-cover requests complete as soon as the receiving staff member
            accepts them.
          </p>
        </div>

        <p>
          To require a Manager or Admin to approve accepted shift covers, turn
          the setting on under{" "}
          <Link href="/manager">Manager → Staff Settings</Link>.
        </p>
      </section>
    );
  }

  return (
    <section aria-label="Manager shift swap approvals">
      {notice && <p className="inbox-notice">{notice}</p>}

      {!requests.length ? (
        <div className="inbox-empty">
          <strong>No shift swaps need approval</strong>
          <p>Accepted staff cover requests will appear here.</p>
        </div>
      ) : (
        <div className="inbox-layout">
          <section className="inbox-request-list">
            <h2>Awaiting approval</h2>

            {requests.map(request => (
              <button
                key={request.id}
                className={`inbox-request-card ${
                  selectedId === request.id ? "selected" : ""
                }`}
                aria-pressed={selectedId === request.id}
                onClick={() => setSelectedId(request.id)}
              >
                <div className="inbox-card-top">
                  <strong>
                    {request.requester.name} → {request.targetStaffMember.name}
                  </strong>

                  <span className="request-status request-pending">
                    Manager approval
                  </span>
                </div>

                <small>
                  {shiftDateLabel(request.requesterShift.shiftDate)}
                </small>

                <strong>{shiftTimeLabel(request.requesterShift)}</strong>
              </button>
            ))}
          </section>

          <section className="inbox-detail">
            {!selected ? (
              <div className="inbox-empty">
                <strong>Select a request</strong>
              </div>
            ) : (
              <>
                <div className="inbox-detail-header">
                  <h2>Shift cover approval</h2>

                  <span className="request-status request-pending">
                    Awaiting approval
                  </span>
                </div>

                <div className="request-note">
                  <strong>
                    {shiftDateLabel(selected.requesterShift.shiftDate)}
                  </strong>

                  <p>
                    {selected.requester.name} →{" "}
                    {selected.targetStaffMember.name} ·{" "}
                    <strong>
                      {shiftTimeLabel(selected.requesterShift)}
                    </strong>
                  </p>
                </div>

                {selected.note && (
                  <div className="request-note">
                    <strong>Note</strong>
                    <p>{selected.note}</p>
                  </div>
                )}

                <p className="muted-text">
                  The receiving staff member has already accepted this shift.
                </p>

                <div className="request-actions">
                  <button
                    className="primary-button"
                    disabled={busy}
                    onClick={() => decide(selected, "manager-approve")}
                  >
                    Approve
                  </button>

                  <button
                    className="danger-button"
                    disabled={busy}
                    onClick={() => decide(selected, "manager-reject")}
                  >
                    Decline
                  </button>
                </div>
              </>
            )}
          </section>
        </div>
      )}
    </section>
  );
}
