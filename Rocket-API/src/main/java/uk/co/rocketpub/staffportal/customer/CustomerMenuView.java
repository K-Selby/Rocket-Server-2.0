package uk.co.rocketpub.staffportal.customer;

public record CustomerMenuView(
        long id,
        String slug,
        String title,
        String description,
        boolean primary,
        String sidebarName,
        String icon,
        String cardColor,
        String textColor,
        String pdfUrl) {
}
