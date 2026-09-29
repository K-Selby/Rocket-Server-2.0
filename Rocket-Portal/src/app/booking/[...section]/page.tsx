const pageNames: Record<string, { title: string; description: string }> = {
  bookings: { title: "Table Bookings", description: "Create, edit and manage normal table bookings." },
  "bookings/new": { title: "New Booking", description: "The new JavaScript booking form will be added here next." },
  "large-parties": { title: "Large Parties", description: "Manage large-party enquiries, deposits and reserved areas." },
  customers: { title: "Customers", description: "View returning customers and their seating preferences." },
  allergens: { title: "Allergen Menu", description: "Manage the pub allergen menu." },
  tables: { title: "Tables", description: "Manage table capacities, areas and availability." },
  archive: { title: "Archive", description: "Review previous and cancelled bookings." },
  "table-layout": { title: "Table Layout", description: "Edit the visual table layout." },
};

export default async function BookingSection({ params }: { params: Promise<{ section: string[] }> }) {
  const path = (await params).section.join("/");
  const page = pageNames[path] ?? { title: "Booking Portal", description: "This booking page is being migrated." };

  return <main className="booking-main"><section className="booking-page booking-placeholder"><span className="booking-page-kicker">Booking Portal</span><h1>{page.title}</h1><p>{page.description}</p><div className="booking-coming-soon">This page is ready for its booking tools.</div></section></main>;
}
