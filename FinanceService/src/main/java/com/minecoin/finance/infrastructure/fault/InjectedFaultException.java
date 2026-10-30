package com.minecoin.finance.infrastructure.fault;

public class InjectedFaultException extends RuntimeException {

    public InjectedFaultException() {
        super("Injected fault");
    }
}
