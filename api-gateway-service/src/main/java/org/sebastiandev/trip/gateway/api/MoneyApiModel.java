package org.sebastiandev.trip.gateway.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record MoneyApiModel(
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @PositiveOrZero long amountMinor) {
    public MoneyApiModel normalized() {
        return new MoneyApiModel(currency.toUpperCase(), amountMinor);
    }
}
