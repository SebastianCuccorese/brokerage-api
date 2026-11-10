package dev.cuccorese.brokerage.market;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Declares {@code immutable_unaccent} (V6) as returning a string. Hibernate types an unknown function as
 * Object, and the cast it would need to compare it changes the expression, so Postgres could no longer use
 * the trigram indexes built on it.
 */
public class UnaccentFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        contributions.getFunctionRegistry().registerNamed("immutable_unaccent",
                contributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.STRING));
    }
}
