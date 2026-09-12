"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import RocketEmailCard from "./RocketEmailCard";

import {
  CurrentUser,
  StaffRole,
  useCurrentUser,
} from "../context/CurrentUserContext";

const API_URL = "";

type StaffSettings = {
  id: number;
  requireManagerShiftSwapApproval: boolean;
};

type ManagedStaff = CurrentUser & {
  active: boolean;
  activeOnRota: boolean;
};

export default function ManagerPage() {
  const router = useRouter();
  const { currentUser, loadingUser, userError } = useCurrentUser();

  const [tab, setTab] = useState<"staff" | "settings">("staff");
  const [staff, setStaff] = useState<ManagedStaff[]>([]);

  const [showAddStaff, setShowAddStaff] = useState(false);
  const [name, setName] = useState("");
  const [role, setRole] = useState<StaffRole>("STAFF");
  const [editingId, setEditingId] = useState<number | null>(null);

  const [settings, setSettings] = useState<StaffSettings | null>(null);

  const [saving, setSaving] = useState(false);
  const [savingSettings, setSavingSettings] = useState(false);
  const [resettingPasswordId, setResettingPasswordId] = useState<number | null>(null);

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
      router.replace("/rota");
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

  async function updateManagerApproval(enabled: boolean) {
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
            requireManagerShiftSwapApproval: enabled,
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
          ? "Manager approval is now required for shift covers."
          : "Shift covers can now complete without manager approval."
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
        className="inbox-kinds"
        aria-label="Manager section"
      >
        <button
          aria-pressed={tab === "staff"}
          onClick={() => setTab("staff")}
        >
          Staff
        </button>

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
                      <strong>
                        {staffMember.name}
                      </strong>

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
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={
                  settings.requireManagerShiftSwapApproval
                }
                disabled={savingSettings}
                onChange={event =>
                  updateManagerApproval(
                    event.target.checked
                  )
                }
              />

              <span>
                <strong>
                  Require managers to approve shift covers
                </strong>
                <br />
                Staff must still accept the cover first. The rota only changes after a Manager approves it.
              </span>
            </label>
          )}
        </section>
      )}
    </main>
  );
}
