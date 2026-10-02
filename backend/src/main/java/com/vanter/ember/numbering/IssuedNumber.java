package com.vanter.ember.numbering;

/** A freshly issued document number and the printable code built from it (prefix frozen at first use). */
public record IssuedNumber(int number, String code) {
}
