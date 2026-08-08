package com.ibm.consulting.sim.ai.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonaStateDeltaTest {

    @Test
    void zeroDeltaHasNoEffect() {
        PersonaStateDelta zero = PersonaStateDelta.zero();
        assertThat(zero.trust()).isZero();
        assertThat(zero.interest()).isZero();
        assertThat(zero.patience()).isZero();
    }

    @Test
    void clampsPositiveDeltaToMaxAbsoluteValue() {
        PersonaStateDelta delta = new PersonaStateDelta(25, 100, 11).clamped();
        assertThat(delta.trust()).isEqualTo(10);
        assertThat(delta.interest()).isEqualTo(10);
        assertThat(delta.patience()).isEqualTo(10);
    }

    @Test
    void clampsNegativeDeltaToMinAbsoluteValue() {
        PersonaStateDelta delta = new PersonaStateDelta(-25, -100, -11).clamped();
        assertThat(delta.trust()).isEqualTo(-10);
        assertThat(delta.interest()).isEqualTo(-10);
        assertThat(delta.patience()).isEqualTo(-10);
    }

    @Test
    void leavesInBoundsValuesUnchanged() {
        PersonaStateDelta delta = new PersonaStateDelta(3, -7, 0).clamped();
        assertThat(delta.trust()).isEqualTo(3);
        assertThat(delta.interest()).isEqualTo(-7);
        assertThat(delta.patience()).isEqualTo(0);
    }
}
