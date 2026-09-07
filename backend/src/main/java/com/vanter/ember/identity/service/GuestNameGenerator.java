package com.vanter.ember.identity.service;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** Friendly "Animal Adjetivo" display name for a guest diner who joins a table without an account. */
@Component
public class GuestNameGenerator {

    private static final String[] ANIMALS = {
            "Puma", "Zorro", "Búho", "Lince", "Halcón", "Nutria", "Tejón", "Colibrí",
            "Jaguar", "Garza", "Erizo", "Alce", "Tucán", "Foca", "Ardilla", "Mapache"
    };

    private static final String[] ADJECTIVES = {
            "Veloz", "Sereno", "Curioso", "Astuto", "Sagaz", "Tranquilo", "Alegre", "Ágil",
            "Noble", "Valiente", "Sutil", "Radiante", "Amable", "Osado", "Gentil", "Vivaz"
    };

    public String next() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return ANIMALS[r.nextInt(ANIMALS.length)] + " " + ADJECTIVES[r.nextInt(ADJECTIVES.length)];
    }
}
