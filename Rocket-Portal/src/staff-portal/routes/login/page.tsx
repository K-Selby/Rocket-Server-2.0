"use client";

import { CSSProperties, FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useCurrentUser } from "@/app/context/CurrentUserContext";
import { sharedAssetUrl } from "@/app/components/RocketBrand";
import styles from "./login.module.css";

const API = "";

type Mode = "login" | "request" | "reset";

type LoginResponse = {
  id: number;
  name: string;
  role: "STAFF" | "MANAGER" | "ADMIN";
  mustChangePassword: boolean;
};

export default function LoginPage() {
  const router = useRouter();
  const { currentUser, loadingUser, refreshCurrentUser } = useCurrentUser();

  const [mode, setMode] = useState<Mode>("login");
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    if (!loadingUser && currentUser) {
      router.replace(currentUser.mustChangePassword ? "/staff/setup" : "/staff/rota");
    }
  }, [currentUser, loadingUser, router]);

  function showMode(nextMode: Mode) {
    setMode(nextMode);
    setError("");
    setNotice("");
  }

  async function submitLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");

    try {
      const response = await fetch(`${API}/api/auth/login`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ login, password }),
      });

      const data: LoginResponse | null = await response.json().catch(() => null);

      if (!response.ok || !data) {
        throw new Error(
          data && "message" in data
            ? String(data.message)
            : "Incorrect username/email or password.",
        );
      }

      await refreshCurrentUser();
      router.replace(data.mustChangePassword ? "/staff/setup" : "/staff/rota");
    } catch (loginError) {
      setError(
        loginError instanceof Error
          ? loginError.message
          : "Could not sign in.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function requestReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");

    try {
      const response = await fetch(`${API}/api/auth/forgot-password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || "The reset email could not be sent.");
      }

      setNotice(
        "If that email is linked and verified, a six-digit reset code has been sent. "
        + "If you have not linked an email, contact a manager.",
      );
      setMode("reset");
    } catch (resetError) {
      setError(
        resetError instanceof Error
          ? resetError.message
          : "The reset email could not be sent.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function resetPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");

    try {
      const response = await fetch(`${API}/api/auth/reset-password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email,
          code,
          newPassword,
          confirmPassword,
        }),
      });

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || "The password could not be reset.");
      }

      setNotice("Your password has been reset. You can now sign in.");
      setPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setCode("");
      setMode("login");
    } catch (resetError) {
      setError(
        resetError instanceof Error
          ? resetError.message
          : "The password could not be reset.",
      );
    } finally {
      setBusy(false);
    }
  }

  if (loadingUser) {
    return (
      <main className={styles.page}>
        <div className={styles.card}>
          <p>Checking sign in...</p>
        </div>
      </main>
    );
  }

  const pageStyle = {
    "--rocket-wallpaper": `url("${sharedAssetUrl("images/rocket-pub-wallpaper.jpg")}")`,
  } as CSSProperties;

  const subtitle = mode === "login"
    ? "Sign in to continue"
    : mode === "request"
      ? "Recover your password"
      : "Enter your reset code";

  return (
    <main className={styles.page} style={pageStyle}>
      <section className={styles.card}>
        <div className={styles.brand}>
          <div className={styles.rocket} aria-hidden="true">
            🚀
          </div>
          <h1>Rocket Staff Portal</h1>
          <p>{subtitle}</p>
        </div>

        {mode === "login" && (
          <form className={styles.form} onSubmit={submitLogin}>
            <label>
              Username or email
              <input
                type="text"
                value={login}
                autoComplete="username"
                autoFocus
                required
                onChange={(event) => setLogin(event.target.value)}
              />
            </label>

            <label>
              Password
              <input
                type="password"
                value={password}
                autoComplete="current-password"
                required
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>

            <div className={styles.textActions}>
              <button type="button" onClick={() => showMode("request")}>
                Forgot your password?
              </button>
            </div>

            {error && (
              <p className={styles.error} role="alert">
                {error}
              </p>
            )}

            {notice && (
              <p className={styles.notice} role="status">
                {notice}
              </p>
            )}

            <button type="submit" disabled={busy}>
              {busy ? "Signing in..." : "Sign in"}
            </button>
          </form>
        )}

        {mode === "request" && (
          <form className={styles.form} onSubmit={requestReset}>
            <p className={styles.formHint}>
              Enter the email linked to your account. Password recovery only
              works if your email has been verified. If you have not linked an
              email, contact a manager.
            </p>

            <label>
              Email
              <input
                type="email"
                value={email}
                autoComplete="email"
                autoFocus
                required
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>

            {error && (
              <p className={styles.error} role="alert">
                {error}
              </p>
            )}

            {notice && (
              <p className={styles.notice} role="status">
                {notice}
              </p>
            )}

            <button type="submit" disabled={busy}>
              {busy ? "Sending code..." : "Send reset code"}
            </button>

            <button
              className={styles.secondaryAction}
              type="button"
              onClick={() => showMode("login")}
            >
              Back to sign in
            </button>
          </form>
        )}

        {mode === "reset" && (
          <form className={styles.form} onSubmit={resetPassword}>
            <p className={styles.formHint}>
              Enter the six-digit code sent to your linked email, then choose a
              new password.
            </p>

            <label>
              Email
              <input
                type="email"
                value={email}
                required
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>

            <label>
              Reset code
              <input
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                value={code}
                required
                onChange={(event) => {
                  setCode(event.target.value.replace(/\D/g, ""));
                }}
              />
            </label>

            <label>
              New password
              <input
                type="password"
                value={newPassword}
                autoComplete="new-password"
                minLength={8}
                required
                onChange={(event) => setNewPassword(event.target.value)}
              />
            </label>

            <label>
              Confirm new password
              <input
                type="password"
                value={confirmPassword}
                autoComplete="new-password"
                minLength={8}
                required
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
            </label>

            {error && (
              <p className={styles.error} role="alert">
                {error}
              </p>
            )}

            {notice && (
              <p className={styles.notice} role="status">
                {notice}
              </p>
            )}

            <button type="submit" disabled={busy}>
              {busy ? "Resetting password..." : "Reset password"}
            </button>

            <div className={styles.textActions}>
              <button type="button" onClick={() => showMode("request")}>
                Send a new code
              </button>
              <button type="button" onClick={() => showMode("login")}>
                Back to sign in
              </button>
            </div>
          </form>
        )}
      </section>
    </main>
  );
}
