"use client";

import {
  createContext,
  ReactNode,
  useContext,
  useEffect,
  useState,
} from "react";

export type StaffRole = "STAFF" | "MANAGER" | "ADMIN";

export type CurrentUser = {
  id: number;
  name: string;
  role: StaffRole;
  mustChangePassword: boolean;
  email: string | null;
  emailVerified: boolean;
  pendingEmail: string | null;
};

type CurrentUserContextType = {
  currentUser: CurrentUser | null;
  loadingUser: boolean;
  userError: string;
  refreshCurrentUser: () => Promise<void>;
  clearCurrentUser: () => void;
};

const CurrentUserContext =
  createContext<CurrentUserContextType | undefined>(
    undefined
  );

const API = "http://localhost:8080";

export function CurrentUserProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [currentUser, setCurrentUser] =
    useState<CurrentUser | null>(null);

  const [loadingUser, setLoadingUser] =
    useState(true);

  const [userError, setUserError] =
    useState("");

  async function refreshCurrentUser() {
    const response = await fetch(
      `${API}/api/auth/me`,
      {
        credentials: "include",
      }
    );

    if (response.status === 401) {
      setCurrentUser(null);
      return;
    }

    if (!response.ok) {
      throw new Error(
        "Could not check your Staff Portal session."
      );
    }

    const user: CurrentUser =
      await response.json();

    setCurrentUser(user);
    setUserError("");
  }

  function clearCurrentUser() {
    setCurrentUser(null);
  }

  useEffect(() => {
    let active = true;

    async function loadCurrentUser() {
      try {
        const response = await fetch(
          `${API}/api/auth/me`,
          {
            credentials: "include",
          }
        );

        if (!active) return;

        if (response.status === 401) {
          setCurrentUser(null);
          return;
        }

        if (!response.ok) {
          throw new Error();
        }

        const user: CurrentUser =
          await response.json();

        if (!active) return;

        setCurrentUser(user);
        setUserError("");
      } catch {
        if (active) {
          setCurrentUser(null);
          setUserError(
            "Could not reach the Staff Portal server."
          );
        }
      } finally {
        if (active) {
          setLoadingUser(false);
        }
      }
    }

    loadCurrentUser();

    return () => {
      active = false;
    };
  }, []);

  return (
    <CurrentUserContext.Provider
      value={{
        currentUser,
        loadingUser,
        userError,
        refreshCurrentUser,
        clearCurrentUser,
      }}
    >
      {children}
    </CurrentUserContext.Provider>
  );
}

export function useCurrentUser() {
  const context = useContext(
    CurrentUserContext
  );

  if (!context) {
    throw new Error(
      "useCurrentUser must be used inside CurrentUserProvider"
    );
  }

  return context;
}
