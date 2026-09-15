"use client";

import { CSSProperties, FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { useCurrentUser } from "@/app/context/CurrentUserContext";
import styles from "../login/login.module.css";
import setupStyles from "./setup.module.css";
import RocketLogo, { sharedAssetUrl } from "@/app/components/RocketBrand";

const API = "";

export default function SetupPage() {
  const router = useRouter();
  const { currentUser, loadingUser, refreshCurrentUser } = useCurrentUser();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
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
      throw new Error(body?.message ?? "Account setup could not be completed.");
    }

    await refreshCurrentUser();
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");

    try {
      await request("/api/account/password", {
        method: "POST",
        body: JSON.stringify({ currentPassword, newPassword, confirmPassword }),
      });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setNotice("Password saved. You can now link an email or skip this step.");
    } catch (changeError) {
      setError(changeError instanceof Error ? changeError.message : "Your password could not be changed.");
    } finally {
      setBusy(false);
    }
  }

  async function startEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");

    try {
      await request("/api/account/email", {
        method: "POST",
        body: JSON.stringify({ email }),
      });
      setEmail("");
      setNotice("A six-digit verification code has been sent.");
    } catch (emailError) {
      setError(emailError instanceof Error ? emailError.message : "The verification email could not be sent.");
    } finally {
      setBusy(false);
    }
  }

  async function verifyEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");

    try {
      await request("/api/account/email/verify", {
        method: "POST",
        body: JSON.stringify({ code }),
      });
      setCode("");
      setNotice("Your email is verified and your account is ready.");
    } catch (emailError) {
      setError(emailError instanceof Error ? emailError.message : "The verification code could not be checked.");
    } finally {
      setBusy(false);
    }
  }

  async function emailAction(action: "resend" | "change") {
    setBusy(true);
    setError("");
    setNotice("");

    try {
      await request(action === "resend" ? "/api/account/email/resend" : "/api/account/email/pending", {
        method: action === "resend" ? "POST" : "DELETE",
      });
      setCode("");
      if (action === "resend") setNotice("A new verification code has been sent.");
    } catch (emailError) {
      setError(emailError instanceof Error ? emailError.message : "The email step could not be updated.");
    } finally {
      setBusy(false);
    }
  }

  if (loadingUser) {
    return <main className={styles.page}><div className={styles.card}><p>Loading account setup...</p></div></main>;
  }

  if (!currentUser) return null;

  const passwordStep = currentUser.mustChangePassword;
  const complete = !passwordStep && currentUser.emailVerified;
  const pageStyle = {
    "--rocket-wallpaper": `url("${sharedAssetUrl("images/rocket-pub-wallpaper.jpg")}")`,
  } as CSSProperties;

  return (
    <main className={styles.page} style={pageStyle}>
      <section className={`${styles.card} ${setupStyles.card}`}>
        <div className={styles.brand}>
          <RocketLogo className={styles.rocketLogo} />
          <h1>{passwordStep ? "Create your password" : complete ? "Account ready" : currentUser.pendingEmail ? "Verify your email" : "Link your email"}</h1>
          <p>{passwordStep ? "Replace the temporary password before entering the Staff Portal." : complete ? "Your password and recovery email are set up." : "Email is optional, but it enables password recovery."}</p>
        </div>

        <div className={setupStyles.progress} aria-label={`Step ${passwordStep ? "1" : "2"} of 2`}>
          <div className={setupStyles.progressLabels}>
            <span>Step {passwordStep ? "1" : "2"} of 2</span>
            <strong>{passwordStep ? "Password" : "Email"}</strong>
          </div>
          <div className={setupStyles.progressTrack}>
            <span className={passwordStep ? setupStyles.progressHalf : setupStyles.progressFull} />
          </div>
        </div>

        {error && <p className={styles.error} role="alert">{error}</p>}
        {notice && <p className={setupStyles.notice} role="status">{notice}</p>}

        {passwordStep ? (
          <form className={styles.form} onSubmit={changePassword}>
            <label>Temporary password<input type="password" value={currentPassword} autoComplete="current-password" required autoFocus onChange={event => setCurrentPassword(event.target.value)} /></label>
            <label>New password<input type="password" value={newPassword} minLength={8} autoComplete="new-password" required onChange={event => setNewPassword(event.target.value)} /></label>
            <label>Confirm new password<input type="password" value={confirmPassword} minLength={8} autoComplete="new-password" required onChange={event => setConfirmPassword(event.target.value)} /></label>
            <p className={setupStyles.formHint}>Use at least eight characters. Your new password cannot be the same as your temporary password.</p>
            <button type="submit" disabled={busy}>{busy ? "Saving password..." : "Save password and continue"}</button>
          </form>
        ) : complete ? (
          <div className={setupStyles.setupActions}>
            <div className={setupStyles.successMark} aria-hidden="true">✓</div>
            <p><strong>{currentUser.email}</strong> is linked to your account.</p>
            <button type="button" onClick={() => router.replace("/staff/rota")}>Enter Staff Portal</button>
          </div>
        ) : currentUser.pendingEmail ? (
          <form className={styles.form} onSubmit={verifyEmail}>
            <p className={setupStyles.emailHelp}>Enter the code sent to <strong>{currentUser.pendingEmail}</strong>.</p>
            <label>Six-digit code<input className={setupStyles.codeInput} inputMode="numeric" pattern="[0-9]{6}" maxLength={6} value={code} placeholder="000000" required autoFocus onChange={event => setCode(event.target.value.replace(/\D/g, ""))} /></label>
            <button type="submit" disabled={busy || code.length !== 6}>{busy ? "Checking code..." : "Verify email"}</button>
            <div className={setupStyles.textActions}>
              <button type="button" disabled={busy} onClick={() => emailAction("resend")}>Resend code</button>
              <button type="button" disabled={busy} onClick={() => emailAction("change")}>Use a different email</button>
            </div>
          </form>
        ) : (
          <form className={styles.form} onSubmit={startEmail}>
            <label>Email address<input type="email" value={email} autoComplete="email" placeholder="you@example.com" required autoFocus onChange={event => setEmail(event.target.value)} /></label>
            <p className={setupStyles.formHint}>You will use this email for password recovery and may also use it to sign in after verification.</p>
            <button type="submit" disabled={busy}>{busy ? "Sending code..." : "Send verification code"}</button>
            <button type="button" className={setupStyles.secondaryAction} disabled={busy} onClick={() => router.replace("/staff/rota")}>Skip for now</button>
          </form>
        )}
      </section>
    </main>
  );
}
