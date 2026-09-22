package br.com.deladopara.cart.application;

public class CartInvalidInputException extends RuntimeException {
    public CartInvalidInputException(String message) {
        super(message);
    }
}
