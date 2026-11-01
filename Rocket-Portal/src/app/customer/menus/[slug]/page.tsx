import CustomerMenuPage from "@/customer-portal/CustomerMenuPage";

export default async function MenuPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  return <CustomerMenuPage slug={slug} />;
}
