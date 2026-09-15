package uk.co.rocketpub.staffportal.dto;

import java.time.LocalDate;

// Represents the JSON needed to create a new rota week.
public class CreateRotaWeekRequest {

    private LocalDate weekStart;
    private String notes;

    public CreateRotaWeekRequest() {
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
