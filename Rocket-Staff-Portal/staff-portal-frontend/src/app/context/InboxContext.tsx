"use client";

import {
  createContext,
  ReactNode,
  useContext,
  useEffect,
  useState,
} from "react";

import {
  CurrentUser,
  useCurrentUser,
} from "./CurrentUserContext";

export type DayOffRequest = {
  id: number;
  requester: CurrentUser;
  dates: string[];
  status:
    | "PENDING"
    | "APPROVED"
    | "DECLINED"
    | "CANCELLED";
  note: string | null;
  createdAt: string;
  actedAt: string | null;
  actedBy: CurrentUser | null;
};

type ShiftSwapSummary = {
  id: number;
  requester: CurrentUser;
  targetStaffMember: CurrentUser;
  status:
    | "PENDING"
    | "AWAITING_MANAGER_APPROVAL"
    | "APPROVED"
    | "REJECTED"
    | "CANCELLED";
};

export function isRecent(request: DayOffRequest) {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - 14);

  return (
    new Date(
      request.actedAt || request.createdAt
    ).getTime() >= cutoff.getTime()
  );
}

function decisionKey(request: DayOffRequest) {
  return `${request.id}:${request.status}:${
    request.actedAt || request.createdAt
  }`;
}

type InboxState = {
  requests: DayOffRequest[];
  loading: boolean;
  error: string;

  pendingCount: number;
  unreadCount: number;

  shiftPendingCount: number;
  managerShiftApprovalCount: number;

  count: number;

  isUnread: (
    request: DayOffRequest
  ) => boolean;

  markRead: (
    request:
      | DayOffRequest
      | DayOffRequest[]
  ) => void;

  updateRequest: (
    request: DayOffRequest
  ) => void;

  refresh: () => void;
};

const InboxContext =
  createContext<InboxState | null>(null);

export function InboxProvider({
  children,
}: {
  children: ReactNode;
}) {
  const { currentUser } =
    useCurrentUser();

  return (
    <UserInbox
      key={`${currentUser?.id}-${currentUser?.role}`}
      user={currentUser}
    >
      {children}
    </UserInbox>
  );
}

function UserInbox({
  user,
  children,
}: {
  user: CurrentUser | null;
  children: ReactNode;
}) {
  const [requests, setRequests] =
    useState<DayOffRequest[]>([]);

  const [
    incomingSwaps,
    setIncomingSwaps,
  ] = useState<ShiftSwapSummary[]>([]);

  const [
    managerSwaps,
    setManagerSwaps,
  ] = useState<ShiftSwapSummary[]>([]);

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState("");

  const [seen, setSeen] =
    useState<string[]>([]);

  const [
    seenReady,
    setSeenReady,
  ] = useState(false);

  const [revision, setRevision] =
    useState(0);

  const manager =
    user?.role === "ADMIN" ||
    user?.role === "MANAGER";

  const storageKey =
    `rocket-inbox-read:${user?.id}`;

  // Remember read day-off decisions for this account.
  useEffect(() => {
    function loadSeen() {
      try {
        const value: unknown =
          JSON.parse(
            localStorage.getItem(
              storageKey
            ) || "[]"
          );

        setSeen(
          Array.isArray(value)
            ? value.filter(
                (
                  item
                ): item is string =>
                  typeof item ===
                  "string"
              )
            : []
        );
      } catch {
        setSeen([]);
      }

      setSeenReady(true);
    }

    function storageChanged(
      event: StorageEvent
    ) {
      if (
        event.key === storageKey ||
        event.key === null
      ) {
        loadSeen();
      }
    }

    loadSeen();

    window.addEventListener(
      "storage",
      storageChanged
    );

    return () =>
      window.removeEventListener(
        "storage",
        storageChanged
      );
  }, [storageKey]);

  /*
   * Poll all Inbox sources together.
   * Pending shift covers remain counted until resolved.
   */
  useEffect(() => {
    if (!user) return;

    let active = true;
    let fetching = false;

    async function load() {
      if (fetching) return;
      fetching = true;

      try {
        const dayOffUrl =
          `http://localhost:8080/api/day-off-requests/${
            manager ? "manager" : "mine"
          }?currentUserId=${user!.id}`;

        const incomingUrl =
          `http://localhost:8080/api/shift-swaps/incoming/${user!.id}`;

        const [
          dayOffResponse,
          incomingResponse,
          managerResponse,
        ] = await Promise.all([
          fetch(dayOffUrl),
          fetch(incomingUrl),

          manager
            ? fetch(
                `http://localhost:8080/api/shift-swaps/manager/pending?currentUserId=${user!.id}`
              )
            : Promise.resolve(null),
        ]);

        const dayOffData =
          await dayOffResponse
            .json()
            .catch(() => null);

        const incomingData =
          await incomingResponse
            .json()
            .catch(() => null);

        const managerData =
          managerResponse
            ? await managerResponse
                .json()
                .catch(() => null)
            : [];

        if (
          !dayOffResponse.ok ||
          !Array.isArray(dayOffData)
        ) {
          throw new Error(
            dayOffData?.message ||
              "Could not load Inbox updates."
          );
        }

        if (
          !incomingResponse.ok ||
          !Array.isArray(incomingData)
        ) {
          throw new Error(
            incomingData?.message ||
              "Could not load shift requests."
          );
        }

        if (
          managerResponse &&
          (
            !managerResponse.ok ||
            !Array.isArray(
              managerData
            )
          )
        ) {
          throw new Error(
            managerData?.message ||
              "Could not load manager approvals."
          );
        }

        if (active) {
          setRequests(dayOffData);
          setIncomingSwaps(
            incomingData
          );
          setManagerSwaps(
            managerData
          );
          setError("");
        }
      } catch (err) {
        if (active) {
          setError(
            err instanceof Error
              ? err.message
              : "Could not load Inbox updates."
          );
        }
      } finally {
        fetching = false;

        if (active) {
          setLoading(false);
        }
      }
    }

    load();

    const timer =
      window.setInterval(
        load,
        30000
      );

    window.addEventListener(
      "focus",
      load
    );

    return () => {
      active = false;

      window.clearInterval(timer);

      window.removeEventListener(
        "focus",
        load
      );
    };
  }, [
    user,
    manager,
    revision,
  ]);

  function isUnread(
    request: DayOffRequest
  ) {
    return (
      seenReady &&
      request.requester.id ===
        user?.id &&
      (
        request.status ===
          "APPROVED" ||
        request.status ===
          "DECLINED"
      ) &&
      isRecent(request) &&
      !seen.includes(
        decisionKey(request)
      )
    );
  }

  function markRead(
    value:
      | DayOffRequest
      | DayOffRequest[]
  ) {
    const decisions = (
      Array.isArray(value)
        ? value
        : [value]
    ).filter(
      request =>
        request.requester.id ===
          user?.id &&
        (
          request.status ===
            "APPROVED" ||
          request.status ===
            "DECLINED"
        )
    );

    if (!decisions.length) return;

    let stored: string[] = [];

    try {
      const value: unknown =
        JSON.parse(
          localStorage.getItem(
            storageKey
          ) || "[]"
        );

      if (Array.isArray(value)) {
        stored = value.filter(
          (
            item
          ): item is string =>
            typeof item ===
            "string"
        );
      }
    } catch {
      /* Browser storage is optional. */
    }

    const next = [
      ...new Set([
        ...seen,
        ...stored,
        ...decisions.map(
          decisionKey
        ),
      ]),
    ];

    setSeen(next);

    try {
      localStorage.setItem(
        storageKey,
        JSON.stringify(next)
      );
    } catch {
      /* Keep the in-memory state. */
    }
  }

  const pendingCount = manager
    ? requests.filter(
        request =>
          request.status ===
          "PENDING"
      ).length
    : 0;

  const unreadCount =
    requests.filter(isUnread).length;

  /*
   * Opening the Inbox does not clear this.
   * Only Accept or Decline does.
   */
  const shiftPendingCount =
    incomingSwaps.filter(
      request =>
        request.status ===
          "PENDING" &&
        request.targetStaffMember.id ===
          user?.id
    ).length;

  const managerShiftApprovalCount =
    manager
      ? managerSwaps.filter(
          request =>
            request.status ===
            "AWAITING_MANAGER_APPROVAL"
        ).length
      : 0;

  const count =
    pendingCount +
    unreadCount +
    shiftPendingCount +
    managerShiftApprovalCount;

  return (
    <InboxContext.Provider
      value={{
        requests,
        loading:
          !!user && loading,
        error,

        pendingCount,
        unreadCount,

        shiftPendingCount,
        managerShiftApprovalCount,

        count,

        isUnread,
        markRead,

        updateRequest(request) {
          setRequests(current =>
            current.map(item =>
              item.id ===
              request.id
                ? request
                : item
            )
          );

          setRevision(
            value => value + 1
          );
        },

        refresh() {
          setRevision(
            value => value + 1
          );
        },
      }}
    >
      {children}
    </InboxContext.Provider>
  );
}

export function useInbox() {
  const context =
    useContext(InboxContext);

  if (!context) {
    throw new Error(
      "useInbox must be used inside InboxProvider"
    );
  }

  return context;
}
