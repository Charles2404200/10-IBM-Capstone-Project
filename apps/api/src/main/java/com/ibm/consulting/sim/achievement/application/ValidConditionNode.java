package com.ibm.consulting.sim.achievement.application;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Validates the mutually exclusive GROUP and LEAF achievement-rule shapes. */
@Documented
@Constraint(validatedBy = ConditionNodeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidConditionNode {
    String message() default "achievement condition node has an invalid structure";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
