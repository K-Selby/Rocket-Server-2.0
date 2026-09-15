package uk.co.rocketpub.staffportal.customer;

import java.util.List;

public record AllergenItemView(
        long id,
        String name,
        String category,
        String ingredients,
        String milkStatus,
        String nutsStatus,
        String eggStatus,
        String glutenStatus,
        boolean vegetarian,
        boolean canMakeVegetarian,
        String vegetarianChanges,
        boolean canMakeGlutenFree,
        String glutenFreeChanges,
        List<AllergenSideView> sides) {
}
