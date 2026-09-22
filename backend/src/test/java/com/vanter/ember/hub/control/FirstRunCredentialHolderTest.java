package com.vanter.ember.hub.control;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FirstRunCredentialHolderTest {

    @Test
    void get_returnsNullWhenNothingSet() {
        FirstRunCredentialHolder holder = new FirstRunCredentialHolder();

        assertThat(holder.get()).isNull();
    }

    @Test
    void set_thenGet_returnsWhatWasSet() {
        FirstRunCredentialHolder holder = new FirstRunCredentialHolder();

        holder.set("owner@tenant-grill.local", "temp-pw-123");

        assertThat(holder.get()).isEqualTo(
                new FirstRunCredentialHolder.Credential("owner@tenant-grill.local", "temp-pw-123"));
    }

    @Test
    void clear_removesTheSetCredential() {
        FirstRunCredentialHolder holder = new FirstRunCredentialHolder();
        holder.set("owner@tenant-grill.local", "temp-pw-123");

        holder.clear();

        assertThat(holder.get()).isNull();
    }

    @Test
    void set_overwritesAPreviousValue() {
        FirstRunCredentialHolder holder = new FirstRunCredentialHolder();
        holder.set("first@tenant-grill.local", "first-pw");

        holder.set("second@tenant-grill.local", "second-pw");

        assertThat(holder.get()).isEqualTo(
                new FirstRunCredentialHolder.Credential("second@tenant-grill.local", "second-pw"));
    }
}
