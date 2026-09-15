package uk.co.rocketpub.staffportal.model;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class StaffRoleConverter implements AttributeConverter<StaffRole, String> {

    @Override
    public String convertToDatabaseColumn(StaffRole role) {
        return role == null ? null : role.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public StaffRole convertToEntityAttribute(String value) {
        return value == null ? null : StaffRole.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
