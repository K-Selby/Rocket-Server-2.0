package uk.co.rocketpub.staffportal.email;

public record RocketEmailStatus(boolean configured, boolean connected, String sender) {}