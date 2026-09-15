import { redirect } from "next/navigation";

export default function FoodMenuRedirectPage() {
  redirect("/assets/menus/the-rocket-pub-food-menu.pdf");
}
