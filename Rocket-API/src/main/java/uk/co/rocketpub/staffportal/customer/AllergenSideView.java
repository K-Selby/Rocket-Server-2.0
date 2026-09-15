package uk.co.rocketpub.staffportal.customer;

public record AllergenSideView(
        long id,
        String name,
        String milkStatus,
        String nutsStatus,
        String eggStatus,
        String glutenStatus,
        boolean vegetarian,
        boolean canMakeVegetarian,
        boolean canMakeGlutenFree) {
}
