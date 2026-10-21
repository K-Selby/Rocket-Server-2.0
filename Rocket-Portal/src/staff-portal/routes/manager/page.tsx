"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import RocketEmailCard from "./RocketEmailCard";

import {
  CurrentUser,
  StaffRole,
  useCurrentUser,
} from "@/app/context/CurrentUserContext";

const API_URL = "";

type StaffSettings = {
  id: number;
  requireManagerShiftSwapApproval: boolean;
  notificationsEnabled: boolean;
};

type ManagedStaff = CurrentUser & {
  active: boolean;
  activeOnRota: boolean;
};

type NotificationTestType =
  | "ROTA_PUBLISHED"
  | "SHIFT_REQUEST"
  | "SHIFT_MANAGER_APPROVAL"
  | "SHIFT_APPROVED"
  | "SHIFT_DECLINED"
  | "DAY_OFF_SUMMARY"
  | "DAY_OFF_APPROVED"
  | "DAY_OFF_DECLINED";

const notificationTests: Array<{
  value: NotificationTestType;
  label: string;
  title: string;
  message: string;
  action: string;
}> = [
  { value: "ROTA_PUBLISHED", label: "Rota published", title: "Your rota has been published", message: "The rota for the selected week has been published. Your shifts are shown below.", action: "View full rota or request cover" },
  { value: "SHIFT_REQUEST", label: "Shift cover request", title: "New shift cover request", message: "Alex has asked you to cover the following shift.", action: "Review shift cover request" },
  { value: "SHIFT_MANAGER_APPROVAL", label: "Shift cover awaiting manager approval", title: "Shift cover needs approval", message: "A shift cover has been accepted and is waiting for manager approval.", action: "Review and approve" },
  { value: "SHIFT_APPROVED", label: "Shift cover approved", title: "Shift cover approved", message: "The shift cover has been approved by a manager. The rota has been updated.", action: "View updated rota" },
  { value: "SHIFT_DECLINED", label: "Shift cover declined", title: "Shift cover declined", message: "The shift cover request was declined. The original shift remains on the rota.", action: "View rota" },
  { value: "DAY_OFF_SUMMARY", label: "Daily day-off request summary", title: "3 day-off requests waiting", message: "There are 3 day-off requests waiting for manager review.", action: "Review day-off requests" },
  { value: "DAY_OFF_APPROVED", label: "Day-off request approved", title: "Day-off request approved", message: "Your day-off request has been approved.", action: "View request" },
  { value: "DAY_OFF_DECLINED", label: "Day-off request declined", title: "Day-off request declined", message: "Your day-off request has been declined. Open the request to view its details.", action: "View request" },
];

function currentWeekStart() {
  const today = new Date();
  today.setDate(today.getDate() - today.getDay());
  return [
    today.getFullYear(),
    String(today.getMonth() + 1).padStart(2, "0"),
    String(today.getDate()).padStart(2, "0"),
  ].join("-");
}

export default function ManagerPage() {
  const router = useRouter();
  const { currentUser, loadingUser, userError } = useCurrentUser();

  const [tab, setTab] = useState<"staff" | "notifications" | "settings">("staff");
  const [staff, setStaff] = useState<ManagedStaff[]>([]);

  const [showAddStaff, setShowAddStaff] = useState(false);
  const [name, setName] = useState("");
  const [role, setRole] = useState<StaffRole>("STAFF");
  const [editingId, setEditingId] = useState<number | null>(null);

  const [settings, setSettings] = useState<StaffSettings | null>(null);

  const [saving, setSaving] = useState(false);
  const [savingSettings, setSavingSettings] = useState(false);
  const [resettingPasswordId, setResettingPasswordId] = useState<number | null>(null);
  const [savingNotificationId, setSavingNotificationId] = useState<number | null>(null);
  const [testRecipientId, setTestRecipientId] = useState("");
  const [testNotificationType, setTestNotificationType] = useState<NotificationTestType>("ROTA_PUBLISHED");
  const [testWeekStart, setTestWeekStart] = useState(currentWeekStart);
  const [sendingTest, setSendingTest] = useState(false);

  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const canManage =
    currentUser?.role === "MANAGER" ||
    currentUser?.role === "ADMIN";

  const sortedStaff = useMemo(() => {
    const roleOrder: Record<StaffRole, number> = {
      STAFF: 0,
      MANAGER: 1,
      ADMIN: 2,
    };

    return [...staff].sort((first, second) => {
      const roleDifference =
        roleOrder[first.role] - roleOrder[second.role];

      if (roleDifference !== 0) return roleDifference;

      return first.name.localeCompare(second.name, "en-GB", {
        sensitivity: "base",
      });
    });
  }, [staff]);

  useEffect(() => {
    async function loadManagerData() {
      try {
        const [staffResponse, settingsResponse] = await Promise.all([
          fetch(`${API_URL}/api/staff`, { credentials: "include" }),
          fetch(`${API_URL}/api/staff-settings`, { credentials: "include" }),
        ]);

        if (!staffResponse.ok) {
          throw new Error("Could not load staff");
        }

        if (!settingsResponse.ok) {
          throw new Error("Could not load staff settings");
        }

        setStaff(await staffResponse.json());
        setSettings(await settingsResponse.json());
        setError("");
      } catch (err) {
        setError(
          err instanceof Error
            ? err.message
            : "Could not load Manager settings."
        );
      }
    }

    if (canManage) loadManagerData();
  }, [canManage]);

  useEffect(() => {
    if (!loadingUser && currentUser && !canManage) {
      router.replace("/staff/rota");
    }
  }, [loadingUser, currentUser, canManage, router]);

  async function reloadStaff() {
    const response = await fetch(`${API_URL}/api/staff`, { credentials: "include" });

    if (!response.ok) {
      throw new Error("Could not load staff");
    }

    setStaff(await response.json());
  }

  function closeAddStaff() {
    setShowAddStaff(false);
    setName("");
    setRole("STAFF");
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!currentUser || !name.trim()) return;

    setSaving(true);
    setError("");
    setNotice("");

    try {
      const response = await fetch(
        `${API_URL}/api/staff?currentUserId=${currentUser.id}`,
        {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            name: name.trim(),
            active: true,
            activeOnRota: true,
            role,
          }),
        }
      );

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(
          data?.message ||
          `Could not create staff member (${response.status})`
        );
      }

      await reloadStaff();
      closeAddStaff();

      setNotice("Staff member added.");
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Could not add staff member."
      );
    } finally {
      setSaving(false);
    }
  }

  async function handleRoleChange(
    staffMember: ManagedStaff,
    newRole: StaffRole
  ) {
    if (!currentUser || staffMember.role === "ADMIN") return;

    setError("");
    setNotice("");

    try {
      const response = await fetch(
        `${API_URL}/api/staff/${staffMember.id}?currentUserId=${currentUser.id}`,
        {
          method: "PUT",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            name: staffMember.name,
            active: staffMember.active,
            activeOnRota: staffMember.activeOnRota,
            role: newRole,
          }),
        }
      );

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(
          data?.message ||
          `Could not update role (${response.status})`
        );
      }

      await reloadStaff();

      setNotice("Staff role updated.");
    } catch (err) {
      setError(
        err instanceof TypeError
          ? "Could not reach the server."
          : err instanceof Error
            ? err.message
            : "Could not update staff role."
      );
    }
  }

  async function handleRotaActiveChange(
    staffMember: ManagedStaff,
    activeOnRota: boolean
  ) {
    if (!currentUser || staffMember.role === "ADMIN") return;

    setError("");
    setNotice("");

    try {
      const response = await fetch(
        `${API_URL}/api/staff/${staffMember.id}?currentUserId=${currentUser.id}`,
        {
          method: "PUT",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            name: staffMember.name,
            active: staffMember.active,
            activeOnRota,
            role: staffMember.role,
          }),
        }
      );

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(
          data?.message ||
          `Could not update rota status (${response.status})`
        );
      }

      await reloadStaff();

      setNotice(
        activeOnRota
          ? `${staffMember.name} is now active on the rota.`
          : `${staffMember.name} is no longer active on the rota.`
      );
    } catch (err) {
      setError(
        err instanceof TypeError
          ? "Could not reach the server."
          : err instanceof Error
            ? err.message
            : "Could not update rota status."
      );
    }
  }

  async function handleStaffNotifications(staffMember: ManagedStaff, enabled: boolean) {
    if (!currentUser || savingNotificationId !== null) return;
    setSavingNotificationId(staffMember.id);
    setError("");
    setNotice("");
    try {
      const response = await fetch(`${API_URL}/api/staff/${staffMember.id}/notifications`, {
        method: "PUT", credentials: "include", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ notificationsEnabled: enabled }),
      });
      const data = await response.json().catch(() => null);
      if (!response.ok) throw new Error(data?.message || "Could not update notifications.");
      setStaff(current => current.map(member => member.id === data.id ? data : member));
      setNotice(`${staffMember.name}'s notifications are now ${enabled ? "on" : "off"}.`);
    } catch (notificationError) {
      setError(notificationError instanceof Error ? notificationError.message : "Could not update notifications.");
    } finally { setSavingNotificationId(null); }
  }

  async function handleDelete(staffMember: ManagedStaff) {
    if (!currentUser || staffMember.role === "ADMIN") return;

    const confirmed = window.confirm(
      `Are you sure you want to delete ${staffMember.name}?`
    );

    if (!confirmed) return;

    setError("");
    setNotice("");

    try {
      const response = await fetch(
        `${API_URL}/api/staff/${staffMember.id}?currentUserId=${currentUser.id}`,
        {
          method: "DELETE",
          credentials: "include",
        }
      );

      if (!response.ok) {
        throw new Error("Could not delete staff member");
      }

      if (editingId === staffMember.id) {
        setEditingId(null);
      }

      await reloadStaff();

      setNotice("Staff member deleted.");
    } catch {
      setError(
        "Could not delete this staff member. They may have existing rota or diary records."
      );
    }
  }

  async function handleResetPassword(staffMember: ManagedStaff) {
    if (!currentUser || staffMember.role === "ADMIN" || staffMember.id === currentUser.id) return;

    const confirmed = window.confirm(
      `Reset ${staffMember.name}'s password to the temporary password “Password”? They will have to create a new password when they next sign in.`
    );

    if (!confirmed) return;

    setResettingPasswordId(staffMember.id);
    setError("");
    setNotice("");

    try {
      const response = await fetch(`${API_URL}/api/staff/${staffMember.id}/reset-password`, {
        method: "POST",
        credentials: "include",
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || "Could not reset this password.");
      }

      setNotice(`${staffMember.name}'s password has been reset. They must create a new password when they next sign in.`);
    } catch (resetError) {
      setError(resetError instanceof Error ? resetError.message : "Could not reset this password.");
    } finally {
      setResettingPasswordId(null);
    }
  }

  async function updateManagerApproval(enabled: boolean, notificationSetting = false) {
    if (!currentUser || !settings || savingSettings) return;

    setSavingSettings(true);
    setError("");
    setNotice("");

    try {
      const response = await fetch(
        `${API_URL}/api/staff-settings?currentUserId=${currentUser.id}`,
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            ...settings,
            requireManagerShiftSwapApproval: notificationSetting ? settings.requireManagerShiftSwapApproval : enabled,
            notificationsEnabled: notificationSetting ? enabled : settings.notificationsEnabled,
          }),
        }
      );

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(
          data?.message ||
          "Could not update Staff Settings."
        );
      }

      setSettings(data);

      setNotice(
        enabled
          ? notificationSetting ? "Notifications are enabled for everyone." : "Manager approval is now required for shift covers."
          : notificationSetting ? "Notifications are disabled for everyone." : "Shift covers can now complete without manager approval."
      );
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Could not save Staff Settings."
      );
    } finally {
      setSavingSettings(false);
    }
  }

  async function sendNotificationTest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!testRecipientId || !testWeekStart || sendingTest) return;

    setSendingTest(true);
    setError("");
    setNotice("");

    try {
      const response = await fetch(`${API_URL}/api/email/test`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          staffMemberId: Number(testRecipientId),
          notificationType: testNotificationType,
          weekStart: testWeekStart,
        }),
      });
      const data = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(data?.message || "Could not send the test rota notification.");
      }

      const recipient = staff.find(member => member.id === Number(testRecipientId));
      const testName = notificationTests.find(test => test.value === testNotificationType)?.label;
      setNotice(`${testName || "Test notification"} sent to ${recipient?.email || "the selected email"}.`);
    } catch (sendError) {
      setError(sendError instanceof Error ? sendError.message : "Could not send the test rota notification.");
    } finally {
      setSendingTest(false);
    }
  }

  if (!loadingUser && !currentUser) {
    return (
      <main className="page-content">
        <h1>Manager</h1>

        <p role="alert">
          {userError || "No current user is available."}
        </p>

        <button
          type="button"
          onClick={() => window.location.reload()}
        >
          Retry
        </button>
      </main>
    );
  }

  if (loadingUser || !currentUser) {
    return (
      <main className="page-content">
        <p>Loading...</p>
      </main>
    );
  }

  if (!canManage) return null;

  return (
    <main className="page-content">
      <div className="page-header">
        <div>
          <h1>Manager</h1>
          <p>
            Manage staff accounts and Staff Portal settings.
          </p>
        </div>
      </div>
      {currentUser.role === "ADMIN" && <RocketEmailCard />}
      <nav
        className={`inbox-kinds manager-tabs ${currentUser.role === "ADMIN" ? "has-notification-tab" : ""}`}
        aria-label="Manager section"
      >
        <button
          aria-pressed={tab === "staff"}
          onClick={() => setTab("staff")}
        >
          Staff
        </button>

        {currentUser.role === "ADMIN" && (
          <button
            aria-pressed={tab === "notifications"}
            onClick={() => setTab("notifications")}
          >
            Notifications
          </button>
        )}

        <button
          aria-pressed={tab === "settings"}
          onClick={() => setTab("settings")}
        >
          Staff Settings
        </button>
      </nav>

      {error && (
        <div className="form-error">
          {error}
        </div>
      )}

      {notice && (
        <div className="page-success">
          {notice}
        </div>
      )}

      {tab === "staff" ? (
        <section className="manager-section">
          <div className="manager-section-header staff-section-header">
            <div>
              <h2>Staff</h2>
              <p>
                Manage staff and managers who can appear on the rota.
              </p>
            </div>

            <button
              type="button"
              className="primary-button"
              onClick={() => {
                setShowAddStaff(current => !current);
                setEditingId(null);
                setError("");
                setNotice("");
              }}
            >
              {showAddStaff ? "Close" : "Add staff"}
            </button>
          </div>

          {showAddStaff && (
            <form
              className="staff-create-form"
              onSubmit={handleCreate}
            >
              <div className="form-field">
                <label htmlFor="staff-name">
                  Name
                </label>

                <input
                  id="staff-name"
                  type="text"
                  value={name}
                  onChange={event =>
                    setName(event.target.value)
                  }
                  placeholder="Staff name"
                  autoFocus
                  required
                />
              </div>

              <div className="form-field">
                <label htmlFor="staff-role">
                  Role
                </label>

                <select
                  id="staff-role"
                  value={role}
                  onChange={event =>
                    setRole(
                      event.target.value as StaffRole
                    )
                  }
                >
                  <option value="STAFF">
                    Staff
                  </option>

                  <option value="MANAGER">
                    Manager
                  </option>
                </select>
              </div>

              <div className="event-form-actions">
                <button
                  className="primary-button"
                  type="submit"
                  disabled={saving}
                >
                  {saving ? "Adding..." : "Add staff"}
                </button>

                <button
                  type="button"
                  className="secondary-button"
                  disabled={saving}
                  onClick={closeAddStaff}
                >
                  Close
                </button>
              </div>
            </form>
          )}

          <div className="staff-management-list">
            {sortedStaff.map(staffMember => {
              const isAdmin =
                staffMember.role === "ADMIN";

              const canResetPassword =
                !isAdmin && staffMember.id !== currentUser.id;

              const isEditing =
                editingId === staffMember.id;

              const roleClass =
                staffMember.role === "STAFF"
                  ? "staff-role-staff"
                  : staffMember.role === "MANAGER"
                    ? "staff-role-manager"
                    : "staff-role-admin";

              return (
                <div
                  className={`staff-management-row ${isAdmin ? "protected-admin" : ""
                    }`}
                  key={staffMember.id}
                >
                  <div className="staff-management-summary">
                    <div className="staff-management-name">
                      <div><strong>{staffMember.name}</strong><small className="staff-email-status">{staffMember.emailVerified && staffMember.email ? staffMember.email : "Email not bound or verified"}</small></div>

                      {isAdmin && (
                        <span className="protected-label">
                          Protected
                        </span>
                      )}
                    </div>

                    <span
                      className={`staff-role-label ${roleClass}`}
                    >
                      {staffMember.role === "ADMIN"
                        ? "Admin"
                        : staffMember.role === "MANAGER"
                          ? "Manager"
                          : "Staff"}
                    </span>

                    <label className="staff-notification-toggle" title={`${staffMember.name} email notifications`}>
                      <span>{staffMember.notificationsEnabled ? "Notifications on" : "Notifications off"}</span>
                      <span className="setting-switch">
                        <input type="checkbox" checked={staffMember.notificationsEnabled}
                          disabled={savingNotificationId !== null || !staffMember.emailVerified}
                          onChange={event => handleStaffNotifications(staffMember, event.target.checked)} />
                        <span className="setting-switch-track" aria-hidden="true" />
                      </span>
                    </label>

                    {!isAdmin && (
                      <button
                        type="button"
                        className="secondary-button"
                        onClick={() =>
                          setEditingId(
                            isEditing
                              ? null
                              : staffMember.id
                          )
                        }
                      >
                        {isEditing ? "Close" : "Edit"}
                      </button>
                    )}
                  </div>

                  {isEditing && !isAdmin && (
                    <div className="staff-edit-panel">
                      <div className="form-field">
                        <label
                          htmlFor={`role-${staffMember.id}`}
                        >
                          Role
                        </label>

                        <select
                          id={`role-${staffMember.id}`}
                          value={staffMember.role}
                          onChange={event =>
                            handleRoleChange(
                              staffMember,
                              event.target.value as StaffRole
                            )
                          }
                        >
                          <option value="STAFF">
                            Staff
                          </option>

                          <option value="MANAGER">
                            Manager
                          </option>
                        </select>
                      </div>

                      <label className="checkbox-label">
                        <input
                          type="checkbox"
                          checked={staffMember.activeOnRota}
                          onChange={event =>
                            handleRotaActiveChange(
                              staffMember,
                              event.target.checked
                            )
                          }
                        />

                        <span>
                          <strong>
                            Active on rota
                          </strong>
                          <br />
                          Show this person on the rota and allow them to receive shift cover requests.
                        </span>
                      </label>

                      <div className="staff-edit-danger">
                        {canResetPassword && (
                          <div className="staff-edit-actions">
                            <button
                              type="button"
                              className="secondary-button"
                              disabled={resettingPasswordId === staffMember.id}
                              onClick={() => handleResetPassword(staffMember)}
                            >
                              {resettingPasswordId === staffMember.id
                                ? "Resetting..."
                                : "Reset password"}
                            </button>
                          </div>
                        )}
                        <button
                          type="button"
                          className="danger-button"
                          onClick={() =>
                            handleDelete(staffMember)
                          }
                        >
                          Delete Staff Member
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </section>
      ) : tab === "notifications" ? (
        <section className="manager-section">
          <div className="manager-section-header">
            <div>
              <h2>Notifications</h2>
              <p>Preview and send test staff email notifications.</p>
            </div>
          </div>

          <div className="notification-test-layout">
            <form className="notification-test-form" onSubmit={sendNotificationTest}>
              <div>
                <span className="notification-test-label">Email preview</span>
                <h3>Send a test notification</h3>
                <p>
                  Choose a notification and a verified staff email. Test messages are clearly
                  marked as tests and do not change any requests or rota information.
                </p>
              </div>

              <div className="form-field">
                <label htmlFor="notification-type">Notification</label>
                <select
                  id="notification-type"
                  value={testNotificationType}
                  onChange={event => setTestNotificationType(event.target.value as NotificationTestType)}
                >
                  {notificationTests.map(test => (
                    <option key={test.value} value={test.value}>{test.label}</option>
                  ))}
                </select>
              </div>

              <div className="form-field">
                <label htmlFor="notification-recipient">Send test to</label>
                <select
                  id="notification-recipient"
                  value={testRecipientId}
                  onChange={event => setTestRecipientId(event.target.value)}
                  required
                >
                  <option value="">Choose a verified staff email</option>
                  {sortedStaff
                    .filter(member => member.emailVerified && member.email)
                    .map(member => (
                      <option key={member.id} value={member.id}>
                        {member.name} — {member.email}
                      </option>
                    ))}
                </select>
              </div>

              <div className="form-field">
                <label htmlFor="notification-week">Example week beginning</label>
                <input
                  id="notification-week"
                  type="date"
                  value={testWeekStart}
                  onChange={event => setTestWeekStart(event.target.value)}
                  required
                />
                <small>The rota test uses saved shifts. Other tests use example dates from this week.</small>
              </div>

              <button className="primary-button" type="submit" disabled={sendingTest}>
                {sendingTest ? "Sending test..." : "Send test notification"}
              </button>
            </form>

            <article className="notification-email-preview" aria-label="Rota email preview">
              <div className="notification-preview-brand">The Rocket Pub</div>
              <h3>{notificationTests.find(test => test.value === testNotificationType)?.title}</h3>
              <p>Hi {staff.find(member => member.id === Number(testRecipientId))?.name || "XXX"},</p>
              <p>
                {notificationTests.find(test => test.value === testNotificationType)?.message}
              </p>
              <div className="notification-preview-shifts">
                {testNotificationType === "ROTA_PUBLISHED" ? (
                  <>
                    <div><strong>Monday XX</strong><span>5 - F</span></div>
                    <div><strong>Tuesday XX</strong><span>3 - 9</span></div>
                    <div><strong>Thursday XX</strong><span>6 - F</span></div>
                    <div><strong>Saturday XX</strong><span>4 - 8</span></div>
                  </>
                ) : testNotificationType === "DAY_OFF_SUMMARY" ? (
                  <div><strong>Outstanding requests</strong><span>3</span></div>
                ) : testNotificationType === "DAY_OFF_APPROVED" ? (
                  <div className="notification-preview-days">
                    <strong>Day requested</strong>
                    <ul><li>Friday XX</li></ul>
                  </div>
                ) : testNotificationType === "DAY_OFF_DECLINED" ? (
                  <div className="notification-preview-days">
                    <strong>Days requested</strong>
                    <ul>
                      <li>Friday XX</li>
                      <li>Sunday XX – Monday XX</li>
                    </ul>
                  </div>
                ) : (
                  <>
                    {testNotificationType === "SHIFT_MANAGER_APPROVAL" && <div><strong>From / covering</strong><span>Alex / Jamie</span></div>}
                    <div><strong>Date</strong><span>Monday XX</span></div>
                    <div><strong>Shift</strong><span>5 - F</span></div>
                  </>
                )}
              </div>
              <span className="notification-preview-link">
                {notificationTests.find(test => test.value === testNotificationType)?.action}
              </span>
            </article>
          </div>
        </section>
      ) : (
        <section className="manager-section">
          <div className="manager-section-header">
            <div>
              <h2>Staff Settings</h2>
              <p>
                Control how staff features behave.
              </p>
            </div>
          </div>

          {!settings ? (
            <p>Loading settings...</p>
          ) : (
            <div className="staff-settings-list">
              <label className="staff-setting-row">
                <span className="staff-setting-copy">
                  <strong>Email notifications</strong>
                  <span>Allow notification emails to be sent to staff with verified email addresses.</span>
                </span>
                <span className="setting-switch">
                  <input
                    type="checkbox"
                    checked={settings.notificationsEnabled}
                    disabled={savingSettings}
                    onChange={event => updateManagerApproval(event.target.checked, true)}
                  />
                  <span className="setting-switch-track" aria-hidden="true" />
                </span>
              </label>

              <label className="staff-setting-row">
                <span className="staff-setting-copy">
                  <strong>Manager approval for shift covers</strong>
                  <span>Staff accept the cover first. The rota changes only after a manager approves it.</span>
                </span>
                <span className="setting-switch">
                  <input
                    type="checkbox"
                    checked={settings.requireManagerShiftSwapApproval}
                    disabled={savingSettings}
                    onChange={event => updateManagerApproval(event.target.checked)}
                  />
                  <span className="setting-switch-track" aria-hidden="true" />
                </span>
              </label>
            </div>
          )}
        </section>
      )}
    </main>
  );
}
