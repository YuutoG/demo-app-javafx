package org.ascencio.demoapp;

public record Cuenta(
        int id,
        int empresaId,
        String codigo,
        String nombre,
        TipoCuenta tipo
) {}