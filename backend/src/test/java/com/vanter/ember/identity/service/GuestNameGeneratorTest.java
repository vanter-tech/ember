package com.vanter.ember.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;

class GuestNameGeneratorTest {

    private final GuestNameGenerator generator = new GuestNameGenerator();

    @RepeatedTest(50)
    void next_returnsTwoNonBlankWordsWithinLength() {
        String name = generator.next();
        assertThat(name).isNotBlank();
        assertThat(name.length()).isLessThanOrEqualTo(30);
        assertThat(name.split(" ")).hasSize(2);
        assertThat(name).matches("[A-Za-zÁÉÍÓÚÑáéíóúñ]+ [A-Za-zÁÉÍÓÚÑáéíóúñ]+");
    }
}
