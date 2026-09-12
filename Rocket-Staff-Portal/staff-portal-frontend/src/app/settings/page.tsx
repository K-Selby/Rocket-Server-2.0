"use client";

import { FormEvent, useState } from "react";
import { useCurrentUser } from "../context/CurrentUserContext";

const API = "http://localhost:8080";

export default function AccountSettingsPage() {
  const { currentUser, loadingUser, refreshCurrentUser } = useCurrentUser();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [passwordBusy, setPasswordBusy] = useState(false);
  const [emailBusy, setEmailBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function request(path: string, options: RequestInit) {
    const response = await fetch(`${API}${path}`, {
      credentials: "include",
      headers: { "Content-Type": "application/json", ...options.headers },
      ...options,
    });

    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new Error(body?.message ?? "The account change could not be completed.");
    }

    await refreshCurrentUser();
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPasswordBusy(true);
    setError("");
    setNotice("");

    try {
      await request("/api/account/password", {
        method: "POST",
        body: JSON.stringify({ currentPassword, newPassword, confirmPassword }),
      });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setNotice("Your password has been changed.");
    } catch (changeError) {
      setError(changeError instanceof Error ? changeError.message : "Your password could not be changed.");
    } finally {
      setPasswordBusy(false);
    }
  }

  async function startEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setEmailBusy(true);
    setError("");
    setNotice("");

    try {
      await request("/api/account/email", {
        method: "POST",
        body: JSON.stringify({ email }),
      });
      setEmail("");
      setCode("");
      setNotice("A six-digit verification code has been sent to your email.");
    } catch (emailError) {
      setError(emailError instanceof Error ? emailError.message : "The verification email could not be sent.");
    } finally {
      setEmailBusy(false);
    }
  }

  async function verifyEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setEmailBusy(true);
    setError("");
    setNotice("");

    try {
      await request("/api/account/email/verify", {
        method: "POST",
        body: JSON.stringify({ code }),
      });
      setCode("");
      setNotice("Your email address has been verified.");
    } catch (emailError) {
      setError(emailError instanceof Error ? emailError.message : "The verification code could not be checked.");
    } finally {
      setEmailBusy(false);
    }
  }

  async function emailAction(action: "resend" | "cancel") {
    setEmailBusy(true);
    setError("");
    setNotice("");

    try {
      await request(action === "resend" ? "/api/account/email/resend" : "/api/account/email/pending", {
        method: action === "resend" ? "POST" : "DELETE",
      });
      setCode("");
      setNotice(action === "resend" ? "A new verification code has been sent." : "The pending email change has been cancelled.");
    } catch (emailError) {
      setError(emailError instanceof Error ? emailError.message : "The email change could not be completed.");
    } finally {
      setEmailBusy(false);
    }
  }

  if (loadingUser) {
    return <main className="page-content"><p>Loading account settings...</p></main>;
  }

  if (!currentUser) return null;

  return (
    <main className="page-content account-settings-page">
      <div className="page-header">
        <h1>Account settings</h1>
        <p>Manage your Rocket Staff Portal password and email.</p>
      </div>

      {!currentUser.emailVerified && (
        <div className="account-email-warning">
          <span aria-hidden="true">!</span>
          <div><strong>No verified email linked</strong><p>Password recovery will not work until an email has been verified.</p></div>
        </div>
      )}

      {error && <div className="form-error" role="alert">{error}</div>}
      {notice && <div className="page-success" role="status">{notice}</div>}

      <div className="account-settings-grid">
        <section className="account-settings-card">
          <div className="account-card-header">
            <div><h2>Password</h2><p>Use at least eight characters and do not reuse the default password.</p></div>
          </div>

          <form className="account-form" onSubmit={changePassword}>
            <label>Current password<input type="password" value={currentPassword} autoComplete="current-password" required onChange={event => setCurrentPassword(event.target.value)} /></label>
            <label>New password<input type="password" value={newPassword} minLength={8} autoComplete="new-password" required onChange={event => setNewPassword(event.target.value)} /></label>
            <label>Confirm new password<input type="password" value={confirmPassword} minLength={8} autoComplete="new-password" required onChange={event => setConfirmPassword(event.target.value)} /></label>
            <button className="primary-button" type="submit" disabled={passwordBusy}>{passwordBusy ? "Changing password..." : "Change password"}</button>
          </form>
        </section>

        <section className="account-settings-card">
          <div className="account-card-header">
            <div><h2>Email</h2><p>A verified email lets you sign in by email and recover a forgotten password.</p></div>
            <span className={`account-email-state ${currentUser.emailVerified ? "verified" : "action-needed"}`}>
              {currentUser.emailVerified ? "Verified" : "Optional"}
            </span>
          </div>

          {currentUser.emailVerified && (
            <div className="verified-email-row"><div><span>Verified email</span><strong>{currentUser.email}</strong></div></div>
          )}

          {currentUser.pendingEmail ? (
            <div className="pending-email-panel">
              <p>Enter the six-digit code sent to <strong>{currentUser.pendingEmail}</strong>.</p>
              <form className="verification-code-form" onSubmit={verifyEmail}>
                <input aria-label="Six-digit verification code" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} value={code} placeholder="000000" required onChange={event => setCode(event.target.value.replace(/\D/g, ""))} />
                <button className="primary-button" type="submit" disabled={emailBusy || code.length !== 6}>Verify email</button>
              </form>
              <div className="pending-email-actions">
                <button type="button" disabled={emailBusy} onClick={() => emailAction("resend")}>Resend code</button>
                <button type="button" disabled={emailBusy} onClick={() => emailAction("cancel")}>Cancel change</button>
              </div>
            </div>
          ) : (
            <form className="account-form" onSubmit={startEmail}>
              <label>{currentUser.emailVerified ? "Change email address" : "Email address"}<input type="email" value={email} autoComplete="email" placeholder="you@example.com" required onChange={event => setEmail(event.target.value)} /></label>
              <button className="primary-button" type="submit" disabled={emailBusy}>{emailBusy ? "Sending code..." : "Send verification code"}</button>
            </form>
          )}
        </section>
      </div>

    </main>
  );
}
