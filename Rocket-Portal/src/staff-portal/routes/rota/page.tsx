"use client";

import { useEffect, useState } from "react";
import { useCurrentUser } from "@/app/context/CurrentUserContext";

type StaffMember = {
  id: number;
  name: string;
  role: "STAFF" | "MANAGER" | "ADMIN";
  active: boolean;
  activeOnRota: boolean;
};

type RotaWeek = {
  weekStart: string;
  published: boolean;
  editing: boolean;
  notes: string | null;
  publishedAt: string | null;
  publishedBy: StaffMember | null;
  hiddenStaffIds: number[];
  publishedHiddenStaffIds: number[];
  dismissedAvailabilityIds: number[];
};

type RotaShift = {
  id: number;
  shiftDate: string;
  startTime: string | null;
  endTime: string | null;
  finishShift: boolean;
  kitchenShift: boolean;
  note: string | null;
  staffMember: StaffMember;
};

type DiaryEntry = {
  id: number;
  entryDate: string;
  type: string;
  status: string;
  staffMember: StaffMember | null;
  availableFrom: string | null;
  availableTo: string | null;
  availableUntilFinish: boolean;
};

type ParsedShift = {
  startTime: string | null;
  endTime: string | null;
  finishShift: boolean;
  kitchenShift: boolean;
};

type ShiftSwapRequest = {
  id: number;
};

const API = "";

function localDate(date: Date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}

function startOfWeek(date: Date) {
  const sunday = new Date(date);

  sunday.setHours(12, 0, 0, 0);
  sunday.setDate(
    sunday.getDate() - sunday.getDay()
  );

  return sunday;
}

function getWeekDays(weekStart: string) {
  const start =
    new Date(`${weekStart}T12:00:00`);

  return Array.from(
    { length: 7 },
    (_, index) => {
      const day = new Date(start);

      day.setDate(
        start.getDate() + index
      );

      return localDate(day);
    }
  );
}

function formatDayName(date: string) {
  return new Date(
    `${date}T12:00:00`
  ).toLocaleDateString("en-GB", {
    weekday: "short",
  });
}

function formatDayDate(date: string) {
  return new Date(
    `${date}T12:00:00`
  ).toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "short",
  });
}

function formatFullDate(date: string) {
  return new Date(
    `${date}T12:00:00`
  ).toLocaleDateString("en-GB", {
    weekday: "long",
    day: "numeric",
    month: "long",
  });
}

function formatWeekHeading(
  weekStart: string
) {
  const days = getWeekDays(weekStart);

  const start =
    new Date(`${days[0]}T12:00:00`);

  const end =
    new Date(`${days[6]}T12:00:00`);

  const first =
    start.toLocaleDateString(
      "en-GB",
      {
        day: "2-digit",
        month: "short",
      }
    );

  const last =
    end.toLocaleDateString(
      "en-GB",
      {
        day: "2-digit",
        month: "short",
        year: "numeric",
      }
    );

  return `${first} – ${last}`;
}

function pubTime(
  time: string | null
) {
  if (!time) return "";

  const [hourText, minute] =
    time.split(":");

  const hour =
    Number(hourText);

  const displayHour =
    hour === 0
      ? 12
      : hour > 12
        ? hour - 12
        : hour;

  return minute === "00"
    ? String(displayHour)
    : `${displayHour}:${minute}`;
}

function formatShift(
  shift?: RotaShift
) {
  if (!shift) return "";

  if (shift.kitchenShift) {
    return "K";
  }

  const start =
    pubTime(shift.startTime);

  return shift.finishShift
    ? `${start}-F`
    : `${start}-${pubTime(
      shift.endTime
    )}`;
}

function formatAvailability(
  entry: DiaryEntry
) {
  const start =
    pubTime(entry.availableFrom);

  return entry.availableUntilFinish
    ? `${start}-F`
    : `${start}-${pubTime(
      entry.availableTo
    )}`;
}

function parseShift(
  value: string
): ParsedShift | null {
  const clean =
    value
      .trim()
      .toUpperCase()
      .replace(/\s+/g, "");

  if (clean === "K") {
    return {
      startTime: null,
      endTime: null,
      finishShift: false,
      kitchenShift: true,
    };
  }

  const match = clean.match(
    /^(\d{1,2})(?::([0-5]\d))?-(F|\d{1,2}(?::[0-5]\d)?)$/
  );

  if (!match) return null;

  function convert(value: string) {
    const [
      hourText,
      minute = "00",
    ] = value.split(":");

    let hour =
      Number(hourText);

    if (
      hour < 0 ||
      hour > 23
    ) {
      return null;
    }

    if (
      hour >= 1 &&
      hour <= 11
    ) {
      hour += 12;
    }

    return `${String(hour).padStart(
      2,
      "0"
    )}:${minute}`;
  }

  const start = convert(
    match[1] +
    (
      match[2]
        ? `:${match[2]}`
        : ""
    )
  );

  if (!start) return null;

  if (match[3] === "F") {
    return {
      startTime: start,
      endTime: null,
      finishShift: true,
      kitchenShift: false,
    };
  }

  const end =
    convert(match[3]);

  if (!end) return null;

  return {
    startTime: start,
    endTime: end,
    finishShift: false,
    kitchenShift: false,
  };
}

async function api<T>(
  path: string,
  options?: RequestInit
): Promise<T> {
  const response =
    await fetch(
      `${API}${path}`,
      options
    );

  const data =
    await response
      .json()
      .catch(() => null);

  if (!response.ok) {
    throw new Error(
      data?.message ||
      `Request failed (${response.status})`
    );
  }

  return data as T;
}

function errorMessage(
  error: unknown
) {
  if (error instanceof TypeError) {
    return "Could not reach the server.";
  }

  return error instanceof Error
    ? error.message
    : "Something went wrong.";
}

export default function RotaPage() {
  const {
    currentUser,
    loadingUser,
  } = useCurrentUser();

  const [
    weekStart,
    setWeekStart,
  ] = useState(
    localDate(
      startOfWeek(
        new Date()
      )
    )
  );

  const [
    week,
    setWeek,
  ] =
    useState<RotaWeek | null>(
      null
    );

  const [
    staff,
    setStaff,
  ] =
    useState<StaffMember[]>([]);

  const [
    shifts,
    setShifts,
  ] =
    useState<RotaShift[]>([]);

  const [
    diary,
    setDiary,
  ] =
    useState<DiaryEntry[]>([]);

  const [
    editingCell,
    setEditingCell,
  ] =
    useState<string | null>(
      null
    );

  const [
    editValue,
    setEditValue,
  ] =
    useState("");

  const [
    staffMenu,
    setStaffMenu,
  ] =
    useState<number | null>(
      null
    );

  const [
    swapShift,
    setSwapShift,
  ] =
    useState<RotaShift | null>(
      null
    );

  const [
    targetStaffId,
    setTargetStaffId,
  ] =
    useState("");

  const [
    swapNote,
    setSwapNote,
  ] =
    useState("");

  const [
    sendingSwap,
    setSendingSwap,
  ] =
    useState(false);

  const [
    loading,
    setLoading,
  ] =
    useState(true);

  const [
    weekLoading,
    setWeekLoading,
  ] =
    useState(true);

  const [
    saving,
    setSaving,
  ] =
    useState(false);

  const [
    error,
    setError,
  ] =
    useState("");

  const [
    notice,
    setNotice,
  ] =
    useState("");

  const canManage =
    currentUser?.role ===
    "MANAGER" ||
    currentUser?.role ===
    "ADMIN";

  const isEditing =
    Boolean(
      canManage &&
      week?.editing
    );

  const weekDays =
    getWeekDays(
      weekStart
    );

  const today =
    localDate(
      new Date()
    );

  const hiddenIds =
    new Set(
      isEditing
        ? week
          ?.hiddenStaffIds ??
        []
        : week
          ?.publishedHiddenStaffIds ??
        []
    );

  const dismissedAvailability =
    new Set(
      week
        ?.dismissedAvailabilityIds ??
      []
    );

  const rotaStaff =
    staff.filter(
      member =>
        member.activeOnRota
    );

  const visibleStaff =
    rotaStaff.filter(
      member =>
        !hiddenIds.has(
          member.id
        )
    );

  const hiddenStaff =
    rotaStaff.filter(
      member =>
        hiddenIds.has(
          member.id
        )
    );

  const coverTargets =
    swapShift
      ? rotaStaff.filter(
        member =>
          member.id !==
          currentUser?.id &&
          member.active &&
          !isApprovedDayOff(
            member.id,
            swapShift.shiftDate
          )
      )
      : [];

  useEffect(() => {
    async function loadStaff() {
      try {
        const response =
          await fetch(
            `${API}/api/staff/rota`,
            { credentials: "include" }
          );

        if (!response.ok) {
          throw new Error(
            "Could not load staff."
          );
        }

        setStaff(
          await response.json()
        );
      } catch (loadError) {
        setError(
          errorMessage(
            loadError
          )
        );
      } finally {
        setLoading(false);
      }
    }

    loadStaff();
  }, []);

  useEffect(() => {
    async function loadDiary() {
      const days =
        getWeekDays(
          weekStart
        );

      try {
        const response =
          await fetch(
            `${API}/api/diary/range?from=${days[0]}&to=${days[6]}`
          );

        if (!response.ok) {
          throw new Error(
            "Could not load Staff Diary."
          );
        }

        setDiary(
          await response.json()
        );
      } catch (loadError) {
        setError(
          errorMessage(
            loadError
          )
        );
      }
    }

    loadDiary();
  }, [weekStart]);

  useEffect(() => {
    if (!currentUser) return;

    const user = currentUser;

    async function loadWeek() {
      setWeekLoading(true);

      try {
        const response =
          await fetch(
            `${API}/api/rota/weeks/${weekStart}?currentUserId=${user.id}`
          );

        if (
          response.status ===
          404
        ) {
          setWeek(null);
          setShifts([]);
          setError("");

          return;
        }

        const data =
          await response
            .json()
            .catch(
              () => null
            );

        if (!response.ok) {
          throw new Error(
            data?.message ||
            "Could not load rota."
          );
        }

        const loadedWeek =
          data as RotaWeek;

        const shiftResponse =
          await fetch(
            `${API}/api/rota/weeks/${loadedWeek.weekStart}/shifts?currentUserId=${user.id}`
          );

        const shiftData: RotaShift[] =
          await shiftResponse.json();

        if (!shiftResponse.ok) {
          throw new Error("Could not load shifts.");
        }

        setWeek(loadedWeek);
        setShifts(shiftData);

        setEditingCell(
          null
        );

        setStaffMenu(null);
        closeSwap();
        setError("");
      } catch (loadError) {
        setError(
          errorMessage(
            loadError
          )
        );
      } finally {
        setWeekLoading(false);
      }
    }

    loadWeek();
  }, [
    currentUser,
    weekStart,
  ]);

  function findShift(
    staffId: number,
    date: string
  ) {
    return shifts.find(
      shift =>
        shift.staffMember.id ===
        staffId &&
        shift.shiftDate ===
        date
    );
  }

  function findAvailability(
    staffId: number,
    date: string
  ) {
    return diary.find(
      entry =>
        entry.entryDate ===
        date &&
        entry.type ===
        "AVAILABLE" &&
        entry.staffMember?.id ===
        staffId &&
        !dismissedAvailability.has(
          entry.id
        )
    );
  }

  function isApprovedDayOff(
    staffId: number,
    date: string
  ) {
    return diary.some(
      entry =>
        entry.entryDate ===
        date &&
        entry.type ===
        "DAY_OFF" &&
        entry.status ===
        "APPROVED" &&
        entry.staffMember?.id ===
        staffId
    );
  }

  function drawRotaCanvas(
    printFriendly = false
  ) {
    if (!week) return null;

    const width = 3508;
    const height = 2480;

    const canvas =
      document.createElement(
        "canvas"
      );

    canvas.width = width;
    canvas.height = height;

    const context =
      canvas.getContext(
        "2d"
      );

    if (!context) {
      return null;
    }

    const margin = 150;
    const tableTop = 350;
    const staffWidth = 650;

    const tableWidth =
      width -
      margin * 2;

    const dayWidth =
      (
        tableWidth -
        staffWidth
      ) / 7;

    const availableHeight =
      height -
      tableTop -
      120;

    const headerHeight = 150;

    const rowHeight =
      Math.min(
        180,
        Math.max(
          80,
          (
            availableHeight -
            headerHeight
          ) /
          Math.max(
            visibleStaff.length,
            1
          )
        )
      );

    context.fillStyle =
      "#ffffff";

    context.fillRect(
      0,
      0,
      width,
      height
    );

    context.fillStyle =
      "#111111";

    context.font =
      "bold 92px Arial";

    context.textAlign =
      "left";

    context.textBaseline =
      "middle";

    context.fillText(
      "Rocket Pub Staff Rota",
      margin,
      115
    );

    context.font =
      "48px Arial";

    context.fillStyle =
      "#333333";

    context.fillText(
      formatWeekHeading(
        weekStart
      ),
      margin,
      210
    );

    context.fillStyle =
      printFriendly
        ? "#000000"
        : "#176b3a";

    context.fillRect(
      margin,
      275,
      tableWidth,
      14
    );

    function drawCell(
      x: number,
      y: number,
      cellWidth: number,
      cellHeight: number,
      background =
        "#ffffff"
    ) {
      context!.fillStyle =
        printFriendly
          ? "#ffffff"
          : background;

      context!.fillRect(
        x,
        y,
        cellWidth,
        cellHeight
      );

      context!.strokeStyle =
        printFriendly
          ? "#000000"
          : "#555555";

      context!.lineWidth = 3;

      context!.strokeRect(
        x,
        y,
        cellWidth,
        cellHeight
      );
    }

    function drawCentredText(
      text: string,
      x: number,
      y: number,
      cellWidth: number,
      cellHeight: number,
      font: string,
      colour =
        "#111111"
    ) {
      context!.fillStyle =
        printFriendly
          ? "#000000"
          : colour;

      context!.font = font;

      context!.textAlign =
        "center";

      context!.textBaseline =
        "middle";

      context!.fillText(
        text,
        x + cellWidth / 2,
        y + cellHeight / 2
      );
    }

    drawCell(
      margin,
      tableTop,
      staffWidth,
      headerHeight,
      printFriendly
        ? "#ffffff"
        : "#e5ece7"
    );

    drawCentredText(
      "Staff",
      margin,
      tableTop,
      staffWidth,
      headerHeight,
      "bold 44px Arial"
    );

    weekDays.forEach(
      (
        day,
        index
      ) => {
        const x =
          margin +
          staffWidth +
          dayWidth * index;

        drawCell(
          x,
          tableTop,
          dayWidth,
          headerHeight,
          printFriendly
            ? "#ffffff"
            : "#e5ece7"
        );

        const date =
          new Date(
            `${day}T12:00:00`
          );

        const dayName =
          date.toLocaleDateString(
            "en-GB",
            {
              weekday:
                "short",
            }
          );

        const dayDate =
          date.toLocaleDateString(
            "en-GB",
            {
              day:
                "2-digit",
              month:
                "short",
            }
          );

        context.fillStyle =
          "#111111";

        context.textAlign =
          "center";

        context.textBaseline =
          "middle";

        context.font =
          "bold 40px Arial";

        context.fillText(
          dayName,
          x +
          dayWidth / 2,
          tableTop + 52
        );

        context.font =
          "34px Arial";

        context.fillText(
          dayDate,
          x +
          dayWidth / 2,
          tableTop + 102
        );
      }
    );

    visibleStaff.forEach(
      (
        member,
        rowIndex
      ) => {
        const y =
          tableTop +
          headerHeight +
          rowHeight *
          rowIndex;

        drawCell(
          margin,
          y,
          staffWidth,
          rowHeight,
          printFriendly
            ? "#ffffff"
            : "#f7f7f7"
        );

        context.fillStyle =
          "#111111";

        context.font =
          `bold ${Math.min(
            42,
            rowHeight *
            0.34
          )}px Arial`;

        context.textAlign =
          "left";

        context.textBaseline =
          "middle";

        context.fillText(
          member.name,
          margin + 35,
          y +
          rowHeight / 2
        );

        weekDays.forEach(
          (
            day,
            dayIndex
          ) => {
            const x =
              margin +
              staffWidth +
              dayWidth *
              dayIndex;

            const shift =
              findShift(
                member.id,
                day
              );

            const dayOff =
              isApprovedDayOff(
                member.id,
                day
              );

            drawCell(
              x,
              y,
              dayWidth,
              rowHeight
            );

            if (dayOff) {
              if (
                !printFriendly
              ) {
                context.save();

                context.beginPath();

                context.rect(
                  x,
                  y,
                  dayWidth,
                  rowHeight
                );

                context.clip();

                context.strokeStyle =
                  "#bbbbbb";

                context.lineWidth =
                  4;

                for (
                  let line =
                    -rowHeight;
                  line <
                  dayWidth +
                  rowHeight;
                  line += 36
                ) {
                  context.beginPath();

                  context.moveTo(
                    x + line,
                    y +
                    rowHeight
                  );

                  context.lineTo(
                    x +
                    line +
                    rowHeight,
                    y
                  );

                  context.stroke();
                }

                context.restore();
              }

              drawCentredText(
                "/",
                x,
                y,
                dayWidth,
                rowHeight,
                `bold ${Math.min(
                  58,
                  rowHeight *
                  0.48
                )}px Arial`,
                "#333333"
              );

              return;
            }

            if (!shift) {
              return;
            }

            drawCentredText(
              formatShift(
                shift
              ),
              x,
              y,
              dayWidth,
              rowHeight,
              `bold ${Math.min(
                44,
                rowHeight *
                0.38
              )}px Arial`
            );
          }
        );
      }
    );

    return canvas;
  }

  function downloadRotaImage() {
    const canvas =
      drawRotaCanvas(
        false
      );

    if (!canvas) return;

    const link =
      document.createElement(
        "a"
      );

    link.download =
      `rocket-rota-${weekStart}.png`;

    link.href =
      canvas.toDataURL(
        "image/png"
      );

    link.click();
  }

  function printRota() {
    const canvas =
      drawRotaCanvas(
        true
      );

    if (!canvas) return;

    const link =
      document.createElement(
        "a"
      );

    link.download =
      `rocket-rota-${weekStart}-print.png`;

    link.href =
      canvas.toDataURL(
        "image/png"
      );

    link.click();
  }

  function moveWeek(
    amount: number
  ) {
    const date =
      new Date(
        `${weekStart}T12:00:00`
      );

    date.setDate(
      date.getDate() +
      amount * 7
    );

    setWeekStart(
      localDate(date)
    );

    setWeekLoading(true);
    setEditingCell(null);
    setStaffMenu(null);
    closeSwap();
    setNotice("");
    setError("");
  }

  function cellKey(
    staffId: number,
    date: string
  ) {
    return `${staffId}-${date}`;
  }

  function openCell(
    member: StaffMember,
    date: string,
    shift?: RotaShift
  ) {
    if (
      !isEditing ||
      isApprovedDayOff(
        member.id,
        date
      )
    ) {
      return;
    }

    setEditingCell(
      cellKey(
        member.id,
        date
      )
    );

    setEditValue(
      formatShift(
        shift
      )
    );

    setStaffMenu(null);
  }

  function closeCell() {
    setEditingCell(null);
    setEditValue("");
  }

  function openSwap(
    shift: RotaShift
  ) {
    if (
      !currentUser ||
      !week?.published ||
      week.editing
    ) {
      return;
    }

    if (
      shift.staffMember.id !==
      currentUser.id ||
      shift.shiftDate <
      today
    ) {
      return;
    }

    setSwapShift(shift);
    setTargetStaffId("");
    setSwapNote("");
    setError("");
  }

  function closeSwap() {
    setSwapShift(null);
    setTargetStaffId("");
    setSwapNote("");
  }

  async function sendSwapRequest() {
    if (
      !currentUser ||
      !swapShift ||
      !targetStaffId ||
      sendingSwap
    ) {
      return;
    }

    const targetStaff =
      staff.find(
        member =>
          member.id ===
          Number(
            targetStaffId
          )
      );

    if (!targetStaff) {
      setError(
        "Select a staff member."
      );

      return;
    }

    setSendingSwap(true);
    setError("");

    try {
      await api<ShiftSwapRequest>(
        `/api/shift-swaps?currentUserId=${currentUser.id}`,
        {
          method: "POST",
          headers: {
            "Content-Type":
              "application/json",
          },
          body:
            JSON.stringify({
              requesterShiftId:
                swapShift.id,
              targetStaffMemberId:
                targetStaff.id,
              targetShiftId:
                null,
              note:
                swapNote
                  .trim() ||
                null,
            }),
        }
      );

      closeSwap();

      setNotice(
        `Shift cover request sent to ${targetStaff.name}.`
      );
    } catch (swapError) {
      setError(
        errorMessage(
          swapError
        )
      );
    } finally {
      setSendingSwap(false);
    }
  }

  async function createDraft() {
    if (
      !currentUser ||
      !canManage
    ) {
      return;
    }

    try {
      const created =
        await api<RotaWeek>(
          `/api/rota/weeks?currentUserId=${currentUser.id}`,
          {
            method:
              "POST",
            headers: {
              "Content-Type":
                "application/json",
            },
            body:
              JSON.stringify({
                weekStart,
                notes: null,
              }),
          }
        );

      setWeek(created);
      setShifts([]);

      setNotice(
        "Draft rota created. Changes now save automatically."
      );

      setError("");
    } catch (createError) {
      setError(
        errorMessage(
          createError
        )
      );
    }
  }

  async function saveShift(
    member: StaffMember,
    date: string,
    parsed: ParsedShift,
    existing?: RotaShift
  ) {
    if (
      !currentUser ||
      !week ||
      !isEditing
    ) {
      return;
    }

    const body = {
      staffMemberId:
        member.id,
      shiftDate:
        date,
      startTime:
        parsed.startTime,
      endTime:
        parsed.endTime,
      finishShift:
        parsed.finishShift,
      kitchenShift:
        parsed.kitchenShift,
      note:
        existing?.note ??
        null,
    };

    const saved =
      await api<RotaShift>(
        existing
          ? `/api/rota/shifts/${existing.id}?currentUserId=${currentUser.id}`
          : `/api/rota/weeks/${week.weekStart}/shifts?currentUserId=${currentUser.id}`,
        {
          method:
            existing
              ? "PUT"
              : "POST",
          headers: {
            "Content-Type":
              "application/json",
          },
          body:
            JSON.stringify(
              body
            ),
        }
      );

    setShifts(
      current =>
        existing
          ? current.map(
            shift =>
              shift.id ===
                saved.id
                ? saved
                : shift
          )
          : [
            ...current,
            saved,
          ]
    );

    return saved;
  }

  async function saveCell(
    member: StaffMember,
    date: string,
    existing?: RotaShift
  ) {
    if (
      !currentUser ||
      !week ||
      !isEditing ||
      saving
    ) {
      return;
    }

    const clean =
      editValue.trim();

    if (!clean) {
      if (existing) {
        await removeShift(
          existing
        );
      } else {
        closeCell();
      }

      return;
    }

    const parsed =
      parseShift(clean);

    if (!parsed) {
      setError(
        "Use a shift such as 12-3, 5-F or K."
      );

      return;
    }

    setSaving(true);
    setError("");

    try {
      await saveShift(
        member,
        date,
        parsed,
        existing
      );

      closeCell();

      setNotice(
        "Changes saved automatically."
      );
    } catch (saveError) {
      setError(
        errorMessage(
          saveError
        )
      );
    } finally {
      setSaving(false);
    }
  }

  async function acceptAvailability(
    member: StaffMember,
    entry: DiaryEntry
  ) {
    if (
      !currentUser ||
      !week ||
      !isEditing ||
      saving ||
      !entry.availableFrom
    ) {
      return;
    }

    setSaving(true);
    setError("");

    const parsed:
      ParsedShift = {
      startTime:
        entry.availableFrom,
      endTime:
        entry.availableUntilFinish
          ? null
          : entry.availableTo,
      finishShift:
        entry.availableUntilFinish,
      kitchenShift:
        false,
    };

    try {
      await saveShift(
        member,
        entry.entryDate,
        parsed
      );

      setNotice(
        `${member.name}'s ${formatAvailability(
          entry
        )} availability added to the rota.`
      );
    } catch (saveError) {
      setError(
        errorMessage(
          saveError
        )
      );
    } finally {
      setSaving(false);
    }
  }

  async function dismissAvailability(
    entry: DiaryEntry
  ) {
    if (
      !currentUser ||
      !week ||
      !isEditing ||
      saving
    ) {
      return;
    }

    try {
      const updated =
        await api<RotaWeek>(
          `/api/rota/weeks/${week.weekStart}/dismissed-availability/${entry.id}?currentUserId=${currentUser.id}`,
          {
            method:
              "PUT",
          }
        );

      setWeek(updated);

      setNotice(
        "Availability suggestion dismissed."
      );

      setError("");
    } catch (dismissError) {
      setError(
        errorMessage(
          dismissError
        )
      );
    }
  }

  async function removeShift(
    shift: RotaShift
  ) {
    if (
      !currentUser ||
      !isEditing ||
      saving
    ) {
      return;
    }

    setSaving(true);

    try {
      await api<void>(
        `/api/rota/shifts/${shift.id}?currentUserId=${currentUser.id}`,
        {
          method:
            "DELETE",
        }
      );

      setShifts(
        current =>
          current.filter(
            item =>
              item.id !==
              shift.id
          )
      );

      closeCell();

      setNotice(
        "Changes saved automatically."
      );

      setError("");
    } catch (removeError) {
      setError(
        errorMessage(
          removeError
        )
      );
    } finally {
      setSaving(false);
    }
  }

  async function startEditing() {
    if (
      !currentUser ||
      !week ||
      !canManage
    ) {
      return;
    }

    try {
      const updated =
        await api<RotaWeek>(
          `/api/rota/weeks/${week.weekStart}/edit?currentUserId=${currentUser.id}`,
          {
            method:
              "PUT",
          }
        );

      setWeek(updated);

      setNotice(
        "Editing rota. Staff still see the currently published version."
      );

      setError("");
    } catch (editError) {
      setError(
        errorMessage(
          editError
        )
      );
    }
  }

  async function publishOrUpdate() {
    if (
      !currentUser ||
      !week ||
      !canManage ||
      !week.editing
    ) {
      return;
    }

    const updating =
      week.published;

    const confirmed =
      window.confirm(
        updating
          ? "Update the published rota with these changes?"
          : "Publish this rota to staff?"
      );

    if (!confirmed) {
      return;
    }

    try {
      const updated =
        await api<RotaWeek>(
          `/api/rota/weeks/${week.weekStart}/publish?currentUserId=${currentUser.id}`,
          {
            method:
              "PUT",
          }
        );

      setWeek(updated);
      setEditingCell(null);

      setNotice(
        updating
          ? "Published rota updated."
          : "Rota published."
      );

      setError("");
    } catch (publishError) {
      setError(
        errorMessage(
          publishError
        )
      );
    }
  }

  async function hideStaff(
    member: StaffMember
  ) {
    if (
      !currentUser ||
      !week ||
      !isEditing
    ) {
      return;
    }

    try {
      const updated =
        await api<RotaWeek>(
          `/api/rota/weeks/${week.weekStart}/hidden-staff/${member.id}?currentUserId=${currentUser.id}`,
          {
            method:
              "PUT",
          }
        );

      setWeek(updated);
      setStaffMenu(null);

      setNotice(
        `${member.name} hidden from this rota.`
      );
    } catch (hideError) {
      setError(
        errorMessage(
          hideError
        )
      );
    }
  }

  async function showStaff(
    member: StaffMember
  ) {
    if (
      !currentUser ||
      !week ||
      !isEditing
    ) {
      return;
    }

    try {
      const updated =
        await api<RotaWeek>(
          `/api/rota/weeks/${week.weekStart}/hidden-staff/${member.id}?currentUserId=${currentUser.id}`,
          {
            method:
              "DELETE",
          }
        );

      setWeek(updated);

      setNotice(
        `${member.name} restored to this rota.`
      );
    } catch (showError) {
      setError(
        errorMessage(
          showError
        )
      );
    }
  }

  if (
    loadingUser ||
    loading
  ) {
    return (
      <main>
        <h1>
          Rota
        </h1>

        <p>
          Loading rota...
        </p>
      </main>
    );
  }

  if (!currentUser) {
    return (
      <main>
        <h1>
          Rota
        </h1>

        <p>
          No current user is available.
        </p>
      </main>
    );
  }

  return (
    <>
      <main className="rota-page">
        <div className="rota-page-card">
          <div className="rota-header">
            <h1>
              Staff Rota
            </h1>

            <p>
              Week from Sunday{" "}
              {formatDayDate(
                weekStart
              )}{" "}
              {weekStart.slice(
                0,
                4
              )}
            </p>
          </div>

          <div className="week-controls">
            <button
              onClick={() =>
                moveWeek(-1)
              }
            >
              ← Previous
            </button>

            <strong>
              {formatWeekHeading(
                weekStart
              )}
            </strong>

            <button
              onClick={() =>
                moveWeek(1)
              }
            >
              Next →
            </button>
          </div>

          {error && (
            <div className="page-error">
              {error}
            </div>
          )}

          {notice && (
            <div className="page-success">
              {notice}
            </div>
          )}

          {weekLoading ? (
            <p>
              Loading week...
            </p>
          ) : !week ? (
            <div className="rota-empty-state">
              <h2>
                No rota available for this week yet
              </h2>

              {canManage ? (
                <>
                  <p>
                    Create a draft, build the rota, then publish it when ready.
                  </p>

                  <button
                    className="primary-button"
                    onClick={
                      createDraft
                    }
                  >
                    Create draft rota
                  </button>
                </>
              ) : (
                <p>
                  Check back once the rota has been published.
                </p>
              )}
            </div>
          ) : (
            <>
              <div className="rota-status">
                <strong>
                  {!week.published
                    ? "DRAFT"
                    : week.editing
                      ? "EDITING"
                      : "PUBLISHED"}
                </strong>

                {week.editing && (
                  <span>
                    Changes save automatically
                  </span>
                )}

                {!week.editing &&
                  week.publishedAt && (
                    <span>
                      Issued{" "}
                      {new Date(
                        week.publishedAt
                      ).toLocaleString(
                        "en-GB",
                        {
                          day:
                            "2-digit",
                          month:
                            "2-digit",
                          hour:
                            "2-digit",
                          minute:
                            "2-digit",
                        }
                      )}

                      {week.publishedBy &&
                        ` by ${week.publishedBy.name}`}
                    </span>
                  )}

                {canManage &&
                  week.published &&
                  !week.editing && (
                    <button
                      className="primary-button"
                      onClick={
                        startEditing
                      }
                    >
                      Edit rota
                    </button>
                  )}

                {canManage &&
                  week.editing && (
                    <button
                      className="primary-button"
                      onClick={
                        publishOrUpdate
                      }
                    >
                      {week.published
                        ? "Update rota"
                        : "Publish rota"}
                    </button>
                  )}

                <div className="rota-export-actions">
                  <button
                    className="secondary-button"
                    onClick={
                      downloadRotaImage
                    }
                  >
                    Rota image
                  </button>

                  <button
                    className="secondary-button"
                    onClick={
                      printRota
                    }
                  >
                    Print rota
                  </button>
                </div>
              </div>

              {isEditing &&
                hiddenStaff.length >
                0 && (
                  <div className="rota-hidden-staff">
                    <strong>
                      Hidden:
                    </strong>

                    {hiddenStaff.map(
                      member => (
                        <button
                          key={
                            member.id
                          }
                          onClick={() =>
                            showStaff(
                              member
                            )
                          }
                        >
                          +{" "}
                          {
                            member.name
                          }
                        </button>
                      )
                    )}
                  </div>
                )}

              <div className="rota-scroll">
                <table className="rota-table">
                  <thead>
                    <tr>
                      <th>
                        Staff
                      </th>

                      {weekDays.map(
                        day => (
                          <th
                            key={
                              day
                            }
                          >
                            <span className="rota-day-name">
                              {formatDayName(
                                day
                              )}
                            </span>

                            <span className="rota-day-date">
                              {formatDayDate(
                                day
                              )}
                            </span>
                          </th>
                        )
                      )}
                    </tr>
                  </thead>

                  <tbody>
                    {visibleStaff.map(
                      member => (
                        <tr
                          key={
                            member.id
                          }
                        >
                          <th>
                            <div className="rota-staff-name">
                              <span>
                                {
                                  member.name
                                }
                              </span>

                              {isEditing && (
                                <div className="rota-staff-menu">
                                  <button
                                    className="rota-menu-button"
                                    onClick={() =>
                                      setStaffMenu(
                                        current =>
                                          current ===
                                            member.id
                                            ? null
                                            : member.id
                                      )
                                    }
                                  >
                                    ⋯
                                  </button>

                                  {staffMenu ===
                                    member.id && (
                                      <div className="rota-menu-popover">
                                        <button
                                          onClick={() =>
                                            hideStaff(
                                              member
                                            )
                                          }
                                        >
                                          Hide from rota
                                        </button>
                                      </div>
                                    )}
                                </div>
                              )}
                            </div>
                          </th>

                          {weekDays.map(
                            day => {
                              const shift =
                                findShift(
                                  member.id,
                                  day
                                );

                              const dayOff =
                                isApprovedDayOff(
                                  member.id,
                                  day
                                );

                              const availability =
                                isEditing &&
                                  !shift &&
                                  !dayOff
                                  ? findAvailability(
                                    member.id,
                                    day
                                  )
                                  : undefined;

                              const key =
                                cellKey(
                                  member.id,
                                  day
                                );

                              const cellOpen =
                                editingCell ===
                                key;

                              const ownPublishedShift =
                                Boolean(
                                  shift &&
                                  week.published &&
                                  !week.editing &&
                                  shift.staffMember.id ===
                                  currentUser.id &&
                                  shift.shiftDate >=
                                  today
                                );

                              if (
                                dayOff
                              ) {
                                return (
                                  <td
                                    key={
                                      day
                                    }
                                    className="rota-day-off"
                                  >
                                    <span>
                                      /
                                    </span>
                                  </td>
                                );
                              }

                              if (
                                availability
                              ) {
                                return (
                                  <td
                                    key={
                                      day
                                    }
                                  >
                                    <div className="rota-availability-suggestion">
                                      <span>
                                        {formatAvailability(
                                          availability
                                        )}
                                      </span>

                                      <div className="rota-availability-actions">
                                        <button
                                          className="rota-availability-accept"
                                          title="Use this availability"
                                          disabled={
                                            saving
                                          }
                                          onClick={() =>
                                            acceptAvailability(
                                              member,
                                              availability
                                            )
                                          }
                                        >
                                          ✓
                                        </button>

                                        <button
                                          className="rota-availability-dismiss"
                                          title="Dismiss this suggestion"
                                          disabled={
                                            saving
                                          }
                                          onClick={() =>
                                            dismissAvailability(
                                              availability
                                            )
                                          }
                                        >
                                          ×
                                        </button>
                                      </div>
                                    </div>
                                  </td>
                                );
                              }

                              return (
                                <td
                                  key={
                                    day
                                  }
                                  className={
                                    cellOpen
                                      ? "rota-cell-editing"
                                      : ""
                                  }
                                >
                                  {cellOpen ? (
                                    <div className="rota-inline-editor">
                                      <input
                                        autoFocus
                                        value={
                                          editValue
                                        }
                                        placeholder="e.g. 5-F"
                                        disabled={
                                          saving
                                        }
                                        onChange={
                                          event =>
                                            setEditValue(
                                              event
                                                .target
                                                .value
                                            )
                                        }
                                        onKeyDown={
                                          event => {
                                            if (
                                              event.key ===
                                              "Enter"
                                            ) {
                                              saveCell(
                                                member,
                                                day,
                                                shift
                                              );
                                            }

                                            if (
                                              event.key ===
                                              "Escape"
                                            ) {
                                              closeCell();
                                            }
                                          }
                                        }
                                      />

                                      <button
                                        className="rota-confirm-button"
                                        disabled={
                                          saving
                                        }
                                        onClick={() =>
                                          saveCell(
                                            member,
                                            day,
                                            shift
                                          )
                                        }
                                      >
                                        ✓
                                      </button>

                                      <button
                                        className="rota-cancel-button"
                                        disabled={
                                          saving
                                        }
                                        onClick={
                                          closeCell
                                        }
                                      >
                                        ×
                                      </button>

                                      {shift && (
                                        <button
                                          className="rota-remove-button"
                                          disabled={
                                            saving
                                          }
                                          onClick={() =>
                                            removeShift(
                                              shift
                                            )
                                          }
                                        >
                                          Remove
                                        </button>
                                      )}
                                    </div>
                                  ) : (
                                    <button
                                      className={`rota-cell-button ${ownPublishedShift
                                          ? "rota-own-shift"
                                          : ""
                                        }`}
                                      disabled={
                                        !isEditing &&
                                        !ownPublishedShift
                                      }
                                      onClick={() => {
                                        if (
                                          isEditing
                                        ) {
                                          openCell(
                                            member,
                                            day,
                                            shift
                                          );
                                        }
                                      }}
                                    >
                                      {shift && (
                                        <span className="rota-shift-badge">
                                          {formatShift(
                                            shift
                                          )}
                                        </span>
                                      )}

                                      {ownPublishedShift &&
                                        shift && (
                                          <span
                                            className="rota-swap-hover"
                                            onClick={
                                              event => {
                                                event.stopPropagation();

                                                openSwap(
                                                  shift
                                                );
                                              }
                                            }
                                          >
                                            Cover shift
                                          </span>
                                        )}
                                    </button>
                                  )}
                                </td>
                              );
                            }
                          )}
                        </tr>
                      )
                    )}
                  </tbody>
                </table>
              </div>
            </>
          )}

          {swapShift && (
            <div
              className="rota-swap-overlay"
              role="presentation"
              onMouseDown={event => {
                if (
                  event.target ===
                  event.currentTarget
                ) {
                  closeSwap();
                }
              }}
            >
              <section
                className="rota-swap-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="swap-title"
              >
                <div className="rota-swap-heading">
                  <div>
                    <h2 id="swap-title">
                      Shift cover
                    </h2>

                    <p>
                      {formatFullDate(
                        swapShift.shiftDate
                      )}
                      {" · "}
                      <strong>
                        {formatShift(
                          swapShift
                        )}
                      </strong>
                    </p>
                  </div>

                  <button
                    className="rota-swap-close"
                    aria-label="Close"
                    onClick={
                      closeSwap
                    }
                  >
                    ×
                  </button>
                </div>

                {coverTargets.length >
                  0 ? (
                  <>
                    <div className="rota-swap-field">
                      <span className="rota-swap-field-label">
                        Who do you want to ask?
                      </span>

                      <small>
                        Choose a staff member who is active on the rota.
                      </small>

                      <div
                        style={{
                          display:
                            "grid",
                          gridTemplateColumns:
                            "repeat(3, minmax(0, 1fr))",
                          gap:
                            "0.45rem",
                        }}
                      >
                        {coverTargets.map(
                          member => {
                            const selected =
                              targetStaffId ===
                              String(
                                member.id
                              );

                            return (
                              <button
                                key={
                                  member.id
                                }
                                type="button"
                                className={
                                  selected
                                    ? "primary-button"
                                    : "secondary-button"
                                }
                                aria-pressed={
                                  selected
                                }
                                disabled={
                                  sendingSwap
                                }
                                style={{
                                  width:
                                    "100%",
                                  minWidth:
                                    0,
                                  textAlign:
                                    "left",
                                }}
                                onClick={() =>
                                  setTargetStaffId(
                                    String(
                                      member.id
                                    )
                                  )
                                }
                              >
                                {
                                  member.name
                                }
                              </button>
                            );
                          }
                        )}
                      </div>
                    </div>

                    <label className="rota-swap-field">
                      <span className="rota-swap-field-label">
                        Note{" "}
                        <small>
                          Optional
                        </small>
                      </span>

                      <textarea
                        value={
                          swapNote
                        }
                        maxLength={
                          300
                        }
                        disabled={
                          sendingSwap
                        }
                        placeholder="Add a short note..."
                        onChange={
                          event =>
                            setSwapNote(
                              event
                                .target
                                .value
                            )
                        }
                      />
                    </label>

                    <p className="rota-swap-help">
                      They will receive this shift in their Inbox. If they accept, the shift will move from you to them.
                    </p>

                    <div className="rota-swap-actions">
                      <button
                        className="secondary-button"
                        disabled={
                          sendingSwap
                        }
                        onClick={
                          closeSwap
                        }
                      >
                        Cancel
                      </button>

                      <button
                        className="primary-button"
                        disabled={
                          !targetStaffId ||
                          sendingSwap
                        }
                        onClick={
                          sendSwapRequest
                        }
                      >
                        {sendingSwap
                          ? "Sending..."
                          : "Send request"}
                      </button>
                    </div>
                  </>
                ) : (
                  <>
                    <p className="rota-swap-empty">
                      There are no staff available to receive this shift.
                    </p>

                    <div className="rota-swap-actions">
                      <button
                        className="secondary-button"
                        onClick={
                          closeSwap
                        }
                      >
                        Close
                      </button>
                    </div>
                  </>
                )}
              </section>
            </div>
          )}
        </div>
      </main>
    </>
  );
}
