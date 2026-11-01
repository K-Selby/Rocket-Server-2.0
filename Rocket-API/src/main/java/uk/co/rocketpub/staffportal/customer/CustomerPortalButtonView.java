package uk.co.rocketpub.staffportal.customer;

public record CustomerPortalButtonView(
        String key, String title, String description, String sidebarName,
        String icon, String cardColor, String textColor) {}
