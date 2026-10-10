package br.com.deladopara.shipping.application;

public final class ShippingProviderOperationException extends RuntimeException {

    private final Certainty certainty;

    public ShippingProviderOperationException(Certainty certainty) {
        super(
                certainty == Certainty.DEFINITIVE_FAILURE
                        ? "shipping provider rejected the request"
                        : "shipping provider outcome is unknown");
        this.certainty = certainty;
    }

    public Certainty certainty() {
        return certainty;
    }

    public enum Certainty {
        DEFINITIVE_FAILURE,
        UNKNOWN
    }
}
