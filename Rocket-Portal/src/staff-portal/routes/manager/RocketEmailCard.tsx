"use client";

import { useEffect, useState } from "react";

const API = "/api/email";
type EmailStatus = { configured: boolean; connected: boolean; sender: string };

export default function RocketEmailCard() {
    const [status, setStatus] = useState<EmailStatus | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");

    async function loadStatus() {
        const response = await fetch(`${API}/status`, {
            credentials: "include",
            cache: "no-store",
        });

        if (!response.ok) {
            throw new Error("Could not check Rocket Email. Make sure you are signed in as Admin.");
        }

        setStatus(await response.json());
    }

    useEffect(() => {
        let active = true;

        async function initialise() {
            const result = new URLSearchParams(window.location.search).get("email");

            if (result) {
                const url = new URL(window.location.href);
                url.searchParams.delete("email");
                window.history.replaceState(null, "", url);
            }

            try {
                await loadStatus();

                if (!active) return;

                if (result === "connected") {
                    setNotice("Microsoft connection saved.");
                } else if (result === "connection-failed") {
                    setError("Microsoft connection failed or expired. Please try again.");
                }
            } catch {
                if (active) {
                    setError("Could not check Rocket Email. Make sure you are signed in as Admin.");
                }
            }
        }

        void initialise();

        return () => {
            active = false;
        };
    }, []);

    async function act(action: "connect" | "disconnect" | "refresh") {
        setBusy(true);
        setError("");
        setNotice("");

        try {
            if (action === "refresh") {
                await loadStatus();
                return;
            }

            const response = await fetch(`${API}/microsoft/${action}`, {
                method: "POST",
                credentials: "include",
            });

            if (!response.ok) {
                throw new Error("Rocket Email could not complete this action. Check your Admin session and server configuration.");
            }

            if (action === "connect") {
                const data: { authorizationUrl: string } = await response.json();
                window.location.assign(data.authorizationUrl);
            } else {
                await loadStatus();
                setNotice("Rocket Email disconnected from the Staff Portal.");
            }
        } catch (err) {
            setError(err instanceof Error ? err.message : "Rocket Email is unavailable.");
        } finally {
            setBusy(false);
        }
    }

    return (
        <section className="rocket-email-card" aria-labelledby="rocket-email-heading">
            <div className="rocket-email-details">
                <div className="rocket-email-icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" focusable="false">
                        <path d="M3.5 6.5h17v11h-17z" />
                        <path d="m4.5 7.5 7.5 6 7.5-6" />
                    </svg>
                </div>

                <div>
                    <div className="rocket-email-title-row">
                        <h2 id="rocket-email-heading">Rocket Email</h2>
                        <span
                            className={`rocket-email-status ${
                                status?.connected
                                    ? "rocket-email-status-connected"
                                    : "rocket-email-status-disconnected"
                            }`}
                            role="status"
                        >
                            {status ? (status.connected ? "Connected" : "Disconnected") : "Checking…"}
                        </span>
                    </div>

                    <p className="rocket-email-address">
                        {status?.sender ?? "rocketpubserver@outlook.com"}
                    </p>

                    <p className="rocket-email-description">
                        {!status
                            ? "Checking the Microsoft Outlook connection."
                            : !status.configured
                                ? "Microsoft email credentials have not been configured on the Staff Portal server."
                                : status.connected
                                    ? "Microsoft Outlook is ready to send Staff Portal emails."
                                    : "Connect the Rocket mailbox to start sending Staff Portal emails."}
                    </p>
                </div>
            </div>

            <div className="rocket-email-actions">
                {status?.connected ? (
                    <button type="button" className="secondary-button rocket-email-disconnect" disabled={busy} onClick={() => act("disconnect")}>
                        Disconnect
                    </button>
                ) : (
                    <button type="button" className="primary-button" disabled={busy || !status?.configured} onClick={() => act("connect")}>
                        Connect Microsoft Email
                    </button>
                )}

                <button type="button" className="rocket-email-refresh" disabled={busy} onClick={() => act("refresh")}>
                    Refresh status
                </button>
            </div>

            {error && <div className="form-error rocket-email-message" role="alert">{error}</div>}
            {notice && <div className="page-success rocket-email-message" role="status">{notice}</div>}
        </section>
    );
}
