package dev.cuccorese.brokerage.shared.idempotency;

/** A resource an idempotency key can point at. */
public interface Identified {

    Integer getId();
}
