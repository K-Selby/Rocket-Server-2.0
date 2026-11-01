import type { Metadata } from "next";

import "./globals.css";

import AppShell from "./components/AppShell";
import { CurrentUserProvider } from "./context/CurrentUserContext";
import { InboxProvider } from "./context/InboxContext";

export const metadata: Metadata = {
  title: "The Rocket Pub",
  description: "The Rocket Pub customer and staff portals",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <head>
        <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,400,0..1,0" />
      </head>
      <body>
        <CurrentUserProvider>
          <InboxProvider>
            <AppShell>{children}</AppShell>
          </InboxProvider>
        </CurrentUserProvider>
      </body>
    </html>
  );
}
