package org.ascencio.demoapp;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Movimiento(
        int id,
        int empresaId,
        int cuentaId,
        LocalDate fecha,
        String concepto,
        BigDecimal debe,
        BigDecimal haber,
        int numPartida
) {}