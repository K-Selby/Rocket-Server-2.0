"use client";

import { useEffect, useMemo, useState } from "react";
import { CurrentUser, useCurrentUser } from "../context/CurrentUserContext";

type DiaryEntry = {
  id: number;
  entryDate: string;
  type: "DAY_OFF" | "UNAVAILABLE" | "AVAILABLE" | "NO_ONE_OFF" | "NOTE";
  status: "REQUESTED" | "APPROVED" | "REJECTED" | "INFO";
  availableFrom: string | null;
  availableTo: string | null;
  availableUntilFinish: boolean;
  note: string | null;
  requestGroupId: string | null;
  staffMember: CurrentUser | null;
};

type PubEvent = {
  id: number;
  eventDate: string;
  eventTime: string | null;
  title: string;
  eventType: "FOOTBALL" | "EVENT";
  note: string | null;
};

type LargeParty = {
  id: number;
  eventDate: string;
  startTime: string | null;
  endTime: string | null;
  name: string;
  partySize: number;
  occasion: string | null;
  note: string | null;
};

type RequestType = "DAY_OFF" | "AVAILABLE";

const API_URL = "http://localhost:8080";

/* Converts a local date to YYYY-MM-DD without UTC changing the day. */
function formatLocalDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

/* Builds complete Sunday-Saturday rows for the calendar. */
function buildCalendarDays(year: number, month: number) {
  const firstDay = new Date(year, month, 1);
  const lastDay = new Date(year, month + 1, 0);

  const start = new Date(firstDay);
  start.setDate(start.getDate() - start.getDay());

  const end = new Date(lastDay);
  end.setDate(end.getDate() + (6 - end.getDay()));

  const days: Date[] = [];
  const current = new Date(start);

  while (current <= end) {
    days.push(new Date(current));
    current.setDate(current.getDate() + 1);
  }

  return days;
}

function formatTime(time: string | null) {
  return time ? time.substring(0, 5) : "";
}

/* Football is stored normally and only decorated in the UI. */
function formatEventTitle(event: PubEvent) {
  return event.eventType === "FOOTBALL" ? `⚽ ${event.title}` : event.title;
}

/* Converts backend 24-hour times into pub-style times. */
function formatPubTime(time: string | null) {
  if (!time) return "";

  const [hourText, minuteText] = time.split(":");
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const displayHour = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour;

  return minute === 0
    ? String(displayHour)
    : `${displayHour}:${String(minute).padStart(2, "0")}`;
}

function formatAvailability(entry: DiaryEntry) {
  const from = formatPubTime(entry.availableFrom);
  const until = entry.availableUntilFinish ? "F" : formatPubTime(entry.availableTo);

  return `${from}-${until}`;
}

/* Converts shorthand such as 3-8 or 3-F into Spring times. */
function parseAvailabilityShorthand(value: string) {
  const match = value
    .trim()
    .toUpperCase()
    .match(/^(\d{1,2})(?::([0-5]\d))?\s*-\s*(F|\d{1,2}(?::[0-5]\d)?)$/);

  if (!match) return null;

  function convertPart(part: string) {
    const [hourText, minuteText = "00"] = part.split(":");
    const hour = Number(hourText);
    const minute = Number(minuteText);

    if (hour < 1 || hour > 12) return null;

    const twentyFourHour = hour === 12 ? 12 : hour + 12;

    return `${String(twentyFourHour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
  }

  const startText = match[1] + (match[2] ? `:${match[2]}` : "");
  const availableFrom = convertPart(startText);

  if (!availableFrom) return null;

  if (match[3] === "F") {
    return {
      availableFrom,
      availableTo: null,
      availableUntilFinish: true,
    };
  }

  const availableTo = convertPart(match[3]);
  if (!availableTo) return null;

  return {
    availableFrom,
    availableTo,
    availableUntilFinish: false,
  };
}

/* Reads validation messages returned by Spring. */
async function getApiError(response: Response) {
  try {
    const data = await response.json();
    if (data.message) return data.message;
  } catch {
    /* Use fallback below. */
  }

  return "Something went wrong.";
}

function formatSelectedDate(date: string) {
  return new Date(`${date}T12:00:00`).toLocaleDateString("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
  });
}

export default function DiaryPage() {
  const today = new Date();
  const { currentUser, loadingUser } = useCurrentUser();

  const [year, setYear] = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth());

  const [diaryEntries, setDiaryEntries] = useState<DiaryEntry[]>([]);
  const [events, setEvents] = useState<PubEvent[]>([]);
  const [largeParties, setLargeParties] = useState<LargeParty[]>([]);
  const [staff, setStaff] = useState<CurrentUser[]>([]);

  const [selectedDates, setSelectedDates] = useState<string[]>([
    formatLocalDate(today),
  ]);

  const [multiSelect, setMultiSelect] = useState(false);
  const [showRequestForm, setShowRequestForm] = useState(false);
  const [showEventForm, setShowEventForm] = useState(false);

  const [requestType, setRequestType] = useState<RequestType>("DAY_OFF");
  const [selectedStaffId, setSelectedStaffId] = useState("");
  const [availability, setAvailability] = useState("");
  const [requestNote, setRequestNote] = useState("");
  const [approveImmediately, setApproveImmediately] = useState(false);

  const [eventTitle, setEventTitle] = useState("");
  const [eventTime, setEventTime] = useState("");
  const [eventType, setEventType] = useState<PubEvent["eventType"]>("EVENT");
  const [eventNote, setEventNote] = useState("");
  const [editingEventId, setEditingEventId] = useState<number | null>(null);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const calendarDays = useMemo(() => buildCalendarDays(year, month), [year, month]);

  const isAdmin = currentUser?.role === "ADMIN";
  const canManage = currentUser?.role === "MANAGER" || currentUser?.role === "ADMIN";

  /* Loads the visible month without one failed source hiding the whole Diary. */
  useEffect(() => {
    const visibleDays = buildCalendarDays(year, month);
    const from = formatLocalDate(visibleDays[0]);
    const to = formatLocalDate(visibleDays[visibleDays.length - 1]);

    async function loadMonth() {
      setLoading(true);
      setError("");

      const errors: string[] = [];

      const [diaryResult, eventsResult, partiesResult, staffResult] = await Promise.allSettled([
        fetch(`${API_URL}/api/diary/range?from=${from}&to=${to}`),
        fetch(`${API_URL}/api/events?from=${from}&to=${to}`),
        fetch(`${API_URL}/api/large-parties?from=${from}&to=${to}`),
        fetch(`${API_URL}/api/staff/rota`, { credentials: "include" }),
      ]);

      if (diaryResult.status === "fulfilled" && diaryResult.value.ok) {
        setDiaryEntries(await diaryResult.value.json());
      } else {
        setDiaryEntries([]);
        errors.push("Could not load Diary entries.");
      }

      if (eventsResult.status === "fulfilled" && eventsResult.value.ok) {
        setEvents(await eventsResult.value.json());
      } else {
        setEvents([]);
        errors.push("Could not load pub events.");
      }

      if (partiesResult.status === "fulfilled" && partiesResult.value.ok) {
        setLargeParties(await partiesResult.value.json());
      } else {
        setLargeParties([]);
        errors.push("Could not load large parties.");
      }

      if (staffResult.status === "fulfilled" && staffResult.value.ok) {
        setStaff(await staffResult.value.json());
      } else {
        setStaff([]);
        errors.push("Could not load staff.");
      }

      if (errors.length > 0) setError(errors.join(" "));
      setLoading(false);
    }

    loadMonth();
  }, [year, month]);

  async function reloadDiary() {
    const visibleDays = buildCalendarDays(year, month);
    const from = formatLocalDate(visibleDays[0]);
    const to = formatLocalDate(visibleDays[visibleDays.length - 1]);

    const response = await fetch(`${API_URL}/api/diary/range?from=${from}&to=${to}`);

    if (!response.ok) {
      setError("Could not refresh Diary entries.");
      return;
    }

    setDiaryEntries(await response.json());
  }

  function closeForms() {
    setShowEventForm(false);
    setShowRequestForm(false);
    setEditingEventId(null);
  }

  function previousMonth() {
    const previous = new Date(year, month - 1, 1);

    setLoading(true);
    setYear(previous.getFullYear());
    setMonth(previous.getMonth());
    setSelectedDates([formatLocalDate(previous)]);
    closeForms();
  }

  function nextMonth() {
    const next = new Date(year, month + 1, 1);

    setLoading(true);
    setYear(next.getFullYear());
    setMonth(next.getMonth());
    setSelectedDates([formatLocalDate(next)]);
    closeForms();
  }

  /* Normal mode selects one day. Multi-select toggles dates. */
  function selectDate(date: string) {
    setError("");
    setMessage("");

    if (!multiSelect) {
      setSelectedDates([date]);
      closeForms();
      return;
    }

    setSelectedDates(current => {
      if (current.includes(date)) {
        if (current.length === 1) return current;
        return current.filter(selected => selected !== date);
      }

      return [...current, date].sort();
    });

    closeForms();
  }

  function openEventForm() {
    setEditingEventId(null);
    setEventTitle("");
    setEventTime("");
    setEventType("EVENT");
    setEventNote("");

    setShowEventForm(true);
    setShowRequestForm(false);
    setError("");
    setMessage("");
  }

  function openRequestForm(type: RequestType) {
    setRequestType(type);
    setShowRequestForm(true);
    setShowEventForm(false);
    setEditingEventId(null);
    setError("");
    setMessage("");

    if (type === "AVAILABLE") setApproveImmediately(false);
  }

  /* Day off and availability now both use the shared Staff Diary API. */
  async function submitRequest(event: React.FormEvent) {
    event.preventDefault();
    if (!currentUser) return;

    if (isAdmin && !selectedStaffId) {
      setError("Select a staff member first.");
      return;
    }

    setSaving(true);
    setError("");
    setMessage("");

    let availabilityData: ReturnType<typeof parseAvailabilityShorthand> = null;

    if (requestType === "AVAILABLE") {
      availabilityData = parseAvailabilityShorthand(availability);

      if (!availabilityData) {
        setError("Enter availability in a format such as 3-8 or 3-F.");
        setSaving(false);
        return;
      }
    }

    try {
      const response = await fetch(
        `${API_URL}/api/diary?currentUserId=${currentUser.id}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            staffMemberId: isAdmin ? Number(selectedStaffId) : currentUser.id,
            entryDates: selectedDates,
            type: requestType,
            availableFrom: availabilityData?.availableFrom ?? null,
            availableTo: availabilityData?.availableTo ?? null,
            availableUntilFinish: availabilityData?.availableUntilFinish ?? false,
            note: requestNote.trim() || null,
            approveImmediately: requestType === "DAY_OFF" && isAdmin && approveImmediately,
          }),
        }
      );

      if (!response.ok) {
        setError(await getApiError(response));
        setSaving(false);
        return;
      }

      await reloadDiary();

      if (requestType === "DAY_OFF") {
        setMessage(
          selectedDates.length === 1
            ? "Day-off request submitted."
            : `${selectedDates.length} days submitted as one day-off request.`
        );
      } else {
        setMessage(
          selectedDates.length === 1
            ? "Availability saved."
            : `Availability saved for ${selectedDates.length} days.`
        );
      }

      setAvailability("");
      setRequestNote("");
      setApproveImmediately(false);
      setShowRequestForm(false);
    } catch {
      setError(
        requestType === "DAY_OFF"
          ? "Could not submit the day-off request."
          : "Could not save availability."
      );
    }

    setSaving(false);
  }

  /* Adds NO ONE OFF to all selected dates in one request. */
  async function addNoOneOff() {
    if (!currentUser || !canManage) return;

    setSaving(true);
    setError("");
    setMessage("");

    try {
      const response = await fetch(
        `${API_URL}/api/diary?currentUserId=${currentUser.id}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            entryDates: selectedDates,
            type: "NO_ONE_OFF",
          }),
        }
      );

      if (!response.ok) {
        setError(await getApiError(response));
        setSaving(false);
        return;
      }

      await reloadDiary();

      setMessage(
        selectedDates.length === 1
          ? "NO ONE OFF added."
          : `NO ONE OFF added to ${selectedDates.length} days.`
      );
    } catch {
      setError("Could not add NO ONE OFF.");
    }

    setSaving(false);
  }

  /* Creates a new event or updates an existing one. */
  async function saveEvent(event: React.FormEvent) {
    event.preventDefault();

    if (!currentUser || !canManage) return;

    if (selectedDates.length !== 1) {
      setError("Select one date when adding or editing an event.");
      return;
    }

    setSaving(true);
    setError("");
    setMessage("");

    try {
      const response = await fetch(
        editingEventId
          ? `${API_URL}/api/events/${editingEventId}?currentUserId=${currentUser.id}`
          : `${API_URL}/api/events?currentUserId=${currentUser.id}`,
        {
          method: editingEventId ? "PUT" : "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            eventDate: selectedDates[0],
            eventTime: eventTime || null,
            title: eventTitle.trim(),
            eventType,
            note: eventNote.trim() || null,
          }),
        }
      );

      if (!response.ok) {
        setError(await getApiError(response));
        setSaving(false);
        return;
      }

      const savedEvent: PubEvent = await response.json();

      setEvents(current => {
        if (editingEventId) {
          return current.map(existing =>
            existing.id === savedEvent.id ? savedEvent : existing
          );
        }

        return [...current, savedEvent];
      });

      const wasEditing = editingEventId !== null;

      setEventTitle("");
      setEventTime("");
      setEventType("EVENT");
      setEventNote("");
      setEditingEventId(null);
      setShowEventForm(false);

      setMessage(wasEditing ? "Event updated." : "Event added.");
    } catch {
      setError("Could not save the event.");
    }

    setSaving(false);
  }

  function startEditEvent(pubEvent: PubEvent) {
    setMultiSelect(false);
    setSelectedDates([pubEvent.eventDate]);

    setEventTitle(pubEvent.title);
    setEventTime(pubEvent.eventTime ? formatTime(pubEvent.eventTime) : "");
    setEventType(pubEvent.eventType);
    setEventNote(pubEvent.note ?? "");

    setEditingEventId(pubEvent.id);
    setShowRequestForm(false);
    setShowEventForm(true);
    setError("");
    setMessage("");
  }

  async function deleteEvent(pubEvent: PubEvent) {
    if (!currentUser || !canManage) return;
    if (!window.confirm(`Delete "${pubEvent.title}"?`)) return;

    const response = await fetch(
      `${API_URL}/api/events/${pubEvent.id}?currentUserId=${currentUser.id}`,
      { method: "DELETE" }
    );

    if (!response.ok) {
      setError(await getApiError(response));
      return;
    }

    setEvents(current => current.filter(existing => existing.id !== pubEvent.id));
    setMessage("Event deleted.");
  }

  function canDeleteDiaryEntry(entry: DiaryEntry) {
    if (!currentUser) return false;
    if (currentUser.role === "ADMIN") return true;

    if (entry.type === "NO_ONE_OFF" && currentUser.role === "MANAGER") return true;

    return entry.staffMember?.id === currentUser.id;
  }

  async function deleteDiaryEntry(entry: DiaryEntry) {
    if (!currentUser || !canDeleteDiaryEntry(entry)) return;

    const groupedDayOff =
      entry.type === "DAY_OFF" &&
      entry.requestGroupId &&
      diaryEntries.filter(item => item.requestGroupId === entry.requestGroupId).length > 1;

    const confirmed = window.confirm(
      entry.type === "NO_ONE_OFF"
        ? "Remove NO ONE OFF?"
        : groupedDayOff
          ? "Delete this whole day-off request?"
          : "Delete this Diary entry?"
    );

    if (!confirmed) return;

    const response = await fetch(
      `${API_URL}/api/diary/${entry.id}?currentUserId=${currentUser.id}`,
      { method: "DELETE" }
    );

    if (!response.ok) {
      setError(await getApiError(response));
      return;
    }

    await reloadDiary();

    setMessage(
      groupedDayOff
        ? "Day-off request deleted."
        : "Diary entry deleted."
    );
  }

  const monthTitle = new Date(year, month, 1).toLocaleDateString("en-GB", {
    month: "long",
    year: "numeric",
  });

  const selectedDiaryEntries = diaryEntries
    .filter(entry => selectedDates.includes(entry.entryDate))
    .sort((first, second) => first.id - second.id);

  const selectedEvents = events.filter(event => selectedDates.includes(event.eventDate));
  const selectedParties = largeParties.filter(party => selectedDates.includes(party.eventDate));

  const hasSelectedItems =
    selectedEvents.length > 0 ||
    selectedParties.length > 0 ||
    selectedDiaryEntries.length > 0;

  return (
    <main>
      <div className="diary-header">
        <div>
          <h1>Staff Diary</h1>
          <p>Day-off requests, availability, events and large parties.</p>
        </div>

        <div className="month-controls">
          <button onClick={previousMonth}>← Previous</button>
          <strong>{monthTitle}</strong>
          <button onClick={nextMonth}>Next →</button>
        </div>
      </div>

      {error && <div className="page-error">{error}</div>}
      {message && <div className="page-success">{message}</div>}

      {loading || loadingUser ? (
        <p>Loading diary...</p>
      ) : (
        <div className="diary-layout">
          <div className="calendar-scroll">
            <div className="calendar">
              {["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map(day => (
                <div className="calendar-heading" key={day}>
                  {day}
                </div>
              ))}

              {calendarDays.map(date => {
                const dateString = formatLocalDate(date);
                const inCurrentMonth = date.getMonth() === month;
                const isSelected = selectedDates.includes(dateString);

                const dayEntries = diaryEntries
                  .filter(entry => entry.entryDate === dateString)
                  .sort((first, second) => first.id - second.id);

                const dayEvents = events.filter(event => event.eventDate === dateString);
                const dayParties = largeParties.filter(party => party.eventDate === dateString);
                const dayOffEntries = dayEntries.filter(entry => entry.type === "DAY_OFF");
                const noOneOffEntries = dayEntries.filter(entry => entry.type === "NO_ONE_OFF");
                const availabilityEntries = dayEntries.filter(entry => entry.type === "AVAILABLE");

                return (
                  <button
                    type="button"
                    className={`calendar-day ${!inCurrentMonth ? "outside-month" : ""} ${
                      isSelected ? "selected-calendar-day" : ""
                    }`}
                    key={dateString}
                    onClick={() => selectDate(dateString)}
                  >
                    <div className="calendar-date">{date.getDate()}</div>

                    <div className="calendar-items">
                      {dayEvents.map(event => (
                        <div className="calendar-item calendar-event" key={`event-${event.id}`}>
                          <strong>{formatEventTitle(event)}</strong>
                          {event.eventTime && <span>{formatTime(event.eventTime)}</span>}
                        </div>
                      ))}

                      {dayParties.map(party => (
                        <div className="calendar-item large-party" key={`party-${party.id}`}>
                          <strong>{party.name}</strong>
                          <span>{party.partySize} people</span>
                        </div>
                      ))}

                      {noOneOffEntries.map(entry => (
                        <div className="calendar-item calendar-no-one-off" key={entry.id}>
                          <strong>NO ONE OFF</strong>
                        </div>
                      ))}

                      {dayOffEntries.map((entry, index) => (
                        <div
                          className={`calendar-item diary-${entry.status.toLowerCase()}`}
                          key={entry.id}
                        >
                          <strong>{index + 1}. {entry.staffMember?.name}</strong>

                          <span>
                            {entry.status === "APPROVED"
                              ? "Off"
                              : entry.status === "REQUESTED"
                                ? "Requested"
                                : entry.status}
                          </span>
                        </div>
                      ))}

                      {availabilityEntries.map(entry => (
                        <div className="calendar-item calendar-availability" key={entry.id}>
                          <strong>{entry.staffMember?.name}</strong>
                          <span>{formatAvailability(entry)}</span>
                        </div>
                      ))}
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          <aside className="diary-side-panel">
            <h2>
              {selectedDates.length === 1
                ? formatSelectedDate(selectedDates[0])
                : "Selected dates"}
            </h2>

            <label className="multi-select-control">
              <input
                type="checkbox"
                checked={multiSelect}
                onChange={event => {
                  const enabled = event.target.checked;
                  setMultiSelect(enabled);

                  if (!enabled && selectedDates.length > 1) {
                    setSelectedDates([selectedDates[0]]);
                  }
                }}
              />

              Select multiple days
            </label>

            {multiSelect && (
              <div className="selected-date-list">
                {selectedDates.map(date => (
                  <span className="selected-date-chip" key={date}>
                    {formatSelectedDate(date)}
                  </span>
                ))}
              </div>
            )}

            {canManage && (
              <section className="diary-action-section">
                <button
                  type="button"
                  className="no-one-off-button"
                  disabled={saving}
                  onClick={addNoOneOff}
                >
                  NO ONE OFF
                </button>

                <button
                  type="button"
                  className="secondary-action-button"
                  onClick={openEventForm}
                >
                  Add event
                </button>
              </section>
            )}

            <section className="diary-action-section">
              <button
                type="button"
                className="secondary-action-button"
                onClick={() => openRequestForm("DAY_OFF")}
              >
                Request Day off
              </button>

              <button
                type="button"
                className="secondary-action-button"
                onClick={() => openRequestForm("AVAILABLE")}
              >
                Request a shift
              </button>
            </section>

            {!showEventForm && !showRequestForm && (
              <section className="day-section">
                <h3>Items on selected dates</h3>

                {!hasSelectedItems && (
                  <p className="muted-text">No items on the selected date.</p>
                )}

                {selectedEvents.map(event => (
                  <div className="managed-item event-managed-item" key={`event-${event.id}`}>
                    <div>
                      <strong>{formatEventTitle(event)}</strong>
                      <span>{formatSelectedDate(event.eventDate)}</span>
                    </div>

                    {canManage && (
                      <div className="managed-item-actions">
                        <button onClick={() => startEditEvent(event)}>Edit</button>

                        <button
                          className="delete-small-button"
                          onClick={() => deleteEvent(event)}
                        >
                          Delete
                        </button>
                      </div>
                    )}
                  </div>
                ))}

                {selectedParties.map(party => (
                  <div className="managed-item" key={`party-${party.id}`}>
                    <div>
                      <strong>{party.name}</strong>
                      <span>{party.partySize} people</span>
                    </div>
                  </div>
                ))}

                {selectedDiaryEntries.map(entry => (
                  <div className="managed-item" key={`diary-${entry.id}`}>
                    <div>
                      <strong>
                        {entry.type === "NO_ONE_OFF"
                          ? "NO ONE OFF"
                          : entry.staffMember?.name}
                      </strong>

                      <span>
                        {formatSelectedDate(entry.entryDate)}
                        {entry.type === "AVAILABLE" && ` · ${formatAvailability(entry)}`}
                      </span>
                    </div>

                    {canDeleteDiaryEntry(entry) && (
                      <button
                        className="delete-small-button"
                        onClick={() => deleteDiaryEntry(entry)}
                      >
                        Delete
                      </button>
                    )}
                  </div>
                ))}
              </section>
            )}

            {showEventForm && canManage && (
              <section className="day-section">
                <h3>{editingEventId ? "Edit Event" : "Add Event"}</h3>

                <form className="event-form" onSubmit={saveEvent}>
                  <label>
                    Event name
                    <input
                      value={eventTitle}
                      onChange={event => setEventTitle(event.target.value)}
                      required
                    />
                  </label>

                  <label>
                    Type
                    <select
                      value={eventType}
                      onChange={event =>
                        setEventType(event.target.value as PubEvent["eventType"])
                      }
                    >
                      <option value="EVENT">Event</option>
                      <option value="FOOTBALL">Football</option>
                    </select>
                  </label>

                  <label>
                    Time
                    <input
                      type="time"
                      value={eventTime}
                      onChange={event => setEventTime(event.target.value)}
                    />
                  </label>

                  <label>
                    Note
                    <textarea
                      rows={4}
                      value={eventNote}
                      onChange={event => setEventNote(event.target.value)}
                    />
                  </label>

                  <div className="event-form-actions">
                    <button className="primary-button" disabled={saving}>
                      {saving
                        ? "Saving..."
                        : editingEventId
                          ? "Save event"
                          : "Add event"}
                    </button>

                    <button
                      type="button"
                      className="secondary-button"
                      onClick={closeForms}
                    >
                      Close
                    </button>
                  </div>
                </form>
              </section>
            )}

            {showRequestForm && (
              <section className="day-section request-section">
                <h3>{requestType === "DAY_OFF" ? "Day off" : "Availability"}</h3>

                <form onSubmit={submitRequest}>
                  {isAdmin && (
                    <label>
                      Staff member
                      <select
                        value={selectedStaffId}
                        onChange={event => setSelectedStaffId(event.target.value)}
                        required
                      >
                        <option value="">Select staff...</option>

                        {staff.map(member => (
                          <option value={member.id} key={member.id}>
                            {member.name}
                          </option>
                        ))}
                      </select>
                    </label>
                  )}

                  {requestType === "AVAILABLE" && (
                    <label>
                      Shift availability
                      <input
                        value={availability}
                        onChange={event => setAvailability(event.target.value)}
                        placeholder="e.g. 3-8"
                        required
                      />
                    </label>
                  )}

                  {requestType === "DAY_OFF" && isAdmin && (
                    <label className="checkbox-label">
                      <input
                        type="checkbox"
                        checked={approveImmediately}
                        onChange={event => setApproveImmediately(event.target.checked)}
                      />

                      Add as approved day off
                    </label>
                  )}

                  <label>
                    Note
                    <textarea
                      rows={4}
                      value={requestNote}
                      onChange={event => setRequestNote(event.target.value)}
                    />
                  </label>

                  <div className="event-form-actions">
                    <button className="primary-button" disabled={saving}>
                      {saving
                        ? "Saving..."
                        : requestType === "DAY_OFF"
                          ? "Submit day off"
                          : "Save availability"}
                    </button>

                    <button
                      type="button"
                      className="secondary-button"
                      onClick={closeForms}
                    >
                      Close
                    </button>
                  </div>
                </form>
              </section>
            )}
          </aside>
        </div>
      )}
    </main>
  );
}
