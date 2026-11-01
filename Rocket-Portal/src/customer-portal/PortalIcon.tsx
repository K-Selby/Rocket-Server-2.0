const legacy: Record<string, string> = {
  pdf: "picture_as_pdf", menu: "restaurant_menu", star: "star", cocktail: "local_bar",
  drink: "sports_bar", dessert: "cake", PDF: "picture_as_pdf", "▤": "restaurant_menu", "★": "star",
};

const emojiIcons: Record<string, string> = {
  "📄": "description", "📋": "assignment", "📖": "menu_book", "🍽": "restaurant", "🍔": "lunch_dining",
  "🍕": "local_pizza", "🍰": "cake", "🎂": "cake", "🍦": "icecream", "🍜": "ramen_dining",
  "🍸": "local_bar", "🍹": "local_bar", "🍺": "sports_bar", "🍻": "sports_bar", "🍷": "wine_bar",
  "🥂": "wine_bar", "☕": "coffee", "🫖": "emoji_food_beverage", "💧": "water_full",
  "🎄": "park", "⭐": "star", "🌟": "star", "✨": "auto_awesome", "❤": "favorite",
  "🎉": "celebration", "🎃": "interests", "🐣": "egg", "🏠": "home", "📞": "call",
  "☎": "call", "📍": "location_on", "📅": "calendar_month", "ℹ": "info", "⚠": "warning",
};

export function normalizePortalIcon(value: string) {
  const raw = (value.startsWith("emoji:") ? value.slice(6) : value).replaceAll("\uFE0F", "").trim();
  const match = emojiIcons[raw];
  return match ? `material:${match}` : /\p{Extended_Pictographic}/u.test(raw) ? "material:auto_awesome" : value;
}

export function PortalIcon({ value, monochrome = false }: { value: string; monochrome?: boolean }) {
  value = normalizePortalIcon(value);
  if (value === "material:peanut") return <svg className="portal-symbol portal-peanut" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path fill="currentColor" fillOpacity=".16" d="M20.4 3.6c-2.5-2.5-6.7-1.2-8.4 2-.8 1.5-1 3-2.4 4.4S6.7 11.6 5.2 12.4c-3.2 1.7-4.5 5.9-2 8.4s6.7 1.2 8.4-2c.8-1.5 1-3 2.4-4.4s2.9-1.6 4.4-2.4c3.2-1.7 4.5-5.9 2-8.4Z"/><path d="m15.5 5.5 1-1m1 4 1-1m-5 1 1-1m-3 5 1-1m-5 3 1-1m-4 4 1-1m2 3 1-1"/></svg>;
  const material = value.startsWith("material:") ? value.slice(9) : legacy[value];
  const custom = value.startsWith("emoji:") ? value.slice(6) : value;
  if (material) {
    return <span className={`material-symbols-rounded portal-symbol ${monochrome ? "portal-symbol-monochrome" : ""}`} aria-hidden="true">{material}</span>;
  }
  return <span className={monochrome ? "portal-symbol-monochrome" : "portal-emoji"} aria-hidden="true">{custom || "📄"}</span>;
}
