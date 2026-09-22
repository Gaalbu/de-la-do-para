package br.com.deladopara.pricing.domain;

public record Money(Currency currency, long cents) {
    public Money {
        if (currency == null || cents < 0) {
            throw new IllegalArgumentException("Invalid money");
        }
    }

    public static Money brl(long cents) {
        return new Money(Currency.BRL, cents);
    }
}
